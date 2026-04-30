package com.bjtu.dining.recommendation.dto;

import jakarta.validation.constraints.NotNull;

public record StrategyCompareRequest(
        @NotNull(message = "baseRunId 不能为空")
        Long baseRunId,

        @NotNull(message = "compareRunId 不能为空")
        Long compareRunId
) {
}
