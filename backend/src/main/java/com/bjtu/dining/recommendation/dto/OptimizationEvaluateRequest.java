package com.bjtu.dining.recommendation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record OptimizationEvaluateRequest(
        @NotNull(message = "baseRunId cannot be null")
        Long baseRunId,

        Integer minute,

        String targetCrowdLevel,

        @Valid
        DiversionStrategyParameters strategyParameters
) {
}
