package com.bjtu.dining.taska.model;

import java.util.List;

public final class SimulationAnalyticsDtos {
    private SimulationAnalyticsDtos() {
    }

    public record ArrivalCurvePreviewRequest(
            TaskADtos.UserProfile profile,
            TaskADtos.SimulationScenario scenario
    ) {
    }

    public record ArrivalCurvePoint(
            int minute,
            int arrivals,
            double weight
    ) {
    }

    public record ArrivalCurveResponse(
            Long runId,
            String mealPeriod,
            int durationMinutes,
            int totalArrivals,
            List<ArrivalCurvePoint> points
    ) {
    }
}
