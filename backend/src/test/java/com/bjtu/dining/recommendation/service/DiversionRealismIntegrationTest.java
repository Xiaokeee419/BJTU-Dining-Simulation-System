package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.recommendation.dto.DiversionComparisonRequest;
import com.bjtu.dining.recommendation.dto.DiversionRequest;
import com.bjtu.dining.recommendation.dto.UserProfileRequest;
import com.bjtu.dining.recommendation.repository.CsvSeedRepository;
import com.bjtu.dining.taska.model.TaskADtos;
import com.bjtu.dining.taska.service.SimulationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DiversionRealismIntegrationTest {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private CsvSeedRepository seedRepository;

    @Test
    void lowLoadDoesNotProduceDiversionSuggestions() {
        TaskADtos.SimulationRunResult run = runSimulation(300, "BUSY", 20260611L);

        var diversion = recommendationService.generateDiversion(new DiversionRequest(
                run.runId(),
                null,
                "NORMAL",
                defaultProfileRequest()
        ));

        assertThat(diversion.suggestions()).isEmpty();
    }

    @Test
    void peakDiversionStaysLocalAndDoesNotCreateMoreBusyWindows() {
        TaskADtos.SimulationRunResult baseRun = runSimulation(3000, "BUSY", 20260425L);

        var result = recommendationService.runDiversionComparison(new DiversionComparisonRequest(
                baseRun.runId(),
                null,
                "NORMAL",
                true
        ));
        TaskADtos.SimulationTimePoint comparisonMinute = baseRun.timePoints().stream()
                .filter(point -> point.minute() == result.minute())
                .findFirst()
                .orElseThrow();

        assertThat(result.diversionResult().suggestions()).isNotEmpty();
        assertThat(result.diversionResult().suggestions()).allSatisfy(suggestion -> {
            var source = seedRepository.restaurant(suggestion.fromRestaurantId());
            var target = seedRepository.restaurant(suggestion.toRestaurantId());
            assertThat(target.campusArea()).isEqualTo(source.campusArea());
            assertThat(suggestion.estimatedWaitReduction()).isPositive();
            assertThat(crowdWeight(targetWindowCrowdLevel(comparisonMinute, suggestion.toWindowId())))
                    .isLessThanOrEqualTo(crowdWeight("NORMAL"));
        });
        assertThat(result.comparison()).isNotNull();
        assertThat(result.comparison().busyWindowCountDelta()).isLessThanOrEqualTo(0);

        TaskADtos.SimulationRunResult compareRun = simulationService.getRunResult(result.compareRunId());
        assertThat(compareRun.metrics().totalVirtualUsers()).isEqualTo(baseRun.metrics().totalVirtualUsers());
        assertThat(compareRun.metrics().servedUserCount() + compareRun.metrics().unservedUserCount())
                .isEqualTo(compareRun.metrics().totalVirtualUsers());
    }

    private TaskADtos.SimulationRunResult runSimulation(int users, String crowdLevel, long seed) {
        return simulationService.runSimulation(new TaskADtos.SimulationRunRequest(
                new TaskADtos.UserProfile(
                        "STUDENT",
                        List.of("米饭", "辣味"),
                        10.0,
                        22.0,
                        10
                ),
                new TaskADtos.SimulationScenario(
                        "LUNCH",
                        "WEEKDAY",
                        crowdLevel,
                        1.0,
                        1.2,
                        List.of(),
                        users,
                        60,
                        3,
                        seed
                )
        ));
    }

    private UserProfileRequest defaultProfileRequest() {
        return new UserProfileRequest(
                "STUDENT",
                List.of("米饭", "辣味"),
                10.0,
                22.0,
                10
        );
    }

    private String targetWindowCrowdLevel(TaskADtos.SimulationTimePoint point, long windowId) {
        return point.restaurants().stream()
                .flatMap(restaurant -> restaurant.windows().stream())
                .filter(window -> window.windowId() == windowId)
                .map(TaskADtos.QueueState::crowdLevel)
                .findFirst()
                .orElseThrow();
    }

    private int crowdWeight(String crowdLevel) {
        return switch (crowdLevel) {
            case "EXTREME" -> 4;
            case "BUSY" -> 3;
            case "NORMAL" -> 2;
            default -> 1;
        };
    }
}
