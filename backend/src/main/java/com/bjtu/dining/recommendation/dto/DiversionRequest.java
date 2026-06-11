package com.bjtu.dining.recommendation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DiversionRequest(
        @NotNull(message = "runId cannot be null")
        Long runId,

        Integer minute,

        @NotBlank(message = "targetCrowdLevel cannot be blank")
        String targetCrowdLevel,

        @Valid
        UserProfileRequest profile,

        @Valid
        DiversionStrategyParameters strategyParameters
) {
}
