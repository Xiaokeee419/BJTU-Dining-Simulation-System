package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.common.BadRequestException;
import com.bjtu.dining.common.ResourceNotFoundException;
import com.bjtu.dining.recommendation.dto.DiversionComparisonRequest;
import com.bjtu.dining.recommendation.dto.DiversionComparisonResult;
import com.bjtu.dining.recommendation.dto.DiversionStrategyParameters;
import com.bjtu.dining.recommendation.dto.OptimizationBestResult;
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
        if (totalIterations < 1 || totalIterations > 60) {
            throw new BadRequestException("iterationCount", "iterationCount must be between 1 and 60");
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
                best.metrics()
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

            for (int iteration = 1; iteration <= state.totalIterations(); iteration++) {
                double temperature = temperature(iteration, state.totalIterations());
                DiversionStrategyParameters candidateParameters = iteration == 1
                        ? currentParameters
                        : perturb(currentParameters, temperature, random);
                CandidateEvaluation candidate = evaluate(
                        state.baseRunId(),
                        minute,
                        targetCrowdLevel,
                        candidateParameters
                );

                boolean accepted = currentEvaluation == null
                        || candidate.loss() <= currentEvaluation.loss()
                        || random.nextDouble() < Math.exp(
                                (currentEvaluation.loss() - candidate.loss()) / Math.max(0.05, temperature)
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
            DiversionStrategyParameters parameters
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
        EvaluationMetrics base = result.baseMetrics();
        EvaluationMetrics compare = result.compareMetrics() == null ? base : result.compareMetrics();
        int suggestionCount = result.diversionResult() == null
                ? 0
                : result.diversionResult().suggestions().size();
        LocalLossContext localContext = buildLocalLossContext(baseRun, result);
        double loss = calculateLoss(base, compare, localContext, suggestionCount);
        return new CandidateEvaluation(
                DiversionStrategyParameters.resolve(parameters),
                loss,
                result.compareRunId(),
                compare,
                suggestionCount
        );
    }

    private double calculateLoss(
            EvaluationMetrics base,
            EvaluationMetrics compare,
            LocalLossContext localContext,
            int suggestionCount
    ) {
        double loss = (compare.avgWaitMinutes() - base.avgWaitMinutes()) * 1.5
                + (compare.maxWaitMinutes() - base.maxWaitMinutes()) * 0.8
                + (compare.maxQueueLength() - base.maxQueueLength()) * 1.2
                + (compare.busyWindowCount() - base.busyWindowCount()) * 2.5
                + (compare.extremeWindowCount() - base.extremeWindowCount()) * 5.0
                + (compare.unservedUserCount() - base.unservedUserCount()) * 1.2;
        if (localContext != null) {
            loss += (localContext.compareTopQueueSum() - localContext.baseTopQueueSum()) * 1.4;
            loss += (localContext.compareSourceQueueSum() - localContext.baseSourceQueueSum()) * 1.6;
            loss += (localContext.compareMaxWindowQueue() - localContext.baseMaxWindowQueue()) * 2.0;
            loss += (localContext.compareBusyWindows() - localContext.baseBusyWindows()) * 2.0;
            loss += (localContext.compareExtremeWindows() - localContext.baseExtremeWindows()) * 4.0;
            loss += (localContext.compareAvgWindowWait() - localContext.baseAvgWindowWait()) * 4.0;
        }
        if (suggestionCount == 0) {
            loss += 25.0;
        }
        return round(loss);
    }

    private LocalLossContext buildLocalLossContext(
            TaskADtos.SimulationRunResult baseRun,
            DiversionComparisonResult result
    ) {
        if (result.compareRunId() == null || result.diversionResult() == null) {
            return null;
        }
        TaskADtos.SimulationRunResult compareRun = simulationService.getRunResult(result.compareRunId());
        TaskADtos.SimulationTimePoint basePoint = timePointAt(baseRun, result.minute());
        TaskADtos.SimulationTimePoint comparePoint = timePointAt(compareRun, result.minute());
        if (basePoint == null || comparePoint == null) {
            return null;
        }

        List<Long> sourceWindowIds = result.diversionResult().suggestions().stream()
                .map(item -> item.fromWindowId())
                .distinct()
                .toList();
        return new LocalLossContext(
                topQueueSum(basePoint, 5),
                topQueueSum(comparePoint, 5),
                windowQueueSum(basePoint, sourceWindowIds),
                windowQueueSum(comparePoint, sourceWindowIds),
                maxWindowQueue(basePoint),
                maxWindowQueue(comparePoint),
                averageWindowWait(basePoint),
                averageWindowWait(comparePoint),
                busyWindowCount(basePoint),
                busyWindowCount(comparePoint),
                extremeWindowCount(basePoint),
                extremeWindowCount(comparePoint)
        );
    }

    private DiversionStrategyParameters perturb(
            DiversionStrategyParameters current,
            double temperature,
            Random random
    ) {
        double scale = Math.max(0.08, temperature / 8.0);
        return DiversionStrategyParameters.resolve(new DiversionStrategyParameters(
                current.sourcePressureScale() + random.nextGaussian() * 0.22 * scale,
                current.targetPressureBufferScale() + random.nextGaussian() * 0.24 * scale,
                current.transferScale() + random.nextGaussian() * 0.24 * scale,
                current.maxTransferCount() + (int) Math.round(random.nextGaussian() * 18 * scale),
                current.acceptanceBias() + random.nextGaussian() * 0.07 * scale,
                current.waitReductionWeight() + random.nextGaussian() * 0.012 * scale
        ));
    }

    private double temperature(int iteration, int totalIterations) {
        if (totalIterations <= 1) {
            return 0.25;
        }
        double progress = (iteration - 1.0) / (totalIterations - 1.0);
        return 8.0 * Math.pow(0.25 / 8.0, progress);
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
            int suggestionCount
    ) {
    }

    private record LocalLossContext(
            int baseTopQueueSum,
            int compareTopQueueSum,
            int baseSourceQueueSum,
            int compareSourceQueueSum,
            int baseMaxWindowQueue,
            int compareMaxWindowQueue,
            double baseAvgWindowWait,
            double compareAvgWindowWait,
            int baseBusyWindows,
            int compareBusyWindows,
            int baseExtremeWindows,
            int compareExtremeWindows
    ) {
    }

    private TaskADtos.SimulationTimePoint timePointAt(TaskADtos.SimulationRunResult run, int minute) {
        return run.timePoints().stream()
                .filter(point -> point.minute() == minute)
                .findFirst()
                .orElse(null);
    }

    private int topQueueSum(TaskADtos.SimulationTimePoint point, int limit) {
        return point.restaurants().stream()
                .flatMap(restaurant -> restaurant.windows().stream())
                .map(TaskADtos.QueueState::queueLength)
                .sorted(java.util.Comparator.reverseOrder())
                .limit(limit)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int windowQueueSum(TaskADtos.SimulationTimePoint point, List<Long> windowIds) {
        if (windowIds.isEmpty()) {
            return 0;
        }
        Set<Long> windowIdSet = Set.copyOf(windowIds);
        return point.restaurants().stream()
                .flatMap(restaurant -> restaurant.windows().stream())
                .filter(window -> windowIdSet.contains(window.windowId()))
                .mapToInt(TaskADtos.QueueState::queueLength)
                .sum();
    }

    private int maxWindowQueue(TaskADtos.SimulationTimePoint point) {
        return point.restaurants().stream()
                .flatMap(restaurant -> restaurant.windows().stream())
                .mapToInt(TaskADtos.QueueState::queueLength)
                .max()
                .orElse(0);
    }

    private double averageWindowWait(TaskADtos.SimulationTimePoint point) {
        return point.restaurants().stream()
                .flatMap(restaurant -> restaurant.windows().stream())
                .filter(window -> !"CLOSED".equals(window.status()))
                .mapToDouble(TaskADtos.QueueState::waitMinutes)
                .average()
                .orElse(0.0);
    }

    private int busyWindowCount(TaskADtos.SimulationTimePoint point) {
        return (int) point.restaurants().stream()
                .flatMap(restaurant -> restaurant.windows().stream())
                .filter(window -> "BUSY".equals(window.crowdLevel()))
                .count();
    }

    private int extremeWindowCount(TaskADtos.SimulationTimePoint point) {
        return (int) point.restaurants().stream()
                .flatMap(restaurant -> restaurant.windows().stream())
                .filter(window -> "EXTREME".equals(window.crowdLevel()))
                .count();
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
