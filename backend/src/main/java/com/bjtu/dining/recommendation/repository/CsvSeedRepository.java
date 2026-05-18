package com.bjtu.dining.recommendation.repository;

import com.bjtu.dining.recommendation.model.DishParameter;
import com.bjtu.dining.recommendation.model.RestaurantParameter;
import com.bjtu.dining.recommendation.model.WindowParameter;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CsvSeedRepository {

    private final List<RestaurantParameter> restaurants = new ArrayList<>();
    private final List<WindowParameter> windows = new ArrayList<>();
    private final List<DishParameter> dishes = new ArrayList<>();
    private final Map<Long, RestaurantParameter> restaurantById = new HashMap<>();
    private final Map<Long, WindowParameter> windowById = new HashMap<>();

    @PostConstruct
    void load() throws IOException {
        Path seedRoot = resolveSeedRoot();
        loadRestaurants(seedRoot.resolve("restaurants.csv"));
        loadWindows(seedRoot.resolve("windows.csv"));
        loadDishes(seedRoot.resolve("dishes.csv"));
    }

    public List<RestaurantParameter> restaurants() {
        return Collections.unmodifiableList(restaurants);
    }

    public List<WindowParameter> windows() {
        return Collections.unmodifiableList(windows);
    }

    public List<DishParameter> dishes() {
        return Collections.unmodifiableList(dishes);
    }

    public RestaurantParameter restaurant(Long restaurantId) {
        return restaurantById.get(restaurantId);
    }

    public WindowParameter window(Long windowId) {
        return windowById.get(windowId);
    }

    public List<WindowParameter> windowsByRestaurant(Long restaurantId) {
        return windows.stream()
                .filter(window -> window.restaurantId().equals(restaurantId))
                .toList();
    }

    private Path resolveSeedRoot() {
        List<Path> candidates = List.of(
                Path.of("data", "task-a"),
                Path.of("..", "data", "task-a"),
                Path.of("..", "..", "data", "task-a")
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate.resolve("restaurants.csv"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("未找到 data/task-a 种子数据目录");
    }

    private void loadRestaurants(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {
            String[] row = split(line);
            var restaurant = new RestaurantParameter(
                    parseLong(row[0]),
                    row[2],
                    row[4],
                    parseInt(row[6]),
                    parseDouble(row[7]),
                    row[12]
            );
            restaurants.add(restaurant);
            restaurantById.put(restaurant.restaurantId(), restaurant);
        }
    }

    private void loadWindows(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {
            String[] row = split(line);
            var window = new WindowParameter(
                    parseLong(row[0]),
                    parseLong(row[2]),
                    row[3],
                    row[4],
                    row[16],
                    parseDouble(row[17]),
                    parseDouble(row[18]),
                    parseDouble(row[19]),
                    parseDouble(row[20]),
                    row[21]
            );
            windows.add(window);
            windowById.put(window.windowId(), window);
        }
    }

    private void loadDishes(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {
            String[] row = split(line);
            dishes.add(new DishParameter(
                    parseLong(row[0]),
                    parseLong(row[1]),
                    parseLong(row[2]),
                    row[3],
                    parseDouble(row[4]),
                    parseInt(row[5]),
                    parseDouble(row[6]),
                    row[13]
            ));
        }
    }

    private String[] split(String line) {
        return line.split(",", -1);
    }

    private Long parseLong(String value) {
        return Long.parseLong(value.trim());
    }

    private int parseInt(String value) {
        return Integer.parseInt(value.trim());
    }

    private double parseDouble(String value) {
        return Double.parseDouble(value.trim());
    }
}
