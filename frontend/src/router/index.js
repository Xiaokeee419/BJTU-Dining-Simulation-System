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
      path: '/workspace',
      name: 'workspace',
      component: () => import('../pages/WorkspacePage.vue'),
    },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

export default router
