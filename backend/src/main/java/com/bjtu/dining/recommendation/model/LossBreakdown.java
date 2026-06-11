package com.bjtu.dining.recommendation.model;

public record LossBreakdown(
        double avgWaitDelta,
        double maxWaitDelta,
        double maxQueueDelta,
        double overloadedWindowMinutesDelta,
        double extremeWindowMinutesDelta,
        double unservedUserCountDelta,
        double crossRestaurantDiversionPenalty,
        double acceptedDiversionBenefit,
        double totalLoss
) {
}
