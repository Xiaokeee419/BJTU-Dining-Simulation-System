<template>
  <header class="nav-shell">
    <div class="nav-brand">
      <span class="nav-kicker">BJTU Dining Simulation</span>
      <RouterLink to="/" class="nav-title">v0.4 Flow Console</RouterLink>
    </div>
    <nav class="nav-links">
      <RouterLink to="/" class="nav-link">概览</RouterLink>
      <RouterLink to="/workspace" class="nav-link">工作台</RouterLink>
    </nav>
  </header>
</template>

<script setup>
import { RouterLink } from 'vue-router'
</script>

<style scoped>
.nav-shell {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 28px;
  backdrop-filter: blur(18px);
  background: rgba(12, 18, 26, 0.68);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.nav-brand {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-kicker {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(215, 230, 255, 0.68);
}

.nav-title {
  color: #f7fbff;
  text-decoration: none;
  font-size: 22px;
  font-weight: 700;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 10px;
}

.nav-link {
  padding: 10px 14px;
  border-radius: 999px;
  color: rgba(238, 244, 255, 0.82);
  text-decoration: none;
  transition: 180ms ease;
}

.nav-link.router-link-active {
  color: #0a1320;
  background: linear-gradient(135deg, #ffd166, #ff8f5a);
}

@media (max-width: 720px) {
  .nav-shell {
    padding: 16px 18px;
  }

  .nav-title {
    font-size: 18px;
  }
}
</style>
