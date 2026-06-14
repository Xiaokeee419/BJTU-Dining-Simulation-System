package com.bjtu.dining.recommendation.dto;

public record DiversionSuggestionItem(
        Long fromRestaurantId,
        Long fromWindowId,
        Long toRestaurantId,
        Long toWindowId,
        int suggestedUserCount,
        double acceptanceRate,
        int estimatedAcceptedCount,
        double estimatedWaitReduction,
        double tagSimilarity,
        String reason
) {
}
