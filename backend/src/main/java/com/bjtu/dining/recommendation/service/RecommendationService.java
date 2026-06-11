package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.common.ApiException;
import com.bjtu.dining.common.ResourceNotFoundException;
import com.bjtu.dining.common.TagNormalizationService;
import com.bjtu.dining.recommendation.dto.DiversionComparisonRequest;
import com.bjtu.dining.recommendation.dto.DiversionComparisonResult;
import com.bjtu.dining.recommendation.dto.DiversionRequest;
import com.bjtu.dining.recommendation.dto.DiversionResult;
import com.bjtu.dining.recommendation.dto.DiversionStrategyParameters;
import com.bjtu.dining.recommendation.dto.DiversionSuggestionItem;
import com.bjtu.dining.recommendation.dto.EstimatedSystemBenefit;
import com.bjtu.dining.recommendation.dto.LossConfigResponse;
import com.bjtu.dining.recommendation.dto.StrategyCompareRequest;
import com.bjtu.dining.recommendation.dto.StrategyComparisonResult;
import com.bjtu.dining.recommendation.dto.UserProfileRequest;
import com.bjtu.dining.recommendation.model.AdvancedMetrics;
import com.bjtu.dining.recommendation.model.EvaluationMetrics;
import com.bjtu.dining.recommendation.model.RestaurantSnapshot;
import com.bjtu.dining.recommendation.model.SimulationRunResult;
import com.bjtu.dining.recommendation.model.SimulationTimePoint;
import com.bjtu.dining.recommendation.model.WindowParameter;
import com.bjtu.dining.recommendation.model.WindowSnapshot;
import com.bjtu.dining.recommendation.repository.CsvSeedRepository;
import com.bjtu.dining.taska.model.TaskADtos;
import com.bjtu.dining.taska.service.SimulationService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final Set<String> VALID_USER_TYPES = Set.of(
            "STUDENT",
            "FACULTY",
            "VISITOR",
            "HURRY",
            "BUDGET_SENSITIVE"
    );
    private static final Set<String> VALID_CROWD_LEVELS = Set.of("IDLE", "NORMAL", "BUSY", "EXTREME");
    private static final Set<String> VALID_COMPARE_TYPES = Set.of("SCENARIO", "RECOMMENDATION_AFTER", "DIVERSION_AFTER");

    private final CsvSeedRepository seedRepository;
    private final SimulationProvider simulationProvider;
    private final SimulationService taskASimulationService;
    private final TagNormalizationService tagNormalizationService;

    public RecommendationService(
            CsvSeedRepository seedRepository,
            SimulationProvider simulationProvider,
            SimulationService taskASimulationService,
            TagNormalizationService tagNormalizationService
    ) {
        this.seedRepository = seedRepository;
        this.simulationProvider = simulationProvider;
        this.taskASimulationService = taskASimulationService;
        this.tagNormalizationService = tagNormalizationService;
    }

    public DiversionStrategyParameters defaultStrategyParameters() {
        return DiversionStrategyParameters.defaults();
    }

    public LossConfigResponse lossConfig() {
        return LossConfigResponse.defaults();
    }

    public DiversionResult generateDiversion(DiversionRequest request) {
        String targetCrowdLevel = validateCrowdLevel(request.targetCrowdLevel());
        UserProfileRequest profile = request.profile();
        String userType = profile == null ? "STUDENT" : validateProfile(profile);
        DiversionStrategySupport.ResolvedParameters strategy =
                DiversionStrategySupport.resolve(request.strategyParameters());
        DiversionStrategyParameters strategySnapshot = DiversionStrategySupport.snapshot(strategy);

        SimulationRunResult simulation = loadFinishedSimulation(request.runId(), "Cannot generate diversion suggestions");
        SimulationTimePoint timePoint = selectTimePoint(simulation, request.minute());

        List<WindowDiversionCandidate> targets = findTargetWindows(timePoint, strategy);
        List<WindowDiversionCandidate> sources = findSourceWindows(targets, targetCrowdLevel, strategy);
        if (sources.isEmpty()) {
            return emptyDiversionResult(
                    request.runId(),
                    timePoint.minute(),
                    "No source window exceeds the current diversion threshold.",
                    strategySnapshot
            );
        }
        if (targets.isEmpty()) {
            return emptyDiversionResult(
                    request.runId(),
                    timePoint.minute(),
                    "Overloaded windows exist, but there is no viable low-pressure target window.",
                    strategySnapshot
            );
        }

        List<DiversionSuggestionItem> suggestions = new ArrayList<>();
        Map<Long, Integer> reservedTargetLoad = new HashMap<>();
        for (WindowDiversionCandidate source : sources) {
            Optional<TargetCandidate> target = selectTargetCandidate(
                    source,
                    targets,
                    targetCrowdLevel,
                    reservedTargetLoad,
                    strategy
            );
            if (target.isEmpty()) {
                continue;
            }

            TargetCandidate selectedTarget = target.get();
            int suggestedUserCount = suggestedUserCount(
                    source,
                    selectedTarget.candidate(),
                    selectedTarget.remainingCapacity(),
                    strategy
            );
            if (suggestedUserCount <= 0) {
                continue;
            }

            double acceptanceRate = acceptanceRate(source, selectedTarget.candidate(), profile, userType, strategy);
            int estimatedAcceptedCount = Math.min(
                    suggestedUserCount,
                    Math.max(1, (int) Math.round(suggestedUserCount * acceptanceRate))
            );
            double tagSimilarity = roundTwo(windowTagSimilarity(source, selectedTarget.candidate()) / 100.0);
            double estimatedWaitReduction = roundOne(
                    estimatedWaitReduction(source, selectedTarget.candidate(), estimatedAcceptedCount)
            );

            reservedTargetLoad.merge(selectedTarget.windowId(), estimatedAcceptedCount, Integer::sum);
            suggestions.add(new DiversionSuggestionItem(
                    source.restaurantId(),
                    source.windowId(),
                    selectedTarget.restaurantId(),
                    selectedTarget.windowId(),
                    suggestedUserCount,
                    roundTwo(acceptanceRate),
                    estimatedAcceptedCount,
                    estimatedWaitReduction,
                    tagSimilarity,
                    diversionReason(source, selectedTarget.candidate(), acceptanceRate, estimatedWaitReduction, tagSimilarity)
            ));
            if (suggestions.size() >= 10) {
                break;
            }
        }

        EstimatedSystemBenefit benefit = estimateSystemBenefit(suggestions);
        String reason = suggestions.isEmpty()
                ? "Overloaded windows exist, but no target can safely absorb the diverted flow."
                : "Generated " + suggestions.size() + " diversion suggestions under target level " + targetCrowdLevel + ".";
        return new DiversionResult(request.runId(), timePoint.minute(), suggestions, reason, strategySnapshot, benefit);
    }

    public DiversionComparisonResult runDiversionComparison(DiversionComparisonRequest request) {
        TaskADtos.SimulationRunResult baseRun = loadFinishedTaskARun(request.baseRunId(), "Cannot compare diversion strategies");
        int resolvedMinute = request.minute() != null
                ? request.minute()
                : resolvePeakMinute(mapSimulation(baseRun));
        String targetCrowdLevel = validateCrowdLevel(defaultCrowdLevel(request.targetCrowdLevel()));
        DiversionResult diversionResult = generateDiversion(new DiversionRequest(
                request.baseRunId(),
                resolvedMinute,
                targetCrowdLevel,
                toUserProfileRequest(baseRun.profile()),
                request.strategyParameters()
        ));

        EvaluationMetrics baseMetrics = mapMetrics(baseRun.metrics());
        AdvancedMetrics baseAdvancedMetrics = taskASimulationService.advancedMetrics(baseRun.runId());
        boolean autoRunCompare = request.autoRunCompare() == null || request.autoRunCompare();
        if (diversionResult.suggestions().isEmpty()) {
            return new DiversionComparisonResult(
                    request.baseRunId(),
                    null,
                    resolvedMinute,
                    diversionResult,
                    null,
                    baseMetrics,
                    null,
                    baseAdvancedMetrics,
                    null,
                    "NO_SUGGESTION"
            );
        }
        if (!autoRunCompare) {
            return new DiversionComparisonResult(
                    request.baseRunId(),
                    null,
                    resolvedMinute,
                    diversionResult,
                    null,
                    baseMetrics,
                    null,
                    baseAdvancedMetrics,
                    null,
                    "READY"
            );
        }

        TaskADtos.SimulationRunResult compareRun = taskASimulationService.runSimulationWithDiversion(
                new TaskADtos.SimulationRunWithDiversionRequest(
                        request.baseRunId(),
                        resolvedMinute,
                        targetCrowdLevel,
                        diversionResult.suggestions().stream().map(this::toTaskADiversionSuggestion).toList(),
                        diversionResult.strategySnapshot()
                )
        );
        StrategyComparisonResult comparison = compareStrategies(new StrategyCompareRequest(
                request.baseRunId(),
                compareRun.runId(),
                "DIVERSION_AFTER"
        ));
        return new DiversionComparisonResult(
                request.baseRunId(),
                compareRun.runId(),
                resolvedMinute,
                diversionResult,
                comparison,
                baseMetrics,
                mapMetrics(compareRun.metrics()),
                baseAdvancedMetrics,
                taskASimulationService.advancedMetrics(compareRun.runId()),
                "COMPLETED"
        );
    }

    public StrategyComparisonResult compareStrategies(StrategyCompareRequest request) {
        String compareType = resolveCompareType(request.compareType());
        SimulationRunResult baseSimulation = loadFinishedSimulation(request.baseRunId(), "Cannot compare strategies");
        SimulationRunResult compareSimulation = loadFinishedSimulation(request.compareRunId(), "Cannot compare strategies");
        EvaluationMetrics baseMetrics = baseSimulation.metrics();
        EvaluationMetrics compareMetrics = compareSimulation.metrics();
        if (baseMetrics == null || compareMetrics == null) {
            throw new ApiException(40400, "Simulation metrics are unavailable", HttpStatus.NOT_FOUND);
        }

        double avgWaitDelta = roundOne(compareMetrics.avgWaitMinutes() - baseMetrics.avgWaitMinutes());
        double maxWaitDelta = roundOne(compareMetrics.maxWaitMinutes() - baseMetrics.maxWaitMinutes());
        int maxQueueDelta = compareMetrics.maxQueueLength() - baseMetrics.maxQueueLength();
        int busyWindowCountDelta = compareMetrics.busyWindowCount() - baseMetrics.busyWindowCount();
        int extremeWindowCountDelta = compareMetrics.extremeWindowCount() - baseMetrics.extremeWindowCount();
        int servedUserCountDelta = compareMetrics.servedUserCount() - baseMetrics.servedUserCount();
        int unservedUserCountDelta = compareMetrics.unservedUserCount() - baseMetrics.unservedUserCount();

        return new StrategyComparisonResult(
                request.baseRunId(),
                request.compareRunId(),
                compareType,
                avgWaitDelta,
                maxWaitDelta,
                maxQueueDelta,
                busyWindowCountDelta,
                extremeWindowCountDelta,
                servedUserCountDelta,
                unservedUserCountDelta,
                buildComparisonConclusion(
                        compareType,
                        avgWaitDelta,
                        maxWaitDelta,
                        maxQueueDelta,
                        busyWindowCountDelta,
                        extremeWindowCountDelta,
                        servedUserCountDelta,
                        unservedUserCountDelta
                )
        );
    }

    private DiversionResult emptyDiversionResult(
            Long runId,
            int minute,
            String reason,
            DiversionStrategyParameters strategySnapshot
    ) {
        return new DiversionResult(
                runId,
                minute,
                List.of(),
                reason,
                strategySnapshot,
                new EstimatedSystemBenefit(0, 0, 0, 0.0, 0.0, 0.0)
        );
    }

    private EstimatedSystemBenefit estimateSystemBenefit(List<DiversionSuggestionItem> suggestions) {
        int suggestedCount = suggestions.stream().mapToInt(DiversionSuggestionItem::suggestedUserCount).sum();
        int acceptedCount = suggestions.stream().mapToInt(DiversionSuggestionItem::estimatedAcceptedCount).sum();
        int crossRestaurantCount = (int) suggestions.stream()
                .filter(item -> !item.fromRestaurantId().equals(item.toRestaurantId()))
                .count();
        double totalWaitReduction = suggestions.stream()
                .mapToDouble(item -> item.estimatedWaitReduction() * item.estimatedAcceptedCount())
                .sum();
        double avgWaitReduction = acceptedCount <= 0 ? 0.0 : totalWaitReduction / acceptedCount;
        double pressureRelease = suggestions.stream()
                .mapToDouble(item -> item.estimatedAcceptedCount() * (0.6 + item.estimatedWaitReduction() * 0.12))
                .sum();
        return new EstimatedSystemBenefit(
                suggestedCount,
                acceptedCount,
                crossRestaurantCount,
                roundOne(avgWaitReduction),
                roundOne(totalWaitReduction),
                roundOne(pressureRelease)
        );
    }

    private SimulationTimePoint selectTimePoint(SimulationRunResult simulation, Integer minute) {
        if (minute == null) {
            return resolvePeakTimePoint(simulation);
        }
        if (minute < 0) {
            throw new ApiException(40001, "Invalid parameter", HttpStatus.BAD_REQUEST,
                    Map.of("field", "minute", "reason", "minute must be greater than or equal to 0"));
        }
        return simulation.timePoints().stream()
                .filter(point -> point.minute() == minute)
                .findFirst()
                .orElseThrow(() -> new ApiException(40001, "Invalid parameter", HttpStatus.BAD_REQUEST,
                        Map.of("field", "minute", "reason", "minute does not exist in the simulation timeline")));
    }

    private SimulationRunResult loadFinishedSimulation(Long runId, String operationText) {
        if (runId == null || runId <= 0) {
            throw new ApiException(40401, "Simulation run not found", HttpStatus.NOT_FOUND);
        }
        SimulationRunResult simulation = simulationProvider.findByRunId(runId);
        if (!"FINISHED".equals(simulation.status())) {
            throw new ApiException(40901, operationText + ": simulation is not finished", HttpStatus.CONFLICT);
        }
        return simulation;
    }

    private TaskADtos.SimulationRunResult loadFinishedTaskARun(Long runId, String operationText) {
        try {
            TaskADtos.SimulationRunResult simulation = taskASimulationService.getRunResult(runId);
            if (!"FINISHED".equals(simulation.status())) {
                throw new ApiException(40901, operationText + ": simulation is not finished", HttpStatus.CONFLICT);
            }
            return simulation;
        } catch (ApiException ex) {
            throw ex;
        } catch (ResourceNotFoundException ex) {
            throw new ApiException(40401, "Simulation run not found", HttpStatus.NOT_FOUND);
        } catch (RuntimeException ex) {
            log.error("Failed to load Task A run {}, operation={}", runId, operationText, ex);
            throw new ApiException(50001, "Failed to load simulation result", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private SimulationTimePoint resolvePeakTimePoint(SimulationRunResult simulation) {
        return simulation.timePoints().stream()
                .max(Comparator
                        .comparingInt(this::totalQueueLength)
                        .thenComparingInt(this::totalCurrentCount)
                        .thenComparingInt(SimulationTimePoint::minute))
                .orElseThrow(() -> new ApiException(40401, "Simulation run not found", HttpStatus.NOT_FOUND));
    }

    private int resolvePeakMinute(SimulationRunResult simulation) {
        return resolvePeakTimePoint(simulation).minute();
    }

    private int totalQueueLength(SimulationTimePoint timePoint) {
        return timePoint.restaurants().stream()
                .flatMap(restaurant -> restaurant.windows().stream())
                .mapToInt(WindowSnapshot::queueLength)
                .sum();
    }

    private int totalCurrentCount(SimulationTimePoint timePoint) {
        return timePoint.restaurants().stream()
                .mapToInt(RestaurantSnapshot::currentCount)
                .sum();
    }

    private SimulationRunResult mapSimulation(TaskADtos.SimulationRunResult source) {
        return new SimulationRunResult(
                source.runId(),
                source.status(),
                source.timePoints().stream().map(this::mapTimePoint).toList(),
                mapMetrics(source.metrics()),
                parseCreatedAt(source.createdAt())
        );
    }

    private SimulationTimePoint mapTimePoint(TaskADtos.SimulationTimePoint source) {
        return new SimulationTimePoint(
                source.minute(),
                source.restaurants().stream().map(this::mapRestaurant).toList()
        );
    }

    private RestaurantSnapshot mapRestaurant(TaskADtos.RestaurantTimePoint source) {
        return new RestaurantSnapshot(
                source.restaurantId(),
                source.name(),
                source.currentCount(),
                source.capacity(),
                source.crowdLevel(),
                source.windows().stream().map(this::mapWindow).toList()
        );
    }

    private WindowSnapshot mapWindow(TaskADtos.QueueState source) {
        return new WindowSnapshot(
                source.windowId(),
                source.name(),
                source.queueLength(),
                source.servingCount(),
                (int) Math.round(source.waitMinutes()),
                source.crowdLevel(),
                source.status()
        );
    }

    private EvaluationMetrics mapMetrics(TaskADtos.EvaluationMetrics source) {
        return new EvaluationMetrics(
                source.avgWaitMinutes(),
                source.maxWaitMinutes(),
                source.maxQueueLength(),
                source.busyWindowCount(),
                source.extremeWindowCount(),
                source.totalVirtualUsers(),
                source.servedUserCount(),
                source.unservedUserCount()
        );
    }

    private OffsetDateTime parseCreatedAt(String createdAt) {
        try {
            return OffsetDateTime.parse(createdAt);
        } catch (RuntimeException ignored) {
            return OffsetDateTime.now();
        }
    }

    private UserProfileRequest toUserProfileRequest(TaskADtos.UserProfile profile) {
        return new UserProfileRequest(
                profile.userType(),
                profile.tasteTags(),
                profile.budgetMin(),
                profile.budgetMax(),
                profile.waitingToleranceMinutes()
        );
    }

    private TaskADtos.DiversionSuggestion toTaskADiversionSuggestion(DiversionSuggestionItem item) {
        return new TaskADtos.DiversionSuggestion(
                item.fromRestaurantId(),
                item.fromWindowId(),
                item.toRestaurantId(),
                item.toWindowId(),
                item.suggestedUserCount(),
                item.acceptanceRate(),
                item.estimatedAcceptedCount()
        );
    }

    private String defaultCrowdLevel(String crowdLevel) {
        if (crowdLevel == null || crowdLevel.isBlank()) {
            return "NORMAL";
        }
        return crowdLevel;
    }

    private String buildComparisonConclusion(
            String compareType,
            double avgWaitDelta,
            double maxWaitDelta,
            int maxQueueDelta,
            int busyWindowCountDelta,
            int extremeWindowCountDelta,
            int servedUserCountDelta,
            int unservedUserCountDelta
    ) {
        boolean queueImproved = maxQueueDelta < 0 || busyWindowCountDelta < 0 || extremeWindowCountDelta < 0 || unservedUserCountDelta < 0;
        boolean waitImproved = avgWaitDelta < 0 || maxWaitDelta < 0;
        boolean worsened = maxQueueDelta > 0 || busyWindowCountDelta > 0 || extremeWindowCountDelta > 0
                || avgWaitDelta > 0 || maxWaitDelta > 0 || unservedUserCountDelta > 0;
        String prefix = switch (compareType) {
            case "DIVERSION_AFTER" -> "After applying diversion, ";
            case "RECOMMENDATION_AFTER" -> "After applying recommendation guidance, ";
            default -> "Comparison result: ";
        };
        if (queueImproved && waitImproved) {
            return prefix + "queue pressure and expected wait both improved.";
        }
        if (queueImproved) {
            return prefix + "queue pressure improved, but wait improvement is limited.";
        }
        if (waitImproved) {
            return prefix + "expected wait improved, but queue pressure improvement is limited.";
        }
        if (worsened) {
            return prefix + "the current strategy is not effective and should be adjusted.";
        }
        return prefix + "the strategy impact is limited.";
    }

    private String validateCrowdLevel(String crowdLevel) {
        String normalized = crowdLevel == null ? "" : crowdLevel.trim().toUpperCase(Locale.ROOT);
        if (!VALID_CROWD_LEVELS.contains(normalized)) {
            throw new ApiException(40002, "Invalid enum value", HttpStatus.BAD_REQUEST,
                    Map.of("field", "targetCrowdLevel", "reason", "targetCrowdLevel is not supported"));
        }
        return normalized;
    }

    private String resolveCompareType(String compareType) {
        if (compareType == null || compareType.isBlank()) {
            return "SCENARIO";
        }
        String normalized = compareType.trim().toUpperCase(Locale.ROOT);
        if (!VALID_COMPARE_TYPES.contains(normalized)) {
            throw new ApiException(40002, "Invalid enum value", HttpStatus.BAD_REQUEST,
                    Map.of("field", "compareType", "reason", "compareType is not supported"));
        }
        return normalized;
    }

    private List<WindowDiversionCandidate> findSourceWindows(
            List<WindowDiversionCandidate> candidates,
            String targetCrowdLevel,
            DiversionStrategySupport.ResolvedParameters strategy
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        double averagePressure = candidates.stream().mapToDouble(item -> pressureScore(item, strategy)).average().orElse(0.0);
        double maxPressure = candidates.stream().mapToDouble(item -> pressureScore(item, strategy)).max().orElse(0.0);
        double cutoff = Math.max(
                averagePressure + sourcePressureMargin(targetCrowdLevel, strategy),
                averagePressure + Math.max(0.18, (maxPressure - averagePressure) * 0.28)
        );

        List<WindowDiversionCandidate> sources = candidates.stream()
                .filter(candidate -> candidate.queueLength() >= Math.max(2, (int) Math.ceil(candidate.serviceRatePerMinute())))
                .filter(candidate -> pressureScore(candidate, strategy) >= cutoff)
                .sorted(Comparator.comparingDouble((WindowDiversionCandidate item) -> pressureScore(item, strategy)).reversed()
                        .thenComparingInt(WindowDiversionCandidate::waitMinutes).reversed()
                        .thenComparingInt(WindowDiversionCandidate::queueLength).reversed())
                .limit(8)
                .toList();
        if (!sources.isEmpty()) {
            return sources;
        }

        return candidates.stream()
                .sorted(Comparator.comparingDouble((WindowDiversionCandidate item) -> pressureScore(item, strategy)).reversed()
                        .thenComparingInt(WindowDiversionCandidate::waitMinutes).reversed())
                .filter(candidate -> pressureScore(candidate, strategy) > averagePressure + 0.12)
                .limit(3)
                .toList();
    }

    private List<WindowDiversionCandidate> findTargetWindows(
            SimulationTimePoint timePoint,
            DiversionStrategySupport.ResolvedParameters strategy
    ) {
        List<WindowDiversionCandidate> targets = new ArrayList<>();
        for (RestaurantSnapshot restaurant : timePoint.restaurants()) {
            for (WindowSnapshot window : restaurant.windows()) {
                WindowParameter parameter = seedRepository.window(window.windowId());
                if (parameter != null && "OPEN".equals(window.status()) && "OPEN".equals(parameter.status())) {
                    targets.add(new WindowDiversionCandidate(restaurant, window, parameter));
                }
            }
        }
        return targets.stream()
                .sorted(Comparator.comparingDouble((WindowDiversionCandidate item) -> pressureScore(item, strategy))
                        .thenComparing(Comparator.comparingDouble(WindowDiversionCandidate::serviceRatePerMinute).reversed()))
                .toList();
    }

    private Optional<TargetCandidate> selectTargetCandidate(
            WindowDiversionCandidate source,
            List<WindowDiversionCandidate> targets,
            String targetCrowdLevel,
            Map<Long, Integer> reservedTargetLoad,
            DiversionStrategySupport.ResolvedParameters strategy
    ) {
        double sourcePressure = pressureScore(source, strategy);

        Optional<TargetCandidate> strictMatch = targets.stream()
                .filter(candidate -> !candidate.windowId().equals(source.windowId()))
                .map(candidate -> buildTargetCandidate(candidate, targetCrowdLevel, reservedTargetLoad, false, strategy))
                .filter(candidate -> candidate.remainingCapacity() > 0)
                .filter(candidate -> candidate.pressureScore() + strictPressureBuffer(targetCrowdLevel, strategy) < sourcePressure)
                .max(Comparator.comparingDouble(candidate -> diversionTargetScore(source, candidate.candidate(), strategy)));
        if (strictMatch.isPresent()) {
            return strictMatch;
        }

        Optional<TargetCandidate> relaxedMatch = targets.stream()
                .filter(candidate -> !candidate.windowId().equals(source.windowId()))
                .map(candidate -> buildTargetCandidate(candidate, targetCrowdLevel, reservedTargetLoad, true, strategy))
                .filter(candidate -> candidate.remainingCapacity() > 0)
                .filter(candidate -> candidate.pressureScore() + 0.03 <= sourcePressure + 0.05)
                .max(Comparator.comparingDouble(candidate -> fallbackTargetScore(source, candidate, strategy)));
        if (relaxedMatch.isPresent()) {
            return relaxedMatch;
        }

        return targets.stream()
                .filter(candidate -> !candidate.windowId().equals(source.windowId()))
                .map(candidate -> buildEmergencyTargetCandidate(source, candidate, reservedTargetLoad, strategy))
                .filter(candidate -> candidate.remainingCapacity() > 0)
                .filter(candidate -> candidate.pressureScore() <= sourcePressure + 0.12)
                .max(Comparator.comparingDouble(candidate -> fallbackTargetScore(source, candidate, strategy)));
    }

    private TargetCandidate buildTargetCandidate(
            WindowDiversionCandidate candidate,
            String targetCrowdLevel,
            Map<Long, Integer> reservedTargetLoad,
            boolean relaxedMode,
            DiversionStrategySupport.ResolvedParameters strategy
    ) {
        int reservedLoad = reservedTargetLoad.getOrDefault(candidate.windowId(), 0);
        int remainingCapacity = remainingTargetCapacity(candidate, targetCrowdLevel, reservedLoad, relaxedMode);
        return new TargetCandidate(candidate, pressureScore(candidate, strategy), remainingCapacity);
    }

    private TargetCandidate buildEmergencyTargetCandidate(
            WindowDiversionCandidate source,
            WindowDiversionCandidate candidate,
            Map<Long, Integer> reservedTargetLoad,
            DiversionStrategySupport.ResolvedParameters strategy
    ) {
        int reservedLoad = reservedTargetLoad.getOrDefault(candidate.windowId(), 0);
        int remainingCapacity = emergencyRemainingCapacity(source, candidate, reservedLoad);
        return new TargetCandidate(candidate, pressureScore(candidate, strategy), remainingCapacity);
    }

    private double diversionTargetScore(
            WindowDiversionCandidate source,
            WindowDiversionCandidate target,
            DiversionStrategySupport.ResolvedParameters strategy
    ) {
        double pressureGap = Math.max(0.0, pressureScore(source, strategy) - pressureScore(target, strategy));
        double waitGap = Math.max(0, source.waitMinutes() - target.waitMinutes()) * 1.8;
        double queueGap = Math.max(0, source.queueLength() - target.queueLength()) * 0.9;
        double tagScore = windowTagSimilarity(source, target);
        double serviceScore = Math.min(22.0, target.serviceRatePerMinute() * 7.5);
        double restaurantAdjustment = source.restaurantId().equals(target.restaurantId())
                ? strategy.sameRestaurantBonus()
                : -strategy.crossRestaurantPenalty();
        return pressureGap * strategy.pressureGapTransferWeight() * 12.0
                + waitGap
                + queueGap
                + tagScore * strategy.tagSimilarityWeight()
                + serviceScore * strategy.serviceRateWeight()
                + restaurantAdjustment;
    }

    private double windowTagSimilarity(WindowDiversionCandidate source, WindowDiversionCandidate target) {
        Set<String> sourceTags = tagNormalizationService.normalize(source.matchingTags());
        if (sourceTags.isEmpty()) {
            return 50.0;
        }
        return tagNormalizationService.matchScore(sourceTags, target.matchingTags());
    }

    private int suggestedUserCount(
            WindowDiversionCandidate source,
            WindowDiversionCandidate target,
            int remainingTargetCapacity,
            DiversionStrategySupport.ResolvedParameters strategy
    ) {
        int queueGap = Math.max(0, source.queueLength() - target.queueLength());
        int sourceExcess = Math.max(0, source.queueLength() - comfortableQueue(source));
        int pressureDriven = Math.max(
                1,
                (int) Math.ceil(
                        Math.max(0.0, pressureScore(source, strategy) - pressureScore(target, strategy))
                                * Math.max(1.0, target.serviceRatePerMinute())
                                * strategy.pressureGapTransferWeight()
                )
        );
        int imbalanceDriven = Math.max(1, (int) Math.ceil(queueGap * strategy.queueGapTransferWeight()));
        int sourceDriven = Math.max(1, (int) Math.ceil(sourceExcess * strategy.sourceExcessTransferWeight()));
        int desired = Math.max(sourceDriven, Math.max(pressureDriven, imbalanceDriven));
        return Math.min(strategy.maxTransferCount(), Math.min(Math.min(source.queueLength(), remainingTargetCapacity), desired));
    }

    private int remainingTargetCapacity(
            WindowDiversionCandidate target,
            String targetCrowdLevel,
            int reservedLoad,
            boolean relaxedMode
    ) {
        int maxQueue = relaxedMode ? relaxedMaxQueueForLevel(target, targetCrowdLevel) : maxQueueForLevel(target, targetCrowdLevel);
        return Math.max(0, maxQueue - target.queueLength() - reservedLoad);
    }

    private int maxQueueForLevel(WindowDiversionCandidate candidate, String targetCrowdLevel) {
        double comfortMinutes = switch (targetCrowdLevel) {
            case "IDLE" -> 5.0;
            case "NORMAL" -> 8.0;
            case "BUSY" -> 11.0;
            default -> 14.0;
        };
        return Math.max(2, (int) Math.floor(comfortMinutes * Math.max(0.8, candidate.serviceRatePerMinute())));
    }

    private int relaxedMaxQueueForLevel(WindowDiversionCandidate candidate, String targetCrowdLevel) {
        double comfortMinutes = switch (targetCrowdLevel) {
            case "IDLE" -> 7.0;
            case "NORMAL" -> 11.0;
            case "BUSY" -> 14.0;
            default -> 17.0;
        };
        return Math.max(3, (int) Math.floor(comfortMinutes * Math.max(0.8, candidate.serviceRatePerMinute())));
    }

    private int emergencyRemainingCapacity(
            WindowDiversionCandidate source,
            WindowDiversionCandidate target,
            int reservedLoad
    ) {
        int queueGap = source.queueLength() - target.queueLength();
        if (queueGap <= 0) {
            return 0;
        }
        if (target.waitMinutes() > source.waitMinutes() + 3) {
            return 0;
        }
        int emergencyQuota = Math.min(3, Math.max(1, queueGap / 4));
        return Math.max(0, emergencyQuota - reservedLoad);
    }

    private double acceptanceRate(
            WindowDiversionCandidate source,
            WindowDiversionCandidate target,
            UserProfileRequest profile,
            String userType,
            DiversionStrategySupport.ResolvedParameters strategy
    ) {
        double tagSimilarity = windowTagSimilarity(source, target) / 100.0;
        double waitReduction = Math.max(0, source.waitMinutes() - target.waitMinutes());
        double pressureGap = Math.max(0.0, pressureScore(source, strategy) - pressureScore(target, strategy));
        double rate = strategy.acceptanceBaseRate()
                + Math.min(0.42, waitReduction * strategy.waitReductionAcceptanceWeight())
                + Math.min(0.22, pressureGap * strategy.pressureGapAcceptanceWeight())
                + tagSimilarity * strategy.tagSimilarityAcceptanceWeight()
                + (source.restaurantId().equals(target.restaurantId()) ? 0.10 : -0.03);

        rate += switch (userType) {
            case "HURRY" -> 0.18;
            case "BUDGET_SENSITIVE" -> 0.05;
            case "FACULTY" -> 0.06;
            case "VISITOR" -> -0.02;
            default -> 0.08;
        };

        if (profile != null && profile.budgetMin() != null && profile.budgetMax() != null) {
            rate += budgetAcceptanceDelta(source, target, profile, userType);
        }
        if (profile != null && profile.waitingToleranceMinutes() != null) {
            rate += target.waitMinutes() <= profile.waitingToleranceMinutes() ? 0.06 : -0.02;
        }

        return clamp(rate, 0.12, 0.98);
    }

    private double pressureScore(
            WindowDiversionCandidate candidate,
            DiversionStrategySupport.ResolvedParameters strategy
    ) {
        double normalizedWait = candidate.waitMinutes() / 7.0;
        double normalizedQueue = candidate.queueLength() / Math.max(1.0, candidate.serviceRatePerMinute() * 4.5);
        double loadRatio = candidate.queueLength() / Math.max(1.0, candidate.serviceRatePerMinute() * 8.0);
        double crowdComponent = switch (candidate.crowdLevel()) {
            case "NORMAL" -> 0.45;
            case "BUSY" -> 1.0;
            case "EXTREME" -> 1.65;
            default -> 0.0;
        };
        return normalizedWait * strategy.pressureWaitWeight()
                + normalizedQueue * strategy.pressureQueueWeight()
                + loadRatio * strategy.pressureLoadWeight()
                + crowdComponent * strategy.pressureCrowdWeight();
    }

    private double strictPressureBuffer(
            String targetCrowdLevel,
            DiversionStrategySupport.ResolvedParameters strategy
    ) {
        double scale = switch (targetCrowdLevel) {
            case "IDLE" -> 1.2;
            case "NORMAL" -> 1.0;
            case "BUSY" -> 0.8;
            default -> 0.6;
        };
        return strategy.strictTargetPressureBuffer() * scale;
    }

    private double fallbackTargetScore(
            WindowDiversionCandidate source,
            TargetCandidate target,
            DiversionStrategySupport.ResolvedParameters strategy
    ) {
        double pressureGap = pressureScore(source, strategy) - target.pressureScore();
        double waitGap = source.waitMinutes() - target.candidate().waitMinutes();
        double queueGap = source.queueLength() - target.candidate().queueLength();
        return pressureGap * 20.0
                + waitGap * 1.2
                + queueGap * 0.7
                + target.remainingCapacity() * 0.4
                + target.candidate().serviceRatePerMinute() * 0.5;
    }

    private double sourcePressureMargin(
            String targetCrowdLevel,
            DiversionStrategySupport.ResolvedParameters strategy
    ) {
        double scale = switch (targetCrowdLevel) {
            case "IDLE" -> 0.6;
            case "NORMAL" -> 1.0;
            case "BUSY" -> 1.5;
            default -> 1.9;
        };
        return strategy.sourcePressureMargin() * scale;
    }

    private int comfortableQueue(WindowDiversionCandidate candidate) {
        return Math.max(2, (int) Math.floor(Math.max(0.8, candidate.serviceRatePerMinute()) * 6.5));
    }

    private double budgetAcceptanceDelta(
            WindowDiversionCandidate source,
            WindowDiversionCandidate target,
            UserProfileRequest profile,
            String userType
    ) {
        double sourceDistance = budgetDistance(source.averagePrice(), profile.budgetMin(), profile.budgetMax());
        double targetDistance = budgetDistance(target.averagePrice(), profile.budgetMin(), profile.budgetMax());
        double delta = (sourceDistance - targetDistance) * 0.02;
        if ("BUDGET_SENSITIVE".equals(userType) && target.averagePrice() > source.averagePrice()) {
            delta -= Math.min(0.12, (target.averagePrice() - source.averagePrice()) * 0.02);
        }
        return clamp(delta, -0.18, 0.18);
    }

    private double budgetDistance(double price, double budgetMin, double budgetMax) {
        if (price < budgetMin) {
            return budgetMin - price;
        }
        if (price > budgetMax) {
            return price - budgetMax;
        }
        return 0.0;
    }

    private double estimatedWaitReduction(
            WindowDiversionCandidate source,
            WindowDiversionCandidate target,
            int estimatedAcceptedCount
    ) {
        double rawGap = Math.max(0, source.waitMinutes() - target.waitMinutes());
        if (rawGap <= 0 || estimatedAcceptedCount <= 0) {
            return 0.0;
        }
        double loadRatio = Math.min(1.0, estimatedAcceptedCount / Math.max(1.0, source.queueLength()));
        return rawGap * Math.max(0.35, loadRatio);
    }

    private String diversionReason(
            WindowDiversionCandidate source,
            WindowDiversionCandidate target,
            double acceptanceRate,
            double estimatedWaitReduction,
            double tagSimilarity
    ) {
        String similarityText = tagSimilarity >= 0.70 ? "taste is close"
                : tagSimilarity >= 0.45 ? "taste gap is small"
                : "taste similarity is limited";
        return target.restaurantName() + " / " + target.windowName()
                + " has lower wait, " + similarityText
                + ", estimated acceptance " + formatPercent(acceptanceRate)
                + ", estimated wait reduction " + formatDelta(estimatedWaitReduction) + " min.";
    }

    private String formatPercent(double rate) {
        return Math.round(rate * 100) + "%";
    }

    private String validateProfile(UserProfileRequest profile) {
        if (profile.budgetMin() != null && profile.budgetMax() != null && profile.budgetMin() > profile.budgetMax()) {
            throw new ApiException(40001, "Invalid parameter", HttpStatus.BAD_REQUEST,
                    Map.of("field", "profile.budgetMin", "reason", "budgetMin must be less than or equal to budgetMax"));
        }
        String userType = normalizeUserType(profile.userType());
        if (!VALID_USER_TYPES.contains(userType)) {
            throw new ApiException(40002, "Invalid enum value", HttpStatus.BAD_REQUEST,
                    Map.of("field", "profile.userType", "reason", "userType is not supported"));
        }
        return userType;
    }

    private String normalizeUserType(String userType) {
        return userType == null ? "" : userType.trim().toUpperCase(Locale.ROOT);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundTwo(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String formatDelta(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private record WindowDiversionCandidate(
            Long restaurantId,
            String restaurantName,
            Long windowId,
            String windowName,
            int queueLength,
            int waitMinutes,
            String crowdLevel,
            double serviceRatePerMinute,
            double averagePrice,
            String matchingTags
    ) {
        private WindowDiversionCandidate(RestaurantSnapshot restaurant, WindowSnapshot window, WindowParameter parameter) {
            this(
                    restaurant.restaurantId(),
                    restaurant.name(),
                    window.windowId(),
                    window.name(),
                    window.queueLength(),
                    window.waitMinutes(),
                    window.crowdLevel(),
                    parameter.serviceRatePerMinute(),
                    (parameter.priceMin() + parameter.priceMax()) / 2.0,
                    parameter.matchingTags()
            );
        }
    }

    private record TargetCandidate(
            WindowDiversionCandidate candidate,
            double pressureScore,
            int remainingCapacity
    ) {
        private Long restaurantId() {
            return candidate.restaurantId();
        }

        private Long windowId() {
            return candidate.windowId();
        }
    }
}
