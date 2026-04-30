# B模块源代码说明文档：Model 数据模型

## 1. 目录位置

```text
backend/src/main/java/com/bjtu/dining/recommendation/model/
```

## 2. 目录职责

Model 目录存放 B 模块内部使用的数据结构，主要包括餐厅、窗口、菜品基础参数，仿真时间片快照，以及仿真评估指标。

这些类大多使用 Java `record` 定义，字段不可变，适合用于数据传递。

## 3. 源码文件说明

### RestaurantParameter.java

餐厅基础参数。

主要字段：

| 字段 | 说明 |
| --- | --- |
| `restaurantId` | 餐厅 ID |
| `name` | 餐厅名称 |
| `location` | 餐厅位置 |
| `capacity` | 餐厅容量 |
| `baseAttraction` | 基础吸引力 |
| `status` | 营业状态 |

用途：

- 餐厅推荐分数计算。
- 餐厅营业状态过滤。
- 仿真快照中餐厅拥挤度计算参考。

### WindowParameter.java

窗口基础参数。

主要字段：

| 字段 | 说明 |
| --- | --- |
| `windowId` | 窗口 ID |
| `restaurantId` | 所属餐厅 ID |
| `name` | 窗口名称 |
| `category` | 主营品类 |
| `matchingTags` | 可匹配标签 |
| `priceMin` | 价格下限 |
| `priceMax` | 价格上限 |
| `serviceRatePerMinute` | 每分钟服务人数 |
| `popularity` | 热度 |
| `status` | 营业状态 |

用途：

- 窗口推荐分数计算。
- 菜品推荐时判断所属窗口是否开放。
- 分流建议中计算目标窗口承接能力。

### DishParameter.java

菜品基础参数。

主要字段：

| 字段 | 说明 |
| --- | --- |
| `dishId` | 菜品 ID |
| `windowId` | 所属窗口 ID |
| `restaurantId` | 所属餐厅 ID |
| `name` | 菜品名称 |
| `price` | 菜品价格 |
| `prepTimeMinutes` | 出餐时间 |
| `popularity` | 菜品热度 |
| `matchingTags` | 菜品标签 |

用途：

- 菜品推荐分数计算。
- 预算匹配判断。
- 用户口味标签匹配。

### SimulationRunResult.java

一次仿真运行结果。

主要字段：

| 字段 | 说明 |
| --- | --- |
| `runId` | 仿真运行 ID |
| `status` | 仿真状态 |
| `timePoints` | 仿真时间片列表 |
| `metrics` | 仿真评估指标 |
| `createdAt` | 创建时间 |

用途：

- 推荐接口根据 `runId` 获取仿真快照。
- 策略对比接口读取 `metrics`。

### SimulationTimePoint.java

单个仿真时间点。

主要字段：

| 字段 | 说明 |
| --- | --- |
| `minute` | 当前仿真分钟 |
| `restaurants` | 该时间点所有餐厅状态 |

用途：

- 推荐和分流接口在指定 `minute` 下读取餐厅、窗口状态。

### RestaurantSnapshot.java

某个时间点的餐厅状态。

主要字段：

| 字段 | 说明 |
| --- | --- |
| `restaurantId` | 餐厅 ID |
| `name` | 餐厅名称 |
| `currentCount` | 当前人数 |
| `capacity` | 餐厅容量 |
| `crowdLevel` | 餐厅拥挤等级 |
| `windows` | 该餐厅下窗口状态 |

用途：

- 餐厅推荐。
- 分流来源和目标窗口遍历。

### WindowSnapshot.java

某个时间点的窗口队列状态。

主要字段：

| 字段 | 说明 |
| --- | --- |
| `windowId` | 窗口 ID |
| `name` | 窗口名称 |
| `queueLength` | 排队人数 |
| `servingCount` | 服务中人数 |
| `waitMinutes` | 预计等待时间 |
| `crowdLevel` | 窗口拥挤等级 |
| `status` | 营业状态 |

用途：

- 推荐分数中的等待时间和拥挤度判断。
- 分流策略中选择来源窗口和目标窗口。

### EvaluationMetrics.java

仿真评估指标。

主要字段：

| 字段 | 说明 |
| --- | --- |
| `avgWaitMinutes` | 平均等待时间 |
| `maxWaitMinutes` | 最大等待时间 |
| `maxQueueLength` | 最大排队长度 |
| `busyWindowCount` | 拥挤窗口数量 |
| `extremeWindowCount` | 极端拥挤窗口数量 |
| `totalVirtualUsers` | 总虚拟人数 |
| `servedUserCount` | 已服务人数 |
| `unservedUserCount` | 未服务人数 |

用途：

- `POST /api/v1/strategies/compare` 中对比两个仿真场景效果。
