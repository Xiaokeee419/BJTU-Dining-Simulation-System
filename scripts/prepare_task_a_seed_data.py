#!/usr/bin/env python3
"""Prepare Task A simulation seed data from the two source XLSX files.

The script intentionally avoids third-party packages so it can run in a
minimal Python environment. It extracts only fields needed by the simulation
module and derives missing simulation parameters deterministically.
"""

from __future__ import annotations

import csv
import hashlib
import random
import re
import textwrap
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
STUDENT_XLSX = ROOT / "docs" / "虚拟学生分布特点名单_带饮食标签.xlsx"
DINING_XLSX = ROOT / "docs" / "北交虚拟食堂信息_关键营业时间与窗口明细.xlsx"
OUT_DIR = ROOT / "data" / "task-a"
SQL_DIR = ROOT / "sql" / "schema"

NS = {
    "main": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "rel": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "pkgrel": "http://schemas.openxmlformats.org/package/2006/relationships",
}

TAG_ALIASES: dict[str, tuple[str, ...]] = {
    "面食为主": ("面食",),
    "米饭为主": ("米饭",),
    "米面结合": ("米饭", "面食"),
    "米饭米粉": ("米饭", "粉面"),
    "米饭粉面": ("米饭", "粉面"),
    "米饭+粥粉面": ("米饭", "粥", "粉面"),
    "粥粉面": ("粥", "粉面"),
    "小面": ("粉面", "辣味"),
    "米粉": ("粉面",),
    "米线": ("粉面",),
    "粉": ("粉面",),
    "面": ("面食",),
    "面包": ("面食",),
    "馕": ("面食",),
    "饼": ("面食",),
    "杂粮饭": ("杂粮", "米饭"),
    "青稞": ("杂粮",),
    "糌粑": ("杂粮",),
    "麻辣": ("辣味",),
    "香辣": ("辣味",),
    "酸辣": ("辣味", "酸香"),
    "鲜辣": ("辣味",),
    "甜辣": ("辣味", "甜口"),
    "酸甜辣": ("辣味", "酸香", "甜口"),
    "红油": ("辣味",),
    "油泼": ("辣味", "面食"),
    "蒜香": ("家常",),
    "孜然": ("牛羊肉",),
    "孜然咸香": ("牛羊肉", "咸鲜"),
    "重口": ("辣味",),
    "咸鲜": ("咸鲜",),
    "咸香": ("咸鲜",),
    "家常": ("家常",),
    "酱香": ("家常",),
    "浓香": ("家常",),
    "浓油赤酱": ("家常",),
    "葱香": ("家常",),
    "炖香": ("家常",),
    "汤香": ("汤类",),
    "汤鲜": ("汤类", "清淡"),
    "汤面": ("汤类", "面食"),
    "清淡": ("清淡",),
    "清鲜": ("清淡",),
    "鲜香": ("清淡",),
    "鲜甜": ("清淡", "甜口"),
    "清爽": ("清淡",),
    "清汤": ("清淡", "汤类"),
    "少油": ("健康",),
    "低油": ("健康",),
    "低盐": ("健康",),
    "低负担": ("健康",),
    "均衡": ("健康",),
    "偏甜": ("甜口",),
    "微甜": ("甜口",),
    "甜香": ("甜口",),
    "甜咸": ("甜口",),
    "清甜": ("甜口",),
    "奶香": ("甜口", "奶制品"),
    "椰香": ("甜口",),
    "果香": ("甜口",),
    "牛羊肉": ("牛羊肉",),
    "海鲜": ("海鲜",),
    "清真友好": ("清真",),
    "清真需求": ("清真",),
    "清真需另设专锅": ("清真",),
    "素食友好": ("素食",),
    "素食可选": ("素食",),
    "轻食友好": ("轻食", "健康"),
    "辣度可调": ("辣味", "可调辣"),
    "可选不辣": ("可调辣",),
    "可少辣": ("可调辣",),
    "可选微辣": ("可调辣",),
    "清淡友好": ("清淡",),
    "少油少盐": ("健康",),
    "少油少盐可选": ("健康",),
    "少油可选": ("健康",),
    "可做半份": ("小份",),
    "可半份": ("小份",),
    "大众友好": ("大众",),
}

KEYWORD_TAG_RULES: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("面食", ("面食",)),
    ("面点", ("面食",)),
    ("面", ("面食",)),
    ("米饭", ("米饭",)),
    ("盖饭", ("米饭",)),
    ("粉", ("粉面",)),
    ("米线", ("粉面",)),
    ("粥", ("粥",)),
    ("杂粮", ("杂粮",)),
    ("甜品", ("甜口",)),
    ("饮品", ("饮品",)),
    ("咖啡", ("饮品",)),
    ("奶", ("奶制品",)),
    ("火锅", ("辣味",)),
    ("花椒", ("辣味",)),
    ("辣椒", ("辣味",)),
    ("麻辣", ("辣味",)),
    ("香辣", ("辣味",)),
    ("酸辣", ("辣味", "酸香")),
    ("酸菜", ("酸香",)),
    ("酸汤", ("酸香",)),
    ("醋香", ("酸香",)),
    ("少辣", ("可调辣",)),
    ("不辣", ("可调辣",)),
    ("辣", ("辣味",)),
    ("清淡", ("清淡",)),
    ("清鲜", ("清淡",)),
    ("咸鲜", ("咸鲜",)),
    ("家常", ("家常",)),
    ("酱香", ("家常",)),
    ("炖菜", ("家常",)),
    ("自选", ("快餐",)),
    ("打菜", ("快餐",)),
    ("小吃", ("快餐",)),
    ("汤", ("汤类",)),
    ("涮肉", ("牛羊肉",)),
    ("牛肉", ("牛羊肉",)),
    ("羊肉", ("牛羊肉",)),
    ("牛羊肉", ("牛羊肉",)),
    ("海鲜", ("海鲜",)),
    ("海味", ("海鲜",)),
    ("清真", ("清真",)),
    ("素食", ("素食",)),
    ("轻食", ("轻食", "健康")),
    ("沙拉", ("轻食", "健康")),
    ("水果", ("蔬果", "健康")),
    ("少油", ("健康",)),
    ("低盐", ("健康",)),
    ("半份", ("小份",)),
    ("快餐", ("快餐",)),
    ("早餐", ("早餐",)),
)


