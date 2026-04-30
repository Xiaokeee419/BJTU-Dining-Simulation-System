package com.bjtu.dining.recommendation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DiversionRequest(
        @NotNull(message = "runId 不能为空")
        Long runId,

        Integer minute,

        @NotBlank(message = "targetCrowdLevel 不能为空")
        String targetCrowdLevel
) {
}
