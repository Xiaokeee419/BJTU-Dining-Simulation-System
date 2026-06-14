<template>
  <div class="app-layout">
    <AppSidebar />
    <div class="app-workspace">
      <main class="app-content">
        <slot />
      </main>
      <footer class="app-footer">
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
