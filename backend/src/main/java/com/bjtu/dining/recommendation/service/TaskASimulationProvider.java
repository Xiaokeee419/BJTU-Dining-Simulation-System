package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.common.ApiException;
import com.bjtu.dining.recommendation.model.EvaluationMetrics;
import com.bjtu.dining.recommendation.model.RestaurantSnapshot;
import com.bjtu.dining.recommendation.model.SimulationRunResult;
import com.bjtu.dining.recommendation.model.SimulationTimePoint;
import com.bjtu.dining.recommendation.model.WindowSnapshot;
import com.bjtu.dining.taska.model.TaskADtos;
import com.bjtu.dining.taska.service.SimulationService;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskASimulationProvider implements SimulationProvider {

    private final SimulationService taskASimulationService;

    public TaskASimulationProvider(SimulationService taskASimulationService) {
        this.taskASimulationService = taskASimulationService;
    }

    @Override
    public SimulationRunResult findByRunId(Long runId) {
        if (runId == null || runId <= 0) {
            throw new ApiException(40401, "仿真运行记录不存在", HttpStatus.NOT_FOUND);
        }
        TaskADtos.SimulationRunResult source = taskASimulationService.getRunResult(runId);
        return mapTaskAResult(source);
    }

    private SimulationRunResult mapTaskAResult(TaskADtos.SimulationRunResult source) {
        return new SimulationRunResult(
                source.runId(),
                source.status(),
                source.timePoints().stream().map(this::mapTimePoint).toList(),
                mapMetrics(source.metrics()),
                parseCreatedAt(source.createdAt())
        );
    }

    private SimulationTimePoint mapTimePoint(TaskADtos.SimulationTimePoint source) {
        return new SimulationTimePoint(
                source.minute(),
                source.restaurants().stream().map(this::mapRestaurant).toList()
        );
    }

    private RestaurantSnapshot mapRestaurant(TaskADtos.RestaurantTimePoint source) {
        return new RestaurantSnapshot(
                source.restaurantId(),
                source.name(),
                source.currentCount(),
                source.capacity(),
                source.crowdLevel(),
                source.windows().stream().map(this::mapWindow).toList()
        );
    }

    private WindowSnapshot mapWindow(TaskADtos.QueueState source) {
        return new WindowSnapshot(
                source.windowId(),
                source.name(),
                source.queueLength(),
                source.servingCount(),
                (int) Math.round(source.waitMinutes()),
                source.crowdLevel(),
                source.status()
        );
    }

    private EvaluationMetrics mapMetrics(TaskADtos.EvaluationMetrics source) {
        return new EvaluationMetrics(
                source.avgWaitMinutes(),
                source.maxWaitMinutes(),
                source.maxQueueLength(),
                source.busyWindowCount(),
                source.extremeWindowCount(),
                source.totalVirtualUsers(),
                source.servedUserCount(),
                source.unservedUserCount()
        );
    }

    private OffsetDateTime parseCreatedAt(String createdAt) {
        try {
            return OffsetDateTime.parse(createdAt);
        } catch (RuntimeException ignored) {
            return OffsetDateTime.now();
        }
    }
}
