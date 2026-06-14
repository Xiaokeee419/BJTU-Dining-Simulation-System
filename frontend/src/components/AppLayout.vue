<template>
  <div class="app-layout">
    <AppSidebar />
    <div class="app-workspace">
      <main class="app-content">
        <slot />
      </main>
      <footer class="app-footer">
        <span>© 北京交通大学人流动力学实验室</span>
        <span class="app-footer-status">
          <i></i>
          系统服务正常
        </span>
      </footer>
    </div>
  </div>
</template>

<script setup>
import AppSidebar from './AppSidebar.vue'
</script>
