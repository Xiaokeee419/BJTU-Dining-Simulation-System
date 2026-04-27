# 任务 A 仿真数据结构设计

版本：v0.1

日期：2026/4/27

适用范围：成员 A 仿真数据生成与效果评估模块

---

## 1. 设计目标

任务 A 的核心目标是把“虚拟学生画像、餐厅窗口参数、菜品参数、课程时间场景”转换成可展示、可推荐、可评估的仿真结果。

本模块不负责推荐排序和前端展示，只负责生成以下数据：

1. 虚拟就餐者。
2. 餐厅和窗口基础参数。
3. 每个时间片的人流和排队状态。
4. 拥挤度和等待时间。
5. 仿真评估指标。

核心流程：

```text
读取种子数据
-> 抽样虚拟学生
-> 根据课程时间生成到达时间
-> 根据偏好和窗口参数选择窗口/菜品
-> 按时间片更新队列
-> 输出 timePoints 和 metrics
```

---

## 2. 种子数据结构

### 2.1 VirtualStudentSeed

来源：`data/task-a/virtual_students.csv`

用途：作为虚拟学生画像池，运行仿真时按 `virtualUserCount` 抽样。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `studentId` | String | 虚拟学生 ID |
| `province` | String | 生源省份 |
| `regionGroup` | String | 地区分组 |
| `userType` | Enum | `STUDENT`、`HURRY`、`BUDGET_SENSITIVE` |
| `stapleTags` | Set<String> | 原始主食标签 |
| `tasteTags` | Set<String> | 原始口味标签 |
| `foodKeywords` | Set<String> | 原始饮食关键词 |
| `serviceTags` | Set<String> | 原始服务标签 |
| `normalizedStapleTags` | Set<String> | 标准主食标签 |
| `normalizedTasteTags` | Set<String> | 标准口味标签 |
| `normalizedFoodTags` | Set<String> | 标准饮食关键词 |
| `normalizedServiceTags` | Set<String> | 标准服务标签 |
| `preferenceTags` | Set<String> | 综合偏好标签，用于匹配窗口和菜品 |
| `budgetMin` | BigDecimal | 最低预算 |
| `budgetMax` | BigDecimal | 最高预算 |
| `waitingToleranceMinutes` | Integer | 等待容忍时间 |
| `breakfastArrivalMinute` | Integer | 早餐到达分钟，从 07:00 开始 |
| `lunchArrivalMinute` | Integer | 午餐到达分钟，从 11:30 开始 |
| `dinnerArrivalMinute` | Integer | 晚餐到达分钟，从 18:00 开始 |

### 2.2 RestaurantSeed

来源：`data/task-a/restaurants.csv`

用途：提供餐厅容量、位置、吸引力和营业状态。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `restaurantId` | Long | 餐厅 ID |
| `name` | String | 餐厅名称 |
| `campusArea` | String | 校区/区域 |
| `location` | String | 具体位置 |
| `restaurantType` | String | 餐厅定位 |
| `capacity` | Integer | 容量 |
| `baseAttraction` | Double | 基础吸引力，范围 0-1 |
| `breakfastHours` | String | 早餐营业时间 |
| `lunchHours` | String | 午餐营业时间 |
| `dinnerHours` | String | 晚餐营业时间 |
| `extendedHours` | String | 延时营业说明 |
| `status` | Enum | `OPEN` 或 `CLOSED` |

### 2.3 WindowSeed

来源：`data/task-a/windows.csv`

用途：提供窗口服务速率、价格带、标签和热度。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `windowId` | Long | 窗口 ID |
| `restaurantId` | Long | 所属餐厅 ID |
| `name` | String | 窗口名称 |
| `category` | String | 主营品类 |
| `recommendedMealPeriod` | Enum | `BREAKFAST`、`LUNCH`、`DINNER`、`ALL` |
| `openHours` | String | 营业时间文本 |
| `priceMin` | BigDecimal | 价格带下限 |
| `priceMax` | BigDecimal | 价格带上限 |
| `serviceRatePerMinute` | Double | 每分钟可服务人数 |
| `popularity` | Double | 热度，范围 0-1 |
| `matchingTags` | Set<String> | 窗口可匹配标签 |
| `status` | Enum | `OPEN` 或 `CLOSED` |

### 2.4 DishSeed

来源：`data/task-a/dishes.csv`

