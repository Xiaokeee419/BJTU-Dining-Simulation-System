package com.bjtu.dining.recommendation.model;

public record RestaurantParameter(
        Long restaurantId,
        String name,
        String campusArea,
        String location,
        int capacity,
        double baseAttraction,
        String status
) {
}
