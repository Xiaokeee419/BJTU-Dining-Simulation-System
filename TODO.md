# TODO.md

仓库根目录长期维护清单。

当前阶段：`v0.4` 稳定化与产品化  
当前主线：`data/task-a/*.csv -> 仿真 -> 分流建议 -> 参数评估 -> 优化任务 -> 前端工作台`

---

## 已完成里程碑

- [x] 学生到达时刻与 `virtual_students.csv` 解耦
- [x] `arrival_rules.csv` 驱动分钟级到达曲线
- [x] `tag_mappings.csv` 驱动标签归一化
- [x] 分流策略参数化
- [x] 单次策略评估接口
- [x] 随机搜索 / 模拟退火优化接口
- [x] `v0.4` 前端工作台
- [x] 清理旧前端 mock/store/页面主链

---

## P0 稳定化

- [ ] 给优化链路补后端测试
  - `RecommendationService`
  - `DiversionStrategyEvaluator`
  - `OptimizationService`

- [ ] 给仿真链路补后端测试
  - `SeedDataService`
  - `ArrivalCurveGenerator`
  - `SimulationService`

- [ ] 做一轮前端工作台交互检查
  - 空状态
  - 错误提示
  - 优化任务轮询失败处理
  - 仿真结果未返回时的保护

- [ ] 清理并更新 `README`
  - 去掉旧 mock / 旧脚本说明
  - 改成当前 `v0.4` 启动方式和接口说明

- [ ] 更新 `docs/public/接口规范.md`
  - 同步新接口
  - 标明已下线旧接口

- [x] 修复 `durationMinutes / stepMinutes` 编辑过程中的 `400`
  - 当前后端强约束：`durationMinutes % stepMinutes == 0`
  - 当前前端实时预览会在用户先改时长、后改步长时触发 `400`
  - 当前前端表单也没有和后端校验对齐，且 HTTP 400 时通常只看到通用报错
  - 需要决定是放宽预览接口校验，还是把这两个参数改成原子更新

- [x] 降低 `stepMinutes` 对仿真结果的非预期放大影响
  - 后端已改为分钟级内部仿真，`stepMinutes` 不再通过批处理顺序改变系统动力学
  - 不再要求 `durationMinutes` 能被 `stepMinutes` 整除
  - `stepMinutes` 当前更接近展示/交互参数，后续如需保留需继续明确语义

- [ ] 收敛“哪些仿真参数真正影响人流曲线”的接口语义
  - 当前 `previewArrivalCurve` 会受 `mealPeriod/dayType/crowdLevel/weatherFactor/eventFactor/virtualUserCount/durationMinutes/randomSeed` 影响
  - 当前 `profile` 和 `stepMinutes` 不影响人流曲线形态
  - 其中 `stepMinutes` 只参与校验和后续仿真步进，不参与曲线生成
  - 需要决定是补文档，还是调整接口 / 页面表现，避免误导

- [ ] 纠正分流“估算接受人数”和仿真实际执行之间的统计偏差
  - 当前 `acceptedDiversionCount` 直接累加建议里的 `estimatedAcceptedCount`
  - 但后续 `maybeRedirectWindow` 与 `applyDiversionToExistingQueues` 并不会逐条回写真实转移人数
  - `advancedMetrics` 与优化 `loss` 现在可能把“估算收益”当成“实际收益”

- [ ] 对齐前端 `Mock` 开关与实际实现
  - `frontend/.env.example` 与 `frontend/README.md` 仍声明 `VITE_USE_MOCK`
  - 代码中并没有读取这个开关，当前始终直连 `/api/v1`
  - 不启动后端时，工作台初始化会直接失败

- [ ] 提升默认场景下的拥挤可视化强度
  - 当前 `LUNCH` 有 48 个可用窗口，总服务能力约 `83.51` 人/分钟
  - 实测 `800/BUSY` 与 `1200/EXTREME` 场景几乎不会出现 `overloadedWindow`
  - 需要收紧 `arrival_rules.csv` 的峰值集中度，或下调热点窗口 `service_rate_per_minute`
  - 当前 `dish.prep_time_minutes` 参与了菜品选择，但没有进入 `serveQueues` 的服务消耗计算，导致拥挤被低估

---

## P1 持久化与可维护性

- [ ] 将仿真结果从纯内存改为可持久化存储
  - 至少支持保存最近运行结果
  - 支持通过 `runId` 重查

- [ ] 将优化任务历史从纯内存改为可持久化存储
  - `job`
  - `iterations`
  - `best result`

- [ ] 给内存态 `runStore / cohortStore / OptimizationStore` 增加上限或清理策略
  - 当前仿真结果和优化任务会持续累积
  - 在持久化落地前，至少需要最近结果保留数、TTL 或手动清理机制

- [ ] 给优化任务增加取消能力
  - `cancel`
  - 任务状态收敛

- [ ] 将策略参数、loss 配置抽成统一配置层
  - 默认值集中管理
  - 更容易做版本化

---

## P2 仿真与策略质量

- [ ] 校准 `arrival_rules.csv`
  - 让峰值更贴近预期拥挤程度
  - 放大分流策略优化效果，但不失真

- [ ] 继续校准窗口拥挤模型
  - `service_rate_per_minute`
  - `prep_time_minutes`
  - `wait -> crowd level` 阈值

- [ ] 重新审视 loss function
  - 是否需要更强调 `extremeWindowMinutes`
  - 是否需要更强调 `queueImbalanceIndex`
  - 是否需要加入“跨餐厅分流惩罚”上限

- [ ] 评估当前参数空间是否过大
  - 合并冗余参数
  - 固定低敏感参数
  - 提高优化收敛性

---

## P3 前端产品化

- [ ] 拆分工作台页面体积
  - 优化图表相关 chunk
  - 做按需加载

- [ ] 增强优化结果展示
  - baseline vs best 指标对比
  - best 参数摘要
  - 每轮 accepted / rejected 标识

- [ ] 增强数据总览展示
  - 学生画像分布
  - arrival rules 更直观的峰值图
  - tag mappings 示例过滤

- [ ] 增强运行记录视图
  - 最近仿真列表
  - 最近优化任务列表

- [ ] 清理当前未接入页面的 API 与死代码
  - `compareDiversion` 已导入但未调用
  - `lossConfig` 已请求但未展示或参与交互
  - `/api/v1/presets/*`、`/api/v1/parameters/*` 目前没有工作台调用链

---

## P4 工程管理

- [ ] 持续维护根目录文档
  - `AGENTS.md`
  - `TODO.md`

- [ ] 形成发布节奏
  - `main`
  - `dev`
  - `feature/*`
  - 版本 tag

- [ ] 补部署说明
  - 本地启动
  - 前后端联调
  - 数据目录要求

- [ ] 统一文档编码和中文可读性
  - 避免 `README` / `docs` 在不同终端乱码

---

## 下次开发建议

1. 先补后端测试。
2. 再修 `duration/step` 与曲线预览语义问题。
3. 再增强默认场景下的拥挤程度。
4. 然后补持久化。
5. 最后继续做优化结果展示增强。
