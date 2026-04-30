#!/usr/bin/env python3
"""Run a minimal Task A dining simulation from prepared seed CSV files.

This script is the algorithm prototype of member A's backend simulation
service. It reads data/task-a/*.csv and writes a JSON result shaped like the
`data` object of POST /api/v1/simulations/run.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import random
from datetime import datetime, timezone, timedelta
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT / "data" / "task-a"
DEFAULT_OUTPUT = DATA_DIR / "sample_simulation_result.json"

MEAL_TO_ARRIVAL_FIELD = {
    "BREAKFAST": "breakfast_arrival_minute",
    "LUNCH": "lunch_arrival_minute",
    "DINNER": "dinner_arrival_minute",
}

MEAL_BASE_HORIZON = {
    "BREAKFAST": 60,
    "LUNCH": 90,
    "DINNER": 90,
}

CROWD_SPREAD_FACTOR = {
    "IDLE": 1.25,
    "NORMAL": 1.0,
    "BUSY": 0.72,
    "EXTREME": 0.52,
}

CROWD_COUNT_FACTOR = {
    "IDLE": 0.75,
    "NORMAL": 1.0,
    "BUSY": 1.18,
    "EXTREME": 1.38,
}


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as file:
        return list(csv.DictReader(file))


def split_tags(value: str) -> set[str]:
    return {item for item in (value or "").split("|") if item}


def to_int(value: str) -> int:
    return int(float(value))


def to_float(value: str) -> float:
    return float(value)


def crowd_level_for_restaurant(current_count: int, capacity: int) -> str:
    if capacity <= 0:
        return "IDLE"
    ratio = current_count / capacity
    if ratio < 0.4:
        return "IDLE"
    if ratio < 0.7:
        return "NORMAL"
    if ratio < 0.9:
        return "BUSY"
    return "EXTREME"


def crowd_level_for_window(wait_minutes: float, status: str) -> str:
    if status == "CLOSED":
        return "IDLE"
    if wait_minutes < 5:
        return "IDLE"
    if wait_minutes < 10:
        return "NORMAL"
    if wait_minutes < 20:
        return "BUSY"
    return "EXTREME"


def normalize_profile_tags(tags: list[str]) -> set[str]:
    mapping = {
        "偏辣": "辣味",
        "微辣": "辣味",
        "香辣": "辣味",
        "麻辣": "辣味",
        "酸辣": "辣味",
        "偏甜": "甜口",
        "清淡": "清淡",
        "米饭": "米饭",
        "面食": "面食",
        "米粉": "粉面",
        "米线": "粉面",
    }
    result: set[str] = set()
    for tag in tags:
        result.add(mapping.get(tag, tag))
    return result


def load_seed_data() -> dict[str, Any]:
    students = read_csv(DATA_DIR / "virtual_students.csv")
    restaurants = read_csv(DATA_DIR / "restaurants.csv")
    windows = read_csv(DATA_DIR / "windows.csv")
    dishes = read_csv(DATA_DIR / "dishes.csv")

    restaurants_by_id = {to_int(row["restaurant_id"]): row for row in restaurants}
    windows_by_id = {to_int(row["window_id"]): row for row in windows}
    dishes_by_window: dict[int, list[dict[str, str]]] = {}
    for dish in dishes:
        dishes_by_window.setdefault(to_int(dish["window_id"]), []).append(dish)

    return {
        "students": students,
        "restaurants": restaurants,
        "windows": windows,
        "dishes": dishes,
        "restaurantsById": restaurants_by_id,
        "windowsById": windows_by_id,
        "dishesByWindow": dishes_by_window,
    }


def scenario_diner_count(args: argparse.Namespace) -> int:
    return args.virtual_user_count


def scaled_arrival_minute(base_minute: int, meal_period: str, duration: int, crowd_level: str, pressure: float) -> int:
    base_horizon = MEAL_BASE_HORIZON[meal_period]
    scaled = base_minute / base_horizon * duration
    center = duration * 0.62
    spread = CROWD_SPREAD_FACTOR[crowd_level] / math.sqrt(max(0.5, pressure))
    adjusted = center + (scaled - center) * spread
    return max(0, min(duration, int(round(adjusted))))


def sample_virtual_diners(seed_data: dict[str, Any], args: argparse.Namespace) -> list[dict[str, Any]]:
    rng = random.Random(args.random_seed)
    students = seed_data["students"]
    target_count = scenario_diner_count(args)
    profile_tags = normalize_profile_tags(args.taste_tags)
    pressure = CROWD_COUNT_FACTOR[args.crowd_level] * args.weather_factor * args.event_factor

    if args.profile_user_type in {"HURRY", "BUDGET_SENSITIVE"}:
        preferred = [row for row in students if row["user_type"] == args.profile_user_type]
        source_pool = preferred if len(preferred) >= max(20, target_count // 5) else students
    else:
        source_pool = students

    if target_count <= len(source_pool):
        sampled = rng.sample(source_pool, target_count)
    else:
        sampled = [rng.choice(source_pool) for _ in range(target_count)]

    arrival_field = MEAL_TO_ARRIVAL_FIELD[args.meal_period]
    diners: list[dict[str, Any]] = []
    for index, student in enumerate(sampled, start=1):
        base_arrival = to_int(student[arrival_field])
        arrival_minute = scaled_arrival_minute(
            base_arrival,
            args.meal_period,
            args.duration_minutes,
            args.crowd_level,
            pressure,
        )
        preference_tags = split_tags(student["preference_tags"]) | profile_tags
        budget_min = max(0.0, args.budget_min + rng.randint(-2, 2))
        budget_max = max(budget_min, args.budget_max + rng.randint(-3, 4))
        waiting_tolerance = max(1, args.waiting_tolerance_minutes + rng.randint(-2, 3))
        diners.append(
            {
                "dinerId": f"D{index:05d}",
                "sourceStudentId": student["student_id"],
                "userType": args.profile_user_type,
                "preferenceTags": preference_tags,
                "budgetMin": budget_min,
                "budgetMax": budget_max,
                "waitingToleranceMinutes": waiting_tolerance,
                "arrivalMinute": arrival_minute,
                "targetRestaurantId": None,
                "targetWindowId": None,
                "targetDishId": None,
                "expectedWaitMinutes": 0.0,
                "servedMinute": None,
            }
        )
    diners.sort(key=lambda item: (item["arrivalMinute"], item["dinerId"]))
    return diners


def is_window_available(window: dict[str, str], meal_period: str, closed_window_ids: set[int]) -> bool:
    window_id = to_int(window["window_id"])
    if window_id in closed_window_ids:
        return False
    if window["status"] != "OPEN":
        return False
    return window["recommended_meal_period"] in {meal_period, "ALL"}


def tag_overlap_score(user_tags: set[str], target_tags: set[str]) -> float:
    if not user_tags:
        return 0.0
    return len(user_tags & target_tags) / len(user_tags)


def budget_overlap_score(budget_min: float, budget_max: float, price_min: float, price_max: float) -> float:
    if price_max < budget_min or price_min > budget_max:
        distance = min(abs(price_max - budget_min), abs(price_min - budget_max))
        return max(0.0, 1.0 - distance / max(1.0, budget_max - budget_min + 8.0))
    overlap = min(budget_max, price_max) - max(budget_min, price_min)
    return max(0.35, min(1.0, overlap / max(1.0, budget_max - budget_min)))


def choose_window(
    diner: dict[str, Any],
    windows: list[dict[str, str]],
    restaurants_by_id: dict[int, dict[str, str]],
    queue_lengths: dict[int, int],
    closed_window_ids: set[int],
    meal_period: str,
    rng: random.Random,
) -> tuple[dict[str, str], float]:
    best_window = None
    best_wait = 0.0
    best_score = -10**9

    for window in windows:
        window_id = to_int(window["window_id"])
        if not is_window_available(window, meal_period, closed_window_ids):
            continue
        restaurant = restaurants_by_id[to_int(window["restaurant_id"])]
        service_rate = max(0.1, to_float(window["service_rate_per_minute"]))
        estimated_wait = queue_lengths[window_id] / service_rate
        target_tags = split_tags(window["matching_tags"])
        tag_score = tag_overlap_score(diner["preferenceTags"], target_tags)
        budget_score = budget_overlap_score(
            diner["budgetMin"],
            diner["budgetMax"],
            to_float(window["price_min"]),
            to_float(window["price_max"]),
        )
        wait_penalty = estimated_wait / max(1, diner["waitingToleranceMinutes"])

        wait_weight = 0.22 if diner["userType"] != "HURRY" else 0.38
        budget_weight = 0.18 if diner["userType"] != "BUDGET_SENSITIVE" else 0.34
        score = (
            to_float(restaurant["base_attraction"]) * 0.18
            + tag_score * 0.34
            + budget_score * budget_weight
            + to_float(window["popularity"]) * 0.16
            - wait_penalty * wait_weight
            + rng.uniform(-0.025, 0.025)
        )

        if score > best_score:
            best_score = score
            best_window = window
            best_wait = estimated_wait

    if best_window is None:
        raise RuntimeError("No available window for simulation")
    return best_window, best_wait


def choose_dish(
    diner: dict[str, Any],
    window: dict[str, str],
    dishes_by_window: dict[int, list[dict[str, str]]],
    rng: random.Random,
) -> dict[str, str] | None:
    window_id = to_int(window["window_id"])
    dishes = dishes_by_window.get(window_id, [])
    if not dishes:
        return None

    best_dish = None
    best_score = -10**9
    for dish in dishes:
        price = to_float(dish["price"])
        budget_score = 1.0 if diner["budgetMin"] <= price <= diner["budgetMax"] else 0.25
        tag_score = tag_overlap_score(diner["preferenceTags"], split_tags(dish["matching_tags"]))
        prep_penalty = to_int(dish["prep_time_minutes"]) / 15
        score = (
            tag_score * 0.34
            + budget_score * 0.32
            + to_float(dish["popularity"]) * 0.24
            - prep_penalty * 0.10
            + rng.uniform(-0.02, 0.02)
        )
        if score > best_score:
            best_score = score
            best_dish = dish
    return best_dish


def snapshot(
    minute: int,
    seed_data: dict[str, Any],
    queue_lengths: dict[int, int],
    service_carry: dict[int, float],
    closed_window_ids: set[int],
    meal_period: str,
) -> dict[str, Any]:
    restaurants_by_id = seed_data["restaurantsById"]
    windows_by_restaurant: dict[int, list[dict[str, Any]]] = {}

    max_queue = 0
    for window in seed_data["windows"]:
        window_id = to_int(window["window_id"])
        restaurant_id = to_int(window["restaurant_id"])
        status = "OPEN" if is_window_available(window, meal_period, closed_window_ids) else "CLOSED"
        queue_length = queue_lengths[window_id] if status == "OPEN" else 0
        service_rate = max(0.1, to_float(window["service_rate_per_minute"]))
        wait_minutes = 0.0 if status == "CLOSED" else queue_length / service_rate
        serving_count = 0 if status == "CLOSED" or queue_length == 0 else math.ceil(service_rate)
        max_queue = max(max_queue, queue_length)
        windows_by_restaurant.setdefault(restaurant_id, []).append(
            {
                "windowId": window_id,
                "name": window["name"],
                "queueLength": queue_length,
                "servingCount": serving_count,
                "waitMinutes": round(wait_minutes, 1),
                "crowdLevel": crowd_level_for_window(wait_minutes, status),
                "status": status,
            }
        )

    restaurants = []
    for restaurant in seed_data["restaurants"]:
        restaurant_id = to_int(restaurant["restaurant_id"])
        window_states = windows_by_restaurant.get(restaurant_id, [])
        current_count = sum(item["queueLength"] + item["servingCount"] for item in window_states)
        capacity = to_int(restaurant["capacity"])
        restaurants.append(
            {
                "restaurantId": restaurant_id,
                "name": restaurant["name"],
                "currentCount": current_count,
                "capacity": capacity,
                "crowdLevel": crowd_level_for_restaurant(current_count, capacity),
                "windows": window_states,
            }
        )
    return {"minute": minute, "restaurants": restaurants, "_maxQueueLength": max_queue}


def simulate(seed_data: dict[str, Any], args: argparse.Namespace) -> dict[str, Any]:
    rng = random.Random(args.random_seed)
    closed_window_ids = set(args.closed_window_ids)
    diners = sample_virtual_diners(seed_data, args)
    queue_lengths = {to_int(window["window_id"]): 0 for window in seed_data["windows"]}
    service_carry = {to_int(window["window_id"]): 0.0 for window in seed_data["windows"]}
    selected_waits: list[float] = []
    time_points: list[dict[str, Any]] = []
    max_queue_length = 0
    max_busy_window_count = 0
    max_extreme_window_count = 0
    served_user_count = 0
    next_diner_index = 0
    previous_minute = 0

    for minute in range(0, args.duration_minutes + 1, args.step_minutes):
        elapsed = minute - previous_minute if minute > 0 else 0

        if elapsed > 0:
            for window in seed_data["windows"]:
                window_id = to_int(window["window_id"])
                if not is_window_available(window, args.meal_period, closed_window_ids):
                    queue_lengths[window_id] = 0
                    service_carry[window_id] = 0.0
                    continue
                service_carry[window_id] += to_float(window["service_rate_per_minute"]) * elapsed
                can_serve = int(service_carry[window_id])
                served = min(queue_lengths[window_id], can_serve)
                queue_lengths[window_id] -= served
                service_carry[window_id] -= served
                served_user_count += served

        while next_diner_index < len(diners) and diners[next_diner_index]["arrivalMinute"] <= minute:
            diner = diners[next_diner_index]
            window, estimated_wait = choose_window(
                diner,
                seed_data["windows"],
                seed_data["restaurantsById"],
                queue_lengths,
                closed_window_ids,
                args.meal_period,
                rng,
            )
            dish = choose_dish(diner, window, seed_data["dishesByWindow"], rng)
            window_id = to_int(window["window_id"])
            queue_lengths[window_id] += 1
            diner["targetRestaurantId"] = to_int(window["restaurant_id"])
            diner["targetWindowId"] = window_id
            diner["targetDishId"] = to_int(dish["dish_id"]) if dish else None
            diner["expectedWaitMinutes"] = round(estimated_wait, 1)
            selected_waits.append(estimated_wait)
            next_diner_index += 1

        point = snapshot(
            minute,
            seed_data,
            queue_lengths,
            service_carry,
            closed_window_ids,
            args.meal_period,
        )
        max_queue_length = max(max_queue_length, point.pop("_maxQueueLength"))
        busy_count = 0
        extreme_count = 0
        for restaurant in point["restaurants"]:
            for window in restaurant["windows"]:
                if window["crowdLevel"] == "BUSY":
                    busy_count += 1
                elif window["crowdLevel"] == "EXTREME":
                    extreme_count += 1
        max_busy_window_count = max(max_busy_window_count, busy_count)
        max_extreme_window_count = max(max_extreme_window_count, extreme_count)
        time_points.append(point)
        previous_minute = minute

    unserved_user_count = sum(queue_lengths.values())
    total_virtual_users = len(diners)
    avg_wait = sum(selected_waits) / len(selected_waits) if selected_waits else 0.0
    max_wait = max(selected_waits) if selected_waits else 0.0

    profile = {
        "userType": args.profile_user_type,
        "tasteTags": args.taste_tags,
        "budgetMin": args.budget_min,
        "budgetMax": args.budget_max,
        "waitingToleranceMinutes": args.waiting_tolerance_minutes,
    }
    scenario = {
        "mealPeriod": args.meal_period,
        "dayType": args.day_type,
        "crowdLevel": args.crowd_level,
        "weatherFactor": args.weather_factor,
        "eventFactor": args.event_factor,
        "closedWindowIds": args.closed_window_ids,
        "virtualUserCount": args.virtual_user_count,
        "durationMinutes": args.duration_minutes,
        "stepMinutes": args.step_minutes,
        "randomSeed": args.random_seed,
    }
    created_at = datetime.now(timezone(timedelta(hours=8))).replace(microsecond=0).isoformat()
    run_id = int(datetime.now().strftime("%Y%m%d%H%M%S"))

    return {
        "runId": run_id,
        "status": "FINISHED",
        "profile": profile,
        "scenario": scenario,
        "timePoints": time_points,
        "metrics": {
            "avgWaitMinutes": round(avg_wait, 1),
            "maxWaitMinutes": round(max_wait, 1),
            "maxQueueLength": max_queue_length,
            "busyWindowCount": max_busy_window_count,
            "extremeWindowCount": max_extreme_window_count,
            "totalVirtualUsers": total_virtual_users,
            "servedUserCount": max(0, total_virtual_users - unserved_user_count),
            "unservedUserCount": unserved_user_count,
        },
        "createdAt": created_at,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Task A local dining simulation")
    parser.add_argument("--meal-period", choices=["BREAKFAST", "LUNCH", "DINNER"], default="LUNCH")
    parser.add_argument("--day-type", choices=["WEEKDAY", "WEEKEND"], default="WEEKDAY")
    parser.add_argument("--crowd-level", choices=["IDLE", "NORMAL", "BUSY", "EXTREME"], default="BUSY")
    parser.add_argument("--weather-factor", type=float, default=1.0)
    parser.add_argument("--event-factor", type=float, default=1.1)
    parser.add_argument("--closed-window-ids", type=int, nargs="*", default=[])
    parser.add_argument("--virtual-user-count", type=int, default=800)
    parser.add_argument("--duration-minutes", type=int, default=60)
    parser.add_argument("--step-minutes", type=int, default=5)
    parser.add_argument("--random-seed", type=int, default=20260427)
    parser.add_argument("--profile-user-type", choices=["STUDENT", "HURRY", "BUDGET_SENSITIVE"], default="STUDENT")
    parser.add_argument("--taste-tags", nargs="*", default=["米饭", "辣味"])
    parser.add_argument("--budget-min", type=float, default=10.0)
    parser.add_argument("--budget-max", type=float, default=22.0)
    parser.add_argument("--waiting-tolerance-minutes", type=int, default=10)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    if args.virtual_user_count < 1 or args.virtual_user_count > 5000:
        parser.error("--virtual-user-count must be between 1 and 5000")
    if args.duration_minutes < 5 or args.duration_minutes > 180:
        parser.error("--duration-minutes must be between 5 and 180")
    if args.step_minutes < 1 or args.step_minutes > 30:
        parser.error("--step-minutes must be between 1 and 30")
    if args.duration_minutes % args.step_minutes != 0:
        parser.error("--duration-minutes must be divisible by --step-minutes")
    if args.weather_factor <= 0 or args.event_factor <= 0:
        parser.error("--weather-factor and --event-factor must be positive")
    if args.budget_min < 0 or args.budget_max < args.budget_min:
        parser.error("budget range is invalid")
    return args


def main() -> None:
    args = parse_args()
    seed_data = load_seed_data()
    result = simulate(seed_data, args)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Simulation result written to {args.output}")
    print(json.dumps(result["metrics"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
