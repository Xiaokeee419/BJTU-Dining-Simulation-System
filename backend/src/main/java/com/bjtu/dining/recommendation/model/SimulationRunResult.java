package com.bjtu.dining.recommendation.model;

import java.time.OffsetDateTime;
import java.util.List;

public record SimulationRunResult(
        Long runId,
        String status,
        List<SimulationTimePoint> timePoints,
        EvaluationMetrics metrics,
        OffsetDateTime createdAt
) {
}
