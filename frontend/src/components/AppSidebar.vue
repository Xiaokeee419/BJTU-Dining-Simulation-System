<template>
  <aside class="app-sidebar">
    <RouterLink to="/" class="sidebar-brand" aria-label="返回仿真驾驶舱">
      <span class="sidebar-brand-mark" aria-hidden="true">
        <i v-for="index in 4" :key="index"></i>
      </span>
      <span class="sidebar-brand-copy">
        <strong>BJTU Simulation</strong>
        <small>人流动力学决策支持系统</small>
      </span>
    </RouterLink>

    <nav class="sidebar-nav" aria-label="主导航">
      <RouterLink
        v-for="item in navItems"
        :key="item.to"
        :to="item.to"
        class="sidebar-nav-item"
        :class="{ active: route.path === item.to }"
        :aria-current="route.path === item.to ? 'page' : undefined"
      >
        <component :is="item.icon" class="sidebar-nav-icon" />
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>

    <div class="sidebar-lab">
      <span class="sidebar-lab-line"></span>
      <strong>北京交通大学</strong>
      <small>人流动力学实验室</small>
    </div>
  </aside>
</template>

<script setup>
import { Connection, DataAnalysis, Grid, Histogram, Monitor, Setting } from '@element-plus/icons-vue'
import { RouterLink, useRoute } from 'vue-router'

const route = useRoute()

const navItems = [
  { to: '/', label: '综合展示', icon: Monitor },
  { to: '/cockpit', label: '仿真驾驶舱', icon: Grid },
  { to: '/flow', label: '人流快照', icon: Connection },
  { to: '/config', label: '场景实验台', icon: Setting },
  { to: '/statistics', label: '实验结果分析', icon: Histogram },
  { to: '/recommendation', label: '分流策略验证', icon: DataAnalysis },
]
</script>

<style scoped>
.app-sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 50;
  display: flex;
  width: var(--sidebar-width);
  flex-direction: column;
  padding: 24px 16px 20px;
  border-right: 1px solid var(--color-outline);
  background: rgb(250 248 255 / 97%);
  backdrop-filter: blur(14px);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 48px;
  padding: 0 8px;
}

.sidebar-brand-mark {
  display: grid;
  flex: 0 0 auto;
  grid-template-columns: repeat(2, 7px);
  grid-template-rows: repeat(2, 7px);
  gap: 3px;
  place-content: center;
  width: 40px;
  height: 40px;
  border-radius: 6px;
  background: var(--color-primary);
  box-shadow: 0 8px 18px rgb(0 24 66 / 16%);
}

.sidebar-brand-mark i {
  border: 1px solid #fff;
  border-radius: 1px;
}

.sidebar-brand-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.sidebar-brand-copy strong {
  color: var(--color-primary);
  font-size: 17px;
  line-height: 1.1;
}

.sidebar-brand-copy small {
  color: var(--color-subtle);
  font-size: 10px;
  line-height: 1.3;
}

.sidebar-nav {
  display: grid;
  gap: 6px;
  margin-top: 48px;
}

.sidebar-nav-item {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 48px;
  padding: 0 14px;
  border-radius: 6px;
  color: var(--color-on-surface-variant);
  font-size: 15px;
  font-weight: 650;
  transition:
    color 0.18s ease,
    background 0.18s ease,
    transform 0.18s ease;
}

.sidebar-nav-item:hover {
  color: var(--color-secondary);
  background: var(--color-surface-container);
}

.sidebar-nav-item.active {
  color: #fff;
  background: var(--color-secondary);
  box-shadow: 0 8px 18px rgb(1 96 168 / 16%);
}

.sidebar-nav-item:active {
  transform: scale(0.985);
}

.sidebar-nav-icon {
  width: 21px;
  height: 21px;
  flex: 0 0 auto;
}

.sidebar-lab {
  display: grid;
  gap: 4px;
  margin-top: auto;
  padding: 18px 10px 0;
  color: var(--color-on-surface-variant);
}

.sidebar-lab-line {
  height: 1px;
  margin: 0 -10px 14px;
  background: var(--color-outline);
}

.sidebar-lab strong {
  color: var(--color-primary);
  font-size: 12px;
}

.sidebar-lab small {
  font-size: 10px;
}

@media (max-width: 960px) {
  .app-sidebar {
    width: var(--sidebar-rail-width);
    padding-right: 10px;
    padding-left: 10px;
  }

  .sidebar-brand {
    justify-content: center;
    padding: 0;
  }

  .sidebar-brand-copy,
  .sidebar-nav-item span,
  .sidebar-lab {
    display: none;
  }

  .sidebar-nav-item {
    justify-content: center;
    padding: 0;
  }
}

@media (max-width: 640px) {
  .app-sidebar {
    inset: 0 0 auto;
    width: auto;
    height: 64px;
    flex-direction: row;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    border-right: 0;
    border-bottom: 1px solid var(--color-outline);
  }

  .sidebar-brand-mark {
    width: 38px;
    height: 38px;
  }

  .sidebar-nav {
    display: flex;
    flex: 1;
    justify-content: flex-end;
    gap: 4px;
    margin: 0;
  }

  .sidebar-nav-item {
    width: 42px;
    min-height: 42px;
  }
}
</style>
