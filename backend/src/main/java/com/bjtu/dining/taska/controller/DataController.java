package com.bjtu.dining.taska.controller;

import com.bjtu.dining.common.ApiResponse;
import com.bjtu.dining.taska.model.TaskAInsightDtos.ArrivalPeakDetail;
import com.bjtu.dining.taska.model.TaskAInsightDtos.ArrivalRuleDetail;
import com.bjtu.dining.taska.model.TaskAInsightDtos.StudentPoolSummary;
import com.bjtu.dining.taska.model.TaskAInsightDtos.TagCountItem;
import com.bjtu.dining.taska.model.TaskAInsightDtos.TagMappingOverview;
import com.bjtu.dining.taska.model.TaskAInsightDtos.TagMappingSummary;
import com.bjtu.dining.taska.service.SeedDataService;
import com.bjtu.dining.taska.service.SeedDataService.ArrivalRuleSeed;
import com.bjtu.dining.taska.service.SeedDataService.SeedData;
import com.bjtu.dining.taska.service.SeedDataService.StudentSeed;
import com.bjtu.dining.taska.service.SeedDataService.TagMappingSeed;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data")
public class DataController {
    private final SeedDataService seedDataService;

    public DataController(SeedDataService seedDataService) {
        this.seedDataService = seedDataService;
    }

    @GetMapping("/student-pool-summary")
    public ApiResponse<StudentPoolSummary> studentPoolSummary() {
        SeedData seedData = seedDataService.seedData();
        Map<String, Long> userTypeCounts = seedData.students().stream()
                .collect(LinkedHashMap::new,
                        (map, student) -> map.merge(student.userType(), 1L, Long::sum),
                        Map::putAll);
        Map<String, Long> tagCounts = new LinkedHashMap<>();
        for (StudentSeed student : seedData.students()) {
            for (String tag : student.preferenceTags()) {
                tagCounts.merge(tag, 1L, Long::sum);
            }
        }
        List<TagCountItem> topTags = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(12)
                .map(entry -> new TagCountItem(entry.getKey(), entry.getValue()))
                .toList();
        StudentPoolSummary summary = new StudentPoolSummary(
                seedData.students().size(),
                Map.copyOf(userTypeCounts),
                round(seedData.students().stream().mapToDouble(StudentSeed::budgetMin).average().orElse(0.0)),
                round(seedData.students().stream().mapToDouble(StudentSeed::budgetMax).average().orElse(0.0)),
                round(seedData.students().stream().mapToInt(StudentSeed::waitingToleranceMinutes).average().orElse(0.0)),
                topTags
        );
        return ApiResponse.ok(summary);
    }

    @GetMapping("/arrival-rules")
    public ApiResponse<List<ArrivalRuleDetail>> arrivalRules() {
        List<ArrivalRuleDetail> rules = seedDataService.seedData().arrivalRulesByMealPeriod().values().stream()
                .sorted(Comparator.comparing(ArrivalRuleSeed::mealPeriod))
                .map(rule -> new ArrivalRuleDetail(
                        rule.mealPeriod(),
                        rule.simulationStartTime(),
                        rule.courseReferenceTimes(),
                        rule.peaks().stream()
                                .map(peak -> new ArrivalPeakDetail(
                                        peak.centerMinute(),
                                        peak.weight(),
                                        peak.standardDeviation()
                                ))
                                .toList(),
                        rule.description()
                ))
                .toList();
        return ApiResponse.ok(rules);
    }

    @GetMapping("/tag-mappings/summary")
    public ApiResponse<TagMappingSummary> tagMappingsSummary() {
        SeedData seedData = seedDataService.seedData();
        Map<String, Long> normalizedTagCounts = new LinkedHashMap<>();
        int exactCount = 0;
        int keywordCount = 0;
        for (TagMappingSeed mapping : seedData.tagMappings()) {
            if ("KEYWORD".equals(mapping.ruleType())) {
                keywordCount++;
            } else {
                exactCount++;
            }
            for (String tag : mapping.normalizedTags()) {
                normalizedTagCounts.merge(tag, 1L, Long::sum);
            }
        }
        List<TagCountItem> normalizedTags = normalizedTagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(16)
                .map(entry -> new TagCountItem(entry.getKey(), entry.getValue()))
                .toList();
        List<TagMappingOverview> samples = seedData.tagMappings().stream()
                .limit(12)
                .map(mapping -> new TagMappingOverview(
                        mapping.ruleType(),
                        mapping.sourceTag(),
                        mapping.normalizedTags().stream().sorted().toList(),
                        mapping.description()
                ))
                .toList();
        TagMappingSummary summary = new TagMappingSummary(
                seedData.tagMappings().size(),
                exactCount,
                keywordCount,
                samples,
                normalizedTags
        );
        return ApiResponse.ok(summary);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
