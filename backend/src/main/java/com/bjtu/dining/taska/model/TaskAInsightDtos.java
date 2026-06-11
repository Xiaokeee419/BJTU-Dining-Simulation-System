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

    public record ArrivalPeakDetail(
            int centerMinute,
            double weight,
            int standardDeviation
    ) {
    }

    public record ArrivalRuleDetail(
            String mealPeriod,
            String simulationStartTime,
            List<String> courseReferenceTimes,
            List<ArrivalPeakDetail> peaks,
            String description
    ) {
    }

    public record TagMappingOverview(
            String ruleType,
            String sourceTag,
            List<String> normalizedTags,
            String description
    ) {
    }

    public record TagMappingSummary(
            int totalMappings,
            int exactMappings,
            int keywordMappings,
            List<TagMappingOverview> samples,
            List<TagCountItem> normalizedTagCounts
    ) {
    }
}
