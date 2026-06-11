package com.bjtu.dining.recommendation.controller;

import com.bjtu.dining.common.ApiResponse;
import com.bjtu.dining.recommendation.dto.OptimizationBestResult;
import com.bjtu.dining.recommendation.dto.OptimizationEvaluateRequest;
import com.bjtu.dining.recommendation.dto.OptimizationEvaluationResult;
import com.bjtu.dining.recommendation.dto.OptimizationIterationItem;
import com.bjtu.dining.recommendation.dto.OptimizationJobResult;
import com.bjtu.dining.recommendation.dto.OptimizationRunRequest;
import com.bjtu.dining.recommendation.service.OptimizationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/optimizations")
public class OptimizationController {
    private final OptimizationService optimizationService;

    public OptimizationController(OptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    @PostMapping("/evaluate")
    public ApiResponse<OptimizationEvaluationResult> evaluate(
            @Valid @RequestBody OptimizationEvaluateRequest request
    ) {
        return ApiResponse.ok(optimizationService.evaluate(request));
    }

    @PostMapping("/run")
    public ApiResponse<OptimizationJobResult> run(@Valid @RequestBody OptimizationRunRequest request) {
        return ApiResponse.ok(optimizationService.run(request));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<OptimizationJobResult> job(@PathVariable String jobId) {
        return ApiResponse.ok(optimizationService.getJob(jobId));
    }

    @GetMapping("/{jobId}/iterations")
    public ApiResponse<List<OptimizationIterationItem>> iterations(@PathVariable String jobId) {
        return ApiResponse.ok(optimizationService.iterations(jobId));
    }

    @GetMapping("/{jobId}/best")
    public ApiResponse<OptimizationBestResult> best(@PathVariable String jobId) {
        return ApiResponse.ok(optimizationService.best(jobId));
    }
}
