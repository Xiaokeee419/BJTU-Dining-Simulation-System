package com.bjtu.dining.recommendation.controller;

import com.bjtu.dining.common.ApiResponse;
import com.bjtu.dining.recommendation.dto.StrategyCompareRequest;
import com.bjtu.dining.recommendation.dto.StrategyComparisonResult;
import com.bjtu.dining.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/strategies")
@CrossOrigin(origins = "http://localhost:5173")
public class StrategyController {

    private final RecommendationService recommendationService;

    public StrategyController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping("/compare")
    public ApiResponse<StrategyComparisonResult> compare(@Valid @RequestBody StrategyCompareRequest request) {
        return ApiResponse.ok(recommendationService.compareStrategies(request));
    }
}
