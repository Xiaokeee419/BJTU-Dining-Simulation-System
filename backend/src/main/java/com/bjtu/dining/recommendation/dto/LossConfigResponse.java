package com.bjtu.dining.recommendation.dto;

public record LossConfigResponse(
        double avgWaitWeight,
        double maxWaitWeight,
        double maxQueueWeight,
        double overloadedWindowMinutesWeight,
        double extremeWindowMinutesWeight,
        double unservedUserCountWeight,
        double crossRestaurantDiversionPenaltyWeight,
        double acceptedDiversionBenefitWeight
) {
    public static LossConfigResponse defaults() {
        return new LossConfigResponse(
                1.0,
                0.8,
                0.6,
                1.2,
                1.8,
                2.5,
                0.4,
                0.3
        );
    }
}