def stable_int(value: str) -> int:
    return int(hashlib.md5(value.encode("utf-8")).hexdigest()[:12], 16)


def stable_random(value: str) -> random.Random:
    return random.Random(stable_int(value))


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def round_half(value: float) -> float:
    return round(value * 2) / 2


def col_to_num(cell_ref: str | None) -> int:
    match = re.match(r"([A-Z]+)", cell_ref or "")
    if not match:
        return 0
    num = 0
    for char in match.group(1):
        num = num * 26 + (ord(char) - 64)
    return num


def read_shared_strings(zip_file: ZipFile) -> list[str]:
    if "xl/sharedStrings.xml" not in zip_file.namelist():
        return []
    root = ET.fromstring(zip_file.read("xl/sharedStrings.xml"))
    values: list[str] = []
    for item in root.findall("main:si", NS):
        values.append("".join(text.text or "" for text in item.findall(".//main:t", NS)))
    return values


def cell_value(cell: ET.Element, shared_strings: list[str]) -> str | None:
    cell_type = cell.get("t")
    if cell_type == "inlineStr":
        return "".join(text.text or "" for text in cell.findall(".//main:t", NS))
    value_node = cell.find("main:v", NS)
    if value_node is None:
        return None
    raw = value_node.text or ""
    if cell_type == "s":
        return shared_strings[int(raw)]
    return raw


def sheet_paths(zip_file: ZipFile) -> dict[str, str]:
    workbook = ET.fromstring(zip_file.read("xl/workbook.xml"))
    rel_root = ET.fromstring(zip_file.read("xl/_rels/workbook.xml.rels"))
    rels = {
        rel.get("Id"): rel.get("Target")
        for rel in rel_root.findall("pkgrel:Relationship", NS)
    }
    result: dict[str, str] = {}
    for sheet in workbook.findall("main:sheets/main:sheet", NS):
        rel_id = sheet.get(f"{{{NS['rel']}}}id")
        target = rels[rel_id]
        if target.startswith("/"):
            path = target.lstrip("/")
        elif target.startswith("xl/"):
            path = target
        else:
            path = f"xl/{target}"
        result[sheet.get("name") or ""] = path
    return result


def read_sheet_rows(path: Path, sheet_name: str) -> list[list[str | None]]:
    with ZipFile(path) as zip_file:
        shared_strings = read_shared_strings(zip_file)
        paths = sheet_paths(zip_file)
        root = ET.fromstring(zip_file.read(paths[sheet_name]))
        rows: list[list[str | None]] = []
        for row in root.findall("main:sheetData/main:row", NS):
            values_by_col: dict[int, str | None] = {}
            for cell in row.findall("main:c", NS):
                col = col_to_num(cell.get("r"))
                values_by_col[col] = cell_value(cell, shared_strings)
            if values_by_col:
                rows.append([values_by_col.get(i) for i in range(1, max(values_by_col) + 1)])
        return rows


def records_from_rows(rows: list[list[str | None]], header_index: int) -> list[dict[str, str]]:
    headers = [header or "" for header in rows[header_index]]
    records: list[dict[str, str]] = []
    for row in rows[header_index + 1 :]:
        if not any(value not in (None, "") for value in row):
            continue
        record: dict[str, str] = {}
        for index, header in enumerate(headers):
            if not header:
                continue
            value = row[index] if index < len(row) else None
            record[header] = "" if value is None else str(value).strip()
        records.append(record)
    return records


def parse_int_code(value: str, prefix: str) -> int:
    match = re.search(r"(\d+)", value or "")
    if not match:
        raise ValueError(f"Invalid {prefix} code: {value}")
    return int(match.group(1))


def parse_price_band(value: str) -> tuple[float, float]:
    numbers = [float(number) for number in re.findall(r"\d+(?:\.\d+)?", value or "")]
    if not numbers:
        return 10.0, 20.0
    if len(numbers) == 1:
        return numbers[0], numbers[0]
    return min(numbers[0], numbers[1]), max(numbers[0], numbers[1])


def tag_parts(value: str) -> list[str]:
    return [part.strip() for part in re.split(r"[|+、,/，;；\s]+", value or "") if part.strip()]


def join_unique(parts: list[str] | tuple[str, ...]) -> str:
    return "|".join(dict.fromkeys(parts))


def split_tags(value: str) -> str:
    return join_unique(tag_parts(value))


def normalize_one_tag(tag: str) -> tuple[str, ...]:
    if tag in TAG_ALIASES:
        return TAG_ALIASES[tag]
    matched: list[str] = []
    for keyword, normalized_tags in KEYWORD_TAG_RULES:
        if keyword in tag:
            matched.extend(normalized_tags)
    return tuple(dict.fromkeys(matched)) if matched else (tag,)


def normalize_tags(value: str) -> str:
    normalized: list[str] = []
    for tag in tag_parts(value):
        normalized.extend(normalize_one_tag(tag))
    return join_unique(normalized)


def merge_tags(*values: str) -> str:
    parts: list[str] = []
    for value in values:
        parts.extend(tag_parts(value))
    return join_unique(parts)


def split_dishes(value: str) -> list[str]:
    parts = [part.strip() for part in re.split(r"[、,，/；;]+", value or "") if part.strip()]
    return list(dict.fromkeys(parts))


def to_meal_period(value: str) -> str:
    if "早餐" in value:
        return "BREAKFAST"
    if "晚餐" in value:
        return "DINNER"
    if "全天" in value or "延时" in value or "按餐厅" in value:
        return "ALL"
    return "LUNCH"


