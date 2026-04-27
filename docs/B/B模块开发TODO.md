# B模块开发TODO

适用成员：成员 B

负责模块：分流策略与推荐算法模块

参考文档：

- `docs/public/新版分工方案.md`
- `docs/public/接口规范.md`
- `docs/public/分支约定.md`

## 1. 模块目标

成员 B 负责基于成员 A 生成的仿真结果，计算餐厅推荐、窗口推荐、菜品推荐、分流建议和场景对比结论。

B 不负责生成原始人流数据，不负责维护完整仿真时间序列，也不负责前端图表展示。

## 2. 建议功能分支

建议按以下功能分支推进：

```text
feature/b-recommendation-api
feature/b-diversion-strategy
feature/b-strategy-compare
```

如果开发时间较紧，也可以合并为两个分支：

```text
feature/b-recommendation-api
feature/b-strategy-tools
```

## 3. 开发前准备

- [ ] 确认后端工程目录和技术栈。
- [ ] 确认统一响应结构是否已有公共封装。
- [ ] 确认成员 A 的仿真结果查询接口是否已可用。
- [ ] 确认餐厅、窗口、菜品参数查询接口是否已可用。
- [ ] 确认 B 模块代码包名、Controller 路径和 Service 分层规范。

## 4. 数据依赖

B 需要从 A 或基础参数接口读取以下数据：

- [ ] `runId` 对应的一次完整仿真结果。
- [ ] 指定 `minute` 的餐厅人流快照。
- [ ] 指定 `minute` 的窗口排队长度、服务中人数、等待时间。
- [ ] 餐厅和窗口拥挤度 `crowdLevel`。
- [ ] 仿真评估指标 `metrics`。
- [ ] 餐厅参数：名称、位置、容量、营业状态等。
- [ ] 窗口参数：所属餐厅、服务速率、营业状态等。
- [ ] 菜品参数：所属窗口、价格、标签、热门度、出餐时间等。

## 5. 推荐接口

分支建议：`feature/b-recommendation-api`

### 5.1 生成推荐结果

接口：

```text
POST /api/v1/recommendations/generate
```

TODO：

- [ ] 定义请求 DTO：`runId`、`minute`、`profile`、`limit`。
- [ ] 校验 `runId` 是否存在。
- [ ] 校验仿真运行状态是否已完成。
- [ ] 校验 `limit`，默认 3，最大 10。
- [ ] 不传 `minute` 时默认使用最后一个时间点。
- [ ] 读取指定时间点的仿真快照。
- [ ] 读取菜品、窗口、餐厅基础参数。
- [ ] 计算餐厅推荐分数。
- [ ] 计算窗口推荐分数。
- [ ] 计算菜品推荐分数。
- [ ] 按分数降序排序并生成 `rank`。
- [ ] 为每条推荐结果生成中文 `reason`。
- [ ] 返回餐厅、窗口、菜品推荐列表。
- [ ] 返回 `diversionSuggestion`。
- [ ] 生成 `generatedAt`。
- [ ] 保存或缓存本次推荐结果，供查询接口使用。

### 5.2 查询已生成推荐结果

接口：

```text
GET /api/v1/recommendations/runs/{runId}
```

TODO：

- [ ] 根据 `runId` 查询最近一次推荐结果。
- [ ] 支持按 `minute` 过滤。
- [ ] 如果没有已生成结果，返回明确错误或按约定重新生成。
- [ ] 返回结构与生成推荐接口保持一致。

## 6. 推荐算法规则

TODO：

- [ ] 设计餐厅评分规则。
- [ ] 设计窗口评分规则。
- [ ] 设计菜品评分规则。
- [ ] 统一分数范围，建议使用 `0-100`。
- [ ] 对关闭餐厅、关闭窗口、不可用菜品进行过滤或降权。
- [ ] 对超过用户等待容忍度的窗口进行降权。
- [ ] 对预算范围内的菜品加分。
- [ ] 对口味标签匹配的菜品加分。
- [ ] 对热门度较高的菜品加分。
- [ ] 对出餐时间较长的菜品降权。
- [ ] 对拥挤度高的餐厅或窗口降权。

推荐评分可以先采用规则加权，不需要机器学习模型。

## 7. 分流建议接口

分支建议：`feature/b-diversion-strategy`

接口：

```text
POST /api/v1/recommendations/diversion
```

TODO：

- [ ] 定义请求 DTO：`runId`、`minute`、`targetCrowdLevel`。
- [ ] 读取指定时间点的餐厅和窗口状态。
- [ ] 找出拥挤或极端拥挤的来源窗口。
- [ ] 找出等待时间较短、拥挤度较低、营业中的目标窗口。
- [ ] 尽量匹配相同或相近口味标签的窗口。
- [ ] 计算建议分流人数 `suggestedUserCount`。
- [ ] 生成分流原因 `reason`。
- [ ] 返回 `suggestions` 列表。
- [ ] 没有合适分流目标时，返回空列表和可解释原因。

## 8. 场景对比接口

分支建议：`feature/b-strategy-compare`

接口：

```text
POST /api/v1/strategies/compare
```

TODO：

- [ ] 定义请求 DTO：`baseRunId`、`compareRunId`。
- [ ] 查询两个仿真运行的 `metrics`。
- [ ] 计算 `avgWaitDelta`。
- [ ] 计算 `maxQueueDelta`。
- [ ] 计算 `busyWindowCountDelta`。
- [ ] 计算 `extremeWindowCountDelta`。
- [ ] 计算 `servedUserCountDelta`。
- [ ] 生成中文 `conclusion`。
- [ ] 处理任一 `runId` 不存在的错误。
- [ ] 处理仿真未完成的错误。

## 9. 错误处理

TODO：

- [ ] `40401`：`runId` 不存在。
- [ ] `40901`：仿真尚未完成。
- [ ] `40001`：参数校验失败，例如 `limit` 超出范围。
- [ ] `40002`：枚举值非法，例如 `targetCrowdLevel` 非法。
- [ ] 所有接口成功时返回统一结构，`code = 0`。
- [ ] 所有错误响应包含明确 `message`。

## 10. 联调检查

TODO：

- [ ] C 能调用 `POST /api/v1/recommendations/generate` 并展示推荐结果。
- [ ] C 能展示餐厅、窗口、菜品三类推荐。
- [ ] C 能直接展示 B 返回的中文推荐理由。
- [ ] C 能展示分流建议。
- [ ] C 能调用 `POST /api/v1/strategies/compare` 展示场景对比结论。
- [ ] 推荐结果会随不同 `runId`、`minute`、用户画像变化。

## 11. 验收标准

- [ ] 可以基于 A 的仿真结果生成餐厅、窗口和菜品推荐。
- [ ] 推荐分数和排序具有基本合理性。
- [ ] 推荐理由具备可解释性，可以直接展示给前端。
- [ ] 可以输出分流建议。
- [ ] 可以比较两个仿真场景的效果差异。
- [ ] 接口路径、字段命名、响应结构符合 `docs/public/接口规范.md`。
- [ ] 合并前项目可以启动，B 模块核心接口可用。

## 12. 非B模块范围

以下内容不属于 B 模块：

- 原始虚拟就餐者生成。
- 原始排队时间序列生成。
- 仿真时间片维护。
- 前端页面开发。
- 图表绘制。
- 餐厅、窗口、菜品基础数据维护。
- 登录、钱包、订单等旧业务功能。
