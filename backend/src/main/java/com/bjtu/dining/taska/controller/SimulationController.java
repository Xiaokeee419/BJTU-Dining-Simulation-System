package com.bjtu.dining.taska.controller;

import com.bjtu.dining.common.ApiResponse;
import com.bjtu.dining.taska.model.TaskADtos.MetricsResponse;
import com.bjtu.dining.taska.model.TaskADtos.SimulationRunRequest;
import com.bjtu.dining.taska.model.TaskADtos.SimulationRunResult;
import com.bjtu.dining.taska.model.TaskADtos.TimelineResponse;
import com.bjtu.dining.taska.service.SimulationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {
    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/run")
    public ApiResponse<SimulationRunResult> run(@RequestBody(required = false) SimulationRunRequest request) {
        return ApiResponse.ok(simulationService.runSimulation(request));
    }

    @GetMapping("/{runId}")
    public ApiResponse<SimulationRunResult> getRunResult(@PathVariable long runId) {
        return ApiResponse.ok(simulationService.getRunResult(runId));
    }

    @GetMapping("/{runId}/timeline")
    public ApiResponse<TimelineResponse> timeline(@PathVariable long runId) {
        return ApiResponse.ok(simulationService.timeline(runId));
    }

    @GetMapping("/{runId}/metrics")
    public ApiResponse<MetricsResponse> metrics(@PathVariable long runId) {
        return ApiResponse.ok(simulationService.metrics(runId));
    }
}
