package com.bjtu.dining.recommendation.dto;

public record OptimizationBottleneckMetrics(
        Integer sourceWindowQueueTotal,
        Double sourceWindowAverageWait,
        int maxSingleWindowQueue,
        int peakTotalQueue,
        int totalOverload,
        int extremeOverloadSeverity,
        int targetWindowOverload,
        double loadImbalancePenalty,
        int unservedUserCount
) {
}
