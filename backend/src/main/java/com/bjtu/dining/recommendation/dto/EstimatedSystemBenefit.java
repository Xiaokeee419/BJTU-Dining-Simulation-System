package com.bjtu.dining.recommendation.dto;

public record EstimatedSystemBenefit(
        int suggestedDiversionCount,
        int estimatedAcceptedCount,
        int crossRestaurantSuggestionCount,
        double averageWaitReduction,
        double totalEstimatedWaitReduction,
        double estimatedPressureRelease
) {
}
