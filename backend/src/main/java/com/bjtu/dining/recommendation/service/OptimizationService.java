package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.common.BadRequestException;
import com.bjtu.dining.common.ResourceNotFoundException;
import com.bjtu.dining.recommendation.dto.DiversionStrategyParameters;
import com.bjtu.dining.recommendation.dto.OptimizationBestResult;
import com.bjtu.dining.recommendation.dto.OptimizationEvaluateRequest;
import com.bjtu.dining.recommendation.dto.OptimizationEvaluationResult;
import com.bjtu.dining.recommendation.dto.OptimizationIterationItem;
import com.bjtu.dining.recommendation.dto.OptimizationJobResult;
import com.bjtu.dining.recommendation.dto.OptimizationRunRequest;
import com.bjtu.dining.recommendation.model.OptimizationJobState;
import com.bjtu.dining.recommendation.repository.OptimizationStore;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class OptimizationService {
    private final DiversionStrategyEvaluator evaluator;
    private final OptimizationStore optimizationStore;
    private final RandomSearchOptimizer randomSearchOptimizer;
    private final SimulatedAnnealingOptimizer simulatedAnnealingOptimizer;

    public OptimizationService(
            DiversionStrategyEvaluator evaluator,
            OptimizationStore optimizationStore,
            RandomSearchOptimizer randomSearchOptimizer,
            SimulatedAnnealingOptimizer simulatedAnnealingOptimizer
    ) {
        this.evaluator = evaluator;
        this.optimizationStore = optimizationStore;
        this.randomSearchOptimizer = randomSearchOptimizer;
        this.simulatedAnnealingOptimizer = simulatedAnnealingOptimizer;
    }

    public OptimizationEvaluationResult evaluate(OptimizationEvaluateRequest request) {
        return evaluator.evaluate(
                request.baseRunId(),
                request.minute(),
                request.targetCrowdLevel(),
                request.strategyParameters()
        );
    }

    public OptimizationJobResult run(OptimizationRunRequest request) {
        int totalIterations = request.iterationCount() == null ? 24 : request.iterationCount();
        if (totalIterations < 1 || totalIterations > 200) {
            throw new BadRequestException("iterationCount", "iterationCount must be between 1 and 200");
        }
        String method = normalizeMethod(request.method());
        String jobId = UUID.randomUUID().toString().replace("-", "");
        OptimizationJobState state = new OptimizationJobState(jobId, request.baseRunId(), method, totalIterations);
        optimizationStore.save(state);

        CompletableFuture.runAsync(() -> executeJob(state, request, method, totalIterations));
        return state.snapshot();
    }

    public OptimizationJobResult getJob(String jobId) {
        return loadState(jobId).snapshot();
    }

    public List<OptimizationIterationItem> iterations(String jobId) {
        return loadState(jobId).iterations();
    }

    public OptimizationBestResult best(String jobId) {
        OptimizationEvaluationResult evaluation = loadState(jobId).bestEvaluation();
        if (evaluation == null) {
            throw new ResourceNotFoundException("optimization best result not found");
        }
        return new OptimizationBestResult(jobId, evaluation);
    }

    private void executeJob(
            OptimizationJobState state,
            OptimizationRunRequest request,
            String method,
            int totalIterations
    ) {
        try {
            state.markRunning();
            long seed = request.randomSeed() == null ? System.currentTimeMillis() : request.randomSeed();
            Random rng = new Random(seed);
            DiversionStrategyParameters currentParameters = request.initialParameters() == null
                    ? DiversionStrategyParameters.defaults()
                    : request.initialParameters();
            OptimizationEvaluationResult currentEvaluation = null;
            OptimizationEvaluationResult bestEvaluation = null;

            for (int iteration = 1; iteration <= totalIterations; iteration++) {
                double temperature = simulatedAnnealingOptimizer.temperatureAt(iteration, totalIterations);
                DiversionStrategyParameters candidateParameters;
                if (iteration == 1) {
                    candidateParameters = currentParameters;
                } else if ("RANDOM_SEARCH".equals(method)) {
                    candidateParameters = randomSearchOptimizer.randomize(
                            request.initialParameters() == null ? DiversionStrategyParameters.defaults() : request.initialParameters(),
                            rng,
                            1.0
                    );
                } else {
                    candidateParameters = randomSearchOptimizer.randomize(currentParameters, rng, temperature);
                }

                OptimizationEvaluationResult candidateEvaluation = evaluator.evaluate(
                        request.baseRunId(),
                        request.minute(),
                        request.targetCrowdLevel(),
                        candidateParameters
                );

                boolean accepted;
                if (currentEvaluation == null) {
                    accepted = true;
                } else if ("RANDOM_SEARCH".equals(method)) {
                    accepted = candidateEvaluation.loss() <= currentEvaluation.loss();
                } else {
                    accepted = simulatedAnnealingOptimizer.accept(
                            currentEvaluation.loss(),
                            candidateEvaluation.loss(),
                            temperature,
                            rng
                    );
                }
                if (accepted) {
                    currentParameters = candidateEvaluation.strategyParameters();
                    currentEvaluation = candidateEvaluation;
                }
                if (bestEvaluation == null || candidateEvaluation.loss() < bestEvaluation.loss()) {
                    bestEvaluation = candidateEvaluation;
                }

                state.addIteration(
                        new OptimizationIterationItem(
                                iteration,
                                round(temperature),
                                candidateEvaluation.strategyParameters(),
                                candidateEvaluation.loss(),
                                accepted,
                                bestEvaluation != null && bestEvaluation.compareRunId() == candidateEvaluation.compareRunId()
                                        && bestEvaluation.loss() == candidateEvaluation.loss(),
                                candidateEvaluation.diversionResult().estimatedSystemBenefit()
                        ),
                        bestEvaluation
                );
            }
            state.markCompleted("Optimization finished");
        } catch (RuntimeException ex) {
            state.markFailed(ex.getMessage() == null ? "Optimization failed" : ex.getMessage());
        }
    }

    private OptimizationJobState loadState(String jobId) {
        return optimizationStore.find(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("optimization job not found"));
    }

    private String normalizeMethod(String method) {
        String normalized = method == null || method.isBlank()
                ? "SIMULATED_ANNEALING"
                : method.trim().toUpperCase(Locale.ROOT);
        if (!List.of("SIMULATED_ANNEALING", "RANDOM_SEARCH").contains(normalized)) {
            throw new BadRequestException("method", "method must be SIMULATED_ANNEALING or RANDOM_SEARCH");
        }
        return normalized;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
