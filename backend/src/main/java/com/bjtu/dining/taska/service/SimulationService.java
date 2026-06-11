package com.bjtu.dining.taska.service;

import com.bjtu.dining.common.BadRequestException;
import com.bjtu.dining.common.ResourceNotFoundException;
import com.bjtu.dining.common.TagNormalizationService;
import com.bjtu.dining.recommendation.dto.DiversionStrategyParameters;
import com.bjtu.dining.recommendation.model.AdvancedMetrics;
import com.bjtu.dining.recommendation.service.DiversionStrategySupport;
import com.bjtu.dining.taska.model.TaskADtos.DiversionSuggestion;
import com.bjtu.dining.taska.model.TaskADtos.EvaluationMetrics;
import com.bjtu.dining.taska.model.TaskADtos.MetricsResponse;
import com.bjtu.dining.taska.model.TaskADtos.QueueState;
import com.bjtu.dining.taska.model.TaskADtos.RestaurantTimePoint;
import com.bjtu.dining.taska.model.TaskADtos.ScenarioPreset;
import com.bjtu.dining.taska.model.TaskADtos.SimulationRunRequest;
import com.bjtu.dining.taska.model.TaskADtos.SimulationRunResult;
import com.bjtu.dining.taska.model.TaskADtos.SimulationRunWithDiversionRequest;
import com.bjtu.dining.taska.model.TaskADtos.SimulationScenario;
import com.bjtu.dining.taska.model.TaskADtos.SimulationTimePoint;
import com.bjtu.dining.taska.model.TaskADtos.TimelineResponse;
import com.bjtu.dining.taska.model.TaskADtos.UserProfile;
import com.bjtu.dining.taska.model.TaskADtos.UserProfilePreset;
import com.bjtu.dining.taska.model.SimulationAnalyticsDtos.ArrivalCurvePoint;
import com.bjtu.dining.taska.model.SimulationAnalyticsDtos.ArrivalCurvePreviewRequest;
import com.bjtu.dining.taska.model.SimulationAnalyticsDtos.ArrivalCurveResponse;
import com.bjtu.dining.taska.model.SimulationAnalyticsDtos.MinuteMetricPoint;
import com.bjtu.dining.taska.model.SimulationAnalyticsDtos.MinuteMetricsResponse;
import com.bjtu.dining.taska.model.SimulationAnalyticsDtos.WindowPressureItem;
import com.bjtu.dining.taska.model.SimulationAnalyticsDtos.WindowPressurePoint;
import com.bjtu.dining.taska.model.SimulationAnalyticsDtos.WindowPressureResponse;
import com.bjtu.dining.taska.service.SeedDataService.ArrivalRuleSeed;
import com.bjtu.dining.taska.service.SeedDataService.DishSeed;
import com.bjtu.dining.taska.service.SeedDataService.RestaurantSeed;
import com.bjtu.dining.taska.service.SeedDataService.SeedData;
import com.bjtu.dining.taska.service.SeedDataService.StudentSeed;
import com.bjtu.dining.taska.service.SeedDataService.WindowSeed;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {
    private static final double WINDOW_SELECTION_TEMPERATURE = 7.8;
    private static final double DISH_SELECTION_TEMPERATURE = 4.2;
    private static final Map<String, Double> CROWD_SPREAD_FACTOR = Map.of(
            "IDLE", 1.25,
            "NORMAL", 1.0,
            "BUSY", 0.66,
            "EXTREME", 0.44
    );
    private static final Map<String, Double> CROWD_COUNT_FACTOR = Map.of(
            "IDLE", 0.75,
            "NORMAL", 1.0,
            "BUSY", 1.18,
            "EXTREME", 1.38
    );
    private static final Map<String, String> TAG_ALIASES = Map.ofEntries(
            Map.entry("偏辣", "辣味"),
            Map.entry("微辣", "辣味"),
            Map.entry("香辣", "辣味"),
            Map.entry("麻辣", "辣味"),
            Map.entry("酸辣", "辣味"),
            Map.entry("偏甜", "甜口"),
            Map.entry("米粉", "粉面"),
            Map.entry("米线", "粉面")
    );

    private final SeedDataService seedDataService;
    private final ArrivalCurveGenerator arrivalCurveGenerator;
    private final TagNormalizationService tagNormalizationService;
    private final AtomicLong runIdGenerator = new AtomicLong(10000);
    private final Map<Long, SimulationRunResult> runStore = new ConcurrentHashMap<>();
    private final Map<Long, List<DinerProfile>> cohortStore = new ConcurrentHashMap<>();
    private final Map<Long, DiversionRunMeta> diversionMetaStore = new ConcurrentHashMap<>();

    public SimulationService(
            SeedDataService seedDataService,
            ArrivalCurveGenerator arrivalCurveGenerator,
            TagNormalizationService tagNormalizationService
    ) {
        this.seedDataService = seedDataService;
        this.arrivalCurveGenerator = arrivalCurveGenerator;
        this.tagNormalizationService = tagNormalizationService;
    }

    public List<UserProfilePreset> userProfilePresets() {
        return List.of(
                new UserProfilePreset("student-spicy-budget", "偏辣预算型学生", "STUDENT", List.of("偏辣", "米饭"), 10.0, 20.0, 10),
                new UserProfilePreset("student-hurry-noodle", "赶时间面食型学生", "HURRY", List.of("面食", "清淡"), 8.0, 18.0, 6),
                new UserProfilePreset("student-balanced", "均衡普通学生", "STUDENT", List.of("米饭", "家常"), 10.0, 24.0, 12),
                new UserProfilePreset("student-budget", "价格敏感型学生", "BUDGET_SENSITIVE", List.of("米饭", "大众"), 6.0, 15.0, 14)
        );
    }

    public List<ScenarioPreset> scenarioPresets() {
        return List.of(
                new ScenarioPreset("weekday-lunch-peak", "工作日午餐高峰", "LUNCH", "WEEKDAY", "BUSY", 1.0, 1.2, List.of(), 800, 60, 3, 20260425),
                new ScenarioPreset("weekday-dinner-normal", "工作日晚餐常态", "DINNER", "WEEKDAY", "NORMAL", 1.0, 1.0, List.of(), 600, 60, 3, 20260426),
                new ScenarioPreset("rainy-lunch-extreme", "雨天午餐极端拥挤", "LUNCH", "WEEKDAY", "EXTREME", 1.25, 1.25, List.of(), 1200, 60, 3, 20260427)
        );
    }

    public SimulationRunResult runSimulation(SimulationRunRequest request) {
        UserProfile profile = normalizeProfile(request == null ? null : request.profile());
        SimulationScenario scenario = normalizeScenario(request == null ? null : request.scenario());
        validate(profile, scenario);
        List<DinerProfile> dinerCohort = sampleVirtualDiners(seedDataService.seedData(), profile, scenario);
        return simulateRun(profile, scenario, dinerCohort, null, null);
    }

    public SimulationRunResult runSimulationWithDiversion(SimulationRunWithDiversionRequest request) {
        if (request == null || request.baseRunId() == null || request.baseRunId() <= 0) {
            throw new BadRequestException("baseRunId", "baseRunId is required");
        }
        if (request.diversionSuggestions() == null || request.diversionSuggestions().isEmpty()) {
            throw new BadRequestException("diversionSuggestions", "diversionSuggestions must not be empty");
        }

        SimulationRunResult baseRun = getRunResult(request.baseRunId());
        if (!"FINISHED".equals(baseRun.status())) {
            throw new BadRequestException("baseRunId", "baseRun must be FINISHED");
        }

        UserProfile profile = normalizeProfile(baseRun.profile());
        SimulationScenario scenario = normalizeScenario(baseRun.scenario());
        validate(profile, scenario);
        List<DinerProfile> dinerCohort = cohortStore.get(baseRun.runId());
        if (dinerCohort == null || dinerCohort.isEmpty()) {
            throw new BadRequestException("baseRunId", "baseRun cohort is unavailable, rerun baseline simulation first");
        }

        int diversionStartMinute = resolveDiversionStartMinute(baseRun, scenario, request.minute());
        DiversionPlan diversionPlan = buildDiversionPlan(
                diversionStartMinute,
                request.diversionSuggestions(),
                request.strategyParameters(),
                seedDataService.seedData()
        );
        return simulateRun(profile, scenario, dinerCohort, diversionPlan, baseRun.runId());
    }

    public SimulationRunResult getRunResult(long runId) {
        SimulationRunResult result = runStore.get(runId);
        if (result == null) {
            throw new ResourceNotFoundException("simulation run not found");
        }
        return result;
    }

    public TimelineResponse timeline(long runId) {
        SimulationRunResult result = getRunResult(runId);
        return new TimelineResponse(runId, result.timePoints());
    }

    public MetricsResponse metrics(long runId) {
        SimulationRunResult result = getRunResult(runId);
        return new MetricsResponse(runId, result.metrics());
    }

    public ArrivalCurveResponse previewArrivalCurve(ArrivalCurvePreviewRequest request) {
        UserProfile profile = normalizeProfile(request == null ? null : request.profile());
        SimulationScenario scenario = normalizeScenario(request == null ? null : request.scenario());
        validatePreview(profile, scenario);
        SeedData seedData = seedDataService.seedData();
        int dinerCount = Math.max(1, scenario.virtualUserCount());
        double pressure = CROWD_COUNT_FACTOR.get(scenario.crowdLevel()) * scenario.weatherFactor() * scenario.eventFactor();
        ArrivalRuleSeed arrivalRule = arrivalRuleFor(seedData, scenario.mealPeriod());
        double[] weights = arrivalCurveGenerator.buildMinuteWeights(
                arrivalRule,
                scenario.dayType(),
                scenario.crowdLevel(),
                scenario.durationMinutes(),
                pressure
        );
        List<Integer> sampledMinutes = arrivalCurveGenerator.generateArrivalMinutes(
                arrivalRule,
                scenario.dayType(),
                scenario.crowdLevel(),
                scenario.durationMinutes(),
                pressure,
                dinerCount,
                new Random(scenario.randomSeed())
        );
        return new ArrivalCurveResponse(
                null,
                scenario.mealPeriod(),
                scenario.durationMinutes(),
                dinerCount,
                buildArrivalCurvePoints(weights, toMinuteCounts(sampledMinutes), scenario.durationMinutes())
        );
    }

    public ArrivalCurveResponse arrivalCurve(long runId) {
        SimulationRunResult result = getRunResult(runId);
        List<DinerProfile> diners = cohortStore.getOrDefault(runId, List.of());
        Map<Integer, Integer> minuteCounts = toMinuteCounts(diners.stream().map(DinerProfile::arrivalMinute).toList());
        SeedData seedData = seedDataService.seedData();
        SimulationScenario scenario = normalizeScenario(result.scenario());
        double pressure = CROWD_COUNT_FACTOR.get(scenario.crowdLevel()) * scenario.weatherFactor() * scenario.eventFactor();
        double[] weights = arrivalCurveGenerator.buildMinuteWeights(
                arrivalRuleFor(seedData, scenario.mealPeriod()),
                scenario.dayType(),
                scenario.crowdLevel(),
                scenario.durationMinutes(),
                pressure
        );
        return new ArrivalCurveResponse(
                runId,
                scenario.mealPeriod(),
                scenario.durationMinutes(),
                diners.size(),
                buildArrivalCurvePoints(weights, minuteCounts, scenario.durationMinutes())
        );
    }

    public MinuteMetricsResponse minuteMetrics(long runId) {
        SimulationRunResult result = getRunResult(runId);
        List<DinerProfile> diners = cohortStore.getOrDefault(runId, List.of());
        Map<Integer, Integer> minuteCounts = toMinuteCounts(diners.stream().map(DinerProfile::arrivalMinute).toList());
        List<MinuteMetricPoint> points = result.timePoints().stream()
                .map(point -> toMinuteMetricPoint(point, minuteCounts.getOrDefault(point.minute(), 0)))
                .toList();
        return new MinuteMetricsResponse(runId, points);
    }

    public WindowPressureResponse windowPressure(long runId) {
        SimulationRunResult result = getRunResult(runId);
        List<WindowPressurePoint> points = result.timePoints().stream()
                .map(this::toWindowPressurePoint)
                .toList();
        return new WindowPressureResponse(runId, points);
    }

    public AdvancedMetrics advancedMetrics(long runId) {
        SimulationRunResult result = getRunResult(runId);
        MinuteMetricsResponse minuteMetrics = minuteMetrics(runId);
        int intervalMinutes = metricIntervalMinutes(minuteMetrics.points());
        int overloadedWindowMinutes = minuteMetrics.points().stream()
                .mapToInt(item -> item.overloadedWindowCount() * intervalMinutes)
                .sum();
        int extremeWindowMinutes = minuteMetrics.points().stream()
                .mapToInt(item -> item.extremeWindowCount() * intervalMinutes)
                .sum();
        double peakWait10m = peakWait10m(minuteMetrics.points(), intervalMinutes);
        double queueImbalanceIndex = round(minuteMetrics.points().stream()
                .mapToDouble(MinuteMetricPoint::queueImbalanceIndex)
                .average()
                .orElse(0.0));
        DiversionRunMeta diversionMeta = diversionMetaStore.get(runId);
        int acceptedDiversionCount = diversionMeta == null ? 0 : diversionMeta.acceptedDiversionCount();
        int crossRestaurantDiversionCount = diversionMeta == null ? 0 : diversionMeta.crossRestaurantDiversionCount();
        double benefitPerAcceptedDiversion = 0.0;
        if (diversionMeta != null && result.compareSourceRunId() != null) {
            SimulationRunResult baseRun = runStore.get(result.compareSourceRunId());
            if (baseRun != null && acceptedDiversionCount > 0) {
                double improvement = Math.max(0.0, baseRun.metrics().avgWaitMinutes() - result.metrics().avgWaitMinutes())
                        + Math.max(0, baseRun.metrics().maxQueueLength() - result.metrics().maxQueueLength()) * 0.25
                        + Math.max(0, baseRun.metrics().extremeWindowCount() - result.metrics().extremeWindowCount()) * 1.2;
                benefitPerAcceptedDiversion = round(improvement / acceptedDiversionCount);
            }
        }
        return new AdvancedMetrics(
                overloadedWindowMinutes,
                extremeWindowMinutes,
                peakWait10m,
                queueImbalanceIndex,
                acceptedDiversionCount,
                crossRestaurantDiversionCount,
                benefitPerAcceptedDiversion
        );
    }

    private SimulationRunResult simulateRun(
            UserProfile profile,
            SimulationScenario scenario,
            List<DinerProfile> dinerCohort,
            DiversionPlan diversionPlan,
            Long compareSourceRunId
    ) {
        SeedData seedData = seedDataService.seedData();
        Set<Long> closedWindowIds = new LinkedHashSet<>(scenario.closedWindowIds());
        List<DinerProfile> diners = dinerCohort;

        Map<Long, Integer> queueLengths = new LinkedHashMap<>();
        Map<Long, Double> serviceCarry = new LinkedHashMap<>();
        for (WindowSeed window : seedData.windows()) {
            queueLengths.put(window.windowId(), 0);
            serviceCarry.put(window.windowId(), 0.0);
        }

        // Wait metrics capture the estimated wait at the moment a diner chooses a window.
        // Diversion of existing queues affects snapshot pressure more directly than these values.
        List<Double> selectedWaits = new ArrayList<>();
        List<SimulationTimePoint> timePoints = new ArrayList<>();
        int maxQueueLength = 0;
        int maxBusyWindowCount = 0;
        int maxExtremeWindowCount = 0;
        int nextDinerIndex = 0;
        boolean diversionApplied = diversionPlan == null || !diversionPlan.hasSuggestions();

        for (int minute = 0; minute <= scenario.durationMinutes(); minute++) {
            if (minute > 0) {
                serveQueues(seedData, scenario, closedWindowIds, queueLengths, serviceCarry, 1);
            }

            if (!diversionApplied && minute >= diversionPlan.startMinute()) {
                applyDiversionToExistingQueues(
                        seedData,
                        scenario,
                        diversionPlan,
                        closedWindowIds,
                        queueLengths
                );
                diversionApplied = true;
            }

            while (nextDinerIndex < diners.size() && diners.get(nextDinerIndex).arrivalMinute() <= minute) {
                DinerProfile diner = diners.get(nextDinerIndex);
                WindowChoice originalChoice = chooseWindow(
                        diner,
                        seedData,
                        queueLengths,
                        closedWindowIds,
                        scenario.mealPeriod()
                );
                WindowChoice finalChoice = maybeRedirectWindow(
                        diner,
                        originalChoice,
                        diversionPlan,
                        minute,
                        seedData,
                        queueLengths,
                        closedWindowIds,
                        scenario.mealPeriod()
                );
                chooseDish(diner, finalChoice.window(), seedData.dishesByWindow());
                queueLengths.compute(finalChoice.window().windowId(), (ignored, value) -> value == null ? 1 : value + 1);
                selectedWaits.add(finalChoice.estimatedWait());
                nextDinerIndex++;
            }

            SnapshotData snapshot = snapshot(minute, seedData, queueLengths, closedWindowIds, scenario.mealPeriod());
            maxQueueLength = Math.max(maxQueueLength, snapshot.maxQueueLength());
            int[] crowdCounts = countCrowdWindows(snapshot.timePoint());
            maxBusyWindowCount = Math.max(maxBusyWindowCount, crowdCounts[0]);
            maxExtremeWindowCount = Math.max(maxExtremeWindowCount, crowdCounts[1]);
            timePoints.add(snapshot.timePoint());
        }

        int unservedUserCount = queueLengths.values().stream().mapToInt(Integer::intValue).sum();
        int totalVirtualUsers = diners.size();
        double avgWait = selectedWaits.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double maxWait = selectedWaits.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        EvaluationMetrics metrics = new EvaluationMetrics(
                round(avgWait),
                round(maxWait),
                maxQueueLength,
                maxBusyWindowCount,
                maxExtremeWindowCount,
                totalVirtualUsers,
                Math.max(0, totalVirtualUsers - unservedUserCount),
                unservedUserCount
        );

        long runId = runIdGenerator.incrementAndGet();
        SimulationRunResult result = new SimulationRunResult(
                runId,
                "FINISHED",
                profile,
                scenario,
                timePoints,
                metrics,
                now(),
                diversionPlan != null && diversionPlan.hasSuggestions(),
                compareSourceRunId,
                diversionPlan == null ? null : diversionPlan.startMinute()
        );
        runStore.put(runId, result);
        cohortStore.put(runId, List.copyOf(dinerCohort));
        if (diversionPlan != null && diversionPlan.hasSuggestions()) {
            diversionMetaStore.put(runId, new DiversionRunMeta(
                    diversionPlan.strategySnapshot(),
                    diversionPlan.acceptedDiversionCount(),
                    diversionPlan.crossRestaurantDiversionCount()
            ));
        } else {
            diversionMetaStore.remove(runId);
        }
        return result;
    }

    private UserProfile normalizeProfile(UserProfile profile) {
        if (profile == null) {
            return new UserProfile("STUDENT", List.of("米饭", "辣味"), 10.0, 22.0, 10);
        }
        return new UserProfile(
                defaultText(profile.userType(), "STUDENT"),
                profile.tasteTags() == null || profile.tasteTags().isEmpty() ? List.of("米饭", "辣味") : profile.tasteTags(),
                profile.budgetMin() == null ? 10.0 : profile.budgetMin(),
                profile.budgetMax() == null ? 22.0 : profile.budgetMax(),
                profile.waitingToleranceMinutes() == null ? 10 : profile.waitingToleranceMinutes()
        );
    }

    private SimulationScenario normalizeScenario(SimulationScenario scenario) {
        if (scenario == null) {
            return new SimulationScenario("LUNCH", "WEEKDAY", "BUSY", 1.0, 1.1, List.of(), 800, 60, 3, 20260427L);
        }
        return new SimulationScenario(
                defaultText(scenario.mealPeriod(), "LUNCH"),
                defaultText(scenario.dayType(), "WEEKDAY"),
                defaultText(scenario.crowdLevel(), "BUSY"),
                scenario.weatherFactor() == null ? 1.0 : scenario.weatherFactor(),
                scenario.eventFactor() == null ? 1.1 : scenario.eventFactor(),
                scenario.closedWindowIds() == null ? List.of() : scenario.closedWindowIds(),
                scenario.virtualUserCount() == null ? 800 : scenario.virtualUserCount(),
                scenario.durationMinutes() == null ? 60 : scenario.durationMinutes(),
                scenario.stepMinutes() == null ? 3 : scenario.stepMinutes(),
                scenario.randomSeed() == null ? 20260427L : scenario.randomSeed()
        );
    }

    private void validate(UserProfile profile, SimulationScenario scenario) {
        validateProfile(profile);
        validateScenario(scenario);
    }

    private void validatePreview(UserProfile profile, SimulationScenario scenario) {
        validateProfile(profile);
        validateScenario(scenario);
    }

    private void validateProfile(UserProfile profile) {
        if (!Set.of("STUDENT", "HURRY", "BUDGET_SENSITIVE").contains(profile.userType())) {
            throw new BadRequestException("profile.userType", "userType must be STUDENT, HURRY or BUDGET_SENSITIVE");
        }
        if (profile.budgetMin() < 0 || profile.budgetMax() < profile.budgetMin()) {
            throw new BadRequestException("profile.budgetMax", "budget range is invalid");
        }
        if (profile.waitingToleranceMinutes() < 1) {
            throw new BadRequestException("profile.waitingToleranceMinutes", "waitingToleranceMinutes must be greater than 0");
        }
    }

    private void validateScenario(SimulationScenario scenario) {
        if (!Set.of("BREAKFAST", "LUNCH", "DINNER").contains(scenario.mealPeriod())) {
            throw new BadRequestException("scenario.mealPeriod", "mealPeriod must be BREAKFAST, LUNCH or DINNER");
        }
        if (!Set.of("WEEKDAY", "WEEKEND").contains(scenario.dayType())) {
            throw new BadRequestException("scenario.dayType", "dayType must be WEEKDAY or WEEKEND");
        }
        if (!CROWD_SPREAD_FACTOR.containsKey(scenario.crowdLevel())) {
            throw new BadRequestException("scenario.crowdLevel", "crowdLevel must be IDLE, NORMAL, BUSY or EXTREME");
        }
        if (scenario.virtualUserCount() < 1 || scenario.virtualUserCount() > 5000) {
            throw new BadRequestException("scenario.virtualUserCount", "virtualUserCount must be between 1 and 5000");
        }
        if (scenario.durationMinutes() < 5 || scenario.durationMinutes() > 180) {
            throw new BadRequestException("scenario.durationMinutes", "durationMinutes must be between 5 and 180");
        }
        if (scenario.stepMinutes() < 1 || scenario.stepMinutes() > 30) {
            throw new BadRequestException("scenario.stepMinutes", "stepMinutes must be between 1 and 30");
        }
        if (scenario.weatherFactor() <= 0 || scenario.eventFactor() <= 0) {
            throw new BadRequestException("scenario.weatherFactor", "weatherFactor and eventFactor must be positive");
        }
    }

    private int resolveDiversionStartMinute(
            SimulationRunResult baseRun,
            SimulationScenario scenario,
            Integer requestedMinute
    ) {
        int resolved = requestedMinute == null ? resolvePeakMinute(baseRun) : requestedMinute;
        if (resolved < 0 || resolved > scenario.durationMinutes()) {
            throw new BadRequestException("minute", "minute is out of simulation range");
        }
        return resolved;
    }

    private DiversionPlan buildDiversionPlan(
            int diversionStartMinute,
            List<DiversionSuggestion> suggestions,
            DiversionStrategyParameters strategyParameters,
            SeedData seedData
    ) {
        DiversionStrategySupport.ResolvedParameters resolvedParameters =
                DiversionStrategySupport.resolve(strategyParameters);
        DiversionStrategyParameters strategySnapshot = DiversionStrategySupport.snapshot(resolvedParameters);
        List<ResolvedDiversionSuggestion> resolvedSuggestions = new ArrayList<>();
        Map<Long, List<ResolvedDiversionSuggestion>> suggestionsBySourceWindow = new LinkedHashMap<>();
        int acceptedDiversionCount = 0;
        int crossRestaurantDiversionCount = 0;
        for (DiversionSuggestion suggestion : suggestions) {
            if (suggestion == null) {
                continue;
            }
            if (suggestion.fromWindowId() == suggestion.toWindowId()) {
                continue;
            }
            WindowSeed fromWindow = seedData.windowsById().get(suggestion.fromWindowId());
            WindowSeed toWindow = seedData.windowsById().get(suggestion.toWindowId());
            if (fromWindow == null || toWindow == null) {
                throw new BadRequestException("diversionSuggestions", "diversionSuggestions contains unknown windowId");
            }

            int acceptedCount = resolvedAcceptedCount(suggestion);
            if (acceptedCount <= 0) {
                continue;
            }

            ResolvedDiversionSuggestion resolved = new ResolvedDiversionSuggestion(
                    suggestion.fromRestaurantId(),
                    suggestion.fromWindowId(),
                    suggestion.toRestaurantId(),
                    suggestion.toWindowId(),
                    suggestion.suggestedUserCount() == null ? acceptedCount : suggestion.suggestedUserCount(),
                    clamp(suggestion.acceptanceRate() == null ? 0.5 : suggestion.acceptanceRate(), 0.0, 1.0),
                    acceptedCount
            );
            resolvedSuggestions.add(resolved);
            suggestionsBySourceWindow.computeIfAbsent(resolved.fromWindowId(), ignored -> new ArrayList<>()).add(resolved);
            acceptedDiversionCount += acceptedCount;
            if (resolved.fromRestaurantId() != resolved.toRestaurantId()) {
                crossRestaurantDiversionCount++;
            }
        }

        if (resolvedSuggestions.isEmpty()) {
            throw new BadRequestException("diversionSuggestions", "no valid diversion suggestion can be applied");
        }

        return new DiversionPlan(
                diversionStartMinute,
                List.copyOf(resolvedSuggestions),
                resolvedParameters,
                strategySnapshot,
                acceptedDiversionCount,
                crossRestaurantDiversionCount,
                suggestionsBySourceWindow.entrySet().stream()
                        .collect(LinkedHashMap::new,
                                (map, entry) -> map.put(entry.getKey(), List.copyOf(entry.getValue())),
                                Map::putAll)
        );
    }

    private List<DinerProfile> sampleVirtualDiners(SeedData seedData, UserProfile profile, SimulationScenario scenario) {
        Random rng = new Random(scenario.randomSeed());
        List<StudentSeed> sourcePool = chooseStudentPool(seedData.students(), profile.userType(), scenario.virtualUserCount());
        List<StudentSeed> sampled = sample(sourcePool, scenario.virtualUserCount(), rng);
        Set<String> profileTags = normalizeProfileTags(profile.tasteTags());
        double pressure = CROWD_COUNT_FACTOR.get(scenario.crowdLevel()) * scenario.weatherFactor() * scenario.eventFactor();
        ArrivalRuleSeed arrivalRule = arrivalRuleFor(seedData, scenario.mealPeriod());
        List<Integer> arrivalMinutes = arrivalCurveGenerator.generateArrivalMinutes(
                arrivalRule,
                scenario.dayType(),
                scenario.crowdLevel(),
                scenario.durationMinutes(),
                pressure,
                sampled.size(),
                rng
        );

        List<DinerProfile> diners = new ArrayList<>();
        for (int index = 0; index < sampled.size(); index++) {
            StudentSeed student = sampled.get(index);
            int arrivalMinute = arrivalMinutes.get(index);
            Set<String> preferenceTags = new LinkedHashSet<>(tagNormalizationService.normalize(student.preferenceTags()));
            preferenceTags.addAll(profileTags);
            double budgetMin = Math.max(0.0, profile.budgetMin() + randomInt(rng, -2, 2));
            double budgetMax = Math.max(budgetMin, profile.budgetMax() + randomInt(rng, -3, 4));
            int waitingToleranceMinutes = Math.max(1, profile.waitingToleranceMinutes() + randomInt(rng, -2, 3));
            double popularityBias = clamp(
                    1.12 + rng.nextDouble() * 0.72 + ("HURRY".equals(profile.userType()) ? -0.08 : 0.15),
                    0.90,
                    2.05
            );
            double queueAversion = clamp(
                    0.88 + rng.nextDouble() * 0.45 + ("HURRY".equals(profile.userType()) ? 0.16 : 0.0),
                    0.70,
                    1.55
            );
            double diversionAcceptanceBias = clamp(
                    0.94 + rng.nextDouble() * 0.42 + ("HURRY".equals(profile.userType()) ? 0.12 : 0.06),
                    0.78,
                    1.55
            );
            diners.add(new DinerProfile(
                    "D%05d".formatted(index + 1),
                    student.studentId(),
                    profile.userType(),
                    Set.copyOf(preferenceTags),
                    budgetMin,
                    budgetMax,
                    waitingToleranceMinutes,
                    arrivalMinute,
                    popularityBias,
                    queueAversion,
                    diversionAcceptanceBias,
                    rng.nextDouble(),
                    rng.nextDouble(),
                    rng.nextDouble(),
                    rng.nextLong()
            ));
        }
        diners.sort(Comparator.comparingInt(DinerProfile::arrivalMinute).thenComparing(DinerProfile::dinerId));
        return List.copyOf(diners);
    }

    private ArrivalRuleSeed arrivalRuleFor(SeedData seedData, String mealPeriod) {
        ArrivalRuleSeed rule = seedData.arrivalRulesByMealPeriod().get(mealPeriod);
        if (rule == null) {
            throw new BadRequestException("scenario.mealPeriod", "arrival rule is missing for mealPeriod=" + mealPeriod);
        }
        return rule;
    }

    private List<StudentSeed> chooseStudentPool(List<StudentSeed> students, String userType, int targetCount) {
        if (!Set.of("HURRY", "BUDGET_SENSITIVE").contains(userType)) {
            return students;
        }
        List<StudentSeed> preferred = students.stream()
                .filter(item -> userType.equals(item.userType()))
                .toList();
        return preferred.size() >= Math.max(20, targetCount / 5) ? preferred : students;
    }

    private List<StudentSeed> sample(List<StudentSeed> sourcePool, int targetCount, Random rng) {
        if (targetCount <= sourcePool.size()) {
            List<StudentSeed> copy = new ArrayList<>(sourcePool);
            for (int i = copy.size() - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                StudentSeed temp = copy.get(i);
                copy.set(i, copy.get(j));
                copy.set(j, temp);
            }
            return new ArrayList<>(copy.subList(0, targetCount));
        }
        List<StudentSeed> result = new ArrayList<>();
        for (int i = 0; i < targetCount; i++) {
            result.add(sourcePool.get(rng.nextInt(sourcePool.size())));
        }
        return result;
    }

    private void serveQueues(
            SeedData seedData,
            SimulationScenario scenario,
            Set<Long> closedWindowIds,
            Map<Long, Integer> queueLengths,
            Map<Long, Double> serviceCarry,
            int elapsed
    ) {
        for (WindowSeed window : seedData.windows()) {
            if (!isWindowAvailable(window, scenario.mealPeriod(), closedWindowIds)) {
                queueLengths.put(window.windowId(), 0);
                serviceCarry.put(window.windowId(), 0.0);
                continue;
            }
            double carry = serviceCarry.get(window.windowId()) + window.serviceRatePerMinute() * elapsed;
            int canServe = (int) carry;
            int served = Math.min(queueLengths.get(window.windowId()), canServe);
            queueLengths.put(window.windowId(), queueLengths.get(window.windowId()) - served);
            serviceCarry.put(window.windowId(), carry - served);
        }
    }

    private WindowChoice chooseWindow(
            DinerProfile diner,
            SeedData seedData,
            Map<Long, Integer> queueLengths,
            Set<Long> closedWindowIds,
            String mealPeriod
    ) {
        List<WindowScore> scoredWindows = new ArrayList<>();
        double maxScore = -1_000_000_000;

        for (WindowSeed window : seedData.windows()) {
            if (!isWindowAvailable(window, mealPeriod, closedWindowIds)) {
                continue;
            }
            RestaurantSeed restaurant = seedData.restaurantsById().get(window.restaurantId());
            double estimatedWait = windowWaitMinutes(window, queueLengths.get(window.windowId()));
            double tagScore = tagOverlapScore(diner.preferenceTags(), window.matchingTags());
            double budgetScore = budgetOverlapScore(diner.budgetMin(), diner.budgetMax(), window.priceMin(), window.priceMax());
            double budgetWeight = "BUDGET_SENSITIVE".equals(diner.userType()) ? 0.18 : 0.08;
            double popularityScore = Math.pow(window.popularity(), 0.80) * 0.42 * diner.popularityBias();
            double attractionScore = restaurant.baseAttraction() * 0.24;
            double waitPenalty = delayedWaitPenalty(diner, estimatedWait);
            double score = attractionScore
                    + tagScore * 0.20
                    + budgetScore * budgetWeight
                    + popularityScore
                    - waitPenalty
                    + stableSignedNoise(diner.decisionSeed(), window.windowId(), 0.014);
            maxScore = Math.max(maxScore, score);
            scoredWindows.add(new WindowScore(window, estimatedWait, score));
        }

        if (scoredWindows.isEmpty()) {
            throw new BadRequestException("scenario.mealPeriod", "no window is available under the current scenario");
        }

        double totalWeight = 0.0;
        List<Double> weights = new ArrayList<>(scoredWindows.size());
        for (WindowScore candidate : scoredWindows) {
            double weight = Math.exp((candidate.rawScore() - maxScore) * WINDOW_SELECTION_TEMPERATURE);
            weights.add(weight);
            totalWeight += weight;
        }

        double threshold = diner.windowChoiceSample() * totalWeight;
        double cumulative = 0.0;
        WindowScore selected = scoredWindows.get(scoredWindows.size() - 1);
        for (int index = 0; index < scoredWindows.size(); index++) {
            cumulative += weights.get(index);
            if (threshold <= cumulative) {
                selected = scoredWindows.get(index);
                break;
            }
        }
        return new WindowChoice(selected.window(), selected.estimatedWait());
    }

    private WindowChoice maybeRedirectWindow(
            DinerProfile diner,
            WindowChoice originalChoice,
            DiversionPlan diversionPlan,
            int minute,
            SeedData seedData,
            Map<Long, Integer> queueLengths,
            Set<Long> closedWindowIds,
            String mealPeriod
    ) {
        if (diversionPlan == null || !diversionPlan.hasSuggestions() || minute < diversionPlan.startMinute()) {
            return originalChoice;
        }

        List<ResolvedDiversionSuggestion> candidates = diversionPlan.suggestionsBySourceWindow().get(originalChoice.window().windowId());
        if (candidates == null || candidates.isEmpty()) {
            return originalChoice;
        }

        WindowChoice redirectedChoice = null;
        double bestOpportunity = -1_000_000_000;
        int sourceQueueLength = queueLengths.getOrDefault(originalChoice.window().windowId(), 0);
        double sourceWait = windowWaitMinutes(originalChoice.window(), sourceQueueLength);
        double sourcePressure = queuePressureScore(originalChoice.window(), sourceQueueLength, sourceWait);
        DiversionStrategySupport.ResolvedParameters strategy = diversionPlan.strategyParameters();
        for (ResolvedDiversionSuggestion candidate : candidates) {
            WindowSeed targetWindow = seedData.windowsById().get(candidate.toWindowId());
            if (targetWindow == null || !isWindowAvailable(targetWindow, mealPeriod, closedWindowIds)) {
                continue;
            }
            int targetQueueLength = queueLengths.getOrDefault(targetWindow.windowId(), 0);
            if (targetQueueLength >= maxReasonableTargetQueue(targetWindow)) {
                continue;
            }
            double targetWait = windowWaitMinutes(targetWindow, targetQueueLength);
            double targetPressure = queuePressureScore(targetWindow, targetQueueLength, targetWait);
            double pressureBuffer = Math.max(0.03, strategy.strictTargetPressureBuffer() * 0.5);
            if (targetPressure >= sourcePressure - pressureBuffer || targetWait > sourceWait + 1.5) {
                continue;
            }
            double pressureGap = Math.max(0.0, sourcePressure - targetPressure);
            double effectiveAcceptanceRate = clamp(
                    candidate.acceptanceRate() * diner.diversionAcceptanceBias() * 1.08
                            + Math.min(0.24, Math.max(0.0, sourceWait - targetWait) * strategy.waitReductionAcceptanceWeight() * 0.9)
                            + Math.min(0.18, pressureGap * strategy.pressureGapAcceptanceWeight() * 0.8)
                            + strategy.acceptanceBaseRate() * 0.18,
                    0.10,
                    0.995
            );
            if (diner.diversionAcceptanceSample() > effectiveAcceptanceRate) {
                continue;
            }
            double opportunityScore = pressureGap * strategy.pressureGapTransferWeight() * 0.26
                    + Math.max(0.0, sourceWait - targetWait) * 0.26
                    + effectiveAcceptanceRate * 0.22;
            if (opportunityScore > bestOpportunity) {
                bestOpportunity = opportunityScore;
                redirectedChoice = new WindowChoice(targetWindow, targetWait);
            }
        }
        return redirectedChoice == null ? originalChoice : redirectedChoice;
    }

    private DishSeed chooseDish(
            DinerProfile diner,
            WindowSeed window,
            Map<Long, List<DishSeed>> dishesByWindow
    ) {
        List<DishSeed> dishes = dishesByWindow.getOrDefault(window.windowId(), List.of());
        if (dishes.isEmpty()) {
            return null;
        }
        List<DishScore> scoredDishes = new ArrayList<>(dishes.size());
        double maxScore = -1_000_000_000;
        for (DishSeed dish : dishes) {
            double budgetScore = diner.budgetMin() <= dish.price() && dish.price() <= diner.budgetMax() ? 1.0 : 0.25;
            double tagScore = tagOverlapScore(diner.preferenceTags(), dish.matchingTags());
            double prepPenalty = dish.prepTimeMinutes() / 15.0;
            double score = tagScore * 0.34
                    + budgetScore * 0.32
                    + dish.popularity() * 0.24
                    - prepPenalty * 0.10
                    + stableSignedNoise(diner.decisionSeed() ^ 0x9E3779B97F4A7C15L, dish.dishId(), 0.012);
            maxScore = Math.max(maxScore, score);
            scoredDishes.add(new DishScore(dish, score));
        }

        double totalWeight = 0.0;
        List<Double> weights = new ArrayList<>(scoredDishes.size());
        for (DishScore candidate : scoredDishes) {
            double weight = Math.exp((candidate.rawScore() - maxScore) * DISH_SELECTION_TEMPERATURE);
            weights.add(weight);
            totalWeight += weight;
        }

        double threshold = diner.dishChoiceSample() * totalWeight;
        double cumulative = 0.0;
        DishSeed selected = scoredDishes.get(scoredDishes.size() - 1).dish();
        for (int index = 0; index < scoredDishes.size(); index++) {
            cumulative += weights.get(index);
            if (threshold <= cumulative) {
                selected = scoredDishes.get(index).dish();
                break;
            }
        }
        return selected;
    }

    private void applyDiversionToExistingQueues(
            SeedData seedData,
            SimulationScenario scenario,
            DiversionPlan diversionPlan,
            Set<Long> closedWindowIds,
            Map<Long, Integer> queueLengths
    ) {
        for (ResolvedDiversionSuggestion suggestion : diversionPlan.suggestions()) {
            WindowSeed sourceWindow = seedData.windowsById().get(suggestion.fromWindowId());
            WindowSeed targetWindow = seedData.windowsById().get(suggestion.toWindowId());
            if (sourceWindow == null || targetWindow == null) {
                continue;
            }
            if (!isWindowAvailable(sourceWindow, scenario.mealPeriod(), closedWindowIds)
                    || !isWindowAvailable(targetWindow, scenario.mealPeriod(), closedWindowIds)) {
                continue;
            }

            int sourceQueueLength = queueLengths.getOrDefault(sourceWindow.windowId(), 0);
            int targetQueueLength = queueLengths.getOrDefault(targetWindow.windowId(), 0);
            if (sourceQueueLength <= 0) {
                continue;
            }

            double sourceWait = windowWaitMinutes(sourceWindow, sourceQueueLength);
            double targetWait = windowWaitMinutes(targetWindow, targetQueueLength);
            double sourcePressure = queuePressureScore(sourceWindow, sourceQueueLength, sourceWait);
            double targetPressure = queuePressureScore(targetWindow, targetQueueLength, targetWait);
            if (targetPressure >= sourcePressure - diversionPlan.strategyParameters().strictTargetPressureBuffer()) {
                continue;
            }

            int targetCapacity = Math.max(0, maxReasonableTargetQueue(targetWindow) - targetQueueLength);
            int balanceDrivenCount = Math.max(
                    1,
                    (int) Math.ceil(Math.max(0.0, sourceQueueLength - targetQueueLength)
                            * diversionPlan.strategyParameters().queueGapTransferWeight())
            );
            int pressureDrivenCount = Math.max(
                    1,
                    (int) Math.ceil(Math.max(0.0, sourcePressure - targetPressure)
                            * Math.max(1.0, targetWindow.serviceRatePerMinute())
                            * diversionPlan.strategyParameters().pressureGapTransferWeight())
            );
            int desiredTransfer = Math.max(
                    Math.max(balanceDrivenCount, pressureDrivenCount),
                    (int) Math.ceil(suggestion.estimatedAcceptedCount() * 1.20)
            );
            int movedCount = Math.min(
                    sourceQueueLength,
                    Math.min(
                            diversionPlan.strategyParameters().maxTransferCount(),
                            Math.min(suggestion.estimatedAcceptedCount(), Math.min(targetCapacity, desiredTransfer))
                    )
            );
            if (movedCount <= 0) {
                continue;
            }

            queueLengths.put(sourceWindow.windowId(), sourceQueueLength - movedCount);
            queueLengths.put(targetWindow.windowId(), targetQueueLength + movedCount);
        }
    }

    private SnapshotData snapshot(
            int minute,
            SeedData seedData,
            Map<Long, Integer> queueLengths,
            Set<Long> closedWindowIds,
            String mealPeriod
    ) {
        Map<Long, List<QueueState>> windowsByRestaurant = new LinkedHashMap<>();
        int maxQueue = 0;
        for (WindowSeed window : seedData.windows()) {
            boolean available = isWindowAvailable(window, mealPeriod, closedWindowIds);
            String status = available ? "OPEN" : "CLOSED";
            int queueLength = available ? queueLengths.get(window.windowId()) : 0;
            double waitMinutes = available ? windowWaitMinutes(window, queueLength) : 0.0;
            int servingCount = available && queueLength > 0 ? (int) Math.ceil(window.serviceRatePerMinute()) : 0;
            maxQueue = Math.max(maxQueue, queueLength);
            QueueState state = new QueueState(
                    window.windowId(),
                    window.name(),
                    queueLength,
                    servingCount,
                    round(waitMinutes),
                    crowdLevelForWindow(waitMinutes, status),
                    status
            );
            windowsByRestaurant.computeIfAbsent(window.restaurantId(), ignored -> new ArrayList<>()).add(state);
        }

        List<RestaurantTimePoint> restaurants = new ArrayList<>();
        for (RestaurantSeed restaurant : seedData.restaurants()) {
            List<QueueState> windows = windowsByRestaurant.getOrDefault(restaurant.restaurantId(), List.of());
            int currentCount = windows.stream().mapToInt(item -> item.queueLength() + item.servingCount()).sum();
            restaurants.add(new RestaurantTimePoint(
                    restaurant.restaurantId(),
                    restaurant.name(),
                    currentCount,
                    restaurant.capacity(),
                    crowdLevelForRestaurant(currentCount, restaurant.capacity()),
                    windows
            ));
        }
        return new SnapshotData(new SimulationTimePoint(minute, restaurants), maxQueue);
    }

    private int[] countCrowdWindows(SimulationTimePoint point) {
        int busy = 0;
        int extreme = 0;
        for (RestaurantTimePoint restaurant : point.restaurants()) {
            for (QueueState window : restaurant.windows()) {
                if ("BUSY".equals(window.crowdLevel())) {
                    busy++;
                } else if ("EXTREME".equals(window.crowdLevel())) {
                    extreme++;
                }
            }
        }
        return new int[] {busy, extreme};
    }

    private boolean isWindowAvailable(WindowSeed window, String mealPeriod, Set<Long> closedWindowIds) {
        if (closedWindowIds.contains(window.windowId())) {
            return false;
        }
        if (!"OPEN".equals(window.status())) {
            return false;
        }
        return mealPeriod.equals(window.recommendedMealPeriod()) || "ALL".equals(window.recommendedMealPeriod());
    }

    private double tagOverlapScore(Set<String> userTags, Set<String> targetTags) {
        if (userTags == null || userTags.isEmpty()) {
            return 0.0;
        }
        long matched = userTags.stream().filter(targetTags::contains).count();
        return matched * 1.0 / userTags.size();
    }

    private double budgetOverlapScore(double budgetMin, double budgetMax, double priceMin, double priceMax) {
        if (priceMax < budgetMin || priceMin > budgetMax) {
            double distance = Math.min(Math.abs(priceMax - budgetMin), Math.abs(priceMin - budgetMax));
            return Math.max(0.0, 1.0 - distance / Math.max(1.0, budgetMax - budgetMin + 8.0));
        }
        double overlap = Math.min(budgetMax, priceMax) - Math.max(budgetMin, priceMin);
        return Math.max(0.35, Math.min(1.0, overlap / Math.max(1.0, budgetMax - budgetMin)));
    }

    private double delayedWaitPenalty(DinerProfile diner, double estimatedWait) {
        double effectiveTolerance = Math.max(1.0, diner.waitingToleranceMinutes() / Math.max(0.65, diner.queueAversion()));
        double waitRatio = estimatedWait / effectiveTolerance;
        double basePenalty;
        if (waitRatio <= 0.75) {
            basePenalty = waitRatio * 0.03;
        } else if (waitRatio <= 1.0) {
            basePenalty = 0.0225 + (waitRatio - 0.75) * 0.18;
        } else {
            double overflow = waitRatio - 1.0;
            basePenalty = 0.0675 + Math.pow(overflow, 1.48) * 0.34;
        }
        double userTypeWeight = switch (diner.userType()) {
            case "HURRY" -> 1.20;
            case "BUDGET_SENSITIVE" -> 0.88;
            default -> 0.95;
        };
        return basePenalty * userTypeWeight;
    }

    private double queuePressureScore(WindowSeed window, int queueLength, double waitMinutes) {
        double normalizedWait = waitMinutes / 7.0;
        double normalizedQueue = queueLength / Math.max(1.0, window.serviceRatePerMinute() * 4.5);
        double servicePenalty = 1.0 / Math.max(1.0, window.serviceRatePerMinute());
        return normalizedWait * 0.95 + normalizedQueue * 0.80 + servicePenalty * 0.20;
    }

    private String crowdLevelForRestaurant(int currentCount, int capacity) {
        if (capacity <= 0) {
            return "IDLE";
        }
        double ratio = currentCount * 1.0 / capacity;
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

    private String crowdLevelForWindow(double waitMinutes, String status) {
        if ("CLOSED".equals(status)) {
            return "IDLE";
        }
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

    private List<ArrivalCurvePoint> buildArrivalCurvePoints(
            double[] weights,
            Map<Integer, Integer> minuteCounts,
            int durationMinutes
    ) {
        List<ArrivalCurvePoint> points = new ArrayList<>(durationMinutes + 1);
        for (int minute = 0; minute <= durationMinutes; minute++) {
            double weight = minute < weights.length ? weights[minute] : 0.0;
            points.add(new ArrivalCurvePoint(minute, minuteCounts.getOrDefault(minute, 0), round(weight)));
        }
        return List.copyOf(points);
    }

    private Map<Integer, Integer> toMinuteCounts(List<Integer> minutes) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (Integer minute : minutes) {
            counts.merge(minute, 1, Integer::sum);
        }
        return counts;
    }

    private MinuteMetricPoint toMinuteMetricPoint(SimulationTimePoint point, int arrivals) {
        List<QueueState> openWindows = point.restaurants().stream()
                .flatMap(restaurant -> restaurant.windows().stream())
                .filter(window -> "OPEN".equals(window.status()))
                .toList();
        int totalQueueLength = openWindows.stream().mapToInt(QueueState::queueLength).sum();
        int totalCurrentCount = point.restaurants().stream().mapToInt(RestaurantTimePoint::currentCount).sum();
        double avgWindowWait = openWindows.stream().mapToDouble(QueueState::waitMinutes).average().orElse(0.0);
        int overloadedWindowCount = (int) openWindows.stream()
                .filter(window -> "BUSY".equals(window.crowdLevel()) || "EXTREME".equals(window.crowdLevel()))
                .count();
        int extremeWindowCount = (int) openWindows.stream()
                .filter(window -> "EXTREME".equals(window.crowdLevel()))
                .count();
        return new MinuteMetricPoint(
                point.minute(),
                arrivals,
                totalQueueLength,
                totalCurrentCount,
                round(avgWindowWait),
                overloadedWindowCount,
                extremeWindowCount,
                round(queueImbalanceIndex(openWindows))
        );
    }

    private WindowPressurePoint toWindowPressurePoint(SimulationTimePoint point) {
        SeedData seedData = seedDataService.seedData();
        List<WindowPressureItem> windows = new ArrayList<>();
        for (RestaurantTimePoint restaurant : point.restaurants()) {
            for (QueueState window : restaurant.windows()) {
                WindowSeed windowSeed = seedData.windowsById().get(window.windowId());
                if (windowSeed == null || !"OPEN".equals(window.status())) {
                    continue;
                }
                windows.add(new WindowPressureItem(
                        restaurant.restaurantId(),
                        restaurant.name(),
                        window.windowId(),
                        window.name(),
                        window.queueLength(),
                        window.waitMinutes(),
                        window.crowdLevel(),
                        round(queuePressureScore(windowSeed, window.queueLength(), window.waitMinutes()))
                ));
            }
        }
        return new WindowPressurePoint(point.minute(), List.copyOf(windows));
    }

    private double peakWait10m(List<MinuteMetricPoint> points, int stepMinutes) {
        if (points.isEmpty()) {
            return 0.0;
        }
        int windowSize = Math.max(1, (int) Math.ceil(10.0 / stepMinutes));
        double best = 0.0;
        for (int start = 0; start < points.size(); start++) {
            int end = Math.min(points.size(), start + windowSize);
            double average = points.subList(start, end).stream()
                    .mapToDouble(MinuteMetricPoint::avgWindowWait)
                    .average()
                    .orElse(0.0);
            best = Math.max(best, average);
        }
        return round(best);
    }

    private double queueImbalanceIndex(List<QueueState> windows) {
        if (windows.isEmpty()) {
            return 0.0;
        }
        double average = windows.stream().mapToInt(QueueState::queueLength).average().orElse(0.0);
        if (average <= 0.0) {
            return 0.0;
        }
        double variance = windows.stream()
                .mapToDouble(window -> {
                    double delta = window.queueLength() - average;
                    return delta * delta;
                })
                .average()
                .orElse(0.0);
        return Math.sqrt(variance) / (average + 1.0);
    }

    private int metricIntervalMinutes(List<MinuteMetricPoint> points) {
        if (points.size() < 2) {
            return 1;
        }
        int minGap = Integer.MAX_VALUE;
        for (int index = 1; index < points.size(); index++) {
            int gap = Math.max(1, points.get(index).minute() - points.get(index - 1).minute());
            minGap = Math.min(minGap, gap);
        }
        return minGap == Integer.MAX_VALUE ? 1 : minGap;
    }

    private Set<String> normalizeProfileTags(List<String> tags) {
        return tagNormalizationService.normalize(tags);
    }

    private int resolvedAcceptedCount(DiversionSuggestion suggestion) {
        if (suggestion.estimatedAcceptedCount() != null && suggestion.estimatedAcceptedCount() > 0) {
            return suggestion.estimatedAcceptedCount();
        }
        if (suggestion.suggestedUserCount() == null || suggestion.suggestedUserCount() <= 0) {
            return 0;
        }
        double acceptanceRate = suggestion.acceptanceRate() == null ? 0.5 : suggestion.acceptanceRate();
        return Math.max(1, (int) Math.round(suggestion.suggestedUserCount() * clamp(acceptanceRate, 0.0, 1.0)));
    }

    private int maxReasonableTargetQueue(WindowSeed targetWindow) {
        return Math.max(3, (int) Math.floor(Math.max(1.0, targetWindow.serviceRatePerMinute()) * 18.0));
    }

    private double windowWaitMinutes(WindowSeed window, int queueLength) {
        return queueLength / Math.max(0.1, window.serviceRatePerMinute());
    }

    private double stableSignedNoise(long baseSeed, long salt, double amplitude) {
        return (stableUnitInterval(baseSeed, salt) - 0.5) * 2.0 * amplitude;
    }

    private double stableUnitInterval(long baseSeed, long salt) {
        long mixed = mix64(baseSeed ^ (salt * 0x9E3779B97F4A7C15L));
        return ((mixed >>> 11) * 0x1.0p-53);
    }

    private long mix64(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    private int resolvePeakMinute(SimulationRunResult run) {
        SimulationTimePoint peak = run.timePoints().stream()
                .max(Comparator
                        .comparingInt(this::totalQueueLength)
                        .thenComparingInt(this::totalCurrentCount)
                        .thenComparingInt(SimulationTimePoint::minute))
                .orElseThrow(() -> new BadRequestException("baseRunId", "baseRun has no time points"));
        return peak.minute();
    }

    private int totalQueueLength(SimulationTimePoint timePoint) {
        return timePoint.restaurants().stream()
                .flatMap(restaurant -> restaurant.windows().stream())
                .mapToInt(QueueState::queueLength)
                .sum();
    }

    private int totalCurrentCount(SimulationTimePoint timePoint) {
        return timePoint.restaurants().stream()
                .mapToInt(RestaurantTimePoint::currentCount)
                .sum();
    }

    private int randomInt(Random rng, int minInclusive, int maxInclusive) {
        return minInclusive + rng.nextInt(maxInclusive - minInclusive + 1);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.ofHours(8))
                .withNano(0)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private record WindowChoice(WindowSeed window, double estimatedWait) {
    }

    private record WindowScore(WindowSeed window, double estimatedWait, double rawScore) {
    }

    private record DishScore(DishSeed dish, double rawScore) {
    }

    private record SnapshotData(SimulationTimePoint timePoint, int maxQueueLength) {
    }

    private record DiversionPlan(
            int startMinute,
            List<ResolvedDiversionSuggestion> suggestions,
            DiversionStrategySupport.ResolvedParameters strategyParameters,
            DiversionStrategyParameters strategySnapshot,
            int acceptedDiversionCount,
            int crossRestaurantDiversionCount,
            Map<Long, List<ResolvedDiversionSuggestion>> suggestionsBySourceWindow
    ) {
        private boolean hasSuggestions() {
            return !suggestions.isEmpty();
        }
    }

    private record ResolvedDiversionSuggestion(
            long fromRestaurantId,
            long fromWindowId,
            long toRestaurantId,
            long toWindowId,
            int suggestedUserCount,
            double acceptanceRate,
            int estimatedAcceptedCount
    ) {
    }

    private record DinerProfile(
            String dinerId,
            String sourceStudentId,
            String userType,
            Set<String> preferenceTags,
            double budgetMin,
            double budgetMax,
            int waitingToleranceMinutes,
            int arrivalMinute,
            double popularityBias,
            double queueAversion,
            double diversionAcceptanceBias,
            double windowChoiceSample,
            double dishChoiceSample,
            double diversionAcceptanceSample,
            long decisionSeed
    ) {
    }

    private record DiversionRunMeta(
            DiversionStrategyParameters strategyParameters,
            int acceptedDiversionCount,
            int crossRestaurantDiversionCount
    ) {
    }
}
