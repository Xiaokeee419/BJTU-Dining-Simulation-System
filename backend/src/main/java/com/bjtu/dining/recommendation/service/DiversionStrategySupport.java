package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.recommendation.dto.DiversionStrategyParameters;

public final class DiversionStrategySupport {
    private DiversionStrategySupport() {
    }

    public static ResolvedParameters resolve(DiversionStrategyParameters raw) {
        DiversionStrategyParameters defaults = DiversionStrategyParameters.defaults();
        DiversionStrategyParameters source = raw == null ? defaults : raw;
        return new ResolvedParameters(
                positive(source.pressureWaitWeight(), defaults.pressureWaitWeight()),
                positive(source.pressureQueueWeight(), defaults.pressureQueueWeight()),
                positive(source.pressureLoadWeight(), defaults.pressureLoadWeight()),
                positive(source.pressureCrowdWeight(), defaults.pressureCrowdWeight()),
                positive(source.sourcePressureMargin(), defaults.sourcePressureMargin()),
                positive(source.strictTargetPressureBuffer(), defaults.strictTargetPressureBuffer()),
                positive(source.sameRestaurantBonus(), defaults.sameRestaurantBonus()),
                positive(source.crossRestaurantPenalty(), defaults.crossRestaurantPenalty()),
                positive(source.tagSimilarityWeight(), defaults.tagSimilarityWeight()),
                positive(source.serviceRateWeight(), defaults.serviceRateWeight()),
                positive(source.pressureGapTransferWeight(), defaults.pressureGapTransferWeight()),
                positive(source.queueGapTransferWeight(), defaults.queueGapTransferWeight()),
                positive(source.sourceExcessTransferWeight(), defaults.sourceExcessTransferWeight()),
                positiveInt(source.maxTransferCount(), defaults.maxTransferCount()),
                positive(source.acceptanceBaseRate(), defaults.acceptanceBaseRate()),
                positive(source.waitReductionAcceptanceWeight(), defaults.waitReductionAcceptanceWeight()),
                positive(source.pressureGapAcceptanceWeight(), defaults.pressureGapAcceptanceWeight()),
                positive(source.tagSimilarityAcceptanceWeight(), defaults.tagSimilarityAcceptanceWeight())
        );
    }

    public static DiversionStrategyParameters snapshot(ResolvedParameters resolved) {
        return new DiversionStrategyParameters(
                resolved.pressureWaitWeight(),
                resolved.pressureQueueWeight(),
                resolved.pressureLoadWeight(),
                resolved.pressureCrowdWeight(),
                resolved.sourcePressureMargin(),
                resolved.strictTargetPressureBuffer(),
                resolved.sameRestaurantBonus(),
                resolved.crossRestaurantPenalty(),
                resolved.tagSimilarityWeight(),
                resolved.serviceRateWeight(),
                resolved.pressureGapTransferWeight(),
                resolved.queueGapTransferWeight(),
                resolved.sourceExcessTransferWeight(),
                resolved.maxTransferCount(),
                resolved.acceptanceBaseRate(),
                resolved.waitReductionAcceptanceWeight(),
                resolved.pressureGapAcceptanceWeight(),
                resolved.tagSimilarityAcceptanceWeight()
        );
    }

    private static double positive(Double value, double fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static int positiveInt(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    public record ResolvedParameters(
            double pressureWaitWeight,
            double pressureQueueWeight,
            double pressureLoadWeight,
            double pressureCrowdWeight,
            double sourcePressureMargin,
            double strictTargetPressureBuffer,
            double sameRestaurantBonus,
            double crossRestaurantPenalty,
            double tagSimilarityWeight,
            double serviceRateWeight,
            double pressureGapTransferWeight,
            double queueGapTransferWeight,
            double sourceExcessTransferWeight,
            int maxTransferCount,
            double acceptanceBaseRate,
            double waitReductionAcceptanceWeight,
            double pressureGapAcceptanceWeight,
            double tagSimilarityAcceptanceWeight
    ) {
    }
}
