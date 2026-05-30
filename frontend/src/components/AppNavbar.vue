<template>
  <header class="navbar">
    <div class="navbar-shell">
      <RouterLink to="/" class="brand">
        <div class="brand-logo">BJ</div>
        <div class="brand-copy">
          <strong>BJTU Dining Simulation System</strong>
          <span>北京交通大学食堂仿真与分流决策平台</span>
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
import { DataAnalysis, HomeFilled, Histogram, Setting, UserFilled } from '@element-plus/icons-vue'
import { RouterLink, useRoute } from 'vue-router'

const route = useRoute()

const navItems = [
  { to: '/', label: '首页', icon: HomeFilled },
  { to: '/config', label: '参数配置', icon: Setting },
  { to: '/flow', label: '人流快照', icon: UserFilled },
  { to: '/statistics', label: '统计分析', icon: Histogram },
  { to: '/recommendation', label: '分流对比', icon: DataAnalysis },
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
  border-radius: 50%;
  color: #2563eb;
  border: 2px solid #2563eb;
  background: linear-gradient(180deg, #ffffff 0%, #edf4ff 100%);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.brand-copy {
  display: grid;
  gap: 4px;
}

.brand-copy strong {
  color: #0f172a;
  font-size: 17px;
  line-height: 1.2;
}

.brand-copy span {
  color: #475569;
  font-size: 11px;
  line-height: 1.2;
}

.nav-links {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  overflow: auto;
}

.nav-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 72px;
  padding: 0 8px;
  color: #475569;
  font-size: 15px;
  font-weight: 700;
  transition: color 0.18s ease;
}

.nav-link::after {
  position: absolute;
  right: 8px;
  bottom: 0;
  left: 8px;
  height: 3px;
  border-radius: 999px 999px 0 0;
  background: transparent;
  content: '';
}

.nav-link:hover,
.nav-link.active {
  color: #2563eb;
}

.nav-link.active::after {
  background: #2563eb;
}

.nav-icon {
  width: 19px;
  height: 19px;
}

@media (max-width: 1280px) {
  .navbar-shell {
    grid-template-columns: 1fr;
    gap: 8px;
    width: min(100%, calc(100% - 36px));
    padding: 8px 0;
  }

  .nav-links {
    justify-content: flex-start;
  }

  .nav-link {
    min-height: 44px;
    padding: 0 14px;
    border-radius: 999px;
  }

  .nav-link.active {
    background: #eff6ff;
  }

  .nav-link::after {
    display: none;
  }
}
</style>