def derive_user_type(student_id: str) -> str:
    value = stable_int(f"user-type:{student_id}") % 100
    if value < 12:
        return "HURRY"
    if value < 24:
        return "BUDGET_SENSITIVE"
    return "STUDENT"


def derive_budget_and_tolerance(student_id: str, user_type: str, taste_tags: str) -> tuple[int, int, int]:
    rng = stable_random(f"budget:{student_id}")
    if user_type == "HURRY":
        budget_min = rng.randint(12, 18)
        budget_max = budget_min + rng.randint(8, 16)
        tolerance = rng.randint(4, 8)
    elif user_type == "BUDGET_SENSITIVE":
        budget_min = rng.randint(7, 10)
        budget_max = budget_min + rng.randint(6, 9)
        tolerance = rng.randint(10, 18)
    else:
        budget_min = rng.randint(9, 14)
        budget_max = budget_min + rng.randint(9, 14)
        tolerance = rng.randint(8, 16)
    if any(keyword in taste_tags for keyword in ("清鲜", "鲜甜", "奶香")):
        budget_max += 2
    return budget_min, budget_max, tolerance


def sample_mixture_minute(seed_key: str, components: list[tuple[float, float, float]], low: int, high: int) -> int:
    rng = stable_random(seed_key)
    marker = rng.random()
    cumulative = 0.0
    center = components[-1][0]
    stddev = components[-1][1]
    for item_center, item_stddev, weight in components:
        cumulative += weight
        if marker <= cumulative:
            center = item_center
            stddev = item_stddev
            break
    return int(round(clamp(rng.gauss(center, stddev), low, high)))


def derive_arrival_minutes(student_id: str) -> tuple[int, int, int]:
    breakfast = sample_mixture_minute(
        f"arrival:breakfast:{student_id}",
        [(35, 10, 0.70), (48, 7, 0.20), (20, 8, 0.10)],
        0,
        60,
    )
    lunch = sample_mixture_minute(
        f"arrival:lunch:{student_id}",
        [(38, 8, 0.75), (52, 10, 0.20), (20, 8, 0.05)],
        0,
        90,
    )
    dinner = sample_mixture_minute(
        f"arrival:dinner:{student_id}",
        [(42, 10, 0.75), (58, 12, 0.20), (25, 8, 0.05)],
        0,
        90,
    )
    return breakfast, lunch, dinner


def derive_capacity(restaurant: dict[str, str], window_count: int) -> int:
    text = f"{restaurant.get('类型定位', '')} {restaurant.get('关键信息', '')}"
    capacity = 120 + window_count * 34
    if "基本伙" in text or "大众" in text:
        capacity += 70
    if "风味" in text:
        capacity += 35
    if "快餐" in text or "西式" in text:
        capacity += 20
    if "清真" in text:
        capacity += 20
    if restaurant.get("延时/连续营业", "") not in ("", "—"):
        capacity += 15
    return int(clamp(capacity, 120, 430))


def derive_base_attraction(restaurant: dict[str, str], window_count: int) -> float:
    text = f"{restaurant.get('类型定位', '')} {restaurant.get('关键信息', '')}"
    value = 0.50 + min(window_count, 6) * 0.045
    if "风味" in text:
        value += 0.08
    if "基本伙" in text or "大众" in text:
        value += 0.06
    if restaurant.get("延时/连续营业", "") not in ("", "—"):
        value += 0.04
    value += (stable_int(f"restaurant:{restaurant.get('序号')}") % 7) / 100
    return round(clamp(value, 0.45, 0.95), 2)


def derive_service_rate(category: str, name: str, price_max: float) -> float:
    text = f"{category} {name}"
    if any(keyword in text for keyword in ("饮品", "甜点", "咖啡", "粥", "包点", "早餐")):
        value = 2.6
    elif any(keyword in text for keyword in ("自选", "基本伙", "快餐", "盖饭", "套餐")):
        value = 2.1
    elif any(keyword in text for keyword in ("饺子", "馄饨", "面", "粉", "米线", "清真")):
        value = 1.55
    elif any(keyword in text for keyword in ("香锅", "冒菜", "烤鱼", "小炒", "烧腊", "西餐", "牛排")):
        value = 1.05
    else:
        value = 1.35
    if price_max >= 30:
        value -= 0.12
    jitter = ((stable_int(f"service:{category}:{name}") % 11) - 5) / 100
    return round(clamp(value + jitter, 0.75, 3.0), 2)


def derive_popularity(key: str, text: str) -> float:
    value = 0.56 + (stable_int(f"popularity:{key}") % 30) / 100
    if any(keyword in text for keyword in ("麻辣", "香锅", "重庆", "盖饭", "面", "快餐")):
        value += 0.06
    if any(keyword in text for keyword in ("轻食", "咖啡", "甜点")):
        value -= 0.03
    return round(clamp(value, 0.45, 0.96), 2)


def derive_dish_price(dish: str, price_min: float, price_max: float, seed_key: str) -> float:
    rng = stable_random(seed_key)
    ratio = 0.50
    if any(keyword in dish for keyword in ("牛", "羊", "鱼", "鸡", "肉", "烤", "香锅", "牛排")):
        ratio = 0.72
    if any(keyword in dish for keyword in ("豆浆", "粥", "鸡蛋", "小菜", "水果", "油条")):
        ratio = 0.15
    ratio = clamp(ratio + rng.uniform(-0.16, 0.16), 0.05, 0.95)
    return round_half(price_min + (price_max - price_min) * ratio)


def derive_prep_time(dish: str, category: str) -> int:
    text = f"{dish} {category}"
    if any(keyword in text for keyword in ("豆浆", "粥", "包", "油条", "鸡蛋", "饮品", "甜点")):
        return 2
    if any(keyword in text for keyword in ("面", "粉", "馄饨", "饺子", "米线")):
        return 5
    if any(keyword in text for keyword in ("盖饭", "套餐", "快餐", "烧腊")):
        return 4
    if any(keyword in text for keyword in ("香锅", "冒菜", "烤鱼", "牛排", "小炒")):
        return 9
    return 6


