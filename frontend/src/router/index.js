import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/simulation' },
    {
      path: '/simulation',
      name: 'simulation',
      component: () => import('../pages/SimulationFlowPage.vue'),
      meta: { title: '仿真运行与人流快照' },
    },
    {
      path: '/rule-diversion',
      name: 'rule-diversion',
      component: () => import('../pages/RuleDiversionPage.vue'),
      meta: { title: '规则分流效果验证' },
    },
    {
      path: '/annealing',
      name: 'annealing',
      component: () => import('../pages/AnnealingOptimizationPage.vue'),
      meta: { title: '模拟退火策略优化' },
    },
    {
      path: '/legacy-dashboard',
      name: 'legacy-dashboard',
      component: () => import('../pages/DiversionOptimizationDashboard.vue'),
      meta: { title: '综合展示（旧版）' },
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

router.afterEach((to) => {
  document.title = `${to.meta.title || '食堂仿真'} - BJTU Simulation`
})

export default router
