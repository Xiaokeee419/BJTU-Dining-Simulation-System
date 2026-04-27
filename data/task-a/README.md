# 任务 A 仿真种子数据说明

本目录由 `scripts/prepare_task_a_seed_data.py` 从两份 Excel 源数据整理生成。

## 生成结果

- 虚拟学生：4169 条，输出文件 `virtual_students.csv`。
- 餐厅：19 条，输出文件 `restaurants.csv`。
- 窗口：79 条，输出文件 `windows.csv`。
- 菜品：254 条，输出文件 `dishes.csv`。
- 课程驱动到达规则：3 条，输出文件 `arrival_rules.csv`。
- 标签标准化规则：130 条，输出文件 `tag_mappings.csv`。

## 关键派生规则

- 早餐仿真从 07:00 开始，主峰在 07:35 左右，用于模拟 08:00 上课前的人流。
- 午餐仿真从 11:30 开始，12:00 下课后形成主峰，主峰在 12:08 左右。
- 晚餐仿真从 18:00 开始，18:30 下课后形成主峰，主峰在 18:42 左右。
- `user_type` 按固定随机种子派生，其中包含普通学生、赶时间用户、预算敏感用户。
- `budget_min`、`budget_max`、`waiting_tolerance_minutes` 根据用户类型和口味标签派生。
- 餐厅 `capacity`、`base_attraction` 根据餐厅类型、窗口数量和延时营业情况派生。
- 窗口 `service_rate_per_minute` 根据主营品类派生，例如早餐/饮品更快，香锅/烤鱼/小炒更慢。
- 菜品从窗口代表菜品拆分得到，并派生 `price`、`prep_time_minutes`、`popularity`。
- 原始标签会保留，同时生成 `normalized_*_tags` 和用于匹配的 `preference_tags`、`matching_tags`。

## 文件与数据库表对应关系

| CSV 文件 | 数据库表 | 用途 |
| --- | --- | --- |
| `virtual_students.csv` | `task_a_virtual_students` | 虚拟学生画像池，用于抽样生成虚拟就餐者。 |
| `restaurants.csv` | `task_a_restaurants` | 餐厅基础参数，用于容量、吸引力和拥挤度计算。 |
| `windows.csv` | `task_a_windows` | 窗口基础参数，用于排队、服务速率和窗口推荐。 |
| `dishes.csv` | `task_a_dishes` | 菜品基础参数，用于菜品偏好和预算匹配。 |
| `arrival_rules.csv` | `task_a_arrival_rules` | 课程时间驱动的人流到达规则。 |
| `tag_mappings.csv` | `task_a_tag_mappings` | 原始标签到标准标签的映射规则。 |

## 关键字段说明

- `breakfast_arrival_minute`：从 07:00 开始计算的早餐到达分钟数。
- `lunch_arrival_minute`：从 11:30 开始计算的午餐到达分钟数。
- `dinner_arrival_minute`：从 18:00 开始计算的晚餐到达分钟数。
- `service_rate_per_minute`：窗口每分钟可服务人数，是后续排队计算的核心字段。
- `base_attraction`：餐厅基础吸引力，后续可与距离、拥挤度共同决定用户去向。
- `popularity`：窗口或菜品热度，后续可参与用户选择和 B 模块推荐计算。
- 标签字段统一用 `|` 分隔，例如 `面食|米饭`、`咸鲜|酱香`。
- `preference_tags`：学生综合偏好标签，由主食、口味、饮食关键词和服务标签标准化合并而来。
- `matching_tags`：窗口/菜品可匹配标签，由主食、口味、人群、友好标签和名称关键词标准化合并而来。

## 标准标签集合

MVP 阶段主要使用以下标准标签做匹配：

- 主食类：`米饭`、`面食`、`粉面`、`粥`、`杂粮`。
- 口味类：`辣味`、`酸香`、`咸鲜`、`家常`、`清淡`、`甜口`。
- 场景类：`快餐`、`早餐`、`汤类`、`饮品`、`甜口`。
- 人群/友好类：`清真`、`素食`、`轻食`、`健康`、`小份`、`可调辣`、`大众`。

## 分布摘要

### 用户类型

- BUDGET_SENSITIVE: 490
- HURRY: 518
- STUDENT: 3161

### 窗口推荐餐别

- ALL: 11
- BREAKFAST: 10
- DINNER: 21
- LUNCH: 37

## 使用建议

- 后端 MVP 阶段可以直接读取 CSV 初始化内存数据。
- 如果接数据库，可先执行 `sql/schema/001_task_a_seed_schema.sql` 建表，再导入本目录 CSV。
- 原始 Excel 不建议直接放入运行代码路径，避免说明性字段影响程序读取。
