# B模块源代码说明文档：测试说明

## 1. 测试源码位置

```text
backend/src/test/java/com/bjtu/dining/recommendation/service/RecommendationServiceTest.java
```

## 2. 测试框架

使用 Spring Boot Test 和 JUnit 5 进行测试。

主要注解：

```text
@SpringBootTest
```

断言库：

```text
AssertJ
```

## 3. 测试对象

当前测试重点覆盖 B 模块核心服务：

```text
RecommendationService
```

测试内容包括：

- 推荐结果生成。
- 已生成推荐结果查询。
- 默认参数处理。
- 参数非法时的错误码。
- 分流建议生成。
- 非法拥挤等级处理。
- 策略对比结果生成。
- 缺失 runId 处理。

## 4. 测试用例说明

| 测试方法 | 测试内容 |
| --- | --- |
| `generateReturnsRestaurantWindowAndDishRecommendations` | 验证推荐生成能返回餐厅、窗口、菜品三类推荐 |
| `getGeneratedReturnsLatestGeneratedResult` | 验证可按 runId 查询最近一次推荐结果 |
| `generateUsesLatestMinuteAndDefaultLimit` | 验证不传 minute 时使用最后时间点，不传 limit 时默认返回 3 条 |
| `getGeneratedRejectsMissingResult` | 验证未生成推荐时返回 `40400` |
| `generateRejectsInvalidLimit` | 验证 `limit` 超出范围时返回 `40001` |
| `generateRejectsInvalidBudgetRange` | 验证预算范围非法时返回 `40001` |
| `generateDiversionReturnsSuggestionsForCrowdedWindows` | 验证拥挤场景下能生成分流建议 |
| `generateDiversionRejectsInvalidTargetCrowdLevel` | 验证非法目标拥挤等级返回 `40002` |
| `compareStrategiesReturnsMetricDeltasAndConclusion` | 验证策略对比能返回各项 delta 和中文结论 |
| `compareStrategiesRejectsMissingRunId` | 验证 runId 不存在时返回 `40401` |

## 5. 测试数据来源

测试中没有依赖真实 A 模块接口，而是使用：

```text
MockSimulationProvider
```

该类会根据 `runId` 生成模拟仿真数据。测试使用不同 `runId` 来得到不同拥挤程度的仿真结果。

CSV 参数数据来自：

```text
data/task-a/restaurants.csv
data/task-a/windows.csv
data/task-a/dishes.csv
```

## 6. 测试执行方式

在后端工程目录执行：

```text
cd backend
mvn test
```

## 7. 最近一次测试结果

```text
Tests run: 10
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

## 8. 测试结论

B 模块核心业务逻辑测试通过。当前推荐、分流、策略对比以及主要异常处理均能按预期工作。
