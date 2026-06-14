<template>
  <aside class="app-sidebar">
    <RouterLink to="/simulation" class="sidebar-brand" aria-label="返回仿真运行">
      <img
        class="sidebar-brand-mark"
        src="/bjtu-emblem.png"
        alt="北京交通大学校徽"
      />
      <span class="sidebar-brand-copy">
        <strong>BJTU Simulation</strong>
        <small>食堂排队仿真与分流优化</small>
      </span>
    </RouterLink>

    <div class="sidebar-flow-label">优化流程</div>
    <nav class="sidebar-nav" aria-label="主导航">
      <RouterLink
        v-for="(item, index) in navItems"
        :key="item.to"
        :to="item.to"
        class="sidebar-nav-item"
        :class="{ active: route.path === item.to }"
        :aria-current="route.path === item.to ? 'page' : undefined"
      >
        <span class="sidebar-step">{{ index + 1 }}</span>
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
import { DataAnalysis, Histogram, Monitor } from '@element-plus/icons-vue'
import { RouterLink, useRoute } from 'vue-router'

const route = useRoute()

const navItems = [
  { to: '/simulation', label: '仿真运行', icon: Monitor },
  { to: '/rule-diversion', label: '规则分流', icon: DataAnalysis },
  { to: '/annealing', label: '模拟退火', icon: Histogram },
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
  flex: 0 0 auto;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  object-fit: contain;
  filter: drop-shadow(0 6px 10px rgb(0 45 110 / 14%));
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

.sidebar-flow-label {
  margin: 44px 12px 10px;
  color: #8490a4;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.sidebar-nav {
  display: grid;
  gap: 8px;
}

.sidebar-nav-item {
  display: grid;
  grid-template-columns: 22px 22px 1fr;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 0 13px;
  border-radius: 7px;
  color: var(--color-on-surface-variant);
  font-size: 14px;
  font-weight: 700;
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

.sidebar-step {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 1px solid currentColor;
  border-radius: 50%;
  font-size: 10px;
  opacity: 0.76;
}

.sidebar-nav-icon {
  width: 20px;
  height: 20px;
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
  .sidebar-flow-label,
  .sidebar-nav-item > span:last-child,
  .sidebar-lab {
    display: none;
  }

  .sidebar-nav {
    margin-top: 48px;
  }

  .sidebar-nav-item {
    display: flex;
    justify-content: center;
    padding: 0;
  }

  .sidebar-step {
    display: none;
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
    width: 42px;
    height: 42px;
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
