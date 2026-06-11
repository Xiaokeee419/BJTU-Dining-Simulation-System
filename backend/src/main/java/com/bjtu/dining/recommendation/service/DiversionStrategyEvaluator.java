package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.recommendation.dto.DiversionRequest;
import com.bjtu.dining.recommendation.dto.DiversionResult;
import com.bjtu.dining.recommendation.dto.DiversionStrategyParameters;
import com.bjtu.dining.recommendation.dto.LossConfigResponse;
import com.bjtu.dining.recommendation.dto.OptimizationEvaluationResult;
import com.bjtu.dining.recommendation.dto.StrategyCompareRequest;
import com.bjtu.dining.recommendation.dto.StrategyComparisonResult;
import com.bjtu.dining.recommendation.dto.UserProfileRequest;
import com.bjtu.dining.recommendation.model.AdvancedMetrics;
import com.bjtu.dining.recommendation.model.EvaluationMetrics;
import com.bjtu.dining.recommendation.model.LossBreakdown;
import com.bjtu.dining.taska.model.TaskADtos;
import com.bjtu.dining.taska.service.SimulationService;
import java.util.Comparator;
import org.springframework.stereotype.Service;

@Service
public class DiversionStrategyEvaluator {
    private final RecommendationService recommendationService;
    private final SimulationService simulationService;

    public DiversionStrategyEvaluator(
            RecommendationService recommendationService,
            SimulationService simulationService
    ) {
        this.recommendationService = recommendationService;
        this.simulationService = simulationService;
    }

    public OptimizationEvaluationResult evaluate(
            long baseRunId,
            Integer minute,
            String targetCrowdLevel,
            DiversionStrategyParameters strategyParameters
    ) {
        TaskADtos.SimulationRunResult baseRun = simulationService.getRunResult(baseRunId);
        int resolvedMinute = minute != null ? minute : resolvePeakMinute(baseRun);
        String resolvedCrowdLevel = targetCrowdLevel == null || targetCrowdLevel.isBlank()
                ? "NORMAL"
                : targetCrowdLevel;
        UserProfileRequest profile = new UserProfileRequest(
                baseRun.profile().userType(),
                baseRun.profile().tasteTags(),
                baseRun.profile().budgetMin(),
                baseRun.profile().budgetMax(),
                baseRun.profile().waitingToleranceMinutes()
        );
        DiversionResult diversionResult = recommendationService.generateDiversion(new DiversionRequest(
                baseRunId,
                resolvedMinute,
                resolvedCrowdLevel,
                profile,
                strategyParameters
        ));

        EvaluationMetrics baseMetrics = mapMetrics(baseRun.metrics());
        AdvancedMetrics baseAdvancedMetrics = simulationService.advancedMetrics(baseRunId);
        if (diversionResult.suggestions().isEmpty()) {
            LossBreakdown lossBreakdown = calculateLoss(
                    baseMetrics,
                    baseMetrics,
                    baseAdvancedMetrics,
                    baseAdvancedMetrics,
                    diversionResult
            );
            return new OptimizationEvaluationResult(
                    baseRunId,
                    null,
                    resolvedMinute,
                    resolvedCrowdLevel,
                    diversionResult.strategySnapshot(),
                    diversionResult,
                    null,
                    baseMetrics,
                    baseMetrics,
                    baseAdvancedMetrics,
                    baseAdvancedMetrics,
                    lossBreakdown,
                    lossBreakdown.totalLoss()
            );
        }

        TaskADtos.SimulationRunResult compareRun = simulationService.runSimulationWithDiversion(
                new TaskADtos.SimulationRunWithDiversionRequest(
                        baseRunId,
                        resolvedMinute,
                        resolvedCrowdLevel,
                        diversionResult.suggestions().stream()
                                .map(item -> new TaskADtos.DiversionSuggestion(
                                        item.fromRestaurantId(),
                                        item.fromWindowId(),
                                        item.toRestaurantId(),
                                        item.toWindowId(),
                                        item.suggestedUserCount(),
                                        item.acceptanceRate(),
                                        item.estimatedAcceptedCount()
                                ))
                                .toList(),
                        diversionResult.strategySnapshot()
                )
        );

        EvaluationMetrics compareMetrics = mapMetrics(compareRun.metrics());
        AdvancedMetrics compareAdvancedMetrics = simulationService.advancedMetrics(compareRun.runId());
        StrategyComparisonResult comparison = recommendationService.compareStrategies(new StrategyCompareRequest(
                baseRunId,
                compareRun.runId(),
                "DIVERSION_AFTER"
        ));
        LossBreakdown lossBreakdown = calculateLoss(
                baseMetrics,
                compareMetrics,
                baseAdvancedMetrics,
                compareAdvancedMetrics,
                diversionResult
        );
        return new OptimizationEvaluationResult(
                baseRunId,
                compareRun.runId(),
                resolvedMinute,
                resolvedCrowdLevel,
                diversionResult.strategySnapshot(),
                diversionResult,
                comparison,
                baseMetrics,
                compareMetrics,
                baseAdvancedMetrics,
                compareAdvancedMetrics,
                lossBreakdown,
                lossBreakdown.totalLoss()
        );
    }

