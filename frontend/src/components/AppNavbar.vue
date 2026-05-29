<template>
  <header class="navbar">
    <div class="navbar-shell">
      <RouterLink to="/" class="brand">
        <div class="brand-logo">
          <div class="brand-ring">BJ</div>
        </div>
        <div class="brand-copy">
          <strong>BJTU Dining Simulation System</strong>
          <span>北京交通大学食堂仿真系统</span>
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

      <div class="nav-side">
        <div class="notice">
          <Bell class="notice-icon" />
          <span>3</span>
        </div>
        <div class="admin-card">
          <div class="avatar">管</div>
          <span>管理员</span>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup>
import {
  Bell,
  DataAnalysis,
  HomeFilled,
  Histogram,
  Setting,
  UserFilled,
} from '@element-plus/icons-vue'
import { RouterLink, useRoute } from 'vue-router'

const route = useRoute()

const navItems = [
  { to: '/', label: '首页', icon: HomeFilled },
  { to: '/flow', label: '实时人流', icon: UserFilled },
  { to: '/config', label: '参数配置', icon: Setting },
  { to: '/statistics', label: '统计分析', icon: Histogram },
  { to: '/recommendation', label: '推荐结果', icon: DataAnalysis },
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
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 24px;
  width: min(1440px, calc(100% - 64px));
  margin: 0 auto;
  min-height: 72px;
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
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(180deg, #ffffff 0%, #edf4ff 100%);
  box-shadow:
    inset 0 0 0 1px rgb(37 99 235 / 18%),
    0 8px 18px rgb(37 99 235 / 10%);
}

.brand-ring {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 2px solid #2563eb;
  border-radius: 50%;
  color: #2563eb;
  font-size: 12px;
  letter-spacing: 0.08em;
  font-weight: 900;
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
  justify-content: center;
  gap: 10px;
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
  content: "";
}

.nav-link:hover {
  color: #2563eb;
}

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

.nav-side {
  display: flex;
  align-items: center;
  gap: 12px;
}

.notice {
  position: relative;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  color: #64748b;
  background: #f8fafc;
  box-shadow: inset 0 0 0 1px #e2e8f0;
}

.notice span {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  color: #ffffff;
  background: #f97316;
  font-size: 12px;
  font-weight: 800;
  line-height: 20px;
  text-align: center;
}

.notice-icon {
  width: 20px;
  height: 20px;
}

.admin-card {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding-left: 4px;
  color: #334155;
  font-weight: 700;
}

.avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  color: #2563eb;
  background: linear-gradient(180deg, #eff6ff 0%, #dbeafe 100%);
  box-shadow: inset 0 0 0 1px rgb(37 99 235 / 10%);
  font-size: 14px;
  font-weight: 900;
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
    overflow: auto;
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

  .nav-side {
    justify-content: flex-end;
  }
}
</style>
