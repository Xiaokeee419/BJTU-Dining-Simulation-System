package com.bjtu.dining.recommendation.dto;

public record StrategyComparisonResult(
        Long baseRunId,
        Long compareRunId,
        String compareType,
        double avgWaitDelta,
        int maxWaitDelta,
        int maxQueueDelta,
        int busyWindowCountDelta,
        int extremeWindowCountDelta,
        int servedUserCountDelta,
        int unservedUserCountDelta,
        String conclusion
) {
}
