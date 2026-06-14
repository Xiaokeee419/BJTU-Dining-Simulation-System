package com.bjtu.dining.recommendation.dto;

public record OptimizationJobResult(
        String taskId,
        Long baseRunId,
        String status,
        int totalIterations,
        int currentIteration,
        double currentTemperature,
        Double currentLoss,
        Double bestLoss,
        DiversionStrategyParameters initialParameters,
        DiversionStrategyParameters currentParameters,
        DiversionStrategyParameters bestParameters,
        Long bestCompareRunId,
        String startedAt,
        String completedAt,
        String message
) {
}
