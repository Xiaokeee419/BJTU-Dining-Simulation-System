package com.bjtu.dining.recommendation.dto;

public record RecommendationItem(
        String targetType,
        Long targetId,
        String name,
        double score,
        int rank,
        String reason,
        Long relatedRestaurantId,
        Long relatedWindowId,
        int estimatedWaitMinutes,
        String crowdLevel
) {
    public RecommendationItem withRank(int newRank) {
        return new RecommendationItem(
                targetType,
                targetId,
                name,
                score,
                newRank,
                reason,
                relatedRestaurantId,
                relatedWindowId,
                estimatedWaitMinutes,
                crowdLevel
        );
    }
}
