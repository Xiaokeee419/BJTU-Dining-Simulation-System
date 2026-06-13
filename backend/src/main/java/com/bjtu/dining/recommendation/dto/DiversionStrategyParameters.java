package com.bjtu.dining.recommendation.dto;

public record DiversionStrategyParameters(
        Double sourcePressureScale,
        Double targetPressureBufferScale,
        Double transferScale,
        Integer maxTransferCount,
        Double acceptanceBias,
        Double waitReductionWeight
) {
    public static DiversionStrategyParameters defaults() {
        return new DiversionStrategyParameters(1.0, 1.0, 1.0, 60, 0.0, 0.032);
    }

    public static DiversionStrategyParameters resolve(DiversionStrategyParameters raw) {
        DiversionStrategyParameters defaults = defaults();
        if (raw == null) {
            return defaults;
        }
        return new DiversionStrategyParameters(
                clamp(raw.sourcePressureScale(), 0.5, 1.6, defaults.sourcePressureScale()),
                clamp(raw.targetPressureBufferScale(), 0.5, 1.8, defaults.targetPressureBufferScale()),
                clamp(raw.transferScale(), 0.4, 1.8, defaults.transferScale()),
                clampInt(raw.maxTransferCount(), 5, 120, defaults.maxTransferCount()),
                clamp(raw.acceptanceBias(), -0.2, 0.25, defaults.acceptanceBias()),
                clamp(raw.waitReductionWeight(), 0.005, 0.08, defaults.waitReductionWeight())
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
