package com.bjtu.dining.recommendation.dto;

import com.bjtu.dining.recommendation.model.EvaluationMetrics;

public record OptimizationBestResult(
        String taskId,
        Double loss,
        Long compareRunId,
        DiversionStrategyParameters parameters,
        EvaluationMetrics metrics,
        OptimizationBottleneckMetrics bottleneckMetrics
) {
}
