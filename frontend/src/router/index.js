import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('../pages/DiversionOptimizationDashboard.vue'),
      meta: { title: '仿真与分流优化展示' },
    },
    {
      path: '/cockpit',
      name: 'home',
      component: () => import('../pages/HomePage.vue'),
      meta: { title: '仿真驾驶舱' },
    },
    {
      path: '/flow',
      name: 'flow',
      component: () => import('../pages/RealTimeFlowPage.vue'),
      meta: { title: '仿真人流快照' },
    },
    {
      path: '/config',
      name: 'config',
      component: () => import('../pages/SimulationConfigPage.vue'),
      meta: { title: '场景实验台' },
    },
    {
      path: '/statistics',
      name: 'statistics',
      component: () => import('../pages/StatisticsPage.vue'),
      meta: { title: '实验结果分析' },
    },
    {
      path: '/recommendation',
      name: 'recommendation',
      component: () => import('../pages/RecommendationPage.vue'),
      meta: { title: '分流策略验证' },
    },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

export default router
