package com.bjtu.dining.recommendation.model;

import com.bjtu.dining.recommendation.dto.DiversionStrategyParameters;
import com.bjtu.dining.recommendation.dto.OptimizationEvaluationResult;

public record OptimizationCandidate(
        int iteration,
        double temperature,
        DiversionStrategyParameters parameters,
        OptimizationEvaluationResult evaluation,
        boolean accepted
) {
}
