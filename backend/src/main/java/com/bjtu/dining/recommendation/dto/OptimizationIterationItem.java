package com.bjtu.dining.recommendation.dto;

public record OptimizationIterationItem(
        int iteration,
        double temperature,
        DiversionStrategyParameters strategyParameters,
        double loss,
        boolean accepted,
        boolean best,
        EstimatedSystemBenefit estimatedSystemBenefit
) {
}
