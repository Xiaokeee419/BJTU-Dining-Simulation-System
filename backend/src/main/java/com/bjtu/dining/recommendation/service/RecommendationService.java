package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.common.ApiException;
import com.bjtu.dining.recommendation.dto.RecommendationGenerateRequest;
import com.bjtu.dining.recommendation.dto.RecommendationItem;
import com.bjtu.dining.recommendation.dto.RecommendationResult;
import com.bjtu.dining.recommendation.dto.UserProfileRequest;
import com.bjtu.dining.recommendation.model.DishParameter;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        SimulationRunResult simulation = simulationProvider.findByRunId(request.runId());
        if (!"FINISHED".equals(simulation.status())) {
            throw new ApiException(40901, "仿真尚未完成，无法生成推荐", HttpStatus.CONFLICT);
        }

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

    private record WindowCandidate(String restaurantName, String windowName, int waitMinutes, String crowdLevel) {
    }
}
