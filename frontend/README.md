# 就餐仿真控制台前端

成员 C 负责的仿真控制台与可视化展示模块。

## 技术栈

- Vue 3 + Vite
- Element Plus
- ECharts
- Pinia
- Axios

## 本地运行

```powershell
cd frontend
npm install
npm run dev
```

默认访问：

```text
http://127.0.0.1:5173
```

## 真实接口与 Mock

当前默认连接真实后端。综合展示页始终读取 CSV、仿真、分流对比和优化接口，不使用前端
Mock。旧页面仍可在独立开发时通过环境变量切换 Mock。

在 `frontend/.env.local` 中使用：

```text
VITE_USE_MOCK=false
VITE_API_BASE_URL=/api/v1
VITE_DEV_PROXY_TARGET=http://localhost:8080
```

此时 Vite 会把 `/api/v1/**` 代理到 `http://localhost:8080`。

综合展示页地址：

```text
http://127.0.0.1:5173/
```

该页面覆盖：

- CSV 数据规模与连续入流曲线
- baseline 仿真及窗口负载
- 分流建议、compare run 和前后指标
- 模拟退火温度、loss、当前参数、best 参数与迭代历史
- API 错误、runId、taskId 和旧数据混用风险调试面板

## 已覆盖的 C 模块验收项

- 用户画像配置
- 场景参数配置
- 运行仿真
- 时间轴播放、暂停、下一步和回到开始
- 餐厅人数、窗口排队和等待时间展示
- 人流与等待趋势图
- 餐厅、窗口、菜品推荐结果展示
- 分流建议展示
- 基准场景与对比场景比较

## 构建验证

```powershell
npm run build
```