    private LossBreakdown calculateLoss(
            EvaluationMetrics baseMetrics,
            EvaluationMetrics compareMetrics,
            AdvancedMetrics baseAdvancedMetrics,
            AdvancedMetrics compareAdvancedMetrics,
            DiversionResult diversionResult
    ) {
        LossConfigResponse config = LossConfigResponse.defaults();
        double avgWaitDelta = compareMetrics.avgWaitMinutes() - baseMetrics.avgWaitMinutes();
        double maxWaitDelta = compareMetrics.maxWaitMinutes() - baseMetrics.maxWaitMinutes();
        double maxQueueDelta = compareMetrics.maxQueueLength() - baseMetrics.maxQueueLength();
        double overloadedWindowMinutesDelta =
                compareAdvancedMetrics.overloadedWindowMinutes() - baseAdvancedMetrics.overloadedWindowMinutes();
        double extremeWindowMinutesDelta =
                compareAdvancedMetrics.extremeWindowMinutes() - baseAdvancedMetrics.extremeWindowMinutes();
        double unservedUserCountDelta = compareMetrics.unservedUserCount() - baseMetrics.unservedUserCount();
        double crossRestaurantPenalty = diversionResult.estimatedSystemBenefit().crossRestaurantSuggestionCount();
        double acceptedBenefit = diversionResult.estimatedSystemBenefit().estimatedAcceptedCount()
                * Math.max(0.0, diversionResult.estimatedSystemBenefit().averageWaitReduction());

        double totalLoss = config.avgWaitWeight() * avgWaitDelta
                + config.maxWaitWeight() * maxWaitDelta
                + config.maxQueueWeight() * maxQueueDelta
                + config.overloadedWindowMinutesWeight() * overloadedWindowMinutesDelta
                + config.extremeWindowMinutesWeight() * extremeWindowMinutesDelta
                + config.unservedUserCountWeight() * unservedUserCountDelta
                + config.crossRestaurantDiversionPenaltyWeight() * crossRestaurantPenalty
                - config.acceptedDiversionBenefitWeight() * acceptedBenefit / 10.0;

        return new LossBreakdown(
                round(avgWaitDelta),
                round(maxWaitDelta),
                round(maxQueueDelta),
                round(overloadedWindowMinutesDelta),
                round(extremeWindowMinutesDelta),
                round(unservedUserCountDelta),
                round(crossRestaurantPenalty),
                round(acceptedBenefit),
                round(totalLoss)
        );
    }

    private EvaluationMetrics mapMetrics(TaskADtos.EvaluationMetrics source) {
        return new EvaluationMetrics(
                source.avgWaitMinutes(),
                source.maxWaitMinutes(),
                source.maxQueueLength(),
                source.busyWindowCount(),
                source.extremeWindowCount(),
                source.totalVirtualUsers(),
                source.servedUserCount(),
                source.unservedUserCount()
        );
    }

    private int resolvePeakMinute(TaskADtos.SimulationRunResult run) {
        return run.timePoints().stream()
                .max(Comparator
                        .comparingInt(this::totalQueueLength)
                        .thenComparingInt(this::totalCurrentCount)
                        .thenComparingInt(TaskADtos.SimulationTimePoint::minute))
                .map(TaskADtos.SimulationTimePoint::minute)
                .orElse(0);
    }

    private int totalQueueLength(TaskADtos.SimulationTimePoint point) {
        return point.restaurants().stream()
                .flatMap(restaurant -> restaurant.windows().stream())
                .mapToInt(TaskADtos.QueueState::queueLength)
                .sum();
    }

    private int totalCurrentCount(TaskADtos.SimulationTimePoint point) {
        return point.restaurants().stream()
                .mapToInt(TaskADtos.RestaurantTimePoint::currentCount)
                .sum();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
