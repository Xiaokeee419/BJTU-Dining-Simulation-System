package com.bjtu.dining.recommendation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RecommendationResult(
        Long runId,
        int minute,
        List<RecommendationItem> restaurants,
        List<RecommendationItem> windows,
        List<RecommendationItem> dishes,
        String diversionSuggestion,
        OffsetDateTime generatedAt
) {
}
