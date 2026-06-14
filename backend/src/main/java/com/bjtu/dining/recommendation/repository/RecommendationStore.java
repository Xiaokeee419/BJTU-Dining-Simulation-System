package com.bjtu.dining.recommendation.repository;

import com.bjtu.dining.recommendation.dto.RecommendationResult;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RecommendationStore {

    private final Map<String, RecommendationResult> results = new ConcurrentHashMap<>();

    public void save(RecommendationResult result) {
        results.put(key(result.runId(), result.minute()), result);
    }

    public Optional<RecommendationResult> find(Long runId, Integer minute) {
        if (minute != null) {
            return Optional.ofNullable(results.get(key(runId, minute)));
        }
        return results.values().stream()
                .filter(result -> result.runId().equals(runId))
                .max(Comparator.comparing(RecommendationResult::generatedAt));
    }

    private String key(Long runId, int minute) {
        return runId + ":" + minute;
    }
}
