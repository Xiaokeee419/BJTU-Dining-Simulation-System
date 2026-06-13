# BJTU Dining Backend

当前后端同时提供仿真、推荐分流、策略对比、CSV 数据概览和参数优化接口。数据来源如下：

- 餐厅、窗口、菜品基础参数：读取仓库根目录 `data/task-a/*.csv`。
- 仿真结果：由 `SimulationService` 基于同一批 CSV 人群生成，并在内存中按 `runId` 保存。
- 分流 compare run：复用 baseline 的同一批虚拟人群进行重放。
- 优化任务：对真实分流参数执行模拟退火搜索，任务状态和迭代历史保存在内存中。

## 运行

环境要求：

- JDK 17
- Maven 3.9+

```bash
cd backend
mvn spring-boot:run
```

服务默认运行在：

```text
http://localhost:8080
```

## 测试

```bash
cd backend
mvn test
```

## 主要接口

```text
GET  /api/v1/data/overview
GET  /api/v1/data/flow-curves
POST /api/v1/simulations/run
GET  /api/v1/simulations/{runId}
POST /api/v1/recommendations/generate
POST /api/v1/recommendations/diversion
GET  /api/v1/recommendations/runs/{runId}
POST /api/v1/strategies/diversion-comparison
POST /api/v1/optimizations/run
GET  /api/v1/optimizations/{taskId}
GET  /api/v1/optimizations/{taskId}/iterations
GET  /api/v1/optimizations/{taskId}/best
```

优化任务和仿真结果当前不做持久化，服务重启后会清空。

示例请求：

```bash
curl -X POST http://localhost:8080/api/v1/recommendations/generate \
  -H "Content-Type: application/json" \
  -d "{\"runId\":10001,\"minute\":30,\"profile\":{\"userType\":\"STUDENT\",\"tasteTags\":[\"偏辣\",\"米饭\"],\"budgetMin\":10,\"budgetMax\":20,\"waitingToleranceMinutes\":10},\"limit\":3}"
```
