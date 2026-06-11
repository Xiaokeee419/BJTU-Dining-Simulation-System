package com.bjtu.dining.recommendation.controller;

import com.bjtu.dining.common.ApiResponse;
import com.bjtu.dining.recommendation.dto.DiversionRequest;
import com.bjtu.dining.recommendation.dto.DiversionResult;
import com.bjtu.dining.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping("/diversion")
    public ApiResponse<DiversionResult> diversion(@Valid @RequestBody DiversionRequest request) {
        return ApiResponse.ok(recommendationService.generateDiversion(request));
    }
}
