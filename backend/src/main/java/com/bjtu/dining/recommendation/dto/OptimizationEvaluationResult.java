package com.bjtu.dining.recommendation.dto;

import com.bjtu.dining.recommendation.model.AdvancedMetrics;
import com.bjtu.dining.recommendation.model.EvaluationMetrics;
import com.bjtu.dining.recommendation.model.LossBreakdown;

public record OptimizationEvaluationResult(
        Long baseRunId,
        Long compareRunId,
        int minute,
        String targetCrowdLevel,
        DiversionStrategyParameters strategyParameters,
        DiversionResult diversionResult,
        StrategyComparisonResult comparison,
        EvaluationMetrics baseMetrics,
        EvaluationMetrics compareMetrics,
        AdvancedMetrics baseAdvancedMetrics,
        AdvancedMetrics compareAdvancedMetrics,
        LossBreakdown lossBreakdown,
        double loss
) {
}
