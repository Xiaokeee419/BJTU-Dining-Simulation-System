package com.bjtu.dining.recommendation.model;

public record WindowSnapshot(
        Long windowId,
        String name,
        int queueLength,
        int servingCount,
        int waitMinutes,
        String crowdLevel,
        String status
) {
}
