# B模块源代码说明文档：Repository 数据访问

## 1. 目录位置

```text
backend/src/main/java/com/bjtu/dining/recommendation/repository/
```

## 2. 目录职责

Repository 目录负责 B 模块的数据读取和临时存储。当前 MVP 阶段没有接入数据库，主要使用 CSV 文件和内存缓存。

## 3. 源码文件说明

### CsvSeedRepository.java

该类负责读取 A 模块整理出的 CSV 种子数据。

读取的数据文件：

```text
data/task-a/restaurants.csv
data/task-a/windows.csv
data/task-a/dishes.csv
```

主要功能：

| 方法 | 说明 |
| --- | --- |
| `restaurants` | 返回全部餐厅参数 |
| `windows` | 返回全部窗口参数 |
| `dishes` | 返回全部菜品参数 |
| `restaurant` | 根据餐厅 ID 查询餐厅参数 |
| `window` | 根据窗口 ID 查询窗口参数 |
| `windowsByRestaurant` | 根据餐厅 ID 查询所属窗口 |

加载流程：

```text
Spring 容器创建 CsvSeedRepository
-> 执行 @PostConstruct 标记的 load 方法
-> 自动定位 data/task-a 目录
-> 读取 restaurants.csv、windows.csv、dishes.csv
-> 转换为 RestaurantParameter、WindowParameter、DishParameter
-> 建立按 ID 查询的内存 Map
```

说明：

- 当前 CSV 解析采用简单逗号分隔，适用于当前项目种子数据格式。
- 如果后续 CSV 字段中出现复杂逗号或引号，应改用专业 CSV 解析库。

### RecommendationStore.java

该类负责在内存中保存已生成的推荐结果。

主要功能：

| 方法 | 说明 |
| --- | --- |
| `save` | 保存一次推荐结果 |
| `find` | 根据 `runId` 和可选 `minute` 查询推荐结果 |

存储方式：

```text
ConcurrentHashMap<String, RecommendationResult>
```

key 格式：

```text
runId:minute
```

查询规则：

- 如果传入 `minute`，精确查询该时间点的推荐结果。
- 如果不传 `minute`，返回该 `runId` 最近一次生成的推荐结果。

说明：

- 当前为 MVP 阶段内存存储，服务重启后数据会丢失。
- 后续如需持久化，可替换为数据库表或 Redis 缓存。

## 4. 与 Service 层关系

`RecommendationService` 通过 `CsvSeedRepository` 读取餐厅、窗口、菜品参数，通过 `RecommendationStore` 保存和查询推荐结果。

Repository 层不负责计算推荐分数，也不生成中文理由。
