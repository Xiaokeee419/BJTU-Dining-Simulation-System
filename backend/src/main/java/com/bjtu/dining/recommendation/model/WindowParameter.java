package com.bjtu.dining.recommendation.model;

public record WindowParameter(
        Long windowId,
        Long restaurantId,
        String restaurantName,
        String name,
        String matchingTags,
        double priceMin,
        double priceMax,
        double serviceRatePerMinute,
        double popularity,
        String status
) {
}