def load_source_data() -> tuple[list[dict[str, str]], list[dict[str, str]], list[dict[str, str]]]:
    students = records_from_rows(read_sheet_rows(STUDENT_XLSX, "虚拟名单"), 0)
    restaurants = [
        row
        for row in records_from_rows(read_sheet_rows(DINING_XLSX, "食堂关键信息"), 1)
        if row.get("序号")
    ]
    windows = [
        row
        for row in records_from_rows(read_sheet_rows(DINING_XLSX, "虚拟窗口明细"), 1)
        if row.get("窗口ID")
    ]
    return students, restaurants, windows


def build_students(source_students: list[dict[str, str]]) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for source in source_students:
        student_id = source["学生ID"]
        user_type = derive_user_type(student_id)
        staple_tags = split_tags(source.get("主食标签", ""))
        taste_tags = split_tags(source.get("口味标签", ""))
        food_keywords = split_tags(source.get("饮食关键词", ""))
        service_tags = split_tags(source.get("食堂服务标签", ""))
        normalized_staple_tags = normalize_tags(staple_tags)
        normalized_taste_tags = normalize_tags(taste_tags)
        normalized_food_tags = normalize_tags(food_keywords)
        normalized_service_tags = normalize_tags(service_tags)
        budget_min, budget_max, tolerance = derive_budget_and_tolerance(student_id, user_type, taste_tags)
        breakfast, lunch, dinner = derive_arrival_minutes(student_id)
        result.append(
            {
                "student_id": student_id,
                "province": source.get("生源省份", ""),
                "region_group": source.get("地区分组", ""),
                "user_type": user_type,
                "staple_tags": staple_tags,
                "taste_tags": taste_tags,
                "food_keywords": food_keywords,
                "service_tags": service_tags,
                "normalized_staple_tags": normalized_staple_tags,
                "normalized_taste_tags": normalized_taste_tags,
                "normalized_food_tags": normalized_food_tags,
                "normalized_service_tags": normalized_service_tags,
                "preference_tags": merge_tags(
                    normalized_staple_tags,
                    normalized_taste_tags,
                    normalized_food_tags,
                    normalized_service_tags,
                ),
                "budget_min": budget_min,
                "budget_max": budget_max,
                "waiting_tolerance_minutes": tolerance,
                "breakfast_arrival_minute": breakfast,
                "lunch_arrival_minute": lunch,
                "dinner_arrival_minute": dinner,
            }
        )
    return result


def build_restaurants_and_windows(
    source_restaurants: list[dict[str, str]], source_windows: list[dict[str, str]]
) -> tuple[list[dict[str, object]], list[dict[str, object]], list[dict[str, object]]]:
    source_code_to_id = {
        restaurant["序号"]: parse_int_code(restaurant["序号"], "restaurant")
        for restaurant in source_restaurants
    }
    name_to_id = {
        restaurant["食堂/餐厅名称"]: source_code_to_id[restaurant["序号"]]
        for restaurant in source_restaurants
    }
    window_count_by_restaurant = Counter(window["所属餐厅"] for window in source_windows)

    restaurants: list[dict[str, object]] = []
    for source in source_restaurants:
        window_count = window_count_by_restaurant[source["食堂/餐厅名称"]]
        restaurants.append(
            {
                "restaurant_id": source_code_to_id[source["序号"]],
                "source_code": source["序号"],
                "name": source["食堂/餐厅名称"],
                "campus_area": source.get("校区/区域", ""),
                "location": source.get("楼层/位置", ""),
                "restaurant_type": source.get("类型定位", ""),
                "capacity": derive_capacity(source, window_count),
                "base_attraction": derive_base_attraction(source, window_count),
                "breakfast_hours": source.get("早餐/早午餐", ""),
                "lunch_hours": source.get("午餐", ""),
                "dinner_hours": source.get("晚餐", ""),
                "extended_hours": source.get("延时/连续营业", ""),
                "status": "OPEN",
            }
        )

    windows: list[dict[str, object]] = []
    dishes: list[dict[str, object]] = []
    dish_id = 1
    for source in source_windows:
        price_min, price_max = parse_price_band(source.get("价格带", ""))
        window_id = parse_int_code(source["窗口ID"], "window")
        restaurant_name = source["所属餐厅"]
        restaurant_id = name_to_id[restaurant_name]
        category = source.get("主营品类", "")
        name = source.get("窗口名称", "")
        taste_tags = split_tags(source.get("口味标签", ""))
        staple_tags = split_tags(source.get("主食标签", ""))
        audience_tags = split_tags(source.get("适配地区/人群标签", ""))
        friendly_tags = split_tags(source.get("饮食友好标签", ""))
        normalized_taste_tags = normalize_tags(taste_tags)
        normalized_staple_tags = normalize_tags(staple_tags)
        normalized_audience_tags = normalize_tags(audience_tags)
        normalized_friendly_tags = normalize_tags(friendly_tags)
        matching_tags = merge_tags(
            normalized_taste_tags,
            normalized_staple_tags,
            normalized_audience_tags,
            normalized_friendly_tags,
            normalize_tags(category),
            normalize_tags(name),
        )
        window_text = " ".join(
            [
                category,
                name,
                source.get("口味标签", ""),
                source.get("代表菜品（虚拟菜单）", ""),
            ]
        )
        windows.append(
            {
                "window_id": window_id,
                "source_window_code": source["窗口ID"],
                "restaurant_id": restaurant_id,
                "restaurant_name": restaurant_name,
                "name": name,
                "category": category,
                "recommended_meal_period": to_meal_period(source.get("推荐餐别", "")),
                "open_hours": source.get("参考营业时间", ""),
                "taste_tags": taste_tags,
                "staple_tags": staple_tags,
                "audience_tags": audience_tags,
                "friendly_tags": friendly_tags,
                "normalized_taste_tags": normalized_taste_tags,
                "normalized_staple_tags": normalized_staple_tags,
                "normalized_audience_tags": normalized_audience_tags,
                "normalized_friendly_tags": normalized_friendly_tags,
                "matching_tags": matching_tags,
                "price_min": price_min,
                "price_max": price_max,
                "service_rate_per_minute": derive_service_rate(category, name, price_max),
                "popularity": derive_popularity(source["窗口ID"], window_text),
                "status": "OPEN",
            }
        )
        for dish_name in split_dishes(source.get("代表菜品（虚拟菜单）", "")):
            dish_seed = f"dish:{source['窗口ID']}:{dish_name}"
            dish_price = derive_dish_price(dish_name, price_min, price_max, dish_seed)
            dishes.append(
                {
                    "dish_id": dish_id,
                    "window_id": window_id,
                    "restaurant_id": restaurant_id,
                    "name": dish_name,
                    "price": dish_price,
                    "prep_time_minutes": derive_prep_time(dish_name, category),
                    "popularity": derive_popularity(dish_seed, f"{dish_name} {window_text}"),
                    "taste_tags": taste_tags,
                    "staple_tags": staple_tags,
                    "friendly_tags": friendly_tags,
                    "normalized_taste_tags": normalized_taste_tags,
                    "normalized_staple_tags": normalized_staple_tags,
                    "normalized_friendly_tags": normalized_friendly_tags,
                    "matching_tags": merge_tags(matching_tags, normalize_tags(dish_name)),
                }
            )
            dish_id += 1

    return restaurants, windows, dishes


