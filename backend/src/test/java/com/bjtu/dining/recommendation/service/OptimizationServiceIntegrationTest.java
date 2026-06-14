package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.recommendation.dto.DiversionStrategyParameters;
import com.bjtu.dining.recommendation.dto.OptimizationRunRequest;
import com.bjtu.dining.taska.model.TaskADtos;
import com.bjtu.dining.taska.service.SimulationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OptimizationServiceIntegrationTest {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private OptimizationService optimizationService;

    @Test
    void optimizationKeepsBestLossAtOrBelowInitialCandidate() throws InterruptedException {
        TaskADtos.SimulationRunResult baseRun = runSimulation(3000, "BUSY", 20260612L);

        var job = optimizationService.run(new OptimizationRunRequest(
                baseRun.runId(),
                30,
                "NORMAL",
                12,
                20260620L,
                new DiversionStrategyParameters(1.0, 1.0, 1.0, 60, 0.0, 0.032)
        ));

        var completed = waitForCompletion(job.taskId());
        var iterations = optimizationService.getIterations(job.taskId());
        var best = optimizationService.getBest(job.taskId());

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(iterations).isNotEmpty();
        assertThat(iterations.get(0).bottleneckMetrics()).isNotNull();
        assertThat(iterations.get(0).bottleneckMetrics().maxSingleWindowQueue()).isGreaterThanOrEqualTo(0);
        assertThat(iterations.get(0).bottleneckMetrics().totalOverload()).isGreaterThanOrEqualTo(0);
        assertThat(best.loss()).isLessThanOrEqualTo(iterations.get(0).loss());
        assertThat(best.bottleneckMetrics()).isNotNull();
    }

    private TaskADtos.SimulationRunResult runSimulation(int users, String crowdLevel, long seed) {
        return simulationService.runSimulation(new TaskADtos.SimulationRunRequest(
                new TaskADtos.UserProfile(
                        "STUDENT",
                        List.of("米饭"),
                        10.0,
                        24.0,
                        12
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

    private com.bjtu.dining.recommendation.dto.OptimizationJobResult waitForCompletion(String taskId)
            throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            var job = optimizationService.getJob(taskId);
            if ("COMPLETED".equals(job.status()) || "FAILED".equals(job.status())) {
                return job;
            }
            Thread.sleep(50L);
        }
        return optimizationService.getJob(taskId);
    }
}
