<template>
  <div class="app-shell">
    <AppNavbar />
    <RouterView />
  </div>
</template>

<script setup>
import { RouterView } from 'vue-router'
import AppNavbar from './components/AppNavbar.vue'
</script>
