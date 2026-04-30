package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.common.ApiException;
import com.bjtu.dining.recommendation.dto.DiversionRequest;
import com.bjtu.dining.recommendation.dto.DiversionResult;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 10;

    private final CsvSeedRepository seedRepository;
    private final MockSimulationProvider simulationProvider;
    private final RecommendationStore recommendationStore;

    public RecommendationService(
            CsvSeedRepository seedRepository,
            MockSimulationProvider simulationProvider,
            RecommendationStore recommendationStore
    ) {
        this.seedRepository = seedRepository;
        this.simulationProvider = simulationProvider;
        this.recommendationStore = recommendationStore;
    }

    public RecommendationResult generate(RecommendationGenerateRequest request) {
        int limit = resolveLimit(request.limit());
        validateProfile(request.profile());

        SimulationRunResult simulation = loadFinishedSimulation(request.runId());
        SimulationTimePoint timePoint = selectTimePoint(simulation, request.minute());
        Set<String> preferredTags = TagMatcher.normalize(request.profile().tasteTags());
        Map<Long, WindowSnapshot> windowStateById = indexWindowState(timePoint);

        List<RecommendationItem> restaurants = rank(
                buildRestaurantRecommendations(timePoint, request.profile(), preferredTags),
                limit
        );
        List<RecommendationItem> windows = rank(
                buildWindowRecommendations(timePoint, request.profile(), preferredTags, windowStateById),
                limit
        );
        List<RecommendationItem> dishes = rank(
                buildDishRecommendations(request.profile(), preferredTags, windowStateById),
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
        SimulationRunResult simulation = loadFinishedSimulation(request.runId());
        SimulationTimePoint timePoint = selectTimePoint(simulation, request.minute());

        List<WindowDiversionCandidate> sources = findSourceWindows(timePoint, targetCrowdLevel);
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
        Set<Long> usedTargets = new HashSet<>();
        for (WindowDiversionCandidate source : sources) {
            Optional<WindowDiversionCandidate> target = targets.stream()
                    .filter(candidate -> !candidate.windowId().equals(source.windowId()))
                    .filter(candidate -> !usedTargets.contains(candidate.windowId()))
                    .filter(candidate -> candidate.waitMinutes() < source.waitMinutes())
                    .filter(candidate -> targetRemainingCapacity(candidate, targetCrowdLevel) > 0)
                    .max(Comparator.comparingDouble(candidate -> diversionTargetScore(source, candidate)));
            if (target.isEmpty()) {
                continue;
            }

            WindowDiversionCandidate selectedTarget = target.get();
            int suggestedUserCount = suggestedUserCount(source, selectedTarget, targetCrowdLevel);
            if (suggestedUserCount <= 0) {
                continue;
            }

            usedTargets.add(selectedTarget.windowId());
            suggestions.add(new DiversionSuggestionItem(
                    source.restaurantId(),
                    source.windowId(),
                    selectedTarget.restaurantId(),
                    selectedTarget.windowId(),
                    suggestedUserCount,
                    diversionReason(source, selectedTarget, suggestedUserCount)
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

    public StrategyComparisonResult compareStrategies(StrategyCompareRequest request) {
        SimulationRunResult baseSimulation = loadFinishedSimulation(request.baseRunId(), "无法比较策略");
        SimulationRunResult compareSimulation = loadFinishedSimulation(request.compareRunId(), "无法比较策略");
        EvaluationMetrics baseMetrics = baseSimulation.metrics();
        EvaluationMetrics compareMetrics = compareSimulation.metrics();
        if (baseMetrics == null || compareMetrics == null) {
            throw new ApiException(40400, "仿真评估指标不存在", HttpStatus.NOT_FOUND);
        }

        double avgWaitDelta = roundOne(compareMetrics.avgWaitMinutes() - baseMetrics.avgWaitMinutes());
        int maxQueueDelta = compareMetrics.maxQueueLength() - baseMetrics.maxQueueLength();
        int busyWindowCountDelta = compareMetrics.busyWindowCount() - baseMetrics.busyWindowCount();
        int extremeWindowCountDelta = compareMetrics.extremeWindowCount() - baseMetrics.extremeWindowCount();
        int servedUserCountDelta = compareMetrics.servedUserCount() - baseMetrics.servedUserCount();

        return new StrategyComparisonResult(
                request.baseRunId(),
                request.compareRunId(),
                avgWaitDelta,
                maxQueueDelta,
                busyWindowCountDelta,
                extremeWindowCountDelta,
                servedUserCountDelta,
                buildComparisonConclusion(
                        avgWaitDelta,
                        maxQueueDelta,
                        busyWindowCountDelta,
                        extremeWindowCountDelta,
                        servedUserCountDelta
                )
        );
    }

    private List<RecommendationItem> buildRestaurantRecommendations(
            SimulationTimePoint timePoint,
            UserProfileRequest profile,
            Set<String> preferredTags
    ) {
        List<RecommendationItem> items = new ArrayList<>();
        for (RestaurantSnapshot snapshot : timePoint.restaurants()) {
            RestaurantParameter restaurant = seedRepository.restaurant(snapshot.restaurantId());
            if (restaurant == null || !"OPEN".equals(restaurant.status())) {
                continue;
            }
            double avgWait = snapshot.windows().stream().mapToInt(WindowSnapshot::waitMinutes).average().orElse(0);
            String worstCrowd = worstCrowd(snapshot.windows());
            double tagScore = seedRepository.windowsByRestaurant(snapshot.restaurantId()).stream()
                    .mapToDouble(window -> TagMatcher.matchScore(preferredTags, window.matchingTags()))
                    .max()
                    .orElse(25.0);
            double budgetScore = seedRepository.windowsByRestaurant(snapshot.restaurantId()).stream()
                    .mapToDouble(window -> windowBudgetScore(window, profile))
                    .max()
                    .orElse(20.0);
            double score = waitScore((int) Math.round(avgWait), profile.waitingToleranceMinutes()) * 0.35
                    + crowdScore(worstCrowd) * 0.25
                    + tagScore * 0.20
                    + restaurant.baseAttraction() * 100 * 0.10
                    + budgetScore * 0.10;
            items.add(new RecommendationItem(
                    "RESTAURANT",
                    snapshot.restaurantId(),
                    snapshot.name(),
                    round(score),
                    0,
                    restaurantReason(avgWait, worstCrowd, tagScore, budgetScore),
                    snapshot.restaurantId(),
                    null,
                    (int) Math.round(avgWait),
                    worstCrowd
            ));
        }
        return items;
    }

    private List<RecommendationItem> buildWindowRecommendations(
            SimulationTimePoint timePoint,
            UserProfileRequest profile,
            Set<String> preferredTags,
            Map<Long, WindowSnapshot> windowStateById
    ) {
        List<RecommendationItem> items = new ArrayList<>();
        for (WindowParameter window : seedRepository.windows()) {
            WindowSnapshot state = windowStateById.get(window.windowId());
            if (state == null || !"OPEN".equals(state.status()) || !"OPEN".equals(window.status())) {
                continue;
            }
            double tagScore = TagMatcher.matchScore(preferredTags, window.matchingTags());
            double budgetScore = windowBudgetScore(window, profile);
            double score = waitScore(state.waitMinutes(), profile.waitingToleranceMinutes()) * 0.35
                    + crowdScore(state.crowdLevel()) * 0.20
                    + tagScore * 0.20
                    + budgetScore * 0.15
                    + window.popularity() * 100 * 0.10;
            items.add(new RecommendationItem(
                    "WINDOW",
                    window.windowId(),
                    window.name(),
                    round(score),
                    0,
                    windowReason(state.waitMinutes(), state.crowdLevel(), tagScore, budgetScore),
                    window.restaurantId(),
                    window.windowId(),
                    state.waitMinutes(),
                    state.crowdLevel()
            ));
        }
        return items;
    }

    private List<RecommendationItem> buildDishRecommendations(
            UserProfileRequest profile,
            Set<String> preferredTags,
            Map<Long, WindowSnapshot> windowStateById
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
            double prepScore = Math.max(0, 100 - dish.prepTimeMinutes() * 8.0);
            double score = tagScore * 0.30
                    + budgetScore * 0.25
                    + waitScore(estimatedWait, profile.waitingToleranceMinutes()) * 0.20
                    + dish.popularity() * 100 * 0.15
                    + prepScore * 0.10;
            items.add(new RecommendationItem(
                    "DISH",
                    dish.dishId(),
                    dish.name(),
                    round(score),
                    0,
                    dishReason(dish, estimatedWait, tagScore, budgetScore),
                    dish.restaurantId(),
                    dish.windowId(),
                    estimatedWait,
                    state.crowdLevel()
            ));
        }
        return items;
    }

    private SimulationTimePoint selectTimePoint(SimulationRunResult simulation, Integer minute) {
        if (minute == null) {
            return simulation.timePoints().stream()
                    .max(Comparator.comparing(SimulationTimePoint::minute))
                    .orElseThrow(() -> new ApiException(40401, "仿真运行记录不存在", HttpStatus.NOT_FOUND));
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

    private String buildComparisonConclusion(
            double avgWaitDelta,
            int maxQueueDelta,
            int busyWindowCountDelta,
            int extremeWindowCountDelta,
            int servedUserCountDelta
    ) {
        List<String> parts = new ArrayList<>();
        parts.add("对比场景平均等待时间" + deltaText(avgWaitDelta, "分钟"));
        parts.add("最大排队长度" + deltaText(maxQueueDelta, "人"));
        parts.add("拥挤窗口数量" + deltaText(busyWindowCountDelta, "个"));
        parts.add("极端拥挤窗口数量" + deltaText(extremeWindowCountDelta, "个"));
        parts.add("已服务人数" + deltaText(servedUserCountDelta, "人"));

        int score = 0;
        score += avgWaitDelta < 0 ? 2 : avgWaitDelta > 0 ? -2 : 0;
        score += maxQueueDelta < 0 ? 1 : maxQueueDelta > 0 ? -1 : 0;
        score += busyWindowCountDelta < 0 ? 1 : busyWindowCountDelta > 0 ? -1 : 0;
        score += extremeWindowCountDelta < 0 ? 1 : extremeWindowCountDelta > 0 ? -1 : 0;
        score += servedUserCountDelta > 0 ? 1 : servedUserCountDelta < 0 ? -1 : 0;

        String summary;
        if (score >= 3) {
            summary = "整体分流效果较好。";
        } else if (score <= -3) {
            summary = "对比场景压力有所上升，建议调整窗口开放或分流策略。";
        } else {
            summary = "整体效果变化不明显，可结合具体窗口排队情况继续观察。";
        }
        return String.join("，", parts) + "，" + summary;
    }

    private String deltaText(double delta, String unit) {
        if (delta < 0) {
            return "降低 " + formatDelta(Math.abs(delta)) + " " + unit;
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
        Set<String> validCrowdLevels = Set.of("IDLE", "NORMAL", "BUSY", "EXTREME");
        if (!validCrowdLevels.contains(normalized)) {
            throw new ApiException(40002, "枚举值非法", HttpStatus.BAD_REQUEST,
                    Map.of("field", "targetCrowdLevel", "reason", "targetCrowdLevel 不在允许范围内"));
        }
        return normalized;
    }

    private List<WindowDiversionCandidate> findSourceWindows(SimulationTimePoint timePoint, String targetCrowdLevel) {
        int targetWeight = crowdWeight(targetCrowdLevel);
        List<WindowDiversionCandidate> sources = new ArrayList<>();
        for (RestaurantSnapshot restaurant : timePoint.restaurants()) {
            for (WindowSnapshot window : restaurant.windows()) {
                WindowParameter parameter = seedRepository.window(window.windowId());
                boolean crowded = "BUSY".equals(window.crowdLevel()) || "EXTREME".equals(window.crowdLevel());
                if (parameter != null && crowded && crowdWeight(window.crowdLevel()) > targetWeight
                        && "OPEN".equals(window.status()) && "OPEN".equals(parameter.status())) {
                    sources.add(new WindowDiversionCandidate(restaurant, window, parameter));
                }
            }
        }
        return sources.stream()
                .sorted(Comparator.comparingInt(WindowDiversionCandidate::waitMinutes).reversed())
                .toList();
    }

    private List<WindowDiversionCandidate> findTargetWindows(SimulationTimePoint timePoint, String targetCrowdLevel) {
        int targetWeight = crowdWeight(targetCrowdLevel);
        List<WindowDiversionCandidate> targets = new ArrayList<>();
        for (RestaurantSnapshot restaurant : timePoint.restaurants()) {
            for (WindowSnapshot window : restaurant.windows()) {
                WindowParameter parameter = seedRepository.window(window.windowId());
                if (parameter != null
                        && "OPEN".equals(window.status())
                        && "OPEN".equals(parameter.status())
                        && crowdWeight(window.crowdLevel()) <= targetWeight) {
                    targets.add(new WindowDiversionCandidate(restaurant, window, parameter));
                }
            }
        }
        return targets;
    }

    private double diversionTargetScore(WindowDiversionCandidate source, WindowDiversionCandidate target) {
        double waitGap = Math.max(0, source.waitMinutes() - target.waitMinutes()) * 4.0;
        double tagScore = windowTagSimilarity(source, target);
        double crowdScore = 20.0 - crowdWeight(target.crowdLevel()) * 4.0;
        double serviceScore = Math.min(20.0, target.serviceRatePerMinute() * 8.0);
        return waitGap * 0.45 + tagScore * 0.30 + crowdScore * 0.15 + serviceScore * 0.10;
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
            String targetCrowdLevel
    ) {
        int sourceExcess = Math.max(0, source.queueLength() - maxQueueForLevel(source, targetCrowdLevel));
        int targetRemaining = targetRemainingCapacity(target, targetCrowdLevel);
        return Math.min(50, Math.min(sourceExcess, targetRemaining));
    }

    private int targetRemainingCapacity(WindowDiversionCandidate target, String targetCrowdLevel) {
        return Math.max(0, maxQueueForLevel(target, targetCrowdLevel) - target.queueLength());
    }

    private int maxQueueForLevel(WindowDiversionCandidate candidate, String targetCrowdLevel) {
        int maxWaitMinutes = switch (targetCrowdLevel) {
            case "IDLE" -> 4;
            case "NORMAL" -> 9;
            case "BUSY" -> 19;
            default -> Math.max(20, candidate.waitMinutes());
        };
        return Math.max(0, (int) Math.floor(maxWaitMinutes * candidate.serviceRatePerMinute()));
    }

    private String diversionReason(
            WindowDiversionCandidate source,
            WindowDiversionCandidate target,
            int suggestedUserCount
    ) {
        String tagText = windowTagSimilarity(source, target) >= 70.0 ? "口味标签相近，" : "";
        return source.restaurantName() + source.windowName()
                + "当前拥挤度为 " + source.crowdLevel()
                + "，预计等待 " + source.waitMinutes()
                + " 分钟；" + target.restaurantName() + target.windowName()
                + "等待约 " + target.waitMinutes()
                + " 分钟，" + tagText
                + "建议分流约 " + suggestedUserCount + " 人。";
    }

    private int resolveLimit(Integer limit) {
        int resolved = limit == null ? DEFAULT_LIMIT : limit;
        if (resolved < 1 || resolved > MAX_LIMIT) {
            throw new ApiException(40001, "参数校验失败", HttpStatus.BAD_REQUEST,
                    Map.of("field", "limit", "reason", "limit 必须在 1 到 10 之间"));
        }
        return resolved;
    }

    private void validateProfile(UserProfileRequest profile) {
        if (profile.budgetMin() > profile.budgetMax()) {
            throw new ApiException(40001, "参数校验失败", HttpStatus.BAD_REQUEST,
                    Map.of("field", "profile.budgetMin", "reason", "budgetMin 必须小于等于 budgetMax"));
        }
        Set<String> validUserTypes = Set.of("STUDENT", "FACULTY", "VISITOR", "HURRY", "BUDGET_SENSITIVE");
        if (!validUserTypes.contains(profile.userType())) {
            throw new ApiException(40002, "枚举值非法", HttpStatus.BAD_REQUEST,
                    Map.of("field", "profile.userType", "reason", "userType 不在允许范围内"));
        }
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
        return switch (crowdLevel) {
            case "EXTREME" -> 4;
            case "BUSY" -> 3;
            case "NORMAL" -> 2;
            default -> 1;
        };
    }

    private double windowBudgetScore(WindowParameter window, UserProfileRequest profile) {
        boolean intersects = window.priceMin() <= profile.budgetMax() && window.priceMax() >= profile.budgetMin();
        if (!intersects) {
            return 20.0;
        }
        boolean fullyInside = window.priceMin() >= profile.budgetMin() && window.priceMax() <= profile.budgetMax();
        return fullyInside ? 100.0 : 75.0;
    }

    private double dishBudgetScore(DishParameter dish, UserProfileRequest profile) {
        if (dish.price() >= profile.budgetMin() && dish.price() <= profile.budgetMax()) {
            return 100.0;
        }
        double distance = dish.price() < profile.budgetMin()
                ? profile.budgetMin() - dish.price()
                : dish.price() - profile.budgetMax();
        return Math.max(10.0, 70.0 - distance * 8.0);
    }

    private double waitScore(int waitMinutes, int toleranceMinutes) {
        if (waitMinutes <= Math.max(1, toleranceMinutes / 2)) {
            return 100.0;
        }
        if (waitMinutes <= toleranceMinutes) {
            return 80.0;
        }
        return Math.max(0.0, 80.0 - (waitMinutes - toleranceMinutes) * 6.0);
    }

    private double crowdScore(String crowdLevel) {
        return switch (crowdLevel) {
            case "IDLE" -> 100.0;
            case "NORMAL" -> 85.0;
            case "BUSY" -> 50.0;
            case "EXTREME" -> 20.0;
            default -> 40.0;
        };
    }

    private String restaurantReason(double avgWait, String crowdLevel, double tagScore, double budgetScore) {
        return reasonPrefix(tagScore, budgetScore)
                + "餐厅平均等待约 " + Math.round(avgWait) + " 分钟，当前拥挤度为 " + crowdLevel + "。";
    }

    private String windowReason(int waitMinutes, String crowdLevel, double tagScore, double budgetScore) {
        return reasonPrefix(tagScore, budgetScore)
                + "预计等待 " + waitMinutes + " 分钟，当前拥挤度为 " + crowdLevel + "。";
    }

    private String dishReason(DishParameter dish, int estimatedWait, double tagScore, double budgetScore) {
        String budgetText = budgetScore >= 100 ? "价格在预算范围内" : "价格与预算接近";
        String tasteText = tagScore >= 70 ? "符合口味偏好" : "口味匹配度一般";
        return tasteText + "，" + budgetText + "，热门度较高，预计总等待约 "
                + estimatedWait + " 分钟。";
    }

    private String reasonPrefix(double tagScore, double budgetScore) {
        String tasteText = tagScore >= 70 ? "口味匹配，" : "口味匹配度一般，";
        String budgetText = budgetScore >= 75 ? "预算匹配，" : "预算匹配度一般，";
        return tasteText + budgetText;
    }

    private double round(double value) {
        return Math.round(Math.max(0, Math.min(100, value)) * 10.0) / 10.0;
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record WindowCandidate(String restaurantName, String windowName, int waitMinutes, String crowdLevel) {
    }

    private record WindowDiversionCandidate(
            Long restaurantId,
            String restaurantName,
            Long windowId,
            String windowName,
            int queueLength,
            int waitMinutes,
            String crowdLevel,
            String matchingTags,
            double serviceRatePerMinute
    ) {
        private WindowDiversionCandidate(
                RestaurantSnapshot restaurant,
                WindowSnapshot window,
                WindowParameter parameter
        ) {
            this(
                    restaurant.restaurantId(),
                    restaurant.name(),
                    window.windowId(),
                    window.name(),
                    window.queueLength(),
                    window.waitMinutes(),
                    window.crowdLevel(),
                    parameter.matchingTags(),
                    parameter.serviceRatePerMinute()
            );
        }
    }
}
