package com.bjtu.dining.taska.model;

import java.util.List;

public record FlowCurveResponse(
        String mealPeriod,
        String sourceFile,
        int sourceRecordCount,
        int bucketMinutes,
        List<FlowCurvePoint> points
) {
    public record FlowCurvePoint(
            int minute,
            int arrivals,
            int cumulativeArrivals
    ) {
    }
}
