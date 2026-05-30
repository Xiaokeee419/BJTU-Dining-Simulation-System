package com.bjtu.dining.taska.service;

import com.bjtu.dining.common.BadRequestException;
import com.bjtu.dining.common.ResourceNotFoundException;
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
    private static final Map<String, Double> CROWD_SPREAD_FACTOR = Map.of(
            "IDLE", 1.25,
            "NORMAL", 1.0,
            "BUSY", 0.72,
            "EXTREME", 0.52
    );
    private static final Map<String, Double> CROWD_COUNT_FACTOR = Map.of(
            "IDLE", 0.75,
            "NORMAL", 1.0,
            "BUSY", 1.18,
            "EXTREME", 1.38
    );
    private static final Map<String, Integer> MEAL_BASE_HORIZON = Map.of(
            "BREAKFAST", 60,
            "LUNCH", 90,
            "DINNER", 90
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
    private final AtomicLong runIdGenerator = new AtomicLong(10000);
    private final Map<Long, SimulationRunResult> runStore = new ConcurrentHashMap<>();

    public SimulationService(SeedDataService seedDataService) {
        this.seedDataService = seedDataService;
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
                new ScenarioPreset("weekday-lunch-peak", "工作日午餐高峰", "LUNCH", "WEEKDAY", "BUSY", 1.0, 1.2, List.of(), 800, 60, 5, 20260425),
                new ScenarioPreset("weekday-dinner-normal", "工作日晚餐常态", "DINNER", "WEEKDAY", "NORMAL", 1.0, 1.0, List.of(), 600, 60, 5, 20260426),
                new ScenarioPreset("rainy-lunch-extreme", "雨天午餐极端拥挤", "LUNCH", "WEEKDAY", "EXTREME", 1.25, 1.25, List.of(), 1200, 60, 5, 20260427)
        );
    }

    public SimulationRunResult runSimulation(SimulationRunRequest request) {
        UserProfile profile = normalizeProfile(request == null ? null : request.profile());
        SimulationScenario scenario = normalizeScenario(request == null ? null : request.scenario());
        validate(profile, scenario);
        return simulateRun(profile, scenario, null, null);
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

        int diversionStartMinute = resolveDiversionStartMinute(baseRun, scenario, request.minute());
        DiversionPlan diversionPlan = buildDiversionPlan(
                diversionStartMinute,
                request.diversionSuggestions(),
                seedDataService.seedData()
        );
        return simulateRun(profile, scenario, diversionPlan, baseRun.runId());
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

    private SimulationRunResult simulateRun(
            UserProfile profile,
            SimulationScenario scenario,
            DiversionPlan diversionPlan,
            Long compareSourceRunId
    ) {
        SeedData seedData = seedDataService.seedData();
        Random rng = new Random(scenario.randomSeed());
        Set<Long> closedWindowIds = new LinkedHashSet<>(scenario.closedWindowIds());
        List<Diner> diners = sampleVirtualDiners(seedData, profile, scenario);

        Map<Long, Integer> queueLengths = new LinkedHashMap<>();
        Map<Long, Double> serviceCarry = new LinkedHashMap<>();
        for (WindowSeed window : seedData.windows()) {
            queueLengths.put(window.windowId(), 0);
            serviceCarry.put(window.windowId(), 0.0);
        }

        List<Double> selectedWaits = new ArrayList<>();
        List<SimulationTimePoint> timePoints = new ArrayList<>();
        int maxQueueLength = 0;
        int maxBusyWindowCount = 0;
        int maxExtremeWindowCount = 0;
        int nextDinerIndex = 0;
        int previousMinute = 0;
        boolean diversionApplied = diversionPlan == null || !diversionPlan.hasSuggestions();

        for (int minute = 0; minute <= scenario.durationMinutes(); minute += scenario.stepMinutes()) {
            int elapsed = minute > 0 ? minute - previousMinute : 0;
            if (elapsed > 0) {
                serveQueues(seedData, scenario, closedWindowIds, queueLengths, serviceCarry, elapsed);
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

            while (nextDinerIndex < diners.size() && diners.get(nextDinerIndex).arrivalMinute <= minute) {
                Diner diner = diners.get(nextDinerIndex);
                WindowChoice originalChoice = chooseWindow(
                        diner,
                        seedData,
                        queueLengths,
                        closedWindowIds,
                        scenario.mealPeriod(),
                        rng
                );
                WindowChoice finalChoice = maybeRedirectWindow(
                        originalChoice,
                        diversionPlan,
                        minute,
                        seedData,
                        queueLengths,
                        closedWindowIds,
                        scenario.mealPeriod(),
                        rng
                );
                DishSeed dish = chooseDish(diner, finalChoice.window(), seedData.dishesByWindow(), rng);
                queueLengths.compute(finalChoice.window().windowId(), (ignored, value) -> value == null ? 1 : value + 1);

                diner.targetRestaurantId = finalChoice.window().restaurantId();
                diner.targetWindowId = finalChoice.window().windowId();
                diner.targetDishId = dish == null ? null : dish.dishId();
                diner.expectedWaitMinutes = round(finalChoice.estimatedWait());
                selectedWaits.add(finalChoice.estimatedWait());
                nextDinerIndex++;
            }

            SnapshotData snapshot = snapshot(minute, seedData, queueLengths, closedWindowIds, scenario.mealPeriod());
            maxQueueLength = Math.max(maxQueueLength, snapshot.maxQueueLength());
            int[] crowdCounts = countCrowdWindows(snapshot.timePoint());
            maxBusyWindowCount = Math.max(maxBusyWindowCount, crowdCounts[0]);
            maxExtremeWindowCount = Math.max(maxExtremeWindowCount, crowdCounts[1]);
            timePoints.add(snapshot.timePoint());
            previousMinute = minute;
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
            return new SimulationScenario("LUNCH", "WEEKDAY", "BUSY", 1.0, 1.1, List.of(), 800, 60, 5, 20260427L);
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
                scenario.stepMinutes() == null ? 5 : scenario.stepMinutes(),
                scenario.randomSeed() == null ? 20260427L : scenario.randomSeed()
        );
    }

    private void validate(UserProfile profile, SimulationScenario scenario) {
        if (!Set.of("STUDENT", "HURRY", "BUDGET_SENSITIVE").contains(profile.userType())) {
            throw new BadRequestException("profile.userType", "userType must be STUDENT, HURRY or BUDGET_SENSITIVE");
        }
        if (profile.budgetMin() < 0 || profile.budgetMax() < profile.budgetMin()) {
            throw new BadRequestException("profile.budgetMax", "budget range is invalid");
        }
        if (profile.waitingToleranceMinutes() < 1) {
            throw new BadRequestException("profile.waitingToleranceMinutes", "waitingToleranceMinutes must be greater than 0");
        }
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
        if (scenario.durationMinutes() % scenario.stepMinutes() != 0) {
            throw new BadRequestException("scenario.stepMinutes", "durationMinutes must be divisible by stepMinutes");
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
        if (resolved % scenario.stepMinutes() != 0) {
            throw new BadRequestException("minute", "minute must align with stepMinutes");
        }
        return resolved;
    }

    private DiversionPlan buildDiversionPlan(
            int diversionStartMinute,
            List<DiversionSuggestion> suggestions,
            SeedData seedData
    ) {
        List<ResolvedDiversionSuggestion> resolvedSuggestions = new ArrayList<>();
        Map<Long, List<ResolvedDiversionSuggestion>> suggestionsBySourceWindow = new LinkedHashMap<>();
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
        }

        if (resolvedSuggestions.isEmpty()) {
            throw new BadRequestException("diversionSuggestions", "no valid diversion suggestion can be applied");
        }

        return new DiversionPlan(
                diversionStartMinute,
                List.copyOf(resolvedSuggestions),
                suggestionsBySourceWindow.entrySet().stream()
                        .collect(LinkedHashMap::new,
                                (map, entry) -> map.put(entry.getKey(), List.copyOf(entry.getValue())),
                                Map::putAll)
        );
    }

    private List<Diner> sampleVirtualDiners(SeedData seedData, UserProfile profile, SimulationScenario scenario) {
        Random rng = new Random(scenario.randomSeed());
        List<StudentSeed> sourcePool = chooseStudentPool(seedData.students(), profile.userType(), scenario.virtualUserCount());
        List<StudentSeed> sampled = sample(sourcePool, scenario.virtualUserCount(), rng);
        Set<String> profileTags = normalizeProfileTags(profile.tasteTags());
        double pressure = CROWD_COUNT_FACTOR.get(scenario.crowdLevel()) * scenario.weatherFactor() * scenario.eventFactor();

        List<Diner> diners = new ArrayList<>();
        for (int index = 0; index < sampled.size(); index++) {
            StudentSeed student = sampled.get(index);
            int baseArrival = arrivalMinute(student, scenario.mealPeriod());
            int arrivalMinute = scaledArrivalMinute(
                    baseArrival,
                    scenario.mealPeriod(),
                    scenario.durationMinutes(),
                    scenario.crowdLevel(),
                    pressure
            );
            Set<String> preferenceTags = new LinkedHashSet<>(student.preferenceTags());
            preferenceTags.addAll(profileTags);
            Diner diner = new Diner();
            diner.dinerId = "D%05d".formatted(index + 1);
            diner.sourceStudentId = student.studentId();
            diner.userType = profile.userType();
            diner.preferenceTags = preferenceTags;
            diner.budgetMin = Math.max(0.0, profile.budgetMin() + randomInt(rng, -2, 2));
            diner.budgetMax = Math.max(diner.budgetMin, profile.budgetMax() + randomInt(rng, -3, 4));
            diner.waitingToleranceMinutes = Math.max(1, profile.waitingToleranceMinutes() + randomInt(rng, -2, 3));
            diner.arrivalMinute = arrivalMinute;
            diners.add(diner);
        }
        diners.sort(Comparator.comparingInt((Diner item) -> item.arrivalMinute).thenComparing(item -> item.dinerId));
        return diners;
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

    private int arrivalMinute(StudentSeed student, String mealPeriod) {
        return switch (mealPeriod) {
            case "BREAKFAST" -> student.breakfastArrivalMinute();
            case "DINNER" -> student.dinnerArrivalMinute();
            default -> student.lunchArrivalMinute();
        };
    }

    private int scaledArrivalMinute(int baseMinute, String mealPeriod, int duration, String crowdLevel, double pressure) {
        int baseHorizon = MEAL_BASE_HORIZON.get(mealPeriod);
        double scaled = baseMinute * 1.0 / baseHorizon * duration;
        double center = duration * 0.62;
        double spread = CROWD_SPREAD_FACTOR.get(crowdLevel) / Math.sqrt(Math.max(0.5, pressure));
        double adjusted = center + (scaled - center) * spread;
        return Math.max(0, Math.min(duration, (int) Math.round(adjusted)));
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
            Diner diner,
            SeedData seedData,
            Map<Long, Integer> queueLengths,
            Set<Long> closedWindowIds,
            String mealPeriod,
            Random rng
    ) {
        WindowSeed bestWindow = null;
        double bestWait = 0.0;
        double bestScore = -1_000_000_000;

        for (WindowSeed window : seedData.windows()) {
            if (!isWindowAvailable(window, mealPeriod, closedWindowIds)) {
                continue;
            }
            RestaurantSeed restaurant = seedData.restaurantsById().get(window.restaurantId());
            double estimatedWait = windowWaitMinutes(window, queueLengths.get(window.windowId()));
            double tagScore = tagOverlapScore(diner.preferenceTags, window.matchingTags());
            double budgetScore = budgetOverlapScore(diner.budgetMin, diner.budgetMax, window.priceMin(), window.priceMax());
            double waitPenalty = estimatedWait / Math.max(1, diner.waitingToleranceMinutes);
            double waitWeight = "HURRY".equals(diner.userType) ? 0.38 : 0.22;
            double budgetWeight = "BUDGET_SENSITIVE".equals(diner.userType) ? 0.34 : 0.18;
            double score = restaurant.baseAttraction() * 0.18
                    + tagScore * 0.34
                    + budgetScore * budgetWeight
                    + window.popularity() * 0.16
                    - waitPenalty * waitWeight
                    + rng.nextDouble(-0.025, 0.025);
            if (score > bestScore) {
                bestScore = score;
                bestWindow = window;
                bestWait = estimatedWait;
            }
        }

        if (bestWindow == null) {
            throw new BadRequestException("scenario.mealPeriod", "no window is available under the current scenario");
        }
        return new WindowChoice(bestWindow, bestWait);
    }

    private WindowChoice maybeRedirectWindow(
            WindowChoice originalChoice,
            DiversionPlan diversionPlan,
            int minute,
            SeedData seedData,
            Map<Long, Integer> queueLengths,
            Set<Long> closedWindowIds,
            String mealPeriod,
            Random rng
    ) {
        if (diversionPlan == null || !diversionPlan.hasSuggestions() || minute < diversionPlan.startMinute()) {
            return originalChoice;
        }

        List<ResolvedDiversionSuggestion> candidates = diversionPlan.suggestionsBySourceWindow().get(originalChoice.window().windowId());
        if (candidates == null || candidates.isEmpty()) {
            return originalChoice;
        }

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
            if (targetWait >= Math.max(originalChoice.estimatedWait(), 20.0)) {
                continue;
            }
            if (rng.nextDouble() > candidate.acceptanceRate()) {
                continue;
            }
            return new WindowChoice(targetWindow, targetWait);
        }
        return originalChoice;
    }

    private DishSeed chooseDish(
            Diner diner,
            WindowSeed window,
            Map<Long, List<DishSeed>> dishesByWindow,
            Random rng
    ) {
        List<DishSeed> dishes = dishesByWindow.getOrDefault(window.windowId(), List.of());
        DishSeed bestDish = null;
        double bestScore = -1_000_000_000;
        for (DishSeed dish : dishes) {
            double budgetScore = diner.budgetMin <= dish.price() && dish.price() <= diner.budgetMax ? 1.0 : 0.25;
            double tagScore = tagOverlapScore(diner.preferenceTags, dish.matchingTags());
            double prepPenalty = dish.prepTimeMinutes() / 15.0;
            double score = tagScore * 0.34
                    + budgetScore * 0.32
                    + dish.popularity() * 0.24
                    - prepPenalty * 0.10
                    + rng.nextDouble(-0.02, 0.02);
            if (score > bestScore) {
                bestScore = score;
                bestDish = dish;
            }
        }
        return bestDish;
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
            if (targetWait >= sourceWait) {
                continue;
            }

            int targetCapacity = Math.max(0, maxReasonableTargetQueue(targetWindow) - targetQueueLength);
            int movedCount = Math.min(sourceQueueLength, Math.min(suggestion.estimatedAcceptedCount(), targetCapacity));
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

    private Set<String> normalizeProfileTags(List<String> tags) {
        Set<String> result = new LinkedHashSet<>();
        for (String tag : tags) {
            result.add(TAG_ALIASES.getOrDefault(tag, tag));
        }
        return result;
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

    private record SnapshotData(SimulationTimePoint timePoint, int maxQueueLength) {
    }

    private record DiversionPlan(
            int startMinute,
            List<ResolvedDiversionSuggestion> suggestions,
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

    private static final class Diner {
        private String dinerId;
        private String sourceStudentId;
        private String userType;
        private Set<String> preferenceTags;
        private double budgetMin;
        private double budgetMax;
        private int waitingToleranceMinutes;
        private int arrivalMinute;
        private Long targetRestaurantId;
        private Long targetWindowId;
        private Long targetDishId;
        private double expectedWaitMinutes;
    }
}
