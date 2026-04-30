package com.bjtu.dining.taska.controller;

import com.bjtu.dining.common.ApiResponse;
import com.bjtu.dining.taska.model.TaskADtos.ScenarioPreset;
import com.bjtu.dining.taska.model.TaskADtos.UserProfilePreset;
import com.bjtu.dining.taska.service.SimulationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presets")
public class PresetController {
    private final SimulationService simulationService;

    public PresetController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/user-profiles")
    public ApiResponse<List<UserProfilePreset>> userProfilePresets() {
        return ApiResponse.ok(simulationService.userProfilePresets());
    }

    @GetMapping("/scenarios")
    public ApiResponse<List<ScenarioPreset>> scenarioPresets() {
        return ApiResponse.ok(simulationService.scenarioPresets());
    }
}
