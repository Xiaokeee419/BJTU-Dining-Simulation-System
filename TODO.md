# TODO.md

本文件是仓库根目录的长期维护 TODO。

规则：

- 只保留“当前仍值得做”的事项
- 已完成的大阶段只保留简短里程碑，不在这里堆积细节
- 每次较大开发结束后，更新状态和优先级

---

## 当前阶段

当前阶段：`v0.4 稳定化与产品化`

当前主线：

`data/task-a/*.csv -> 仿真 -> 分流建议 -> 参数评估 -> 优化任务 -> 前端工作台`

---

## 已完成里程碑

- [x] 学生到达时刻从 `virtual_students.csv` 解耦
- [x] `arrival_rules.csv` 驱动分钟级到达曲线
- [x] `tag_mappings.csv` 驱动标签归一化
- [x] 分流策略参数化
- [x] 单次策略评估接口
- [x] 随机搜索 / 模拟退火优化接口
- [x] v0.4 前端工作台
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

- [ ] 补一轮前端工作台交互检查
  - 空状态
  - 错误提示
  - 优化任务轮询失败处理
  - 仿真结果未返回时的保护

- [ ] 清理和更新 README
  - 去掉旧 mock / 旧脚本说明
  - 改成当前 v0.4 启动方式和接口说明

- [ ] 更新 `docs/public/接口规范.md`
  - 同步新接口
  - 标明已下线旧接口

---

## P1 持久化与可维护性

- [ ] 把仿真结果从纯内存改为可持久化存储
  - 至少支持保存最近运行结果
  - 支持通过 `runId` 重查

- [ ] 把优化任务历史从纯内存改为可持久化存储
  - `job`
  - `iterations`
  - `best result`

- [ ] 给优化任务增加取消能力
  - `cancel`
  - 任务状态收敛

- [ ] 把策略参数、loss 配置抽成统一配置层
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
  - `wait -> crowd level` 的阈值

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
  - 继续做按需加载

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

---

## P4 工程管理

- [ ] 固化根目录长期文档
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
  - 避免 README / docs 在不同终端乱码

---

## 下一次开发建议

如果下一次继续做代码，建议顺序：

1. 先补后端测试
2. 再补持久化
3. 再继续调 loss 和参数空间
4. 最后再做前端展示增强
