package com.bjtu.dining.recommendation.model;

import com.bjtu.dining.recommendation.dto.OptimizationEvaluationResult;
import com.bjtu.dining.recommendation.dto.OptimizationIterationItem;
import com.bjtu.dining.recommendation.dto.OptimizationJobResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class OptimizationJobState {
    private final String jobId;
    private final long baseRunId;
    private final String method;
    private final int totalIterations;
    private final OffsetDateTime startedAt;
    private final List<OptimizationIterationItem> iterations = new ArrayList<>();
    private volatile String status;
    private volatile int currentIteration;
    private volatile OptimizationEvaluationResult bestEvaluation;
    private volatile OffsetDateTime completedAt;
    private volatile String message;

    public OptimizationJobState(String jobId, long baseRunId, String method, int totalIterations) {
        this.jobId = jobId;
        this.baseRunId = baseRunId;
        this.method = method;
        this.totalIterations = totalIterations;
        this.startedAt = OffsetDateTime.now();
        this.status = "PENDING";
        this.currentIteration = 0;
    }

    public synchronized void markRunning() {
        this.status = "RUNNING";
    }

    public synchronized void addIteration(OptimizationIterationItem item, OptimizationEvaluationResult evaluation) {
        this.iterations.add(item);
        this.currentIteration = Math.max(this.currentIteration, item.iteration());
        if (bestEvaluation == null || evaluation.loss() < bestEvaluation.loss()) {
            this.bestEvaluation = evaluation;
        }
    }

    public synchronized void markCompleted(String message) {
        this.status = "COMPLETED";
        this.completedAt = OffsetDateTime.now();
        this.message = message;
    }

    public synchronized void markFailed(String message) {
        this.status = "FAILED";
        this.completedAt = OffsetDateTime.now();
        this.message = message;
    }

    public synchronized OptimizationJobResult snapshot() {
        return new OptimizationJobResult(
                jobId,
                baseRunId,
                method,
                status,
                totalIterations,
                currentIteration,
                bestEvaluation == null ? null : bestEvaluation.loss(),
                bestEvaluation == null ? null : bestEvaluation.strategyParameters(),
                startedAt.toString(),
                completedAt == null ? null : completedAt.toString(),
                message
        );
    }

    public synchronized List<OptimizationIterationItem> iterations() {
        return List.copyOf(iterations);
    }

    public synchronized OptimizationEvaluationResult bestEvaluation() {
        return bestEvaluation;
    }
}
