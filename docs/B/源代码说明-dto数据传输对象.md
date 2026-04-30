# B模块源代码说明文档：DTO 数据传输对象

## 1. 目录位置

```text
backend/src/main/java/com/bjtu/dining/recommendation/dto/
```

## 2. 目录职责

DTO 目录存放 B 模块接口的请求对象和响应对象。Controller 使用这些对象接收 JSON 请求体，Service 使用这些对象组织返回结果。

## 3. 请求 DTO

### RecommendationGenerateRequest.java

推荐生成请求对象，对应接口：

```text
POST /api/v1/recommendations/generate
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `runId` | A 模块生成的仿真运行 ID，不能为空 |
| `minute` | 指定仿真时间点，可为空；为空时使用最后一个时间点 |
| `profile` | 用户画像，不能为空 |
| `limit` | 每类推荐返回数量，可为空；默认 3，最大 10 |

### UserProfileRequest.java

用户画像请求对象。

字段说明：

| 字段 | 说明 |
| --- | --- |
| `userType` | 用户类型，例如 `STUDENT`、`HURRY`、`BUDGET_SENSITIVE` |
| `tasteTags` | 口味或饮食偏好标签 |
| `budgetMin` | 最低预算 |
| `budgetMax` | 最高预算 |
| `waitingToleranceMinutes` | 等待容忍时间 |

### DiversionRequest.java

分流建议请求对象，对应接口：

```text
POST /api/v1/recommendations/diversion
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `runId` | 仿真运行 ID，不能为空 |
| `minute` | 指定仿真时间点，可为空 |
| `targetCrowdLevel` | 分流后期望达到的拥挤等级，不能为空 |

### StrategyCompareRequest.java

策略对比请求对象，对应接口：

```text
POST /api/v1/strategies/compare
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `baseRunId` | 基础场景的仿真运行 ID，不能为空 |
| `compareRunId` | 对比场景的仿真运行 ID，不能为空 |

## 4. 响应 DTO

### RecommendationResult.java

推荐结果响应对象。

主要字段：

| 字段 | 说明 |
| --- | --- |
| `runId` | 仿真运行 ID |
| `minute` | 推荐基于的仿真时间点 |
| `restaurants` | 餐厅推荐列表 |
| `windows` | 窗口推荐列表 |
| `dishes` | 菜品推荐列表 |
| `diversionSuggestion` | 推荐结果中的简要分流建议 |
| `generatedAt` | 推荐生成时间 |

### RecommendationItem.java

单条推荐项。

主要字段：

| 字段 | 说明 |
| --- | --- |
| `targetType` | 推荐目标类型：`RESTAURANT`、`WINDOW`、`DISH` |
| `targetId` | 目标 ID |
| `name` | 推荐对象名称 |
| `score` | 推荐分数 |
| `rank` | 排名 |
| `reason` | 中文推荐理由 |
| `relatedRestaurantId` | 关联餐厅 ID |
| `relatedWindowId` | 关联窗口 ID |
| `estimatedWaitMinutes` | 预计等待时间 |
| `crowdLevel` | 当前拥挤等级 |

### DiversionResult.java

分流建议响应对象。

字段说明：

| 字段 | 说明 |
| --- | --- |
| `runId` | 仿真运行 ID |
| `minute` | 分流建议基于的仿真时间点 |
| `suggestions` | 分流建议列表 |
| `reason` | 总体说明 |

### DiversionSuggestionItem.java

单条分流建议。

字段说明：

| 字段 | 说明 |
| --- | --- |
| `fromRestaurantId` | 来源餐厅 ID |
| `fromWindowId` | 来源窗口 ID |
| `toRestaurantId` | 目标餐厅 ID |
| `toWindowId` | 目标窗口 ID |
| `suggestedUserCount` | 建议分流人数 |
| `reason` | 中文分流原因 |

### StrategyComparisonResult.java

策略对比响应对象。

字段说明：

| 字段 | 说明 |
| --- | --- |
| `baseRunId` | 基础场景 runId |
| `compareRunId` | 对比场景 runId |
| `avgWaitDelta` | 平均等待时间差值 |
| `maxQueueDelta` | 最大排队长度差值 |
| `busyWindowCountDelta` | 拥挤窗口数差值 |
| `extremeWindowCountDelta` | 极端拥挤窗口数差值 |
| `servedUserCountDelta` | 已服务人数差值 |
| `conclusion` | 中文对比结论 |

## 5. 校验说明

请求 DTO 使用 Jakarta Validation 注解，例如：

- `@NotNull`
- `@NotBlank`
- `@Valid`

校验失败后由 `GlobalExceptionHandler` 返回 `40001` 参数校验失败。
