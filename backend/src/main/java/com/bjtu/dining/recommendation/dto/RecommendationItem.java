package com.bjtu.dining.recommendation.dto;

import java.util.List;
import java.util.Map;

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
        String crowdLevel,
        Map<String, Double> scoreBreakdown,
        List<String> matchedTags
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
                crowdLevel,
                scoreBreakdown,
                matchedTags
        );
    }
}
