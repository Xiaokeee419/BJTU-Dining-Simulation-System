package com.bjtu.dining.recommendation.model;

import java.util.List;

public record RestaurantSnapshot(
        Long restaurantId,
        String name,
        int currentCount,
        int capacity,
        String crowdLevel,
        List<WindowSnapshot> windows
) {
}
