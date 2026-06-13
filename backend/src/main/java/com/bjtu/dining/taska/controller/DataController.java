package com.bjtu.dining.taska.controller;

import com.bjtu.dining.common.ApiResponse;
import com.bjtu.dining.taska.model.DataOverviewResponse;
import com.bjtu.dining.taska.model.FlowCurveResponse;
import com.bjtu.dining.taska.service.SeedDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data")
public class DataController {
    private final SeedDataService seedDataService;

    public DataController(SeedDataService seedDataService) {
        this.seedDataService = seedDataService;
    }

    @GetMapping("/overview")
    public ApiResponse<DataOverviewResponse> overview() {
        return ApiResponse.ok(seedDataService.dataOverview());
    }

    @GetMapping("/flow-curves")
    public ApiResponse<FlowCurveResponse> flowCurves(
            @RequestParam(defaultValue = "LUNCH") String mealPeriod,
            @RequestParam(defaultValue = "3") int bucketMinutes
    ) {
        return ApiResponse.ok(seedDataService.flowCurve(mealPeriod, bucketMinutes));
    }
}
