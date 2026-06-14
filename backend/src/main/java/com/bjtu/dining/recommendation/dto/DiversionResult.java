package com.bjtu.dining.recommendation.dto;

import java.util.List;

public record DiversionResult(
        Long runId,
        int minute,
        List<DiversionSuggestionItem> suggestions,
        String reason
) {
}
