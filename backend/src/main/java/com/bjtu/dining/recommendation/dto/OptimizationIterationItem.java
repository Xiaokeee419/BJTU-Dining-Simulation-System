package com.bjtu.dining.recommendation.dto;

import com.bjtu.dining.recommendation.model.EvaluationMetrics;

public record OptimizationIterationItem(
        int iteration,
        double temperature,
        double loss,
        boolean accepted,
        boolean best,
        boolean acceptedWorseSolution,
        Long compareRunId,
        DiversionStrategyParameters candidateParameters,
        DiversionStrategyParameters currentParameters,
        EvaluationMetrics metrics,
        OptimizationBottleneckMetrics bottleneckMetrics,
        int suggestionCount
) {
}
