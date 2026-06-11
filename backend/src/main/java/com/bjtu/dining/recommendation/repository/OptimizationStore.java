package com.bjtu.dining.recommendation.repository;

import com.bjtu.dining.recommendation.model.OptimizationJobState;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class OptimizationStore {
    private final Map<String, OptimizationJobState> jobs = new ConcurrentHashMap<>();

    public void save(OptimizationJobState state) {
        jobs.put(state.snapshot().jobId(), state);
    }

    public Optional<OptimizationJobState> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }
}
