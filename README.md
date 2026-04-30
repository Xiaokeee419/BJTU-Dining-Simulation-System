# BJTU-Dining-Simulation-System

北京交通大学就餐仿真系统。当前开发重点是“前端仿真控制台 + 后端仿真/推荐接口”，成员 A 负责仿真数据生成、参数查询和仿真运行接口。

## 环境要求

- JDK 17
- Maven 3.9+
- Python 3.10+，仅用于成员 A 的离线数据整理脚本

## 启动后端

进入后端目录：

```bash
cd backend
```

启动 Spring Boot 服务：

```bash
mvn spring-boot:run
```

如果不想把 Maven 依赖下载到全局 `~/.m2`，也可以使用项目内本地仓库：

```bash
mvn -Dmaven.repo.local=../.m2/repository spring-boot:run
```

启动成功后，后端默认地址为：

```text
http://localhost:8080
```

## 验证成员 A 接口

查询用户画像预设：

```bash
curl http://localhost:8080/api/v1/presets/user-profiles
```

查询餐厅参数：

```bash
curl http://localhost:8080/api/v1/parameters/restaurants
```

运行一次小规模仿真：

```bash
curl -X POST http://localhost:8080/api/v1/simulations/run \
  -H "Content-Type: application/json" \
  -d '{
    "profile": {
      "userType": "STUDENT",
      "tasteTags": ["米饭", "辣味"],
      "budgetMin": 10,
      "budgetMax": 22,
      "waitingToleranceMinutes": 10
    },
    "scenario": {
      "mealPeriod": "LUNCH",
      "dayType": "WEEKDAY",
      "crowdLevel": "BUSY",
      "weatherFactor": 1.0,
      "eventFactor": 1.1,
      "closedWindowIds": [],
      "virtualUserCount": 80,
      "durationMinutes": 20,
      "stepMinutes": 10,
      "randomSeed": 20260427
    }
  }'
```

成功时会返回统一响应结构，`data` 中包含：

- `runId`：本次仿真运行 ID
- `timePoints`：每个时间点的餐厅和窗口排队状态
- `metrics`：平均等待时间、最大队列长度、拥挤窗口数量等评估指标

拿到 `runId` 后，可以继续查询：

```bash
curl http://localhost:8080/api/v1/simulations/{runId}
curl http://localhost:8080/api/v1/simulations/{runId}/timeline
curl http://localhost:8080/api/v1/simulations/{runId}/metrics
```

## 成员 A 数据说明

成员 A 的后端会读取以下 CSV 作为仿真种子数据：

```text
data/task-a/virtual_students.csv
data/task-a/restaurants.csv
data/task-a/windows.csv
data/task-a/dishes.csv
```

离线调试脚本：

```bash
python3 scripts/run_task_a_simulation.py
```

该脚本会生成：

```text
data/task-a/sample_simulation_result.json
```

这个 JSON 可作为前端尚未接通后端前的 mock 数据。
