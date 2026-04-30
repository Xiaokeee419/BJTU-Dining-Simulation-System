# B模块源代码说明文档：Service 业务逻辑

## 1. 目录位置

```text
backend/src/main/java/com/bjtu/dining/recommendation/service/
```

## 2. 目录职责

Service 层是 B 模块的核心，负责推荐分数计算、分流建议生成、策略对比计算、标签匹配和模拟仿真结果生成。

## 3. 源码文件说明

### RecommendationService.java

该类是 B 模块核心业务服务。

主要公开方法：

| 方法 | 说明 |
| --- | --- |
| `generate` | 生成餐厅、窗口、菜品推荐结果 |
| `getGenerated` | 查询内存中已生成的推荐结果 |
| `generateDiversion` | 生成分流建议 |
| `compareStrategies` | 对比两个仿真场景的 metrics 指标 |

推荐生成流程：

```text
校验 limit 和用户画像
-> 根据 runId 加载仿真结果
-> 选择指定 minute 或最后时间点
-> 读取餐厅、窗口、菜品参数
-> 分别计算餐厅、窗口、菜品推荐分数
-> 按分数降序排序并生成 rank
-> 生成中文 reason 和 diversionSuggestion
-> 保存到 RecommendationStore
```

餐厅推荐主要考虑：

- 餐厅内窗口平均等待时间。
- 窗口最严重拥挤等级。
- 用户偏好标签匹配度。
- 预算匹配度。
- 餐厅基础吸引力。

窗口推荐主要考虑：

- 窗口等待时间。
- 当前拥挤等级。
- 用户偏好标签匹配度。
- 预算匹配度。
- 窗口热度。

菜品推荐主要考虑：

- 菜品标签匹配度。
- 菜品价格是否在预算范围内。
- 窗口等待时间加菜品出餐时间。
- 菜品热度。
- 出餐时间惩罚。

分流建议生成流程：

```text
校验 targetCrowdLevel
-> 加载指定 runId 的仿真结果
-> 找出 BUSY 或 EXTREME 且超过目标拥挤等级的来源窗口
-> 找出等待更短、拥挤度不高于目标等级、营业中的目标窗口
-> 按等待差、标签相似度、拥挤度、服务速率计算目标优先级
-> 计算 suggestedUserCount
-> 生成中文 reason
```

策略对比流程：

```text
加载 baseRunId 和 compareRunId 对应的仿真结果
-> 读取两个结果的 EvaluationMetrics
-> 计算各项 delta
-> 根据等待时间、队列长度、拥挤窗口数、服务人数变化生成 conclusion
```

差值含义：

```text
delta = compare - base
```

例如 `avgWaitDelta < 0` 表示对比场景平均等待时间降低。

### MockSimulationProvider.java

该类是当前 B 模块使用的模拟仿真数据提供器。

作用：

- 在 A 模块真实接口未完成时，为 B 模块提供可复现的仿真结果。
- 根据 `runId` 生成不同拥挤程度的时间序列数据。
- 返回 `SimulationRunResult`，其中包含 `timePoints` 和 `metrics`。

当前行为：

- `runId` 为空或小于等于 0 时抛出 `40401`。
- 正数 `runId` 会生成一组内存仿真数据。
- 偶数和奇数 `runId` 会使用不同拥挤系数，便于策略对比测试。

后续联调说明：

真实 A 接口完成后，应将该类替换为真实数据源，或新增统一 `SimulationProvider` 接口，让 mock 和真实适配器共同实现。

### TagMatcher.java

该类负责标签归一化和标签匹配分数计算。

主要方法：

| 方法 | 说明 |
| --- | --- |
| `normalize(List<String> tags)` | 将用户输入标签列表归一化 |
| `normalize(String tags)` | 将窗口或菜品标签字符串归一化 |
| `matchScore` | 计算用户偏好标签与候选标签的匹配分数 |

标签归一化示例：

- 包含“辣”的标签统一为 `辣味`。
- 包含“米粉”“米线”“粉”的标签统一为 `粉面`。
- 包含“面”的标签统一为 `面食`。
- 包含“饭”的标签统一为 `米饭`。
- 包含“清淡”“少油”的标签统一为 `清淡`。

这样可以降低原始标签表述不一致对推荐结果的影响。

## 4. 错误处理

Service 层通过 `ApiException` 抛出业务异常。

常见错误码：

| code | 场景 |
| --- | --- |
| `40001` | 参数校验失败，例如 `limit` 超出范围、预算范围非法 |
| `40002` | 枚举值非法，例如 `targetCrowdLevel` 不合法 |
| `40400` | 推荐结果或评估指标不存在 |
| `40401` | 仿真运行记录不存在 |
| `40901` | 仿真尚未完成 |

## 5. 设计说明

当前推荐算法采用规则加权，不使用机器学习模型。这样实现简单、可解释性强，适合课程设计 MVP 阶段展示。
