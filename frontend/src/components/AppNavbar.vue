<template>
  <header class="navbar">
    <div class="navbar-shell">
      <RouterLink to="/simulation" class="brand">
        <div class="brand-logo">BJ</div>
        <div class="brand-copy">
          <strong>BJTU Dining Simulation System</strong>
          <span>食堂仿真、规则分流与模拟退火优化</span>
        </div>
      </RouterLink>

      <nav class="nav-links">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-link"
          :class="{ active: route.path === item.to }"
        >
          <component :is="item.icon" class="nav-icon" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
    </div>
  </header>
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
.navbar {
  position: sticky;
  top: 0;
  z-index: 40;
  border-bottom: 1px solid rgb(226 232 240 / 90%);
  box-shadow: 0 10px 30px rgb(15 23 42 / 4%);
  backdrop-filter: blur(14px);
  background: rgb(255 255 255 / 94%);
}

.navbar-shell {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: center;
  gap: 28px;
  width: min(1440px, calc(100% - 64px));
  min-height: 72px;
  margin: 0 auto;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.brand-logo {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 2px solid #2563eb;
  border-radius: 50%;
  color: #2563eb;
  background: linear-gradient(180deg, #fff 0%, #edf4ff 100%);
  font-size: 12px;
  font-weight: 900;
}

.brand-copy {
  display: grid;
  gap: 4px;
}

.brand-copy strong {
  color: #0f172a;
  font-size: 17px;
}

.brand-copy span {
  color: #475569;
  font-size: 11px;
}

.nav-links {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 14px;
  border-radius: 999px;
  color: #475569;
  font-size: 14px;
  font-weight: 700;
}

.nav-link:hover,
.nav-link.active {
  color: #2563eb;
  background: #eff6ff;
}

.nav-icon {
  width: 19px;
  height: 19px;
}
</style>
