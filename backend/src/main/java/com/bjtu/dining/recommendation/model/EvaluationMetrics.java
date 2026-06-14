package com.bjtu.dining.recommendation.model;

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
