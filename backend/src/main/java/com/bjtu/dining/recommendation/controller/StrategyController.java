package com.bjtu.dining.recommendation.controller;

import com.bjtu.dining.common.ApiResponse;
import com.bjtu.dining.recommendation.dto.DiversionComparisonRequest;
import com.bjtu.dining.recommendation.dto.DiversionComparisonResult;
import com.bjtu.dining.recommendation.dto.DiversionStrategyParameters;
import com.bjtu.dining.recommendation.dto.LossConfigResponse;
import com.bjtu.dining.recommendation.dto.StrategyCompareRequest;
import com.bjtu.dining.recommendation.dto.StrategyComparisonResult;
import com.bjtu.dining.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyController {
    private final RecommendationService recommendationService;

    public StrategyController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/default-parameters")
    public ApiResponse<DiversionStrategyParameters> defaultParameters() {
        return ApiResponse.ok(recommendationService.defaultStrategyParameters());
    }

    @GetMapping("/loss-config")
    public ApiResponse<LossConfigResponse> lossConfig() {
        return ApiResponse.ok(recommendationService.lossConfig());
    }

    @PostMapping("/compare")
    public ApiResponse<StrategyComparisonResult> compare(@Valid @RequestBody StrategyCompareRequest request) {
        return ApiResponse.ok(recommendationService.compareStrategies(request));
    }

    @PostMapping("/diversion-comparison")
    public ApiResponse<DiversionComparisonResult> diversionComparison(
            @Valid @RequestBody DiversionComparisonRequest request
    ) {
        return ApiResponse.ok(recommendationService.runDiversionComparison(request));
    }
}
