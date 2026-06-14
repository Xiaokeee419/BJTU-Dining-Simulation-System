package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.common.BadRequestException;
import com.bjtu.dining.common.ResourceNotFoundException;
import com.bjtu.dining.recommendation.dto.DiversionComparisonRequest;
import com.bjtu.dining.recommendation.dto.DiversionComparisonResult;
import com.bjtu.dining.recommendation.dto.DiversionStrategyParameters;
import com.bjtu.dining.recommendation.dto.OptimizationBestResult;
import com.bjtu.dining.recommendation.dto.OptimizationBottleneckMetrics;
import com.bjtu.dining.recommendation.dto.OptimizationIterationItem;
import com.bjtu.dining.recommendation.dto.OptimizationJobResult;
import com.bjtu.dining.recommendation.dto.OptimizationRunRequest;
import com.bjtu.dining.recommendation.model.EvaluationMetrics;
import com.bjtu.dining.taska.model.TaskADtos;
import com.bjtu.dining.taska.service.SimulationService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OptimizationService {
    private static final int MAX_STORED_JOBS = 80;
    private static final int OVERLOAD_THRESHOLD = 10;
    private static final int EXTREME_OVERLOAD_THRESHOLD = 15;
    private static final Logger log = LoggerFactory.getLogger(OptimizationService.class);
    private static final Set<String> VALID_CROWD_LEVELS = Set.of("IDLE", "NORMAL", "BUSY", "EXTREME");

    private final RecommendationService recommendationService;
    private final SimulationService simulationService;
    private final Map<String, OptimizationState> jobs = new ConcurrentHashMap<>();
    private final Deque<String> jobOrder = new ConcurrentLinkedDeque<>();
    private final Object jobStoreLock = new Object();

    public OptimizationService(
            RecommendationService recommendationService,
            SimulationService simulationService
    ) {
        this.recommendationService = recommendationService;
        this.simulationService = simulationService;
    }

    public OptimizationJobResult run(OptimizationRunRequest request) {
        simulationService.getRunResult(request.baseRunId());
        int totalIterations = request.iterationCount() == null ? 16 : request.iterationCount();
        if (totalIterations < 1 || totalIterations > 200) {
            throw new BadRequestException("iterationCount", "iterationCount must be between 1 and 200");
        }
        String targetCrowdLevel = normalizeCrowdLevel(request.targetCrowdLevel());
        DiversionStrategyParameters initial =
                DiversionStrategyParameters.resolve(request.initialParameters());
        String taskId = UUID.randomUUID().toString().replace("-", "");
        OptimizationState state = new OptimizationState(
                taskId,
                request.baseRunId(),
                totalIterations,
                initial
        );
        jobs.put(taskId, state);
        trackJob(taskId);
        trimCompletedJobs();

        long randomSeed = request.randomSeed() == null ? System.currentTimeMillis() : request.randomSeed();
        CompletableFuture.runAsync(() -> execute(
                state,
                request.minute(),
                targetCrowdLevel,
                randomSeed
        ));
        return state.snapshot();
    }

    public OptimizationJobResult getJob(String taskId) {
        return state(taskId).snapshot();
    }

    public List<OptimizationIterationItem> getIterations(String taskId) {
        return state(taskId).iterations();
    }

    public OptimizationBestResult getBest(String taskId) {
        OptimizationState state = state(taskId);
        CandidateEvaluation best = state.best();
        if (best == null) {
            throw new ResourceNotFoundException("optimization best result not found");
        }
        return new OptimizationBestResult(
                taskId,
                best.loss(),
                best.compareRunId(),
                best.parameters(),
                best.metrics(),
                best.bottleneckMetrics()
        );
    }

    private void execute(
            OptimizationState state,
            Integer minute,
            String targetCrowdLevel,
            long randomSeed
    ) {
        try {
            state.markRunning();
            Random random = new Random(randomSeed);
            DiversionStrategyParameters currentParameters = state.initialParameters();
            CandidateEvaluation currentEvaluation = null;
            CandidateEvaluation bestEvaluation = null;
            List<Long> referenceSourceWindowIds = List.of();
            int stagnantIterations = 0;

            for (int iteration = 1; iteration <= state.totalIterations(); iteration++) {
                double temperature = temperature(iteration, state.totalIterations());
                DiversionStrategyParameters candidateParameters = candidateParameters(
                        iteration,
                        state.initialParameters(),
                        currentParameters,
                        bestEvaluation,
                        temperature,
                        stagnantIterations,
                        random
                );
                CandidateEvaluation candidate = evaluate(
                        state.baseRunId(),
                        minute,
                        targetCrowdLevel,
                        candidateParameters,
                        referenceSourceWindowIds
                );
                if (iteration == 1) {
                    referenceSourceWindowIds = candidate.sourceWindowIds();
                }

                double acceptanceScale = acceptanceScale(
                        iteration,
                        currentEvaluation,
                        temperature
                );
                boolean accepted = currentEvaluation == null
                        || candidate.loss() <= currentEvaluation.loss()
                        || random.nextDouble() < Math.exp(
                                (currentEvaluation.loss() - candidate.loss()) / acceptanceScale
                        );
                boolean acceptedWorse = accepted
                        && currentEvaluation != null
                        && candidate.loss() > currentEvaluation.loss();
                if (accepted) {
                    currentParameters = candidate.parameters();
                    currentEvaluation = candidate;
                }
                boolean becameBest = bestEvaluation == null || candidate.loss() < bestEvaluation.loss();
                if (becameBest) {
                    bestEvaluation = candidate;
                    stagnantIterations = 0;
                } else {
                    stagnantIterations++;
                }

                state.addIteration(
                        new OptimizationIterationItem(
                                iteration,
                                round(temperature),
                                candidate.loss(),
                                accepted,
                                becameBest,
                                acceptedWorse,
                                candidate.compareRunId(),
                                candidate.parameters(),
                                currentParameters,
                                candidate.metrics(),
                                candidate.bottleneckMetrics(),
                                candidate.suggestionCount()
                        ),
                        currentEvaluation,
                        bestEvaluation,
                        temperature
                );
            }
            state.markCompleted("Optimization finished");
            trimCompletedJobs();
        } catch (RuntimeException ex) {
            log.error("Optimization task {} failed", state.taskId(), ex);
            state.markFailed(ex.getMessage() == null ? "Optimization failed" : ex.getMessage());
            trimCompletedJobs();
        }
    }

    private CandidateEvaluation evaluate(
            Long baseRunId,
            Integer minute,
            String targetCrowdLevel,
            DiversionStrategyParameters parameters,
            List<Long> referenceSourceWindowIds
    ) {
        TaskADtos.SimulationRunResult baseRun = simulationService.getRunResult(baseRunId);
        DiversionComparisonResult result = recommendationService.runDiversionComparison(
                new DiversionComparisonRequest(
                        baseRunId,
                        minute,
                        targetCrowdLevel,
                        true,
                        parameters
                )
        );
        EvaluationMetrics compare = result.compareMetrics() == null
                ? result.baseMetrics()
                : result.compareMetrics();
        int suggestionCount = result.diversionResult() == null
                ? 0
                : result.diversionResult().suggestions().size();
        List<Long> candidateSourceWindowIds = result.diversionResult() == null
                ? List.of()
                : result.diversionResult().suggestions().stream()
                        .map(item -> item.fromWindowId())
                        .distinct()
                        .toList();
        List<Long> sourceWindowIds = referenceSourceWindowIds == null || referenceSourceWindowIds.isEmpty()
                ? candidateSourceWindowIds
                : referenceSourceWindowIds;
        List<Long> targetWindowIds = result.diversionResult() == null
                ? List.of()
                : result.diversionResult().suggestions().stream()
                        .map(item -> item.toWindowId())
                        .distinct()
                        .toList();
        TaskADtos.SimulationRunResult compareRun = result.compareRunId() == null
                ? baseRun
                : simulationService.getRunResult(result.compareRunId());
        OptimizationBottleneckMetrics bottleneckMetrics = buildBottleneckMetrics(
                compareRun,
                result.minute(),
                sourceWindowIds,
                targetWindowIds,
                compare.unservedUserCount()
        );
        OptimizationBottleneckMetrics baselineMetrics = buildBottleneckMetrics(
                baseRun,
                result.minute(),
                sourceWindowIds,
                targetWindowIds,
                result.baseMetrics().unservedUserCount()
        );
        double loss = calculateLoss(compare, baselineMetrics, bottleneckMetrics, suggestionCount);
        return new CandidateEvaluation(
                DiversionStrategyParameters.resolve(parameters),
                loss,
                result.compareRunId(),
                compare,
                bottleneckMetrics,
                suggestionCount,
                List.copyOf(sourceWindowIds)
        );
    }

    private OptimizationBottleneckMetrics buildBottleneckMetrics(
            TaskADtos.SimulationRunResult run,
            int minute,
            List<Long> sourceWindowIds,
            List<Long> targetWindowIds,
            int unservedUserCount
    ) {
        TaskADtos.SimulationTimePoint comparisonPoint = timePointAt(run, minute);
        Integer sourceQueueTotal = null;
        Double sourceAverageWait = null;
        if (comparisonPoint != null && !sourceWindowIds.isEmpty()) {
            Set<Long> sourceWindowIdSet = Set.copyOf(sourceWindowIds);
            List<TaskADtos.QueueState> sourceWindows = comparisonPoint.restaurants().stream()
                    .flatMap(restaurant -> restaurant.windows().stream())
                    .filter(window -> sourceWindowIdSet.contains(window.windowId()))
                    .toList();
            if (!sourceWindows.isEmpty()) {
                sourceQueueTotal = sourceWindows.stream()
                        .mapToInt(TaskADtos.QueueState::queueLength)
                        .sum();
                sourceAverageWait = round(sourceWindows.stream()
                        .mapToDouble(TaskADtos.QueueState::waitMinutes)
                        .average()
                        .orElse(0.0));
            }
        }

        int maxSingleWindowQueue = 0;
        int peakTotalQueue = 0;
        int totalOverload = 0;
        int extremeOverloadSeverity = 0;
        int targetWindowOverload = 0;
        double loadImbalancePenalty = 0.0;
        Set<Long> targetWindowIdSet = Set.copyOf(targetWindowIds);
        for (TaskADtos.SimulationTimePoint point : run.timePoints()) {
            int pointTotalQueue = 0;
            for (TaskADtos.RestaurantTimePoint restaurant : point.restaurants()) {
                double averageRestaurantQueue = restaurant.windows().stream()
                        .mapToInt(TaskADtos.QueueState::queueLength)
                        .average()
                        .orElse(0.0);
                for (TaskADtos.QueueState window : restaurant.windows()) {
                    int queueLength = window.queueLength();
                    pointTotalQueue += queueLength;
                    maxSingleWindowQueue = Math.max(maxSingleWindowQueue, queueLength);
                    totalOverload += Math.max(queueLength - OVERLOAD_THRESHOLD, 0);
                    int extremeExcess = Math.max(queueLength - EXTREME_OVERLOAD_THRESHOLD, 0);
                    extremeOverloadSeverity += extremeExcess * extremeExcess;
                    if (targetWindowIdSet.contains(window.windowId())) {
                        targetWindowOverload += Math.max(queueLength - OVERLOAD_THRESHOLD, 0);
                    }
                    double deviation = queueLength - averageRestaurantQueue;
                    loadImbalancePenalty += deviation * deviation;
                }
            }
            peakTotalQueue = Math.max(peakTotalQueue, pointTotalQueue);
        }

        return new OptimizationBottleneckMetrics(
                sourceQueueTotal,
                sourceAverageWait,
                maxSingleWindowQueue,
                peakTotalQueue,
                totalOverload,
                extremeOverloadSeverity,
                targetWindowOverload,
                round(loadImbalancePenalty),
                unservedUserCount
        );
    }

    private double calculateLoss(
            EvaluationMetrics compare,
            OptimizationBottleneckMetrics baseline,
            OptimizationBottleneckMetrics bottleneck,
            int suggestionCount
    ) {
        double sourceQueue = bottleneck.sourceWindowQueueTotal() == null
                ? 0.0
                : bottleneck.sourceWindowQueueTotal();
        double sourceWait = bottleneck.sourceWindowAverageWait() == null
                ? 0.0
                : bottleneck.sourceWindowAverageWait();
        double baselineSourceQueue = baseline.sourceWindowQueueTotal() == null
                ? sourceQueue
                : baseline.sourceWindowQueueTotal();
        double baselineSourceWait = baseline.sourceWindowAverageWait() == null
                ? sourceWait
                : baseline.sourceWindowAverageWait();
        double sourceQueueRelief = Math.max(0.0, baselineSourceQueue - sourceQueue);
        double sourceWaitRelief = Math.max(0.0, baselineSourceWait - sourceWait);
        double loss = sourceQueue * 3.2
                + sourceWait * 7.0
                + bottleneck.maxSingleWindowQueue() * 5.0
                + bottleneck.peakTotalQueue() * 0.08
                + bottleneck.totalOverload() * 2.4
                + bottleneck.extremeOverloadSeverity() * 0.35
                + bottleneck.targetWindowOverload() * 1.4
                + bottleneck.loadImbalancePenalty() * 0.10
                + bottleneck.unservedUserCount() * 30.0
                + compare.avgWaitMinutes()
                + compare.maxWaitMinutes() * 0.20
                - sourceQueueRelief * 1.8
                - sourceWaitRelief * 4.0;
        if (suggestionCount == 0) {
            loss += 250.0;
        }
        return round(loss);
    }

    private DiversionStrategyParameters candidateParameters(
            int iteration,
            DiversionStrategyParameters initial,
            DiversionStrategyParameters current,
            CandidateEvaluation best,
            double temperature,
            int stagnantIterations,
            Random random
    ) {
        if (iteration == 1) {
            return initial;
        }
        if (iteration == 2) {
            return DiversionStrategyParameters.resolve(new DiversionStrategyParameters(
                    initial.sourcePressureScale(),
                    initial.targetPressureBufferScale(),
                    initial.transferScale(),
                    initial.maxTransferCount(),
                    0.35,
                    0.10,
                    1.2,
                    1.4,
                    6.0
            ));
        }
        if (iteration == 3) {
            return DiversionStrategyParameters.resolve(new DiversionStrategyParameters(
                    0.45,
                    2.2,
                    2.4,
                    160,
                    0.42,
                    0.115,
                    1.35,
                    1.8,
                    2.0
            ));
        }
        if (iteration == 4) {
            return DiversionStrategyParameters.resolve(new DiversionStrategyParameters(
                    0.55,
                    1.5,
                    1.9,
                    130,
                    0.30,
                    0.09,
                    1.65,
                    1.25,
                    4.0
            ));
        }
        if (iteration == 5) {
            return DiversionStrategyParameters.resolve(new DiversionStrategyParameters(
                    0.65,
                    2.4,
                    2.6,
                    180,
                    0.45,
                    0.12,
                    1.1,
                    2.1,
                    0.5
            ));
        }
        if (iteration > 12 && (stagnantIterations >= 8 || iteration % 15 == 0)) {
            return globalCandidate(random);
        }
        if (iteration > 12 && random.nextDouble() < 0.22) {
            return globalCandidate(random);
        }
        double bestCenterProbability = iteration <= 12 ? 0.72 : 0.48;
        DiversionStrategyParameters searchCenter = best != null
                && random.nextDouble() < bestCenterProbability
                ? best.parameters()
                : current;
        return perturb(searchCenter, temperature, random);
    }

    private DiversionStrategyParameters globalCandidate(Random random) {
        return DiversionStrategyParameters.resolve(new DiversionStrategyParameters(
                uniform(random, 0.35, 1.8),
                uniform(random, 0.3, 2.5),
                uniform(random, 0.25, 2.8),
                3 + random.nextInt(178),
                uniform(random, -0.3, 0.45),
                uniform(random, 0.003, 0.12),
                uniform(random, 0.5, 1.8),
                uniform(random, 0.5, 2.2),
                uniform(random, 0.0, 18.0)
        ));
    }

    private DiversionStrategyParameters perturb(
            DiversionStrategyParameters current,
            double temperature,
            Random random
    ) {
        double scale = Math.max(0.08, Math.min(1.8, temperature / 8.0));
        return DiversionStrategyParameters.resolve(new DiversionStrategyParameters(
                current.sourcePressureScale() + random.nextGaussian() * 0.22 * scale,
                current.targetPressureBufferScale() + random.nextGaussian() * 0.24 * scale,
                current.transferScale() + random.nextGaussian() * 0.24 * scale,
                current.maxTransferCount() + (int) Math.round(random.nextGaussian() * 18 * scale),
                current.acceptanceBias() + random.nextGaussian() * 0.07 * scale,
                current.waitReductionWeight() + random.nextGaussian() * 0.018 * scale,
                current.pressureWaitWeight() + random.nextGaussian() * 0.18 * scale,
                current.pressureQueueWeight() + random.nextGaussian() * 0.22 * scale,
                current.crossRestaurantPenalty() + random.nextGaussian() * 2.5 * scale
        ));
    }

    private double temperature(int iteration, int totalIterations) {
        if (totalIterations <= 1) {
            return 0.25;
        }
        double progress = (iteration - 1.0) / (totalIterations - 1.0);
        double baseTemperature = 8.0 * Math.pow(0.25 / 8.0, progress);
        if (iteration <= 12) {
            return baseTemperature;
        }
        int cycleLength = Math.max(12, Math.min(24, totalIterations / 4));
        int cyclePosition = (iteration - 13) % cycleLength;
        double reheatingPulse = cyclePosition == 0
                ? 4.5
                : 4.5 * Math.pow(Math.max(0.0, 1.0 - cyclePosition / 5.0), 2);
        return Math.max(baseTemperature, reheatingPulse);
    }

    private double acceptanceScale(
            int iteration,
            CandidateEvaluation current,
            double temperature
    ) {
        if (current == null || iteration <= 12) {
            return Math.max(0.05, temperature);
        }
        double relativeLossScale = Math.max(1.0, current.loss() * 0.035);
        return Math.max(0.25, relativeLossScale * Math.max(0.15, temperature / 8.0));
    }

    private double uniform(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private String normalizeCrowdLevel(String value) {
        String normalized = value == null || value.isBlank()
                ? "NORMAL"
                : value.trim().toUpperCase(Locale.ROOT);
        if (!VALID_CROWD_LEVELS.contains(normalized)) {
            throw new BadRequestException(
                    "targetCrowdLevel",
                    "targetCrowdLevel must be IDLE, NORMAL, BUSY or EXTREME"
            );
        }
        return normalized;
    }

    private OptimizationState state(String taskId) {
        return java.util.Optional.ofNullable(jobs.get(taskId))
                .orElseThrow(() -> new ResourceNotFoundException("optimization task not found"));
    }

    private void trackJob(String taskId) {
        synchronized (jobStoreLock) {
            jobOrder.remove(taskId);
            jobOrder.addLast(taskId);
        }
    }

    private void trimCompletedJobs() {
        synchronized (jobStoreLock) {
            int attempts = jobOrder.size();
            while (jobs.size() > MAX_STORED_JOBS && attempts-- > 0) {
                String oldestTaskId = jobOrder.pollFirst();
                if (oldestTaskId == null) {
                    break;
                }
                OptimizationState candidate = jobs.get(oldestTaskId);
                if (candidate == null) {
                    continue;
                }
                if (candidate.isTerminal()) {
                    jobs.remove(oldestTaskId);
                } else {
                    jobOrder.addLast(oldestTaskId);
                }
            }
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record CandidateEvaluation(
            DiversionStrategyParameters parameters,
            double loss,
            Long compareRunId,
            EvaluationMetrics metrics,
            OptimizationBottleneckMetrics bottleneckMetrics,
            int suggestionCount,
            List<Long> sourceWindowIds
    ) {
    }

    private TaskADtos.SimulationTimePoint timePointAt(TaskADtos.SimulationRunResult run, int minute) {
        return run.timePoints().stream()
                .filter(point -> point.minute() == minute)
                .findFirst()
                .orElse(null);
    }

    private static final class OptimizationState {
        private final String taskId;
        private final Long baseRunId;
        private final int totalIterations;
        private final DiversionStrategyParameters initialParameters;
        private final String startedAt = now();
        private final List<OptimizationIterationItem> iterations = new ArrayList<>();
        private String status = "PENDING";
        private int currentIteration;
        private double currentTemperature;
        private CandidateEvaluation current;
        private CandidateEvaluation best;
        private String completedAt;
        private String message = "Waiting to start";

        private OptimizationState(
                String taskId,
                Long baseRunId,
                int totalIterations,
                DiversionStrategyParameters initialParameters
        ) {
            this.taskId = taskId;
            this.baseRunId = baseRunId;
            this.totalIterations = totalIterations;
            this.initialParameters = initialParameters;
        }

        private synchronized void markRunning() {
            status = "RUNNING";
            message = "Optimization is running";
        }

        private synchronized void addIteration(
                OptimizationIterationItem item,
                CandidateEvaluation current,
                CandidateEvaluation best,
                double temperature
        ) {
            iterations.add(item);
            currentIteration = item.iteration();
            currentTemperature = temperature;
            this.current = current;
            this.best = best;
        }

        private synchronized void markCompleted(String message) {
            status = "COMPLETED";
            completedAt = now();
            this.message = message;
        }

        private synchronized void markFailed(String message) {
            status = "FAILED";
            completedAt = now();
            this.message = message;
        }

        private synchronized OptimizationJobResult snapshot() {
            return new OptimizationJobResult(
                    taskId,
                    baseRunId,
                    status,
                    totalIterations,
                    currentIteration,
                    currentTemperature,
                    current == null ? null : current.loss(),
                    best == null ? null : best.loss(),
                    initialParameters,
                    current == null ? initialParameters : current.parameters(),
                    best == null ? null : best.parameters(),
                    best == null ? null : best.compareRunId(),
                    startedAt,
                    completedAt,
                    message
            );
        }

        private synchronized List<OptimizationIterationItem> iterations() {
            return List.copyOf(iterations);
        }

        private synchronized CandidateEvaluation best() {
            return best;
        }

        private synchronized boolean isTerminal() {
            return "COMPLETED".equals(status) || "FAILED".equals(status);
        }

        private String taskId() {
            return taskId;
        }

        private Long baseRunId() {
            return baseRunId;
        }

        private int totalIterations() {
            return totalIterations;
        }

        private DiversionStrategyParameters initialParameters() {
            return initialParameters;
        }

        private static String now() {
            return OffsetDateTime.now(ZoneOffset.ofHours(8)).toString();
        }
    }
}
