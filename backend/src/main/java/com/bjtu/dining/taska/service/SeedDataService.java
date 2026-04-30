package com.bjtu.dining.taska.service;

import com.bjtu.dining.taska.model.TaskADtos.DishParameter;
import com.bjtu.dining.taska.model.TaskADtos.RestaurantParameter;
import com.bjtu.dining.taska.model.TaskADtos.WindowParameter;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SeedDataService {
    private final Path configuredSeedDir;
    private SeedData seedData;

    public SeedDataService(@Value("${app.seed-data-dir:../data/task-a}") String seedDataDir) {
        this.configuredSeedDir = Path.of(seedDataDir);
    }

    @PostConstruct
    public void load() {
        Path dir = resolveSeedDir();
        try {
            List<Map<String, String>> studentRows = readCsv(dir.resolve("virtual_students.csv"));
            List<Map<String, String>> restaurantRows = readCsv(dir.resolve("restaurants.csv"));
            List<Map<String, String>> windowRows = readCsv(dir.resolve("windows.csv"));
            List<Map<String, String>> dishRows = readCsv(dir.resolve("dishes.csv"));

            List<StudentSeed> students = studentRows.stream()
                    .map(this::toStudent)
                    .toList();
            List<RestaurantSeed> restaurants = restaurantRows.stream()
                    .map(this::toRestaurant)
                    .sorted(Comparator.comparingLong(RestaurantSeed::restaurantId))
                    .toList();
            List<WindowSeed> windows = windowRows.stream()
                    .map(this::toWindow)
                    .sorted(Comparator.comparingLong(WindowSeed::windowId))
                    .toList();
            List<DishSeed> dishes = dishRows.stream()
                    .map(this::toDish)
                    .sorted(Comparator.comparingLong(DishSeed::dishId))
                    .toList();

            Map<Long, RestaurantSeed> restaurantsById = restaurants.stream()
                    .collect(Collectors.toMap(RestaurantSeed::restaurantId, item -> item, (a, b) -> a, LinkedHashMap::new));
            Map<Long, WindowSeed> windowsById = windows.stream()
                    .collect(Collectors.toMap(WindowSeed::windowId, item -> item, (a, b) -> a, LinkedHashMap::new));
            Map<Long, List<DishSeed>> dishesByWindow = new HashMap<>();
            for (DishSeed dish : dishes) {
                dishesByWindow.computeIfAbsent(dish.windowId(), ignored -> new ArrayList<>()).add(dish);
            }

            this.seedData = new SeedData(
                    students,
                    restaurants,
                    windows,
                    dishes,
                    restaurantsById,
                    windowsById,
                    dishesByWindow
            );
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取成员 A 种子数据目录：" + dir.toAbsolutePath(), ex);
        }
    }

    public SeedData seedData() {
        return seedData;
    }

    public List<RestaurantParameter> restaurantParameters() {
        return seedData.restaurants().stream()
                .map(item -> new RestaurantParameter(
                        item.restaurantId(),
                        item.name(),
                        item.location(),
                        item.capacity(),
                        item.baseAttraction(),
                        item.status()
                ))
                .toList();
    }

    public List<WindowParameter> windowParameters(Long restaurantId) {
        return seedData.windows().stream()
                .filter(item -> restaurantId == null || item.restaurantId() == restaurantId)
                .map(item -> new WindowParameter(
                        item.windowId(),
                        item.restaurantId(),
                        item.name(),
                        item.serviceRatePerMinute(),
                        item.status()
                ))
                .toList();
    }

    public List<DishParameter> dishParameters(Long restaurantId, Long windowId) {
        return seedData.dishes().stream()
                .filter(item -> restaurantId == null || item.restaurantId() == restaurantId)
                .filter(item -> windowId == null || item.windowId() == windowId)
                .map(item -> new DishParameter(
                        item.dishId(),
                        item.restaurantId(),
                        item.windowId(),
                        item.name(),
                        item.price(),
                        item.prepTimeMinutes(),
                        item.popularity(),
                        List.copyOf(item.matchingTags())
                ))
                .toList();
    }

    private Path resolveSeedDir() {
        if (Files.exists(configuredSeedDir.resolve("restaurants.csv"))) {
            return configuredSeedDir;
        }
        Path fromProjectRoot = Path.of("data", "task-a");
        if (Files.exists(fromProjectRoot.resolve("restaurants.csv"))) {
            return fromProjectRoot;
        }
        return configuredSeedDir;
    }

    private StudentSeed toStudent(Map<String, String> row) {
        return new StudentSeed(
                text(row, "student_id"),
                text(row, "user_type"),
                splitTags(row.get("preference_tags")),
                number(row, "budget_min"),
                number(row, "budget_max"),
                integer(row, "waiting_tolerance_minutes"),
                integer(row, "breakfast_arrival_minute"),
                integer(row, "lunch_arrival_minute"),
                integer(row, "dinner_arrival_minute")
        );
    }

    private RestaurantSeed toRestaurant(Map<String, String> row) {
        return new RestaurantSeed(
                integer(row, "restaurant_id"),
                text(row, "name"),
                text(row, "location"),
                integer(row, "capacity"),
                number(row, "base_attraction"),
                text(row, "status")
        );
    }

    private WindowSeed toWindow(Map<String, String> row) {
        return new WindowSeed(
                integer(row, "window_id"),
                integer(row, "restaurant_id"),
                text(row, "name"),
                text(row, "recommended_meal_period"),
                number(row, "price_min"),
                number(row, "price_max"),
                number(row, "service_rate_per_minute"),
                number(row, "popularity"),
                splitTags(row.get("matching_tags")),
                text(row, "status")
        );
    }

    private DishSeed toDish(Map<String, String> row) {
        return new DishSeed(
                integer(row, "dish_id"),
                integer(row, "window_id"),
                integer(row, "restaurant_id"),
                text(row, "name"),
                number(row, "price"),
                integer(row, "prep_time_minutes"),
                number(row, "popularity"),
                splitTags(row.get("matching_tags"))
        );
    }

    private List<Map<String, String>> readCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> headers = parseCsvLine(stripBom(lines.get(0)));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            List<String> values = parseCsvLine(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                row.put(headers.get(j), j < values.size() ? values.get(j) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        result.add(current.toString());
        return result;
    }

    private String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private Set<String> splitTags(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : value.split("\\|")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private String text(Map<String, String> row, String key) {
        return row.getOrDefault(key, "");
    }

    private int integer(Map<String, String> row, String key) {
        return (int) Math.round(number(row, key));
    }

    private double number(Map<String, String> row, String key) {
        String value = row.getOrDefault(key, "0").trim();
        if (value.isEmpty() || "—".equals(value)) {
            return 0;
        }
        return Double.parseDouble(value);
    }

    public record SeedData(
            List<StudentSeed> students,
            List<RestaurantSeed> restaurants,
            List<WindowSeed> windows,
            List<DishSeed> dishes,
            Map<Long, RestaurantSeed> restaurantsById,
            Map<Long, WindowSeed> windowsById,
            Map<Long, List<DishSeed>> dishesByWindow
    ) {
    }

    public record StudentSeed(
            String studentId,
            String userType,
            Set<String> preferenceTags,
            double budgetMin,
            double budgetMax,
            int waitingToleranceMinutes,
            int breakfastArrivalMinute,
            int lunchArrivalMinute,
            int dinnerArrivalMinute
    ) {
    }

    public record RestaurantSeed(
            long restaurantId,
            String name,
            String location,
            int capacity,
            double baseAttraction,
            String status
    ) {
    }

    public record WindowSeed(
            long windowId,
            long restaurantId,
            String name,
            String recommendedMealPeriod,
            double priceMin,
            double priceMax,
            double serviceRatePerMinute,
            double popularity,
            Set<String> matchingTags,
            String status
    ) {
    }

    public record DishSeed(
            long dishId,
            long windowId,
            long restaurantId,
            String name,
            double price,
            int prepTimeMinutes,
            double popularity,
            Set<String> matchingTags
    ) {
    }
}
