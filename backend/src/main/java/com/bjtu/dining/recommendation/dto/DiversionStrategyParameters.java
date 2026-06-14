package com.bjtu.dining.recommendation.dto;

public record DiversionStrategyParameters(
        Double sourcePressureScale,
        Double targetPressureBufferScale,
        Double transferScale,
        Integer maxTransferCount,
        Double acceptanceBias,
        Double waitReductionWeight,
        Double pressureWaitWeight,
        Double pressureQueueWeight,
        Double crossRestaurantPenalty
) {
    public static DiversionStrategyParameters defaults() {
        return new DiversionStrategyParameters(
                1.4,
                0.85,
                0.52,
                20,
                -0.18,
                0.018,
                1.0,
                1.0,
                14.0
        );
    }

    public static DiversionStrategyParameters resolve(DiversionStrategyParameters raw) {
        DiversionStrategyParameters defaults = defaults();
        if (raw == null) {
            return defaults;
        }
        return new DiversionStrategyParameters(
                clamp(raw.sourcePressureScale(), 0.35, 1.8, defaults.sourcePressureScale()),
                clamp(raw.targetPressureBufferScale(), 0.3, 2.5, defaults.targetPressureBufferScale()),
                clamp(raw.transferScale(), 0.25, 2.8, defaults.transferScale()),
                clampInt(raw.maxTransferCount(), 3, 180, defaults.maxTransferCount()),
                clamp(raw.acceptanceBias(), -0.3, 0.45, defaults.acceptanceBias()),
                clamp(raw.waitReductionWeight(), 0.003, 0.12, defaults.waitReductionWeight()),
                clamp(raw.pressureWaitWeight(), 0.5, 1.8, defaults.pressureWaitWeight()),
                clamp(raw.pressureQueueWeight(), 0.5, 2.2, defaults.pressureQueueWeight()),
                clamp(raw.crossRestaurantPenalty(), 0.0, 18.0, defaults.crossRestaurantPenalty())
        );
    }

    private static double clamp(Double value, double min, double max, double fallback) {
        if (value == null || !Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
