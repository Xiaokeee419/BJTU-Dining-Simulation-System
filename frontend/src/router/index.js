import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../pages/HomePage.vue'),
    },
    {
      path: '/flow',
      name: 'flow',
      component: () => import('../pages/RealTimeFlowPage.vue'),
    },
    {
      path: '/config',
      name: 'config',
      component: () => import('../pages/SimulationConfigPage.vue'),
    },
    {
      path: '/statistics',
      name: 'statistics',
      component: () => import('../pages/StatisticsPage.vue'),
    },
    {
      path: '/recommendation',
      name: 'recommendation',
      component: () => import('../pages/RecommendationPage.vue'),
    },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

export default router