def build_tag_mappings() -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for source_tag, normalized_tags in sorted(TAG_ALIASES.items()):
        rows.append(
            {
                "rule_type": "EXACT",
                "source_tag": source_tag,
                "normalized_tags": "|".join(normalized_tags),
                "description": "精确标签映射",
            }
        )
    for keyword, normalized_tags in KEYWORD_TAG_RULES:
        rows.append(
            {
                "rule_type": "KEYWORD",
                "source_tag": f"*{keyword}*",
                "normalized_tags": "|".join(normalized_tags),
                "description": "关键词兜底映射",
            }
        )
    return rows


def read_csv(path: Path) -> list[dict[str, object]]:
    with path.open(newline="", encoding="utf-8-sig") as file:
        return [dict(row) for row in csv.DictReader(file)]


def enrich_existing_students(rows: list[dict[str, object]]) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for row in rows:
        staple_tags = str(row.get("staple_tags", ""))
        taste_tags = str(row.get("taste_tags", ""))
        food_keywords = str(row.get("food_keywords", ""))
        service_tags = str(row.get("service_tags", ""))
        normalized_staple_tags = normalize_tags(staple_tags)
        normalized_taste_tags = normalize_tags(taste_tags)
        normalized_food_tags = normalize_tags(food_keywords)
        normalized_service_tags = normalize_tags(service_tags)
        result.append(
            {
                "student_id": row.get("student_id", ""),
                "province": row.get("province", ""),
                "region_group": row.get("region_group", ""),
                "user_type": row.get("user_type", ""),
                "staple_tags": staple_tags,
                "taste_tags": taste_tags,
                "food_keywords": food_keywords,
                "service_tags": service_tags,
                "normalized_staple_tags": normalized_staple_tags,
                "normalized_taste_tags": normalized_taste_tags,
                "normalized_food_tags": normalized_food_tags,
                "normalized_service_tags": normalized_service_tags,
                "preference_tags": merge_tags(
                    normalized_staple_tags,
                    normalized_taste_tags,
                    normalized_food_tags,
                    normalized_service_tags,
                ),
                "budget_min": row.get("budget_min", ""),
                "budget_max": row.get("budget_max", ""),
                "waiting_tolerance_minutes": row.get("waiting_tolerance_minutes", ""),
                "breakfast_arrival_minute": row.get("breakfast_arrival_minute", ""),
                "lunch_arrival_minute": row.get("lunch_arrival_minute", ""),
                "dinner_arrival_minute": row.get("dinner_arrival_minute", ""),
            }
        )
    return result


def enrich_existing_windows(rows: list[dict[str, object]]) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for row in rows:
        taste_tags = str(row.get("taste_tags", ""))
        staple_tags = str(row.get("staple_tags", ""))
        audience_tags = str(row.get("audience_tags", ""))
        friendly_tags = str(row.get("friendly_tags", ""))
        normalized_taste_tags = normalize_tags(taste_tags)
        normalized_staple_tags = normalize_tags(staple_tags)
        normalized_audience_tags = normalize_tags(audience_tags)
        normalized_friendly_tags = normalize_tags(friendly_tags)
        matching_tags = merge_tags(
            normalized_taste_tags,
            normalized_staple_tags,
            normalized_audience_tags,
            normalized_friendly_tags,
            normalize_tags(str(row.get("category", ""))),
            normalize_tags(str(row.get("name", ""))),
        )
        result.append(
            {
                "window_id": row.get("window_id", ""),
                "source_window_code": row.get("source_window_code", ""),
                "restaurant_id": row.get("restaurant_id", ""),
                "restaurant_name": row.get("restaurant_name", ""),
                "name": row.get("name", ""),
                "category": row.get("category", ""),
                "recommended_meal_period": row.get("recommended_meal_period", ""),
                "open_hours": row.get("open_hours", ""),
                "taste_tags": taste_tags,
                "staple_tags": staple_tags,
                "audience_tags": audience_tags,
                "friendly_tags": friendly_tags,
                "normalized_taste_tags": normalized_taste_tags,
                "normalized_staple_tags": normalized_staple_tags,
                "normalized_audience_tags": normalized_audience_tags,
                "normalized_friendly_tags": normalized_friendly_tags,
                "matching_tags": matching_tags,
                "price_min": row.get("price_min", ""),
                "price_max": row.get("price_max", ""),
                "service_rate_per_minute": row.get("service_rate_per_minute", ""),
                "popularity": row.get("popularity", ""),
                "status": row.get("status", ""),
            }
        )
    return result


