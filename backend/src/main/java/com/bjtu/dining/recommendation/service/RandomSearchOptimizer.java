package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.recommendation.dto.DiversionStrategyParameters;
import java.util.Random;
import org.springframework.stereotype.Service;

@Service
public class RandomSearchOptimizer {
    public DiversionStrategyParameters randomize(
            DiversionStrategyParameters base,
            Random rng,
            double scale
    ) {
        DiversionStrategySupport.ResolvedParameters resolved = DiversionStrategySupport.resolve(base);
        return new DiversionStrategyParameters(
                jitter(resolved.pressureWaitWeight(), 0.25, scale, rng),
                jitter(resolved.pressureQueueWeight(), 0.25, scale, rng),
                jitter(resolved.pressureLoadWeight(), 0.25, scale, rng),
                jitter(resolved.pressureCrowdWeight(), 0.25, scale, rng),
                jitter(resolved.sourcePressureMargin(), 0.35, scale, rng),
                jitter(resolved.strictTargetPressureBuffer(), 0.30, scale, rng),
                jitter(resolved.sameRestaurantBonus(), 0.40, scale, rng),
                jitter(resolved.crossRestaurantPenalty(), 0.40, scale, rng),
                jitter(resolved.tagSimilarityWeight(), 0.45, scale, rng),
                jitter(resolved.serviceRateWeight(), 0.45, scale, rng),
                jitter(resolved.pressureGapTransferWeight(), 0.35, scale, rng),
                jitter(resolved.queueGapTransferWeight(), 0.35, scale, rng),
                jitter(resolved.sourceExcessTransferWeight(), 0.35, scale, rng),
                Math.max(5, (int) Math.round(jitter(resolved.maxTransferCount(), 0.30, scale, rng))),
                clamp(jitter(resolved.acceptanceBaseRate(), 0.45, scale, rng), 0.05, 0.60),
                jitter(resolved.waitReductionAcceptanceWeight(), 0.50, scale, rng),
                jitter(resolved.pressureGapAcceptanceWeight(), 0.50, scale, rng),
                jitter(resolved.tagSimilarityAcceptanceWeight(), 0.50, scale, rng)
        );
    }

    private double jitter(double base, double spread, double scale, Random rng) {
        double delta = (rng.nextDouble() * 2.0 - 1.0) * spread * scale;
        return Math.max(0.001, base * (1.0 + delta));
    }

    private double jitter(int base, double spread, double scale, Random rng) {
        double delta = (rng.nextDouble() * 2.0 - 1.0) * spread * scale;
        return Math.max(1.0, base * (1.0 + delta));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
