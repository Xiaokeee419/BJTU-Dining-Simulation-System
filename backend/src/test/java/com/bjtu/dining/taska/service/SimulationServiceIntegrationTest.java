package com.bjtu.dining.taska.service;

import com.bjtu.dining.taska.model.TaskADtos;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SimulationServiceIntegrationTest {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private SeedDataService seedDataService;

    @Test
    void highLoadRunContinuesPastConfiguredDurationUntilQueuesCoolDown() {
        TaskADtos.SimulationRunResult run = simulationService.runSimulation(new TaskADtos.SimulationRunRequest(
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
                        "EXTREME",
                        1.0,
                        1.25,
                        List.of(),
                        5000,
                        60,
                        3,
                        20260613L
                )
        ));

        TaskADtos.SimulationTimePoint minute60 = run.timePoints().stream()
                .filter(point -> point.minute() == 60)
                .findFirst()
                .orElseThrow();
        TaskADtos.SimulationTimePoint lastPoint = run.timePoints().get(run.timePoints().size() - 1);

        assertThat(lastPoint.minute()).isGreaterThan(60);
        assertThat(totalQueueLength(lastPoint)).isLessThan(totalQueueLength(minute60));
    }

    @Test
    void breakfastOnlyOpensBreakfastWindowsWhileLunchAndDinnerShareSameWindowSet() {
        TaskADtos.SimulationRunResult breakfastRun = runForMealPeriod("BREAKFAST");
        TaskADtos.SimulationRunResult lunchRun = runForMealPeriod("LUNCH");
        TaskADtos.SimulationRunResult dinnerRun = runForMealPeriod("DINNER");

        Set<Long> breakfastOpenWindowIds = openWindowIds(breakfastRun);
        Set<Long> lunchOpenWindowIds = openWindowIds(lunchRun);
        Set<Long> dinnerOpenWindowIds = openWindowIds(dinnerRun);

        Set<Long> expectedBreakfastWindowIds = seedDataService.seedData().windows().stream()
                .filter(window -> "OPEN".equals(window.status()))
                .filter(window -> "BREAKFAST".equals(window.recommendedMealPeriod()))
                .map(SeedDataService.WindowSeed::windowId)
                .collect(Collectors.toSet());
        Set<Long> expectedMainMealWindowIds = seedDataService.seedData().windows().stream()
                .filter(window -> "OPEN".equals(window.status()))
                .filter(window -> !"BREAKFAST".equals(window.recommendedMealPeriod()))
                .map(SeedDataService.WindowSeed::windowId)
                .collect(Collectors.toSet());

        assertThat(breakfastOpenWindowIds).isEqualTo(expectedBreakfastWindowIds);
        assertThat(lunchOpenWindowIds).isEqualTo(expectedMainMealWindowIds);
        assertThat(dinnerOpenWindowIds).isEqualTo(expectedMainMealWindowIds);
    }

    private int totalQueueLength(TaskADtos.SimulationTimePoint point) {
        return point.restaurants().stream()
                .flatMap(restaurant -> restaurant.windows().stream())
                .mapToInt(TaskADtos.QueueState::queueLength)
                .sum();
    }

    private TaskADtos.SimulationRunResult runForMealPeriod(String mealPeriod) {
        return simulationService.runSimulation(new TaskADtos.SimulationRunRequest(
                new TaskADtos.UserProfile(
                        "STUDENT",
                        List.of("绫抽キ", "杈ｅ懗"),
                        10.0,
                        22.0,
                        10
                ),
                new TaskADtos.SimulationScenario(
                        mealPeriod,
                        "WEEKDAY",
                        "NORMAL",
                        1.0,
                        1.0,
                        List.of(),
                        1200,
                        30,
                        3,
                        20260613L
                )
        ));
    }

    private Set<Long> openWindowIds(TaskADtos.SimulationRunResult run) {
        return run.timePoints().get(0).restaurants().stream()
                .flatMap(restaurant -> restaurant.windows().stream())
                .filter(window -> "OPEN".equals(window.status()))
                .map(TaskADtos.QueueState::windowId)
                .collect(Collectors.toSet());
    }
}
