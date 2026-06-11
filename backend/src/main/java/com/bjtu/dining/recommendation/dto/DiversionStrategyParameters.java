package com.bjtu.dining.recommendation.dto;

public record DiversionStrategyParameters(
        Double pressureWaitWeight,
        Double pressureQueueWeight,
        Double pressureLoadWeight,
        Double pressureCrowdWeight,
        Double sourcePressureMargin,
        Double strictTargetPressureBuffer,
        Double sameRestaurantBonus,
        Double crossRestaurantPenalty,
        Double tagSimilarityWeight,
        Double serviceRateWeight,
        Double pressureGapTransferWeight,
        Double queueGapTransferWeight,
        Double sourceExcessTransferWeight,
        Integer maxTransferCount,
        Double acceptanceBaseRate,
        Double waitReductionAcceptanceWeight,
        Double pressureGapAcceptanceWeight,
        Double tagSimilarityAcceptanceWeight
) {
    public static DiversionStrategyParameters defaults() {
        return new DiversionStrategyParameters(
                0.92,
                0.78,
                0.28,
                0.55,
                0.20,
                0.08,
                6.0,
                3.0,
                0.20,
                0.10,
                1.90,
                0.50,
                1.00,
                60,
                0.20,
                0.032,
                0.065,
                0.24
        );
    }
}
