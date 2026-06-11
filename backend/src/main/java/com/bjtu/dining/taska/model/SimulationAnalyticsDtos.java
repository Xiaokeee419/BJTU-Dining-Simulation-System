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

    public record MinuteMetricPoint(
            int minute,
            int arrivals,
            int totalQueueLength,
            int totalCurrentCount,
            double avgWindowWait,
            int overloadedWindowCount,
            int extremeWindowCount,
            double queueImbalanceIndex
    ) {
    }

    public record MinuteMetricsResponse(
            long runId,
            List<MinuteMetricPoint> points
    ) {
    }

    public record WindowPressureItem(
            long restaurantId,
            String restaurantName,
            long windowId,
            String windowName,
            int queueLength,
            double waitMinutes,
            String crowdLevel,
            double pressureScore
    ) {
    }

    public record WindowPressurePoint(
            int minute,
            List<WindowPressureItem> windows
    ) {
    }

    public record WindowPressureResponse(
            long runId,
            List<WindowPressurePoint> points
    ) {
    }
}