def enrich_existing_dishes(rows: list[dict[str, object]], windows_by_id: dict[str, dict[str, object]]) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for row in rows:
        taste_tags = str(row.get("taste_tags", ""))
        staple_tags = str(row.get("staple_tags", ""))
        friendly_tags = str(row.get("friendly_tags", ""))
        normalized_taste_tags = normalize_tags(taste_tags)
        normalized_staple_tags = normalize_tags(staple_tags)
        normalized_friendly_tags = normalize_tags(friendly_tags)
        parent_window = windows_by_id.get(str(row.get("window_id", "")), {})
        matching_tags = merge_tags(
            str(parent_window.get("matching_tags", "")),
            normalized_taste_tags,
            normalized_staple_tags,
            normalized_friendly_tags,
            normalize_tags(str(row.get("name", ""))),
        )
        result.append(
            {
                "dish_id": row.get("dish_id", ""),
                "window_id": row.get("window_id", ""),
                "restaurant_id": row.get("restaurant_id", ""),
                "name": row.get("name", ""),
                "price": row.get("price", ""),
                "prep_time_minutes": row.get("prep_time_minutes", ""),
                "popularity": row.get("popularity", ""),
                "taste_tags": taste_tags,
                "staple_tags": staple_tags,
                "friendly_tags": friendly_tags,
                "normalized_taste_tags": normalized_taste_tags,
                "normalized_staple_tags": normalized_staple_tags,
                "normalized_friendly_tags": normalized_friendly_tags,
                "matching_tags": matching_tags,
            }
        )
    return result


def load_existing_seed_data() -> tuple[
    list[dict[str, object]],
    list[dict[str, object]],
    list[dict[str, object]],
    list[dict[str, object]],
]:
    required_files = [
        OUT_DIR / "virtual_students.csv",
        OUT_DIR / "restaurants.csv",
        OUT_DIR / "windows.csv",
        OUT_DIR / "dishes.csv",
    ]
    missing_files = [path for path in required_files if not path.exists()]
    if missing_files:
        raise FileNotFoundError(f"Missing source XLSX and existing CSV files: {missing_files}")
    students = enrich_existing_students(read_csv(OUT_DIR / "virtual_students.csv"))
    restaurants = read_csv(OUT_DIR / "restaurants.csv")
    windows = enrich_existing_windows(read_csv(OUT_DIR / "windows.csv"))
    windows_by_id = {str(row["window_id"]): row for row in windows}
    dishes = enrich_existing_dishes(read_csv(OUT_DIR / "dishes.csv"), windows_by_id)
    return students, restaurants, windows, dishes


def build_arrival_rules() -> list[dict[str, object]]:
    return [
        {
            "meal_period": "BREAKFAST",
            "simulation_start_time": "07:00",
            "course_reference_time": "08:00",
            "peak_centers_minutes": "35|48|20",
            "peak_weights": "0.70|0.20|0.10",
            "standard_deviations": "10|7|8",
            "description": "早餐人流集中在 8:00 上课前，主峰约 7:35，次峰约 7:48。",
        },
        {
            "meal_period": "LUNCH",
            "simulation_start_time": "11:30",
            "course_reference_time": "12:00",
            "peak_centers_minutes": "38|52|20",
            "peak_weights": "0.75|0.20|0.05",
            "standard_deviations": "8|10|8",
            "description": "午餐人流由 12:00 下课驱动，考虑步行时间，主峰约 12:08。",
        },
        {
            "meal_period": "DINNER",
            "simulation_start_time": "18:00",
            "course_reference_time": "18:30",
            "peak_centers_minutes": "42|58|25",
            "peak_weights": "0.75|0.20|0.05",
            "standard_deviations": "10|12|8",
            "description": "晚餐人流由 18:30 下课驱动，考虑步行时间，主峰约 18:42。",
        },
    ]


