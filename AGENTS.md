# AGENTS.md

## 1. 作用

本文件用于长期管理 `BJTU-Dining-Simulation-System` 项目，给后续开发者或 AI agent 提供统一的项目约束、主链说明和协作规则。

如果后续代码、接口、目录结构或开发重点发生明显变化，需要同步更新本文件。

## 2. 当前项目目标

当前项目主线不是旧的“个性化推荐页面集合”，而是：

`data/task-a/*.csv -> 仿真 -> 分流建议 -> 参数评估 -> 随机搜索/模拟退火优化 -> 前端可视化工作台`

当前有效版本基线为 `v0.4`。

## 3. 仓库结构

- `backend/`
  - Spring Boot 后端
  - 核心模块：
    - `taska/`：仿真、种子数据、时间线、指标
    - `recommendation/`：分流建议、策略比较、优化任务
    - `common/`：异常、统一响应、标签归一化

- `frontend/`
  - Vue 3 + Element Plus + ECharts
  - 当前主页面：
    - `src/pages/HomePage.vue`
    - `src/pages/WorkspacePage.vue`
  - 当前 API 入口：
    - `src/api/v04.js`

- `data/task-a/`
  - 当前唯一真源数据目录
  - 核心 CSV：
    - `virtual_students.csv`
    - `arrival_rules.csv`
    - `restaurants.csv`
    - `windows.csv`
    - `dishes.csv`
    - `tag_mappings.csv`

- `docs/`
  - 开发计划、日志、公共说明

## 4. 当前架构约束

### 4.1 数据真源

- 仿真输入必须优先来自 `data/task-a/*.csv`
- 不要重新引入硬编码人流曲线
- 不要重新把学生到达时刻绑回 `virtual_students.csv`

### 4.2 当前有效后端链路

- 数据概览：
  - `/api/v1/data/*`

- 仿真：
  - `/api/v1/simulations/run`
  - `/api/v1/simulations/run-with-diversion`
  - `/api/v1/simulations/{runId}/arrival-curve`
  - `/api/v1/simulations/{runId}/minute-metrics`
  - `/api/v1/simulations/{runId}/window-pressure`
  - `/api/v1/simulations/{runId}/advanced-metrics`

- 分流策略：
  - `/api/v1/recommendations/diversion`
  - `/api/v1/strategies/default-parameters`
  - `/api/v1/strategies/loss-config`
  - `/api/v1/strategies/diversion-comparison`

- 优化：
  - `/api/v1/optimizations/evaluate`
  - `/api/v1/optimizations/run`
  - `/api/v1/optimizations/{jobId}`
  - `/api/v1/optimizations/{jobId}/iterations`
  - `/api/v1/optimizations/{jobId}/best`

### 4.3 当前状态性限制

- 仿真结果和优化任务仍是内存存储
- 后端重启后：
  - `runStore`
  - `cohortStore`
  - `OptimizationStore`
  都会丢失

这不是 bug，但它是当前版本的明确限制

## 5. 当前关键文件

### 后端核心

- `backend/src/main/java/com/bjtu/dining/taska/service/SeedDataService.java`
- `backend/src/main/java/com/bjtu/dining/taska/service/ArrivalCurveGenerator.java`
- `backend/src/main/java/com/bjtu/dining/taska/service/SimulationService.java`
- `backend/src/main/java/com/bjtu/dining/common/TagNormalizationService.java`
- `backend/src/main/java/com/bjtu/dining/recommendation/service/RecommendationService.java`
- `backend/src/main/java/com/bjtu/dining/recommendation/service/DiversionStrategyEvaluator.java`
- `backend/src/main/java/com/bjtu/dining/recommendation/service/OptimizationService.java`
- `backend/src/main/java/com/bjtu/dining/recommendation/service/RandomSearchOptimizer.java`
- `backend/src/main/java/com/bjtu/dining/recommendation/service/SimulatedAnnealingOptimizer.java`

### 前端核心

- `frontend/src/pages/WorkspacePage.vue`
- `frontend/src/api/v04.js`
- `frontend/src/components/EChartPanel.vue`
- `frontend/src/router/index.js`
- `frontend/src/style.css`

## 6. 开发优先级规则

后续开发优先级按下面顺序判断：

1. 不破坏当前 `v0.4` 主链可运行性
2. 继续强化“仿真拥挤 + 参数化分流 + 优化过程可视化”
3. 优先补稳定性、测试、持久化和文档
4. 不优先恢复已经下线的旧推荐页面链路

## 7. 禁止回退的方向

- 不要重新依赖旧的前端 mock/store 体系
- 不要恢复旧的 `/recommendations/generate` 作为主功能链
- 不要把策略参数重新写回方法内部硬编码常量
- 不要让 `data/task-a` 之外的临时脚本重新变成运行主依赖

## 8. 提交前检查

如果改了后端：

- 运行 `cd backend && mvn -q -DskipTests compile`

如果改了前端：

- 运行 `cd frontend && npm run build`

如果改了接口或流程：

- 至少做一次接口级 smoke test
- 确认前端工作台主流程没有直接报错

## 9. 文档更新规则

发生以下情况时，必须同步更新文档：

- 接口变化：更新 `docs/public/接口规范.md`
- 开发阶段变化：更新根目录 `TODO.md`
- 里程碑实现完成：更新 `docs/B/v0.4_开发日志_*.md`
- 项目主线变化：更新本文件 `AGENTS.md`

## 10. Git 约定

- 稳定分支：`main`
- 集成分支：`dev`
- 功能分支：`feature/*`

提交信息建议格式：

`type(scope): message`

示例：

- `feat(v0.4): add optimization evaluate api`
- `fix(simulation): correct diversion acceptance handling`
- `docs(project): refresh root todo`

## 11. 下一阶段建议方向

- 优化任务持久化
- 后端自动化测试
- 前端包体继续拆分
- 仿真与策略指标校准
- README 与公共文档统一到 v0.4 现状
