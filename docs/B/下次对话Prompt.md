# 下次对话 Prompt

下面这段可以直接复制到新的对话里：

```md
你现在在仓库 `e:\CS\SoftwareDesignTraining\BJTU-Dining-Simulation-System` 中工作，请直接基于当前代码状态继续，不要重复做项目背景分析。

当前关键信息：

- 当前分支：`feature/v0.4`
- 工作区状态：干净
- 最新提交：
  - `4092887 chore(v0.4): remove deprecated docs and helper scripts`
  - `7ec9b5f feat(v0.4): switch simulation seeds to rule-driven arrivals`

当前项目状态：

- `data/task-a/virtual_students.csv` 已删除三餐到达时刻列：
  - `breakfast_arrival_minute`
  - `lunch_arrival_minute`
  - `dinner_arrival_minute`
- 后端已经适配这次数据结构变化。
- 后端现在会在运行时读取：
  - `data/task-a/arrival_rules.csv`
  - `data/task-a/tag_mappings.csv`
- 到达时间逻辑已经从“学生自带时间 + 硬编码扰动”改成“`arrival_rules.csv` 驱动的分钟级到达分布”。
- 标签标准化已经开始从硬编码迁移到 `tag_mappings.csv`。
- 推荐侧旧的 `TagMatcher.java` 已删除，改为共用标签标准化服务。

本轮已改过的关键文件：

- `backend/src/main/java/com/bjtu/dining/common/TagNormalizationService.java`
- `backend/src/main/java/com/bjtu/dining/taska/service/SeedDataService.java`
- `backend/src/main/java/com/bjtu/dining/taska/service/ArrivalCurveGenerator.java`
- `backend/src/main/java/com/bjtu/dining/taska/service/SimulationService.java`
- `backend/src/main/java/com/bjtu/dining/recommendation/service/RecommendationService.java`
- `data/task-a/virtual_students.csv`
- `docs/B/v0.4_开发TODO.md`
- `docs/B/v0.4_开发日志_20260611.md`

当前可用状态：

- 后端在 `8080` 上可访问
- `GET /api/v1/presets/scenarios` 返回 `200`
- `mvn -q -DskipTests compile` 已通过

v0.4 主线目标：

1. 数据真源统一到 `data/task-a/*.csv`
2. 主链路收敛为：人流输入 -> 仿真 -> 分流建议 -> 参数优化 -> 可视化对比
3. 重点做“参数化分流策略 + 随机搜索/模拟退火优化”

下一步优先做这些，不要偏题：

1. 精简 DTO 和旧接口字段
   - 删除或弱化 `tasteTags`、`budgetMin`、`budgetMax`、`waitingToleranceMinutes`
   - 删除或弱化 `weatherFactor`、`eventFactor`、`closedWindowIds`
2. 增加到达曲线 / 分钟级指标接口
   - `POST /api/v1/simulations/arrival-curve-preview`
   - `GET /api/v1/simulations/{runId}/arrival-curve`
   - `GET /api/v1/simulations/{runId}/window-pressure`
   - `GET /api/v1/simulations/{runId}/minute-metrics`
3. 开始参数化分流策略
   - 重点改 `RecommendationService`
   - 提取 `pressure / sourceSelection / targetSelection / transferCount / acceptance` 参数组
4. 增加单次评估接口
   - `POST /api/v1/optimizations/evaluate`
5. 再接随机搜索和模拟退火
   - `POST /api/v1/optimizations/run`
   - `GET /api/v1/optimizations/{jobId}`
   - `GET /api/v1/optimizations/{jobId}/iterations`
   - `GET /api/v1/optimizations/{jobId}/best`

约束：

- 用 `apply_patch` 改文件
- 不要回滚已有提交
- 不要删除用户未明确要求恢复的内容
- 优先保持改动可编译、可运行、可验证

如果开始动代码，请先从接口和 DTO 收口，再进入“分流策略参数化 + evaluate 接口”。
```

相关文档：

- [v0.4_开发TODO.md](/e:/CS/SoftwareDesignTraining/BJTU-Dining-Simulation-System/docs/B/v0.4_开发TODO.md)
- [v0.4_开发日志_20260611.md](/e:/CS/SoftwareDesignTraining/BJTU-Dining-Simulation-System/docs/B/v0.4_开发日志_20260611.md)
