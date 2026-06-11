package com.bjtu.dining.recommendation.service;

import java.util.Random;
import org.springframework.stereotype.Service;

@Service
public class SimulatedAnnealingOptimizer {
    public double temperatureAt(int iteration, int totalIterations) {
        if (totalIterations <= 1) {
            return 0.01;
        }
        double progress = (iteration - 1.0) / (totalIterations - 1.0);
        return Math.max(0.01, 1.0 - progress * 0.92);
    }

    public boolean accept(double currentLoss, double candidateLoss, double temperature, Random rng) {
        if (candidateLoss <= currentLoss) {
            return true;
        }
        double probability = Math.exp(-(candidateLoss - currentLoss) / Math.max(0.0001, temperature));
        return rng.nextDouble() < probability;
    }
}
