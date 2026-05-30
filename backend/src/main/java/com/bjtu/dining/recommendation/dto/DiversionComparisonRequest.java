package com.bjtu.dining.recommendation.dto;

import jakarta.validation.constraints.NotNull;

public record DiversionComparisonRequest(
        @NotNull(message = "baseRunId cannot be null")
        Long baseRunId,

        Integer minute,

        String targetCrowdLevel,

        Boolean autoRunCompare
) {
}
