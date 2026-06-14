package com.bjtu.dining.recommendation.model;

public record DishParameter(
        Long dishId,
        Long windowId,
        Long restaurantId,
        String name,
        double price,
        int prepTimeMinutes,
        double popularity,
        String matchingTags
) {
}
