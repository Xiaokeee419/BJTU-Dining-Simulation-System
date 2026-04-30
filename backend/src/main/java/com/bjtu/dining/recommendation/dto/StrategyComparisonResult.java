package com.bjtu.dining.recommendation.dto;

public record StrategyComparisonResult(
        Long baseRunId,
        Long compareRunId,
        double avgWaitDelta,
        int maxQueueDelta,
        int busyWindowCountDelta,
        int extremeWindowCountDelta,
        int servedUserCountDelta,
        String conclusion
) {
}
