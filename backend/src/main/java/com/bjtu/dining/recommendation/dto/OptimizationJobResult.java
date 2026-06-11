package com.bjtu.dining.recommendation.dto;

public record OptimizationJobResult(
        String jobId,
        Long baseRunId,
        String method,
        String status,
        int totalIterations,
        int currentIteration,
        Double bestLoss,
        DiversionStrategyParameters bestParameters,
        String startedAt,
        String completedAt,
        String message
) {
}
