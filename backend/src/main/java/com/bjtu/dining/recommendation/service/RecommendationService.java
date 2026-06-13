package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.common.ApiException;
import com.bjtu.dining.common.ResourceNotFoundException;
import com.bjtu.dining.recommendation.dto.DiversionComparisonRequest;
import com.bjtu.dining.recommendation.dto.DiversionComparisonResult;
import com.bjtu.dining.recommendation.dto.DiversionRequest;
import com.bjtu.dining.recommendation.dto.DiversionResult;
import com.bjtu.dining.recommendation.dto.DiversionStrategyParameters;
import com.bjtu.dining.recommendation.dto.DiversionSuggestionItem;
import com.bjtu.dining.recommendation.dto.RecommendationGenerateRequest;
import com.bjtu.dining.recommendation.dto.RecommendationItem;
import com.bjtu.dining.recommendation.dto.RecommendationResult;
import com.bjtu.dining.recommendation.dto.StrategyCompareRequest;
import com.bjtu.dining.recommendation.dto.StrategyComparisonResult;
import com.bjtu.dining.recommendation.dto.UserProfileRequest;
import com.bjtu.dining.recommendation.model.DishParameter;
import com.bjtu.dining.recommendation.model.EvaluationMetrics;
import com.bjtu.dining.recommendation.model.RestaurantParameter;
import com.bjtu.dining.recommendation.model.RestaurantSnapshot;
import com.bjtu.dining.recommendation.model.SimulationRunResult;
import com.bjtu.dining.recommendation.model.SimulationTimePoint;
import com.bjtu.dining.recommendation.model.WindowParameter;
import com.bjtu.dining.recommendation.model.WindowSnapshot;
import com.bjtu.dining.recommendation.repository.CsvSeedRepository;
import com.bjtu.dining.recommendation.repository.RecommendationStore;
import com.bjtu.dining.taska.model.TaskADtos;
import com.bjtu.dining.taska.service.SimulationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 10;
    private static final Set<String> VALID_USER_TYPES = Set.of(
            "STUDENT",
            "FACULTY",
            "VISITOR",
            "HURRY",
            "BUDGET_SENSITIVE"
    );
    private static final Set<String> VALID_CROWD_LEVELS = Set.of("IDLE", "NORMAL", "BUSY", "EXTREME");
    private static final Set<String> VALID_COMPARE_TYPES = Set.of("SCENARIO", "RECOMMENDATION_AFTER", "DIVERSION_AFTER");
    private static final int MIN_DIVERSION_WAIT_MINUTES = 6;
    private static final double SAME_RESTAURANT_WALK_MINUTES = 0.5;
    private static final double SAME_AREA_WALK_MINUTES = 2.0;
    private static final double MIN_NET_WAIT_SAVING_MINUTES = 1.0;

    private final CsvSeedRepository seedRepository;
    private final SimulationProvider simulationProvider;
    private final RecommendationStore recommendationStore;
    private final SimulationService taskASimulationService;

    public RecommendationService(
            CsvSeedRepository seedRepository,
            SimulationProvider simulationProvider,
            RecommendationStore recommendationStore,
            SimulationService taskASimulationService
    ) {
        this.seedRepository = seedRepository;
        this.simulationProvider = simulationProvider;
        this.recommendationStore = recommendationStore;
        this.taskASimulationService = taskASimulationService;
    }

    public RecommendationResult generate(RecommendationGenerateRequest request) {
        int limit = resolveLimit(request.limit());
        String userType = validateProfile(request.profile());
        PreferenceWeights weights = weightsFor(userType);

        SimulationRunResult simulation = loadFinishedSimulation(request.runId());
        SimulationTimePoint timePoint = selectTimePoint(simulation, request.minute());
        Set<String> preferredTags = TagMatcher.normalize(request.profile().tasteTags());
        Map<Long, WindowSnapshot> windowStateById = indexWindowState(timePoint);

        List<RecommendationItem> restaurants = rank(
                buildRestaurantRecommendations(timePoint, request.profile(), preferredTags, weights, userType),
                limit
        );
        List<RecommendationItem> windows = rank(
                buildWindowRecommendations(timePoint, request.profile(), preferredTags, windowStateById, weights, userType),
                limit
        );
        List<RecommendationItem> dishes = rank(
                buildDishRecommendations(request.profile(), preferredTags, windowStateById, weights, userType),
                limit
        );

        RecommendationResult result = new RecommendationResult(
                request.runId(),
                timePoint.minute(),
                restaurants,
                windows,
                dishes,
                buildDiversionSuggestion(timePoint),
                OffsetDateTime.now()
        );
        recommendationStore.save(result);
        return result;
    }

    public RecommendationResult getGenerated(Long runId, Integer minute) {
        if (runId == null || runId <= 0) {
            throw new ApiException(40401, "仿真运行记录不存在", HttpStatus.NOT_FOUND);
        }
        return recommendationStore.find(runId, minute)
                .orElseThrow(() -> new ApiException(40400, "推荐结果不存在，请先调用生成推荐接口", HttpStatus.NOT_FOUND));
    }

    public DiversionResult generateDiversion(DiversionRequest request) {
        String targetCrowdLevel = validateCrowdLevel(request.targetCrowdLevel());
        UserProfileRequest profile = request.profile();
        String userType = profile == null ? "STUDENT" : validateProfile(profile);
        DiversionStrategyParameters strategy =
                DiversionStrategyParameters.resolve(request.strategyParameters());

        SimulationRunResult simulation = loadFinishedSimulation(request.runId());
        SimulationTimePoint timePoint = selectTimePoint(simulation, request.minute());

        List<WindowDiversionCandidate> sources = findSourceWindows(timePoint, targetCrowdLevel, strategy);
        if (sources.isEmpty()) {
            return new DiversionResult(
                    request.runId(),
                    timePoint.minute(),
                    List.of(),
                    "当前没有超过目标拥挤度 " + targetCrowdLevel + " 的拥挤窗口，暂不需要分流。"
            );
        }

        List<WindowDiversionCandidate> targets = findTargetWindows(timePoint, targetCrowdLevel);
        if (targets.isEmpty()) {
            return new DiversionResult(
                    request.runId(),
                    timePoint.minute(),
                    List.of(),
                    "存在拥挤窗口，但没有等待时间更短且拥挤度不高于 " + targetCrowdLevel + " 的营业目标窗口。"
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

            double acceptanceRate = acceptanceRate(
                    source,
                    selectedTarget.candidate(),
                    profile,
                    userType,
                    strategy
            );
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

        String reason = suggestions.isEmpty()
                ? "存在拥挤窗口，但暂未找到可承接分流人数的低拥挤营业窗口。"
                : "已生成 " + suggestions.size() + " 条分流建议，目标拥挤度不高于 " + targetCrowdLevel + "。";
        return new DiversionResult(request.runId(), timePoint.minute(), suggestions, reason);
    }

    public DiversionComparisonResult runDiversionComparison(DiversionComparisonRequest request) {
        TaskADtos.SimulationRunResult baseRun = loadFinishedTaskARun(request.baseRunId(), "无法执行分流对比");
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
                    "READY"
            );
        }

        TaskADtos.SimulationRunResult compareRun = taskASimulationService.runSimulationWithDiversion(
                new TaskADtos.SimulationRunWithDiversionRequest(
                        request.baseRunId(),
                        resolvedMinute,
                        targetCrowdLevel,
                        diversionResult.suggestions().stream().map(this::toTaskADiversionSuggestion).toList()
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
                "COMPLETED"
        );
    }

    public StrategyComparisonResult compareStrategies(StrategyCompareRequest request) {
        String compareType = resolveCompareType(request.compareType());
        SimulationRunResult baseSimulation = loadFinishedSimulation(request.baseRunId(), "无法比较策略");
        SimulationRunResult compareSimulation = loadFinishedSimulation(request.compareRunId(), "无法比较策略");
        EvaluationMetrics baseMetrics = baseSimulation.metrics();
        EvaluationMetrics compareMetrics = compareSimulation.metrics();
        if (baseMetrics == null || compareMetrics == null) {
            throw new ApiException(40400, "仿真评估指标不存在", HttpStatus.NOT_FOUND);
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

    private List<RecommendationItem> buildRestaurantRecommendations(
            SimulationTimePoint timePoint,
            UserProfileRequest profile,
            Set<String> preferredTags,
            PreferenceWeights weights,
            String userType
    ) {
        List<RecommendationItem> items = new ArrayList<>();
        for (RestaurantSnapshot snapshot : timePoint.restaurants()) {
            RestaurantParameter restaurant = seedRepository.restaurant(snapshot.restaurantId());
            if (restaurant == null || !"OPEN".equals(restaurant.status())) {
                continue;
            }

            List<WindowParameter> windows = seedRepository.windowsByRestaurant(snapshot.restaurantId());
            Optional<WindowParameter> bestWindow = windows.stream()
                    .max(Comparator.comparingDouble((WindowParameter window) -> TagMatcher.matchScore(preferredTags, window.matchingTags()))
                            .thenComparingDouble(WindowParameter::popularity));
            double avgWait = snapshot.windows().stream().mapToInt(WindowSnapshot::waitMinutes).average().orElse(0.0);
            String worstCrowd = worstCrowd(snapshot.windows());
            double tagScore = bestWindow
                    .map(window -> TagMatcher.matchScore(preferredTags, window.matchingTags()))
                    .orElse(55.0);
            double budgetScore = windows.stream()
                    .mapToDouble(window -> windowBudgetScore(window, profile))
                    .max()
                    .orElse(45.0);
            double popularityScore = windows.stream()
                    .mapToDouble(window -> window.popularity() * 100.0)
                    .average()
                    .orElse(55.0);
            ScoreCard scoreCard = scoreCard(weights, factorScores(
                    "taste", tagScore,
                    "budget", budgetScore,
                    "wait", waitScore((int) Math.round(avgWait), profile.waitingToleranceMinutes()),
                    "crowd", crowdScore(worstCrowd),
                    "popularity", popularityScore,
                    "restaurantAttraction", restaurant.baseAttraction() * 100.0
            ));
            List<String> matchedTags = bestWindow
                    .map(window -> matchedTags(preferredTags, window.matchingTags()))
                    .orElse(List.of());

            items.add(new RecommendationItem(
                    "RESTAURANT",
                    snapshot.restaurantId(),
                    snapshot.name(),
                    scoreCard.total(),
                    0,
                    restaurantReason(profile, userType, matchedTags, avgWait, worstCrowd, budgetScore),
                    snapshot.restaurantId(),
                    bestWindow.map(WindowParameter::windowId).orElse(null),
                    (int) Math.round(avgWait),
                    worstCrowd,
                    scoreCard.breakdown(),
                    matchedTags
            ));
        }
        return items;
    }

    private List<RecommendationItem> buildWindowRecommendations(
            SimulationTimePoint timePoint,
            UserProfileRequest profile,
            Set<String> preferredTags,
            Map<Long, WindowSnapshot> windowStateById,
            PreferenceWeights weights,
            String userType
    ) {
        List<RecommendationItem> items = new ArrayList<>();
        for (WindowParameter window : seedRepository.windows()) {
            WindowSnapshot state = windowStateById.get(window.windowId());
            if (state == null || !"OPEN".equals(state.status()) || !"OPEN".equals(window.status())) {
                continue;
            }

            double tagScore = TagMatcher.matchScore(preferredTags, window.matchingTags());
            double budgetScore = windowBudgetScore(window, profile);
            ScoreCard scoreCard = scoreCard(weights, factorScores(
                    "taste", tagScore,
                    "budget", budgetScore,
                    "wait", waitScore(state.waitMinutes(), profile.waitingToleranceMinutes()),
                    "crowd", crowdScore(state.crowdLevel()),
                    "popularity", window.popularity() * 100.0
            ));
            List<String> matchedTags = matchedTags(preferredTags, window.matchingTags());

            items.add(new RecommendationItem(
                    "WINDOW",
                    window.windowId(),
                    window.name(),
                    scoreCard.total(),
                    0,
                    windowReason(profile, userType, matchedTags, state.waitMinutes(), state.crowdLevel(), budgetScore),
                    window.restaurantId(),
                    window.windowId(),
                    state.waitMinutes(),
                    state.crowdLevel(),
                    scoreCard.breakdown(),
                    matchedTags
            ));
        }
        return items;
    }

    private List<RecommendationItem> buildDishRecommendations(
            UserProfileRequest profile,
            Set<String> preferredTags,
            Map<Long, WindowSnapshot> windowStateById,
            PreferenceWeights weights,
            String userType
    ) {
        List<RecommendationItem> items = new ArrayList<>();
        for (DishParameter dish : seedRepository.dishes()) {
            WindowSnapshot state = windowStateById.get(dish.windowId());
            WindowParameter window = seedRepository.window(dish.windowId());
            if (state == null || window == null || !"OPEN".equals(state.status()) || !"OPEN".equals(window.status())) {
                continue;
            }

            int estimatedWait = state.waitMinutes() + dish.prepTimeMinutes();
            double tagScore = TagMatcher.matchScore(preferredTags, dish.matchingTags());
            double budgetScore = dishBudgetScore(dish, profile);
            double prepScore = Math.max(0.0, 100.0 - dish.prepTimeMinutes() * 8.0);
            ScoreCard scoreCard = scoreCard(weights, factorScores(
                    "taste", tagScore,
                    "budget", budgetScore,
                    "wait", waitScore(estimatedWait, profile.waitingToleranceMinutes()),
                    "crowd", crowdScore(state.crowdLevel()),
                    "popularity", dish.popularity() * 100.0,
                    "prep", prepScore
            ));
            List<String> matchedTags = matchedTags(preferredTags, dish.matchingTags());

            items.add(new RecommendationItem(
                    "DISH",
                    dish.dishId(),
                    dish.name(),
                    scoreCard.total(),
                    0,
                    dishReason(profile, userType, matchedTags, estimatedWait, state.crowdLevel(), budgetScore, dish.prepTimeMinutes()),
                    dish.restaurantId(),
                    dish.windowId(),
                    estimatedWait,
                    state.crowdLevel(),
                    scoreCard.breakdown(),
                    matchedTags
            ));
        }
        return items;
    }

    private SimulationTimePoint selectTimePoint(SimulationRunResult simulation, Integer minute) {
        if (minute == null) {
            return resolvePeakTimePoint(simulation);
        }
        if (minute < 0) {
            throw new ApiException(40001, "参数校验失败", HttpStatus.BAD_REQUEST,
                    Map.of("field", "minute", "reason", "minute 必须大于等于 0"));
        }
        return simulation.timePoints().stream()
                .filter(point -> point.minute() == minute)
                .findFirst()
                .orElseThrow(() -> new ApiException(40001, "参数校验失败", HttpStatus.BAD_REQUEST,
                        Map.of("field", "minute", "reason", "指定 minute 不存在")));
    }

    private SimulationRunResult loadFinishedSimulation(Long runId) {
        return loadFinishedSimulation(runId, "无法生成推荐");
    }

    private SimulationRunResult loadFinishedSimulation(Long runId, String operationText) {
        SimulationRunResult simulation = simulationProvider.findByRunId(runId);
        if (!"FINISHED".equals(simulation.status())) {
            throw new ApiException(40901, "仿真尚未完成，" + operationText, HttpStatus.CONFLICT);
        }
        return simulation;
    }

    private TaskADtos.SimulationRunResult loadFinishedTaskARun(Long runId, String operationText) {
        try {
            TaskADtos.SimulationRunResult simulation = taskASimulationService.getRunResult(runId);
            if (!"FINISHED".equals(simulation.status())) {
                throw new ApiException(40901, "仿真尚未完成，" + operationText, HttpStatus.CONFLICT);
            }
            return simulation;
        } catch (ApiException ex) {
            throw ex;
        } catch (ResourceNotFoundException ex) {
            throw new ApiException(40401, "仿真运行记录不存在", HttpStatus.NOT_FOUND);
        } catch (RuntimeException ex) {
            log.error("加载 Task A 仿真结果失败, runId={}, operation={}", runId, operationText, ex);
            throw new ApiException(50001, "加载仿真结果失败，请检查后端日志", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private SimulationTimePoint resolvePeakTimePoint(SimulationRunResult simulation) {
        return simulation.timePoints().stream()
                .max(Comparator
                        .comparingInt(this::totalQueueLength)
                        .thenComparingInt(this::totalCurrentCount)
                        .thenComparingInt(SimulationTimePoint::minute))
                .orElseThrow(() -> new ApiException(40401, "仿真运行记录不存在", HttpStatus.NOT_FOUND));
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
        int score = 0;
        score += avgWaitDelta < 0 ? 2 : avgWaitDelta > 0 ? -2 : 0;
        score += maxWaitDelta < 0 ? 1 : maxWaitDelta > 0 ? -1 : 0;
        score += maxQueueDelta < 0 ? 2 : maxQueueDelta > 0 ? -2 : 0;
        score += busyWindowCountDelta < 0 ? 1 : busyWindowCountDelta > 0 ? -1 : 0;
        score += extremeWindowCountDelta < 0 ? 2 : extremeWindowCountDelta > 0 ? -2 : 0;
        score += servedUserCountDelta > 0 ? 1 : servedUserCountDelta < 0 ? -1 : 0;
        score += unservedUserCountDelta < 0 ? 2 : unservedUserCountDelta > 0 ? -2 : 0;

        boolean queuePressureImproved = maxQueueDelta < 0
                || busyWindowCountDelta < 0
                || extremeWindowCountDelta < 0
                || unservedUserCountDelta < 0;
        boolean queuePressureWorsened = maxQueueDelta > 0
                || busyWindowCountDelta > 0
                || extremeWindowCountDelta > 0
                || unservedUserCountDelta > 0;
        boolean waitImproved = avgWaitDelta < 0 || maxWaitDelta < 0;
        boolean waitWorsened = avgWaitDelta > 0 || maxWaitDelta > 0;

        StringBuilder builder = new StringBuilder(compareTypePrefix(compareType))
                .append("最大排队长度")
                .append(deltaText(maxQueueDelta, "人"))
                .append("，忙碌窗口数")
                .append(deltaText(busyWindowCountDelta, "个"))
                .append("，极端拥挤窗口数")
                .append(deltaText(extremeWindowCountDelta, "个"))
                .append("，未服务人数")
                .append(deltaText(unservedUserCountDelta, "人"))
                .append("，平均预计等待")
                .append(deltaText(avgWaitDelta, "分钟"));

        if (Math.abs(maxWaitDelta) > 0) {
            builder.append("，最大预计等待").append(deltaText(maxWaitDelta, "分钟"));
        }
        if (servedUserCountDelta != 0) {
            builder.append("，已服务人数").append(deltaText(servedUserCountDelta, "人"));
        }

        if (queuePressureImproved && waitImproved) {
            builder.append("，说明分流策略同时缓解了高峰排队压力，并带来了预计等待改善。");
        } else if (queuePressureImproved) {
            builder.append("，分流策略主要改善了高峰队列压力和窗口拥挤程度，平均预计等待改善有限。");
        } else if (waitImproved && !queuePressureWorsened) {
            builder.append("，平均预计等待有所改善，但高峰队列压力缓解有限，建议继续观察目标窗口的承接能力。");
        } else if (score <= -3 || queuePressureWorsened || waitWorsened) {
            builder.append("，本轮分流策略改善不明显，可能需要调整分流目标或窗口配置。");
        } else {
            builder.append("，本轮分流策略改善有限，建议继续观察目标窗口的承接能力。");
        }
        return builder.toString();
    }

    private String compareTypePrefix(String compareType) {
        return switch (compareType) {
            case "RECOMMENDATION_AFTER" -> "执行推荐引导后，";
            case "DIVERSION_AFTER" -> "执行分流建议后，";
            default -> "对比结果显示，";
        };
    }

    private String deltaText(double delta, String unit) {
        if (delta < 0) {
            return "下降 " + formatDelta(Math.abs(delta)) + " " + unit;
        }
        if (delta > 0) {
            return "增加 " + formatDelta(delta) + " " + unit;
        }
        return "持平";
    }

    private String deltaText(int delta, String unit) {
        if (delta < 0) {
            return "减少 " + Math.abs(delta) + " " + unit;
        }
        if (delta > 0) {
            return "增加 " + delta + " " + unit;
        }
        return "持平";
    }

    private String formatDelta(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String validateCrowdLevel(String crowdLevel) {
        String normalized = crowdLevel == null ? "" : crowdLevel.trim().toUpperCase(Locale.ROOT);
        if (!VALID_CROWD_LEVELS.contains(normalized)) {
            throw new ApiException(40002, "枚举值非法", HttpStatus.BAD_REQUEST,
                    Map.of("field", "targetCrowdLevel", "reason", "targetCrowdLevel 不在允许范围内"));
        }
        return normalized;
    }

    private String resolveCompareType(String compareType) {
        if (compareType == null || compareType.isBlank()) {
            return "SCENARIO";
        }
        String normalized = compareType.trim().toUpperCase(Locale.ROOT);
        if (!VALID_COMPARE_TYPES.contains(normalized)) {
            throw new ApiException(40002, "枚举值非法", HttpStatus.BAD_REQUEST,
                    Map.of("field", "compareType", "reason", "compareType 不在允许范围内"));
        }
        return normalized;
    }

    private List<WindowDiversionCandidate> findSourceWindows(
            SimulationTimePoint timePoint,
            String targetCrowdLevel,
            DiversionStrategyParameters strategy
    ) {
        List<WindowDiversionCandidate> candidates = collectOpenWindowCandidates(timePoint);
        if (candidates.isEmpty()) {
            return List.of();
        }

        double averagePressure = candidates.stream().mapToDouble(this::pressureScore).average().orElse(0.0);
        double maxPressure = candidates.stream().mapToDouble(this::pressureScore).max().orElse(0.0);
        double cutoff = Math.max(
                averagePressure + sourcePressureMargin(targetCrowdLevel) * strategy.sourcePressureScale(),
                averagePressure + Math.max(0.18, (maxPressure - averagePressure) * 0.28)
        );

        List<WindowDiversionCandidate> sources = candidates.stream()
                .filter(this::isMeaningfullyCongested)
                .filter(candidate -> candidate.queueLength() >= Math.max(2, (int) Math.ceil(candidate.serviceRatePerMinute())))
                .filter(candidate -> pressureScore(candidate) >= cutoff)
                .sorted(Comparator.comparingDouble(this::pressureScore).reversed()
                        .thenComparing(Comparator.comparingInt(WindowDiversionCandidate::waitMinutes).reversed())
                        .thenComparing(Comparator.comparingInt(WindowDiversionCandidate::queueLength).reversed()))
                .limit(8)
                .toList();
        if (!sources.isEmpty()) {
            return sources;
        }

        return candidates.stream()
                .filter(this::isMeaningfullyCongested)
                .sorted(Comparator.comparingDouble(this::pressureScore).reversed()
                        .thenComparing(Comparator.comparingInt(WindowDiversionCandidate::waitMinutes).reversed()))
                .filter(candidate -> pressureScore(candidate) > averagePressure + 0.12)
                .limit(3)
                .toList();
    }

    private List<WindowDiversionCandidate> findTargetWindows(SimulationTimePoint timePoint, String targetCrowdLevel) {
        return collectOpenWindowCandidates(timePoint).stream()
                .filter(candidate -> isWithinTargetCrowdLevel(candidate, targetCrowdLevel))
                .sorted(Comparator.comparingDouble(this::pressureScore)
                        .thenComparing(Comparator.comparingDouble(WindowDiversionCandidate::serviceRatePerMinute).reversed()))
                .toList();
    }

    private List<WindowDiversionCandidate> collectOpenWindowCandidates(SimulationTimePoint timePoint) {
        List<WindowDiversionCandidate> targets = new ArrayList<>();
        for (RestaurantSnapshot restaurant : timePoint.restaurants()) {
            for (WindowSnapshot window : restaurant.windows()) {
                WindowParameter parameter = seedRepository.window(window.windowId());
                RestaurantParameter restaurantParameter = seedRepository.restaurant(restaurant.restaurantId());
                if (parameter != null
                        && restaurantParameter != null
                        && "OPEN".equals(window.status())
                        && "OPEN".equals(parameter.status())) {
                    targets.add(new WindowDiversionCandidate(restaurant, window, parameter, restaurantParameter));
                }
            }
        }
        return targets.stream()
                .sorted(Comparator.comparingDouble(this::pressureScore)
                        .thenComparing(Comparator.comparingDouble(WindowDiversionCandidate::serviceRatePerMinute).reversed()))
                .toList();
    }

    private boolean isWithinTargetCrowdLevel(
            WindowDiversionCandidate candidate,
            String targetCrowdLevel
    ) {
        return crowdWeight(candidate.crowdLevel()) <= crowdWeight(targetCrowdLevel);
    }

    private Optional<TargetCandidate> selectTargetCandidate(
            WindowDiversionCandidate source,
            List<WindowDiversionCandidate> targets,
            String targetCrowdLevel,
            Map<Long, Integer> reservedTargetLoad,
            DiversionStrategyParameters strategy
    ) {
        double sourcePressure = pressureScore(source);

        Optional<TargetCandidate> strictMatch = targets.stream()
                .filter(candidate -> !candidate.windowId().equals(source.windowId()))
                .filter(candidate -> isReachableTarget(source, candidate))
                .filter(candidate -> hasMeaningfulNetBenefit(source, candidate))
                .map(candidate -> buildTargetCandidate(candidate, targetCrowdLevel, reservedTargetLoad, false))
                .filter(candidate -> candidate.remainingCapacity() > 0)
                .filter(candidate -> candidate.pressureScore()
                        + strictPressureBuffer(targetCrowdLevel) * strategy.targetPressureBufferScale()
                        < sourcePressure)
                .max(Comparator.comparingDouble(candidate -> diversionTargetScore(source, candidate.candidate())));
        if (strictMatch.isPresent()) {
            return strictMatch;
        }

        Optional<TargetCandidate> relaxedMatch = targets.stream()
                .filter(candidate -> !candidate.windowId().equals(source.windowId()))
                .filter(candidate -> isReachableTarget(source, candidate))
                .filter(candidate -> hasMeaningfulNetBenefit(source, candidate))
                .map(candidate -> buildTargetCandidate(candidate, targetCrowdLevel, reservedTargetLoad, true))
                .filter(candidate -> candidate.remainingCapacity() > 0)
                .filter(candidate -> candidate.pressureScore() + 0.03 <= sourcePressure + 0.05)
                .max(Comparator.comparingDouble(candidate -> fallbackTargetScore(source, candidate)));
        if (relaxedMatch.isPresent()) {
            return relaxedMatch;
        }

        return targets.stream()
                .filter(candidate -> !candidate.windowId().equals(source.windowId()))
                .filter(candidate -> isReachableTarget(source, candidate))
                .filter(candidate -> hasMeaningfulNetBenefit(source, candidate))
                .map(candidate -> buildEmergencyTargetCandidate(source, candidate, reservedTargetLoad))
                .filter(candidate -> candidate.remainingCapacity() > 0)
                .filter(candidate -> candidate.pressureScore() <= sourcePressure + 0.12)
                .max(Comparator.comparingDouble(candidate -> fallbackTargetScore(source, candidate)));
    }

    private TargetCandidate buildTargetCandidate(
            WindowDiversionCandidate candidate,
            String targetCrowdLevel,
            Map<Long, Integer> reservedTargetLoad,
            boolean relaxedMode
    ) {
        int reservedLoad = reservedTargetLoad.getOrDefault(candidate.windowId(), 0);
        int remainingCapacity = remainingTargetCapacity(candidate, targetCrowdLevel, reservedLoad, relaxedMode);
        return new TargetCandidate(candidate, pressureScore(candidate), remainingCapacity);
    }

    private TargetCandidate buildEmergencyTargetCandidate(
            WindowDiversionCandidate source,
            WindowDiversionCandidate candidate,
            Map<Long, Integer> reservedTargetLoad
    ) {
        int reservedLoad = reservedTargetLoad.getOrDefault(candidate.windowId(), 0);
        int remainingCapacity = emergencyRemainingCapacity(source, candidate, reservedLoad);
        return new TargetCandidate(candidate, pressureScore(candidate), remainingCapacity);
    }

    private double diversionTargetScore(WindowDiversionCandidate source, WindowDiversionCandidate target) {
        double pressureGap = Math.max(0.0, pressureScore(source) - pressureScore(target));
        double waitGap = Math.max(0, source.waitMinutes() - target.waitMinutes()) * 1.8;
        double queueGap = Math.max(0, source.queueLength() - target.queueLength()) * 0.9;
        double tagScore = windowTagSimilarity(source, target);
        double serviceScore = Math.min(22.0, target.serviceRatePerMinute() * 7.5);
        double proximityBonus = source.restaurantId().equals(target.restaurantId()) ? 10.0 : 3.0;
        return pressureGap * 24.0 + waitGap + queueGap + tagScore * 0.20 + serviceScore * 0.10 + proximityBonus;
    }

    private double windowTagSimilarity(WindowDiversionCandidate source, WindowDiversionCandidate target) {
        Set<String> sourceTags = TagMatcher.normalize(source.matchingTags());
        if (sourceTags.isEmpty()) {
            return 50.0;
        }
        return TagMatcher.matchScore(sourceTags, target.matchingTags());
    }

    private int suggestedUserCount(
            WindowDiversionCandidate source,
            WindowDiversionCandidate target,
            int remainingTargetCapacity,
            DiversionStrategyParameters strategy
    ) {
        int queueGap = Math.max(0, source.queueLength() - target.queueLength());
        int sourceExcess = Math.max(0, source.queueLength() - comfortableQueue(source));
        int pressureDriven = Math.max(
                1,
                (int) Math.ceil(Math.max(0.0, pressureScore(source) - pressureScore(target)) * Math.max(1.0, target.serviceRatePerMinute()) * 1.9)
        );
        int imbalanceDriven = Math.max(1, (int) Math.ceil(queueGap * 0.50));
        int desired = (int) Math.ceil(
                Math.max(sourceExcess, Math.max(pressureDriven, imbalanceDriven)) * strategy.transferScale()
        );
        return Math.min(
                strategy.maxTransferCount(),
                Math.min(Math.min(source.queueLength(), remainingTargetCapacity), desired)
        );
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
            DiversionStrategyParameters strategy
    ) {
        double tagSimilarity = windowTagSimilarity(source, target) / 100.0;
        double waitReduction = Math.max(0, source.waitMinutes() - target.waitMinutes());
        double pressureGap = Math.max(0.0, pressureScore(source) - pressureScore(target));
        double rate = 0.20
                + strategy.acceptanceBias()
                + Math.min(0.36, waitReduction * strategy.waitReductionWeight())
                + Math.min(0.18, pressureGap * 0.065)
                + tagSimilarity * 0.24
                + (source.restaurantId().equals(target.restaurantId()) ? 0.10 : -0.03);

        rate += switch (userType) {
            case "HURRY" -> 0.18;
            case "BUDGET_SENSITIVE" -> 0.05;
            case "FACULTY" -> 0.06;
            case "VISITOR" -> -0.02;
            default -> 0.08;
        };

        if (profile != null) {
            rate += budgetAcceptanceDelta(source, target, profile, userType);
            rate += target.waitMinutes() <= profile.waitingToleranceMinutes() ? 0.06 : -0.02;
        }

        return clamp(rate, 0.12, 0.98);
    }

    private boolean isMeaningfullyCongested(WindowDiversionCandidate candidate) {
        return candidate.waitMinutes() >= MIN_DIVERSION_WAIT_MINUTES
                || "BUSY".equals(candidate.crowdLevel())
                || "EXTREME".equals(candidate.crowdLevel());
    }

    private boolean isReachableTarget(
            WindowDiversionCandidate source,
            WindowDiversionCandidate target
    ) {
        return source.restaurantId().equals(target.restaurantId())
                || source.campusArea().equals(target.campusArea());
    }

    private boolean hasMeaningfulNetBenefit(
            WindowDiversionCandidate source,
            WindowDiversionCandidate target
    ) {
        return source.waitMinutes() - target.waitMinutes() - estimatedWalkingMinutes(source, target)
                >= MIN_NET_WAIT_SAVING_MINUTES;
    }

    private double estimatedWalkingMinutes(
            WindowDiversionCandidate source,
            WindowDiversionCandidate target
    ) {
        return source.restaurantId().equals(target.restaurantId())
                ? SAME_RESTAURANT_WALK_MINUTES
                : SAME_AREA_WALK_MINUTES;
    }

    private double pressureScore(WindowDiversionCandidate candidate) {
        double normalizedWait = candidate.waitMinutes() / 7.0;
        double normalizedQueue = candidate.queueLength() / Math.max(1.0, candidate.serviceRatePerMinute() * 4.5);
        double loadRatio = candidate.queueLength() / Math.max(1.0, candidate.serviceRatePerMinute() * 8.0);
        double crowdComponent = switch (candidate.crowdLevel()) {
            case "NORMAL" -> 0.45;
            case "BUSY" -> 1.0;
            case "EXTREME" -> 1.65;
            default -> 0.0;
        };
        return normalizedWait * 0.92 + normalizedQueue * 0.78 + loadRatio * 0.28 + crowdComponent * 0.55;
    }

    private double strictPressureBuffer(String targetCrowdLevel) {
        return switch (targetCrowdLevel) {
            case "IDLE" -> 0.10;
            case "NORMAL" -> 0.08;
            case "BUSY" -> 0.06;
            default -> 0.04;
        };
    }

    private double fallbackTargetScore(WindowDiversionCandidate source, TargetCandidate target) {
        double pressureGap = pressureScore(source) - target.pressureScore();
        double waitGap = source.waitMinutes() - target.candidate().waitMinutes();
        double queueGap = source.queueLength() - target.candidate().queueLength();
        return pressureGap * 20.0
                + waitGap * 1.2
                + queueGap * 0.7
                + target.remainingCapacity() * 0.4
                + target.candidate().serviceRatePerMinute() * 0.5;
    }

    private double sourcePressureMargin(String targetCrowdLevel) {
        return switch (targetCrowdLevel) {
            case "IDLE" -> 0.12;
            case "NORMAL" -> 0.20;
            case "BUSY" -> 0.30;
            default -> 0.38;
        };
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
        double netGap = Math.max(0, rawGap - estimatedWalkingMinutes(source, target));
        if (netGap <= 0 || estimatedAcceptedCount <= 0) {
            return 0.0;
        }
        double loadRatio = Math.min(1.0, estimatedAcceptedCount / Math.max(1.0, source.queueLength()));
        return netGap * Math.max(0.35, loadRatio);
    }

    private String diversionReason(
            WindowDiversionCandidate source,
            WindowDiversionCandidate target,
            double acceptanceRate,
            double estimatedWaitReduction,
            double tagSimilarity
    ) {
        String similarityText = tagSimilarity >= 0.70 ? "口味相近" : tagSimilarity >= 0.45 ? "口味差异较小" : "口味存在一定差异";
        return target.restaurantName() + target.windowName()
                + "等待更短，" + similarityText
                + "，预计约 " + formatPercent(acceptanceRate)
                + " 用户接受分流，可减少约 " + formatDelta(estimatedWaitReduction) + " 分钟等待。";
    }

    private String formatPercent(double rate) {
        return Math.round(rate * 100) + "%";
    }

    private int resolveLimit(Integer limit) {
        int resolved = limit == null ? DEFAULT_LIMIT : limit;
        if (resolved < 1 || resolved > MAX_LIMIT) {
            throw new ApiException(40001, "参数校验失败", HttpStatus.BAD_REQUEST,
                    Map.of("field", "limit", "reason", "limit 必须在 1 到 10 之间"));
        }
        return resolved;
    }

    private String validateProfile(UserProfileRequest profile) {
        if (profile.budgetMin() > profile.budgetMax()) {
            throw new ApiException(40001, "参数校验失败", HttpStatus.BAD_REQUEST,
                    Map.of("field", "profile.budgetMin", "reason", "budgetMin 必须小于等于 budgetMax"));
        }
        String userType = normalizeUserType(profile.userType());
        if (!VALID_USER_TYPES.contains(userType)) {
            throw new ApiException(40002, "枚举值非法", HttpStatus.BAD_REQUEST,
                    Map.of("field", "profile.userType", "reason", "userType 不在允许范围内"));
        }
        return userType;
    }

    private String normalizeUserType(String userType) {
        return userType == null ? "" : userType.trim().toUpperCase(Locale.ROOT);
    }

    private Map<Long, WindowSnapshot> indexWindowState(SimulationTimePoint timePoint) {
        Map<Long, WindowSnapshot> result = new HashMap<>();
        for (RestaurantSnapshot restaurant : timePoint.restaurants()) {
            for (WindowSnapshot window : restaurant.windows()) {
                result.put(window.windowId(), window);
            }
        }
        return result;
    }

    private List<RecommendationItem> rank(List<RecommendationItem> items, int limit) {
        List<RecommendationItem> sorted = items.stream()
                .sorted(Comparator.comparing(RecommendationItem::score).reversed()
                        .thenComparing(RecommendationItem::targetId))
                .limit(limit)
                .toList();
        List<RecommendationItem> ranked = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ranked.add(sorted.get(i).withRank(i + 1));
        }
        return ranked;
    }

    private String buildDiversionSuggestion(SimulationTimePoint timePoint) {
        WindowCandidate source = null;
        WindowCandidate target = null;
        for (RestaurantSnapshot restaurant : timePoint.restaurants()) {
            for (WindowSnapshot window : restaurant.windows()) {
                if (source == null && ("BUSY".equals(window.crowdLevel()) || "EXTREME".equals(window.crowdLevel()))) {
                    source = new WindowCandidate(restaurant.name(), window.name(), window.waitMinutes(), window.crowdLevel());
                }
                if ("OPEN".equals(window.status())
                        && ("IDLE".equals(window.crowdLevel()) || "NORMAL".equals(window.crowdLevel()))
                        && (target == null || window.waitMinutes() < target.waitMinutes())) {
                    target = new WindowCandidate(restaurant.name(), window.name(), window.waitMinutes(), window.crowdLevel());
                }
            }
        }
        if (source == null || target == null) {
            return "当前整体排队压力可控，暂不需要明显分流。";
        }
        return "建议将" + source.restaurantName() + source.windowName()
                + "的部分用户分流至" + target.restaurantName() + target.windowName()
                + "，目标窗口等待时间约 " + target.waitMinutes() + " 分钟，可降低局部排队压力。";
    }

    private String worstCrowd(List<WindowSnapshot> windows) {
        return windows.stream()
                .map(WindowSnapshot::crowdLevel)
                .max(Comparator.comparingInt(this::crowdWeight))
                .orElse("IDLE");
    }

    private int crowdWeight(String crowdLevel) {
        return switch (crowdLevel == null ? "IDLE" : crowdLevel) {
            case "EXTREME" -> 4;
            case "BUSY" -> 3;
            case "NORMAL" -> 2;
            default -> 1;
        };
    }

    private double windowBudgetScore(WindowParameter window, UserProfileRequest profile) {
        boolean fullyInside = window.priceMin() >= profile.budgetMin() && window.priceMax() <= profile.budgetMax();
        if (fullyInside) {
            return 100.0;
        }
        boolean intersects = window.priceMin() <= profile.budgetMax() && window.priceMax() >= profile.budgetMin();
        if (intersects) {
            return 72.0;
        }
        double gap = Math.min(
                Math.abs(window.priceMin() - profile.budgetMax()),
                Math.abs(window.priceMax() - profile.budgetMin())
        );
        return gap <= 4 ? 45.0 : 15.0;
    }

    private double dishBudgetScore(DishParameter dish, UserProfileRequest profile) {
        if (dish.price() >= profile.budgetMin() && dish.price() <= profile.budgetMax()) {
            return 100.0;
        }
        double distance = dish.price() < profile.budgetMin()
                ? profile.budgetMin() - dish.price()
                : dish.price() - profile.budgetMax();
        return Math.max(5.0, 75.0 - distance * 9.0);
    }

    private double waitScore(int waitMinutes, int toleranceMinutes) {
        int safeTolerance = Math.max(1, toleranceMinutes);
        if (waitMinutes <= safeTolerance / 2) {
            return 100.0;
        }
        if (waitMinutes <= safeTolerance) {
            return 82.0;
        }
        return Math.max(0.0, 82.0 - (waitMinutes - safeTolerance) * 7.0);
    }

    private double crowdScore(String crowdLevel) {
        return switch (crowdLevel) {
            case "IDLE" -> 100.0;
            case "NORMAL" -> 85.0;
            case "BUSY" -> 50.0;
            case "EXTREME" -> 18.0;
            default -> 40.0;
        };
    }

    private List<String> matchedTags(Set<String> preferredTags, String candidateTags) {
        if (preferredTags.isEmpty()) {
            return List.of();
        }
        Set<String> candidate = TagMatcher.normalize(candidateTags);
        return preferredTags.stream()
                .filter(candidate::contains)
                .sorted()
                .toList();
    }

    private String restaurantReason(
            UserProfileRequest profile,
            String userType,
            List<String> matchedTags,
            double avgWait,
            String crowdLevel,
            double budgetScore
    ) {
        int roundedWait = (int) Math.round(avgWait);
        return String.join("，",
                matchedTagText(matchedTags),
                budgetText(budgetScore),
                "餐厅平均等待约 " + roundedWait + " 分钟",
                toleranceText(roundedWait, profile.waitingToleranceMinutes()),
                "当前拥挤度为 " + crowdLevel + advantageText(crowdLevel, roundedWait, profile.waitingToleranceMinutes()),
                personaText(userType)
        ) + "。";
    }

    private String windowReason(
            UserProfileRequest profile,
            String userType,
            List<String> matchedTags,
            int waitMinutes,
            String crowdLevel,
            double budgetScore
    ) {
        return String.join("，",
                matchedTagText(matchedTags),
                budgetText(budgetScore),
                "预计等待 " + waitMinutes + " 分钟",
                toleranceText(waitMinutes, profile.waitingToleranceMinutes()),
                "当前拥挤度为 " + crowdLevel + advantageText(crowdLevel, waitMinutes, profile.waitingToleranceMinutes()),
                personaText(userType)
        ) + "。";
    }

    private String dishReason(
            UserProfileRequest profile,
            String userType,
            List<String> matchedTags,
            int estimatedWait,
            String crowdLevel,
            double budgetScore,
            int prepTimeMinutes
    ) {
        return String.join("，",
                matchedTagText(matchedTags),
                budgetText(budgetScore),
                "预计总等待约 " + estimatedWait + " 分钟",
                toleranceText(estimatedWait, profile.waitingToleranceMinutes()),
                "所在窗口拥挤度为 " + crowdLevel,
                prepTimeMinutes <= 4 ? "出餐较快" : "出餐时间适中",
                personaText(userType)
        ) + "。";
    }

    private String matchedTagText(List<String> matchedTags) {
        if (matchedTags == null || matchedTags.isEmpty()) {
            return "口味覆盖较广";
        }
        return "匹配口味：" + String.join("、", matchedTags);
    }

    private String budgetText(double budgetScore) {
        if (budgetScore >= 95.0) {
            return "价格落在预算范围内";
        }
        if (budgetScore >= 65.0) {
            return "价格与预算接近";
        }
        return "价格偏离预算";
    }

    private String toleranceText(int waitMinutes, int toleranceMinutes) {
        return waitMinutes <= toleranceMinutes
                ? "低于等待容忍度 " + toleranceMinutes + " 分钟"
                : "高于等待容忍度 " + toleranceMinutes + " 分钟";
    }

    private String advantageText(String crowdLevel, int waitMinutes, int toleranceMinutes) {
        return crowdWeight(crowdLevel) <= crowdWeight("NORMAL") && waitMinutes <= toleranceMinutes
                ? "，相比高拥挤窗口更省时"
                : "";
    }

    private String personaText(String userType) {
        return switch (userType) {
            case "HURRY" -> "更适合赶时间用户";
            case "BUDGET_SENSITIVE" -> "更适合预算敏感用户";
            case "FACULTY" -> "更偏向稳定和舒适体验";
            case "VISITOR" -> "推荐理由较直观";
            default -> "整体在口味、预算和等待之间保持平衡";
        };
    }

    private ScoreCard scoreCard(PreferenceWeights weights, LinkedHashMap<String, Double> rawScores) {
        double weightSum = rawScores.keySet().stream()
                .mapToDouble(weights::weightOf)
                .sum();
        LinkedHashMap<String, Double> breakdown = new LinkedHashMap<>();
        double total = 0.0;
        for (Map.Entry<String, Double> entry : rawScores.entrySet()) {
            double factorWeight = weights.weightOf(entry.getKey());
            if (factorWeight <= 0 || weightSum <= 0) {
                continue;
            }
            double contribution = bounded(entry.getValue()) * factorWeight / weightSum;
            breakdown.put(entry.getKey(), round(contribution));
            total += contribution;
        }
        return new ScoreCard(Collections.unmodifiableMap(breakdown), round(total));
    }

    private LinkedHashMap<String, Double> factorScores(Object... pairs) {
        LinkedHashMap<String, Double> scores = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            scores.put((String) pairs[i], (Double) pairs[i + 1]);
        }
        return scores;
    }

    private PreferenceWeights weightsFor(String userType) {
        return switch (userType) {
            case "HURRY" -> new PreferenceWeights(0.12, 0.08, 0.38, 0.20, 0.10, 0.08, 0.04);
            case "BUDGET_SENSITIVE" -> new PreferenceWeights(0.18, 0.38, 0.18, 0.10, 0.06, 0.06, 0.04);
            case "FACULTY" -> new PreferenceWeights(0.16, 0.10, 0.28, 0.22, 0.08, 0.11, 0.05);
            case "VISITOR" -> new PreferenceWeights(0.18, 0.10, 0.18, 0.10, 0.24, 0.14, 0.06);
            default -> new PreferenceWeights(0.24, 0.18, 0.22, 0.14, 0.10, 0.07, 0.05);
        };
    }

    private double bounded(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(bounded(value) * 10.0) / 10.0;
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundTwo(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record WindowCandidate(String restaurantName, String windowName, int waitMinutes, String crowdLevel) {
    }

    private record PreferenceWeights(
            double taste,
            double budget,
            double waitWeight,
            double crowdWeightValue,
            double popularity,
            double restaurantAttraction,
            double prep
    ) {
        private double weightOf(String factor) {
            return switch (factor) {
                case "taste" -> taste;
                case "budget" -> budget;
                case "wait" -> waitWeight;
                case "crowd" -> crowdWeightValue;
                case "popularity" -> popularity;
                case "restaurantAttraction" -> restaurantAttraction;
                case "prep" -> prep;
                default -> 0.0;
            };
        }
    }

    private record ScoreCard(Map<String, Double> breakdown, double total) {
    }

    private record WindowDiversionCandidate(
            Long restaurantId,
            String restaurantName,
            String campusArea,
            Long windowId,
            String windowName,
            int queueLength,
            int waitMinutes,
            String crowdLevel,
            String matchingTags,
            double serviceRatePerMinute,
            double priceMin,
            double priceMax
    ) {
        private WindowDiversionCandidate(
                RestaurantSnapshot restaurant,
                WindowSnapshot window,
                WindowParameter parameter,
                RestaurantParameter restaurantParameter
        ) {
            this(
                    restaurant.restaurantId(),
                    restaurant.name(),
                    restaurantParameter.campusArea(),
                    window.windowId(),
                    window.name(),
                    window.queueLength(),
                    window.waitMinutes(),
                    window.crowdLevel(),
                    parameter.matchingTags(),
                    parameter.serviceRatePerMinute(),
                    parameter.priceMin(),
                    parameter.priceMax()
            );
        }

        private double averagePrice() {
            return (priceMin + priceMax) / 2.0;
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
