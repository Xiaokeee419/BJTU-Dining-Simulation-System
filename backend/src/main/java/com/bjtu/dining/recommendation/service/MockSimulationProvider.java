package com.bjtu.dining.recommendation.service;

import com.bjtu.dining.common.ApiException;
import com.bjtu.dining.recommendation.model.EvaluationMetrics;
import com.bjtu.dining.recommendation.model.RestaurantParameter;
import com.bjtu.dining.recommendation.model.RestaurantSnapshot;
import com.bjtu.dining.recommendation.model.SimulationRunResult;
import com.bjtu.dining.recommendation.model.SimulationTimePoint;
import com.bjtu.dining.recommendation.model.WindowParameter;
import com.bjtu.dining.recommendation.model.WindowSnapshot;
import com.bjtu.dining.recommendation.repository.CsvSeedRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockSimulationProvider {

    private final CsvSeedRepository seedRepository;
    private final Map<Long, SimulationRunResult> cache = new ConcurrentHashMap<>();

    public MockSimulationProvider(CsvSeedRepository seedRepository) {
        this.seedRepository = seedRepository;
    }

    public SimulationRunResult findByRunId(Long runId) {
        if (runId == null || runId <= 0) {
            throw new ApiException(40401, "仿真运行记录不存在", HttpStatus.NOT_FOUND);
        }
        return cache.computeIfAbsent(runId, this::buildSimulation);
    }

    private SimulationRunResult buildSimulation(Long runId) {
        List<SimulationTimePoint> timePoints = new ArrayList<>();
        for (int minute = 0; minute <= 60; minute += 5) {
            timePoints.add(buildTimePoint(runId, minute));
        }
        EvaluationMetrics metrics = buildMetrics(timePoints);
        return new SimulationRunResult(runId, "FINISHED", timePoints, metrics, OffsetDateTime.now().minusMinutes(3));
    }

    private SimulationTimePoint buildTimePoint(Long runId, int minute) {
        List<RestaurantSnapshot> restaurantSnapshots = new ArrayList<>();
        double runFactor = runId % 2 == 0 ? 0.82 : 1.16;
        double wave = 0.65 + Math.sin((minute + 8) / 60.0 * Math.PI) * 0.75;

        for (RestaurantParameter restaurant : seedRepository.restaurants()) {
            List<WindowSnapshot> windowSnapshots = new ArrayList<>();
            int queueSum = 0;
            int servingSum = 0;

            for (WindowParameter window : seedRepository.windowsByRestaurant(restaurant.restaurantId())) {
                WindowSnapshot snapshot = buildWindowSnapshot(window, runFactor, wave);
                windowSnapshots.add(snapshot);
                queueSum += snapshot.queueLength();
                servingSum += snapshot.servingCount();
            }

            int currentCount = Math.min(
                    restaurant.capacity() + queueSum,
                    (int) Math.round(restaurant.capacity() * 0.32 + queueSum + servingSum)
            );
            restaurantSnapshots.add(new RestaurantSnapshot(
                    restaurant.restaurantId(),
                    restaurant.name(),
                    currentCount,
                    restaurant.capacity(),
                    restaurantCrowdLevel(currentCount, restaurant.capacity()),
                    windowSnapshots
            ));
        }

        return new SimulationTimePoint(minute, restaurantSnapshots);
    }

    private WindowSnapshot buildWindowSnapshot(WindowParameter window, double runFactor, double wave) {
        if (!"OPEN".equals(window.status())) {
            return new WindowSnapshot(window.windowId(), window.name(), 0, 0, 0, "IDLE", "CLOSED");
        }

        double rawQueue = window.popularity() * 9.5 * runFactor * wave
                + (2.2 - window.serviceRatePerMinute()) * 4.0;
        int queueLength = Math.max(0, (int) Math.round(rawQueue));
        int servingCount = Math.max(1, Math.min(3, (int) Math.ceil(window.serviceRatePerMinute())));
        int waitMinutes = (int) Math.ceil(queueLength / Math.max(0.1, window.serviceRatePerMinute()));
        return new WindowSnapshot(
                window.windowId(),
                window.name(),
                queueLength,
                servingCount,
                waitMinutes,
                windowCrowdLevel(waitMinutes),
                "OPEN"
        );
    }

    private EvaluationMetrics buildMetrics(List<SimulationTimePoint> timePoints) {
        int windowCount = 0;
        int waitSum = 0;
        int maxWait = 0;
        int maxQueue = 0;
        int busyCount = 0;
        int extremeCount = 0;

        for (SimulationTimePoint timePoint : timePoints) {
            for (RestaurantSnapshot restaurant : timePoint.restaurants()) {
                for (WindowSnapshot window : restaurant.windows()) {
                    windowCount++;
                    waitSum += window.waitMinutes();
                    maxWait = Math.max(maxWait, window.waitMinutes());
                    maxQueue = Math.max(maxQueue, window.queueLength());
                    if ("BUSY".equals(window.crowdLevel())) {
                        busyCount++;
                    }
                    if ("EXTREME".equals(window.crowdLevel())) {
                        extremeCount++;
                    }
                }
            }
        }

        double avgWait = windowCount == 0 ? 0.0 : round(waitSum * 1.0 / windowCount);
        int totalUsers = 300;
        int unserved = Math.min(80, Math.max(0, maxQueue + extremeCount / 2));
        return new EvaluationMetrics(
                avgWait,
                maxWait,
                maxQueue,
                busyCount,
                extremeCount,
                totalUsers,
                totalUsers - unserved,
                unserved
        );
    }

    private String restaurantCrowdLevel(int currentCount, int capacity) {
        double ratio = capacity <= 0 ? 0 : currentCount * 1.0 / capacity;
        if (ratio < 0.4) {
            return "IDLE";
        }
        if (ratio < 0.7) {
            return "NORMAL";
        }
        if (ratio < 0.9) {
            return "BUSY";
        }
        return "EXTREME";
    }

    private String windowCrowdLevel(int waitMinutes) {
        if (waitMinutes < 5) {
            return "IDLE";
        }
        if (waitMinutes < 10) {
            return "NORMAL";
        }
        if (waitMinutes < 20) {
            return "BUSY";
        }
        return "EXTREME";
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
