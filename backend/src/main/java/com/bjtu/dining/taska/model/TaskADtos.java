package com.bjtu.dining.taska.model;

import java.util.List;

public final class TaskADtos {
    private TaskADtos() {
    }

    public record UserProfilePreset(
            String profileId,
            String name,
            String userType,
            List<String> tasteTags,
            double budgetMin,
            double budgetMax,
            int waitingToleranceMinutes
    ) {
    }

    public record ScenarioPreset(
            String scenarioId,
            String name,
            String mealPeriod,
            String dayType,
            String crowdLevel,
            double weatherFactor,
            double eventFactor,
            List<Long> closedWindowIds,
            int virtualUserCount,
            int durationMinutes,
            int stepMinutes,
            long randomSeed
    ) {
    }

    public record RestaurantParameter(
            long restaurantId,
            String name,
            String location,
            int capacity,
            double baseAttraction,
            String status
    ) {
    }

    public record WindowParameter(
            long windowId,
            long restaurantId,
            String name,
            double serviceRatePerMinute,
            String status
    ) {
    }

    public record DishParameter(
            long dishId,
            long restaurantId,
            long windowId,
            String name,
            double price,
            int prepTimeMinutes,
            double popularity,
            List<String> tags
    ) {
    }

    public record SimulationRunRequest(
            UserProfile profile,
            SimulationScenario scenario
    ) {
    }

    public record UserProfile(
            String userType,
            List<String> tasteTags,
            Double budgetMin,
            Double budgetMax,
            Integer waitingToleranceMinutes
    ) {
    }

    public record SimulationScenario(
            String mealPeriod,
            String dayType,
            String crowdLevel,
            Double weatherFactor,
            Double eventFactor,
            List<Long> closedWindowIds,
            Integer virtualUserCount,
            Integer durationMinutes,
            Integer stepMinutes,
            Long randomSeed
    ) {
    }

    public record QueueState(
            long windowId,
            String name,
            int queueLength,
            int servingCount,
            double waitMinutes,
            String crowdLevel,
            String status
    ) {
    }

    public record RestaurantTimePoint(
            long restaurantId,
            String name,
            int currentCount,
            int capacity,
            String crowdLevel,
            List<QueueState> windows
    ) {
    }

    public record SimulationTimePoint(
            int minute,
            List<RestaurantTimePoint> restaurants
    ) {
    }

    public record EvaluationMetrics(
            double avgWaitMinutes,
            double maxWaitMinutes,
            int maxQueueLength,
            int busyWindowCount,
            int extremeWindowCount,
            int totalVirtualUsers,
            int servedUserCount,
            int unservedUserCount
    ) {
    }

    public record SimulationRunResult(
            long runId,
            String status,
            UserProfile profile,
            SimulationScenario scenario,
            List<SimulationTimePoint> timePoints,
            EvaluationMetrics metrics,
            String createdAt
    ) {
    }

    public record TimelineResponse(
            long runId,
            List<SimulationTimePoint> timePoints
    ) {
    }

    public record MetricsResponse(
            long runId,
            EvaluationMetrics metrics
    ) {
    }
}
