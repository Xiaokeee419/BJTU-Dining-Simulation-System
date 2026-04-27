package com.bjtu.dining.recommendation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RecommendationGenerateRequest(
        @NotNull(message = "runId 不能为空")
        Long runId,
        Integer minute,
        @Valid
        @NotNull(message = "profile 不能为空")
        UserProfileRequest profile,
        Integer limit
) {
}
