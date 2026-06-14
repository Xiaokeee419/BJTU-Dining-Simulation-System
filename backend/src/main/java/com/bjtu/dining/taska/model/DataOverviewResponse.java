package com.bjtu.dining.taska.model;

import java.util.List;

public record DataOverviewResponse(
        String sourceDirectory,
        int studentCount,
        int restaurantCount,
        int windowCount,
        int openWindowCount,
        int dishCount,
        List<String> userTypes,
        List<String> mealPeriods,
        List<String> dataFiles
) {
}
