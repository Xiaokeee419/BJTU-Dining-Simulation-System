package com.bjtu.dining.recommendation.dto;

import com.bjtu.dining.recommendation.model.EvaluationMetrics;

public record DiversionComparisonResult(
        Long baseRunId,
        Long compareRunId,
        int minute,
        DiversionResult diversionResult,
        StrategyComparisonResult comparison,
        EvaluationMetrics baseMetrics,
        EvaluationMetrics compareMetrics,
        String status
) {
}
