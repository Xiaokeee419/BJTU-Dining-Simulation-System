package com.bjtu.dining.recommendation.model;

import java.util.List;

public record SimulationTimePoint(
        int minute,
        List<RestaurantSnapshot> restaurants
) {
}
