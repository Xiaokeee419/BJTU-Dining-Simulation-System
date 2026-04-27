# BJTU Dining Backend

当前后端工程先实现成员 B 的推荐接口，数据来源如下：

- 餐厅、窗口、菜品基础参数：读取仓库根目录 `data/task-a/*.csv`。
- 仿真结果：`MockSimulationProvider` 按 `runId` 生成可复现的内存仿真快照，后续成员 A 的接口完成后替换这一层。

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

## 已实现接口

```text
POST /api/v1/recommendations/generate
GET  /api/v1/recommendations/runs/{runId}
```

示例请求：

```bash
curl -X POST http://localhost:8080/api/v1/recommendations/generate \
  -H "Content-Type: application/json" \
  -d "{\"runId\":10001,\"minute\":30,\"profile\":{\"userType\":\"STUDENT\",\"tasteTags\":[\"偏辣\",\"米饭\"],\"budgetMin\":10,\"budgetMax\":20,\"waitingToleranceMinutes\":10},\"limit\":3}"
```