用途：提供菜品价格、出餐时间、热度和标签。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dishId` | Long | 菜品 ID |
| `windowId` | Long | 所属窗口 ID |
| `restaurantId` | Long | 所属餐厅 ID |
| `name` | String | 菜品名称 |
| `price` | BigDecimal | 菜品价格 |
| `prepTimeMinutes` | Integer | 出餐时间 |
| `popularity` | Double | 热度，范围 0-1 |
| `matchingTags` | Set<String> | 菜品可匹配标签 |

---

## 3. 运行时数据结构

### 3.1 SimulationScenario

来源：前端请求 `POST /api/v1/simulations/run`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `mealPeriod` | Enum | 早餐/午餐/晚餐 |
| `dayType` | Enum | 工作日/周末 |
| `crowdLevel` | Enum | 初始拥挤等级 |
| `weatherFactor` | Double | 天气影响系数 |
| `eventFactor` | Double | 活动影响系数 |
| `closedWindowIds` | Set<Long> | 本次关闭窗口 |
| `virtualUserCount` | Integer | 仿真人数 |
| `durationMinutes` | Integer | 仿真持续分钟 |
| `stepMinutes` | Integer | 时间片长度 |
| `randomSeed` | Long | 随机种子 |

### 3.2 VirtualDiner

运行时虚拟就餐者，由 `VirtualStudentSeed` 抽样并结合场景生成。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dinerId` | String | 本次仿真内的虚拟就餐者 ID |
| `sourceStudentId` | String | 来源学生 ID |
| `userType` | Enum | 用户类型 |
| `preferenceTags` | Set<String> | 偏好标签 |
| `budgetMin` | BigDecimal | 最低预算 |
| `budgetMax` | BigDecimal | 最高预算 |
| `waitingToleranceMinutes` | Integer | 等待容忍时间 |
| `arrivalMinute` | Integer | 到达时间 |
| `targetRestaurantId` | Long | 选择的餐厅 |
| `targetWindowId` | Long | 选择的窗口 |
| `targetDishId` | Long | 选择的菜品 |
| `expectedWaitMinutes` | Double | 选择时预估等待 |
| `servedMinute` | Integer? | 被服务时间，未服务则为空 |

### 3.3 WindowQueueState

每个时间片中某个窗口的队列状态。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `windowId` | Long | 窗口 ID |
| `restaurantId` | Long | 餐厅 ID |
| `queueLength` | Integer | 当前排队人数 |
| `arrivedCount` | Integer | 本时间片新到达人数 |
| `servedCount` | Integer | 本时间片服务人数 |
| `servingCount` | Integer | 当前服务中人数，MVP 可取 `ceil(serviceRatePerMinute)` |
| `waitMinutes` | Double | 预计等待时间 |
| `crowdLevel` | Enum | 拥挤等级 |
| `status` | Enum | 营业状态 |

### 3.4 RestaurantSnapshot

每个时间片中某个餐厅的整体状态。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `restaurantId` | Long | 餐厅 ID |
| `name` | String | 餐厅名称 |
| `currentCount` | Integer | 当前餐厅人数 |
| `capacity` | Integer | 餐厅容量 |
| `crowdLevel` | Enum | 餐厅拥挤等级 |
| `windows` | List<WindowQueueState> | 窗口队列状态 |

### 3.5 SimulationTimePoint

接口返回给 C 的时间片数据。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `minute` | Integer | 当前仿真分钟 |
| `restaurants` | List<RestaurantSnapshot> | 全体餐厅状态 |

### 3.6 EvaluationMetrics

仿真结束后的评估指标。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `avgWaitMinutes` | Double | 平均等待时间 |
| `maxWaitMinutes` | Double | 最大等待时间 |
| `maxQueueLength` | Integer | 最大排队长度 |
| `busyWindowCount` | Integer | 拥挤窗口数量 |
| `extremeWindowCount` | Integer | 极端拥挤窗口数量 |
| `totalVirtualUsers` | Integer | 总虚拟人数 |
| `servedUserCount` | Integer | 已服务人数 |
| `unservedUserCount` | Integer | 未服务人数 |

---

## 4. 标签匹配策略

标签分两层：

1. 原始标签：来自 Excel，用于追溯数据来源。
2. 标准标签：由 `tag_mappings.csv` 生成，用于算法匹配。

学生使用：

```text
preferenceTags = normalizedStapleTags + normalizedTasteTags + normalizedFoodTags + normalizedServiceTags
```

窗口/菜品使用：

```text
matchingTags = normalizedStapleTags + normalizedTasteTags + normalizedAudienceTags + normalizedFriendlyTags + 名称关键词
```

窗口选择时可以计算：

```text
tagMatchScore = 交集标签数量 / 学生偏好标签数量
```

如果学生是 `HURRY`，应增加等待时间惩罚权重。

如果学生是 `BUDGET_SENSITIVE`，应增加预算匹配权重。

---

## 5. 下一步实现顺序

1. 加载 `data/task-a/*.csv` 到内存。
2. 根据 `virtualUserCount` 抽样 `VirtualStudentSeed`，生成 `VirtualDiner`。
3. 根据 `mealPeriod` 选择对应到达时间字段。
4. 按 `arrivalMinute` 排序虚拟就餐者。
5. 为每个虚拟就餐者选择窗口和菜品。
6. 按 `stepMinutes` 推进队列状态。
7. 生成 `SimulationTimePoint`。
8. 汇总 `EvaluationMetrics`。
9. 接入 `POST /api/v1/simulations/run`。
