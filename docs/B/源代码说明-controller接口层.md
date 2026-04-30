# B模块源代码说明文档：Controller 接口层

## 1. 目录位置

```text
backend/src/main/java/com/bjtu/dining/recommendation/controller/
```

## 2. 目录职责

Controller 层负责接收前端或其他模块发来的 HTTP 请求，调用 Service 层完成业务计算，并使用统一响应结构 `ApiResponse` 返回结果。

该层不直接编写推荐算法，也不直接读取 CSV 数据。

## 3. 源码文件说明

### RecommendationController.java

接口基础路径：

```text
/api/v1/recommendations
```

负责推荐和分流相关接口。

包含的方法：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `generate` | `POST /generate` | 调用 `RecommendationService.generate` 生成推荐结果 |
| `getByRunId` | `GET /runs/{runId}` | 调用 `RecommendationService.getGenerated` 查询已生成推荐 |
| `diversion` | `POST /diversion` | 调用 `RecommendationService.generateDiversion` 生成分流建议 |

输入对象：

- `RecommendationGenerateRequest`
- `DiversionRequest`

输出对象：

- `RecommendationResult`
- `DiversionResult`

说明：

- 使用 `@Valid` 触发请求参数校验。
- 使用 `@CrossOrigin(origins = "http://localhost:5173")` 允许前端本地开发端口访问。
- 成功响应统一包装为 `ApiResponse.ok(...)`。

### StrategyController.java

接口基础路径：

```text
/api/v1/strategies
```

负责策略对比接口。

包含的方法：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `compare` | `POST /compare` | 调用 `RecommendationService.compareStrategies` 对比两个仿真场景 |

输入对象：

- `StrategyCompareRequest`

输出对象：

- `StrategyComparisonResult`

说明：

- 请求体中必须包含 `baseRunId` 和 `compareRunId`。
- 返回平均等待时间、最大排队长度、拥挤窗口数、极端拥挤窗口数和已服务人数的差值。
- 返回中文 `conclusion`，可直接给前端展示。

## 4. 异常处理

Controller 不直接捕获业务异常。Service 层抛出的 `ApiException` 会由公共类：

```text
backend/src/main/java/com/bjtu/dining/common/GlobalExceptionHandler.java
```

统一转换为接口错误响应。
