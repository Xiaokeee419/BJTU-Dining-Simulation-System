package com.bjtu.dining.taska.controller;

import com.bjtu.dining.common.ApiResponse;
import com.bjtu.dining.taska.model.DataOverviewResponse;
import com.bjtu.dining.taska.model.FlowCurveResponse;
import com.bjtu.dining.taska.model.TaskAInsightDtos.StudentPoolSummary;
import com.bjtu.dining.taska.model.TaskAInsightDtos.TagCountItem;
import com.bjtu.dining.taska.service.SeedDataService;
import com.bjtu.dining.taska.service.SeedDataService.SeedData;
import com.bjtu.dining.taska.service.SeedDataService.StudentSeed;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
        return ApiResponse.ok(new StudentPoolSummary(
                seedData.students().size(),
                Map.copyOf(userTypeCounts),
                round(seedData.students().stream().mapToDouble(StudentSeed::budgetMin).average().orElse(0.0)),
                round(seedData.students().stream().mapToDouble(StudentSeed::budgetMax).average().orElse(0.0)),
                round(seedData.students().stream()
                        .mapToInt(StudentSeed::waitingToleranceMinutes)
                        .average()
                        .orElse(0.0)),
                topTags
        ));
    }

    @GetMapping("/overview")
    public ApiResponse<DataOverviewResponse> overview() {
        return ApiResponse.ok(seedDataService.dataOverview());
    }

    @GetMapping("/flow-curves")
    public ApiResponse<FlowCurveResponse> flowCurves(
            @RequestParam(defaultValue = "LUNCH") String mealPeriod,
            @RequestParam(defaultValue = "3") int bucketMinutes
    ) {
        return ApiResponse.ok(seedDataService.flowCurve(mealPeriod, bucketMinutes));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
