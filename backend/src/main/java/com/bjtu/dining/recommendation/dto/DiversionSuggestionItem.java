package com.bjtu.dining.recommendation.dto;

public record DiversionSuggestionItem(
        Long fromRestaurantId,
        Long fromWindowId,
        Long toRestaurantId,
        Long toWindowId,
        int suggestedUserCount,
        String reason
) {
}
