package com.bjtu.dining.taska.model;

import java.util.List;
import java.util.Map;

public final class TaskAInsightDtos {
    private TaskAInsightDtos() {
    }

    public record TagCountItem(
            String tag,
            long count
    ) {
    }

    public record StudentPoolSummary(
            int totalStudents,
            Map<String, Long> userTypeCounts,
            double avgBudgetMin,
            double avgBudgetMax,
            double avgWaitingToleranceMinutes,
            List<TagCountItem> topPreferenceTags
    ) {
    }
}
