package com.bjtu.dining.recommendation.model;

public record AdvancedMetrics(
        int overloadedWindowMinutes,
        int extremeWindowMinutes,
        double peakWait10m,
        double queueImbalanceIndex,
        int acceptedDiversionCount,
        int crossRestaurantDiversionCount,
        double benefitPerAcceptedDiversion
) {
}
