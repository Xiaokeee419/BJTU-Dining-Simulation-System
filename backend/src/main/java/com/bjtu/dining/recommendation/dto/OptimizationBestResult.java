package com.bjtu.dining.recommendation.dto;

public record OptimizationBestResult(
        String jobId,
        OptimizationEvaluationResult evaluation
) {
}
