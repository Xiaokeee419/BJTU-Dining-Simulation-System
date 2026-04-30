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

## Mock 与真实接口

默认使用前端 Mock 数据，适合独立开发和演示。

切换真实后端时，在 `frontend/.env.local` 中写入：

```text
VITE_USE_MOCK=false
VITE_API_BASE_URL=/api/v1
VITE_DEV_PROXY_TARGET=http://localhost:8080
```

此时 Vite 会把 `/api/v1/**` 代理到 `http://localhost:8080`。

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
