package com.bjtu.dining.taska.controller;

import com.bjtu.dining.common.ApiResponse;
import com.bjtu.dining.taska.model.TaskADtos.DishParameter;
import com.bjtu.dining.taska.model.TaskADtos.RestaurantParameter;
import com.bjtu.dining.taska.model.TaskADtos.WindowParameter;
import com.bjtu.dining.taska.service.SeedDataService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parameters")
public class ParameterController {
    private final SeedDataService seedDataService;

    public ParameterController(SeedDataService seedDataService) {
        this.seedDataService = seedDataService;
    }

    @GetMapping("/restaurants")
    public ApiResponse<List<RestaurantParameter>> restaurants() {
        return ApiResponse.ok(seedDataService.restaurantParameters());
    }

    @GetMapping("/windows")
    public ApiResponse<List<WindowParameter>> windows(@RequestParam(required = false) Long restaurantId) {
        return ApiResponse.ok(seedDataService.windowParameters(restaurantId));
    }

    @GetMapping("/dishes")
    public ApiResponse<List<DishParameter>> dishes(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) Long windowId
    ) {
        return ApiResponse.ok(seedDataService.dishParameters(restaurantId, windowId));
    }
}
