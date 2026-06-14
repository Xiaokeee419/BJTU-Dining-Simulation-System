package com.bjtu.dining.recommendation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record OptimizationRunRequest(
        @NotNull(message = "baseRunId cannot be null")
        Long baseRunId,
        Integer minute,
        String targetCrowdLevel,
        Integer iterationCount,
        Long randomSeed,
        @Valid
        DiversionStrategyParameters initialParameters
) {
}
