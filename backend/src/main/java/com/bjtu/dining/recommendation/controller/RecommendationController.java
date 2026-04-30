package com.bjtu.dining.recommendation.controller;

import com.bjtu.dining.common.ApiResponse;
import com.bjtu.dining.recommendation.dto.DiversionRequest;
import com.bjtu.dining.recommendation.dto.DiversionResult;
import com.bjtu.dining.recommendation.dto.RecommendationGenerateRequest;
import com.bjtu.dining.recommendation.dto.RecommendationResult;
import com.bjtu.dining.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
@CrossOrigin(origins = "http://localhost:5173")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping("/generate")
    public ApiResponse<RecommendationResult> generate(@Valid @RequestBody RecommendationGenerateRequest request) {
        return ApiResponse.ok(recommendationService.generate(request));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<RecommendationResult> getByRunId(
            @PathVariable Long runId,
            @RequestParam(required = false) Integer minute
    ) {
        return ApiResponse.ok(recommendationService.getGenerated(runId, minute));
    }

    @PostMapping("/diversion")
    public ApiResponse<DiversionResult> diversion(@Valid @RequestBody DiversionRequest request) {
        return ApiResponse.ok(recommendationService.generateDiversion(request));
    }
}