def write_csv(path: Path, rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        raise ValueError(f"No rows to write: {path}")
    with path.open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def write_schema(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        textwrap.dedent(
            """\
            -- Task A simulation seed schema
            -- Generated for the campus dining simulation module.
            -- CSV seed files are stored in data/task-a/.

            CREATE TABLE IF NOT EXISTS task_a_virtual_students (
              student_id VARCHAR(32) PRIMARY KEY,
              province VARCHAR(32) NOT NULL,
              region_group VARCHAR(64) NOT NULL,
              user_type VARCHAR(32) NOT NULL,
              staple_tags VARCHAR(255) NOT NULL,
              taste_tags VARCHAR(255) NOT NULL,
              food_keywords VARCHAR(255) NOT NULL,
              service_tags VARCHAR(255) NOT NULL,
              normalized_staple_tags VARCHAR(255) NOT NULL,
              normalized_taste_tags VARCHAR(255) NOT NULL,
              normalized_food_tags VARCHAR(255) NOT NULL,
              normalized_service_tags VARCHAR(255) NOT NULL,
              preference_tags VARCHAR(512) NOT NULL,
              budget_min DECIMAL(6,2) NOT NULL,
              budget_max DECIMAL(6,2) NOT NULL,
              waiting_tolerance_minutes INT NOT NULL,
              breakfast_arrival_minute INT NOT NULL,
              lunch_arrival_minute INT NOT NULL,
              dinner_arrival_minute INT NOT NULL,
              INDEX idx_task_a_students_user_type (user_type),
              INDEX idx_task_a_students_region (region_group)
            );

            CREATE TABLE IF NOT EXISTS task_a_restaurants (
              restaurant_id BIGINT PRIMARY KEY,
              source_code VARCHAR(16) NOT NULL UNIQUE,
              name VARCHAR(64) NOT NULL,
              campus_area VARCHAR(64) NOT NULL,
              location VARCHAR(128) NOT NULL,
              restaurant_type VARCHAR(128) NOT NULL,
              capacity INT NOT NULL,
              base_attraction DECIMAL(4,2) NOT NULL,
              breakfast_hours VARCHAR(64) NOT NULL,
              lunch_hours VARCHAR(64) NOT NULL,
              dinner_hours VARCHAR(64) NOT NULL,
              extended_hours VARCHAR(64) NOT NULL,
              status VARCHAR(16) NOT NULL,
              INDEX idx_task_a_restaurants_area (campus_area),
              INDEX idx_task_a_restaurants_status (status)
            );

            CREATE TABLE IF NOT EXISTS task_a_windows (
              window_id BIGINT PRIMARY KEY,
              source_window_code VARCHAR(16) NOT NULL UNIQUE,
              restaurant_id BIGINT NOT NULL,
              restaurant_name VARCHAR(64) NOT NULL,
              name VARCHAR(64) NOT NULL,
              category VARCHAR(64) NOT NULL,
              recommended_meal_period VARCHAR(16) NOT NULL,
              open_hours VARCHAR(128) NOT NULL,
              taste_tags VARCHAR(255) NOT NULL,
              staple_tags VARCHAR(255) NOT NULL,
              audience_tags VARCHAR(255) NOT NULL,
              friendly_tags VARCHAR(255) NOT NULL,
              normalized_taste_tags VARCHAR(255) NOT NULL,
              normalized_staple_tags VARCHAR(255) NOT NULL,
              normalized_audience_tags VARCHAR(255) NOT NULL,
              normalized_friendly_tags VARCHAR(255) NOT NULL,
              matching_tags VARCHAR(512) NOT NULL,
              price_min DECIMAL(6,2) NOT NULL,
              price_max DECIMAL(6,2) NOT NULL,
              service_rate_per_minute DECIMAL(5,2) NOT NULL,
              popularity DECIMAL(4,2) NOT NULL,
              status VARCHAR(16) NOT NULL,
              CONSTRAINT fk_task_a_windows_restaurant
                FOREIGN KEY (restaurant_id) REFERENCES task_a_restaurants (restaurant_id),
              INDEX idx_task_a_windows_restaurant (restaurant_id),
              INDEX idx_task_a_windows_meal_period (recommended_meal_period),
              INDEX idx_task_a_windows_status (status)
            );

            CREATE TABLE IF NOT EXISTS task_a_dishes (
              dish_id BIGINT PRIMARY KEY,
              window_id BIGINT NOT NULL,
              restaurant_id BIGINT NOT NULL,
              name VARCHAR(64) NOT NULL,
              price DECIMAL(6,2) NOT NULL,
              prep_time_minutes INT NOT NULL,
              popularity DECIMAL(4,2) NOT NULL,
              taste_tags VARCHAR(255) NOT NULL,
              staple_tags VARCHAR(255) NOT NULL,
              friendly_tags VARCHAR(255) NOT NULL,
              normalized_taste_tags VARCHAR(255) NOT NULL,
              normalized_staple_tags VARCHAR(255) NOT NULL,
              normalized_friendly_tags VARCHAR(255) NOT NULL,
              matching_tags VARCHAR(512) NOT NULL,
              CONSTRAINT fk_task_a_dishes_window
                FOREIGN KEY (window_id) REFERENCES task_a_windows (window_id),
              CONSTRAINT fk_task_a_dishes_restaurant
                FOREIGN KEY (restaurant_id) REFERENCES task_a_restaurants (restaurant_id),
              INDEX idx_task_a_dishes_window (window_id),
              INDEX idx_task_a_dishes_restaurant (restaurant_id),
              INDEX idx_task_a_dishes_price (price)
            );

            CREATE TABLE IF NOT EXISTS task_a_arrival_rules (
              meal_period VARCHAR(16) PRIMARY KEY,
              simulation_start_time VARCHAR(8) NOT NULL,
              course_reference_time VARCHAR(8) NOT NULL,
              peak_centers_minutes VARCHAR(64) NOT NULL,
              peak_weights VARCHAR(64) NOT NULL,
              standard_deviations VARCHAR(64) NOT NULL,
              description VARCHAR(255) NOT NULL
            );

            CREATE TABLE IF NOT EXISTS task_a_tag_mappings (
              mapping_id BIGINT AUTO_INCREMENT PRIMARY KEY,
              rule_type VARCHAR(16) NOT NULL,
              source_tag VARCHAR(64) NOT NULL,
              normalized_tags VARCHAR(255) NOT NULL,
              description VARCHAR(128) NOT NULL,
              INDEX idx_task_a_tag_mappings_source (source_tag)
            );
            """
        ),
        encoding="utf-8",
    )


def write_summary(
    path: Path,
    students: list[dict[str, object]],
    restaurants: list[dict[str, object]],
    windows: list[dict[str, object]],
    dishes: list[dict[str, object]],
    tag_mappings: list[dict[str, object]],
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    user_type_counts = Counter(row["user_type"] for row in students)
    meal_counts = Counter(row["recommended_meal_period"] for row in windows)
    lines = [
        "# 任务 A 仿真种子数据说明",
        "",
        "本目录由 `scripts/prepare_task_a_seed_data.py` 从两份 Excel 源数据整理生成。",
        "",
        "## 生成结果",
        "",
        f"- 虚拟学生：{len(students)} 条，输出文件 `virtual_students.csv`。",
        f"- 餐厅：{len(restaurants)} 条，输出文件 `restaurants.csv`。",
        f"- 窗口：{len(windows)} 条，输出文件 `windows.csv`。",
        f"- 菜品：{len(dishes)} 条，输出文件 `dishes.csv`。",
        "- 课程驱动到达规则：3 条，输出文件 `arrival_rules.csv`。",
        f"- 标签标准化规则：{len(tag_mappings)} 条，输出文件 `tag_mappings.csv`。",
        "",
        "## 关键派生规则",
        "",
        "- 早餐仿真从 07:00 开始，主峰在 07:35 左右，用于模拟 08:00 上课前的人流。",
        "- 午餐仿真从 11:30 开始，12:00 下课后形成主峰，主峰在 12:08 左右。",
        "- 晚餐仿真从 18:00 开始，18:30 下课后形成主峰，主峰在 18:42 左右。",
        "- `user_type` 按固定随机种子派生，其中包含普通学生、赶时间用户、预算敏感用户。",
        "- `budget_min`、`budget_max`、`waiting_tolerance_minutes` 根据用户类型和口味标签派生。",
        "- 餐厅 `capacity`、`base_attraction` 根据餐厅类型、窗口数量和延时营业情况派生。",
        "- 窗口 `service_rate_per_minute` 根据主营品类派生，例如早餐/饮品更快，香锅/烤鱼/小炒更慢。",
        "- 菜品从窗口代表菜品拆分得到，并派生 `price`、`prep_time_minutes`、`popularity`。",
        "- 原始标签会保留，同时生成 `normalized_*_tags` 和用于匹配的 `preference_tags`、`matching_tags`。",
        "",
        "## 文件与数据库表对应关系",
        "",
        "| CSV 文件 | 数据库表 | 用途 |",
        "| --- | --- | --- |",
        "| `virtual_students.csv` | `task_a_virtual_students` | 虚拟学生画像池，用于抽样生成虚拟就餐者。 |",
        "| `restaurants.csv` | `task_a_restaurants` | 餐厅基础参数，用于容量、吸引力和拥挤度计算。 |",
        "| `windows.csv` | `task_a_windows` | 窗口基础参数，用于排队、服务速率和窗口推荐。 |",
        "| `dishes.csv` | `task_a_dishes` | 菜品基础参数，用于菜品偏好和预算匹配。 |",
        "| `arrival_rules.csv` | `task_a_arrival_rules` | 课程时间驱动的人流到达规则。 |",
        "| `tag_mappings.csv` | `task_a_tag_mappings` | 原始标签到标准标签的映射规则。 |",
        "",
        "## 关键字段说明",
        "",
        "- `breakfast_arrival_minute`：从 07:00 开始计算的早餐到达分钟数。",
        "- `lunch_arrival_minute`：从 11:30 开始计算的午餐到达分钟数。",
        "- `dinner_arrival_minute`：从 18:00 开始计算的晚餐到达分钟数。",
        "- `service_rate_per_minute`：窗口每分钟可服务人数，是后续排队计算的核心字段。",
        "- `base_attraction`：餐厅基础吸引力，后续可与距离、拥挤度共同决定用户去向。",
        "- `popularity`：窗口或菜品热度，后续可参与用户选择和 B 模块推荐计算。",
        "- 标签字段统一用 `|` 分隔，例如 `面食|米饭`、`咸鲜|酱香`。",
        "- `preference_tags`：学生综合偏好标签，由主食、口味、饮食关键词和服务标签标准化合并而来。",
        "- `matching_tags`：窗口/菜品可匹配标签，由主食、口味、人群、友好标签和名称关键词标准化合并而来。",
        "",
        "## 标准标签集合",
        "",
        "MVP 阶段主要使用以下标准标签做匹配：",
        "",
        "- 主食类：`米饭`、`面食`、`粉面`、`粥`、`杂粮`。",
        "- 口味类：`辣味`、`酸香`、`咸鲜`、`家常`、`清淡`、`甜口`。",
        "- 场景类：`快餐`、`早餐`、`汤类`、`饮品`、`甜口`。",
        "- 人群/友好类：`清真`、`素食`、`轻食`、`健康`、`小份`、`可调辣`、`大众`。",
        "",
        "## 分布摘要",
        "",
        "### 用户类型",
        "",
    ]
    for key, count in sorted(user_type_counts.items()):
        lines.append(f"- {key}: {count}")
    lines.extend(["", "### 窗口推荐餐别", ""])
    for key, count in sorted(meal_counts.items()):
        lines.append(f"- {key}: {count}")
    lines.extend(
        [
            "",
            "## 使用建议",
            "",
            "- 后端 MVP 阶段可以直接读取 CSV 初始化内存数据。",
            "- 如果接数据库，可先执行 `sql/schema/001_task_a_seed_schema.sql` 建表，再导入本目录 CSV。",
            "- 原始 Excel 不建议直接放入运行代码路径，避免说明性字段影响程序读取。",
            "",
        ]
    )
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    if STUDENT_XLSX.exists() and DINING_XLSX.exists():
        source_students, source_restaurants, source_windows = load_source_data()
        students = build_students(source_students)
        restaurants, windows, dishes = build_restaurants_and_windows(source_restaurants, source_windows)
    else:
        print("Source XLSX files not found; enriching existing CSV seed data.")
        students, restaurants, windows, dishes = load_existing_seed_data()

    arrival_rules = build_arrival_rules()
    tag_mappings = build_tag_mappings()

    write_csv(OUT_DIR / "virtual_students.csv", students)
    write_csv(OUT_DIR / "restaurants.csv", restaurants)
    write_csv(OUT_DIR / "windows.csv", windows)
    write_csv(OUT_DIR / "dishes.csv", dishes)
    write_csv(OUT_DIR / "arrival_rules.csv", arrival_rules)
    write_csv(OUT_DIR / "tag_mappings.csv", tag_mappings)
    write_schema(SQL_DIR / "001_task_a_seed_schema.sql")
    write_summary(OUT_DIR / "README.md", students, restaurants, windows, dishes, tag_mappings)

    print(f"Generated {len(students)} students")
    print(f"Generated {len(restaurants)} restaurants")
    print(f"Generated {len(windows)} windows")
    print(f"Generated {len(dishes)} dishes")
    print(f"Generated {len(tag_mappings)} tag mappings")
    print(f"Output directory: {OUT_DIR}")


if __name__ == "__main__":
    main()
