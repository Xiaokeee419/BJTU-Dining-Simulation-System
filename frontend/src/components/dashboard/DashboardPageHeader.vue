<template>
  <header class="flow-page-header">
    <div>
      <p class="eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <div class="flow-page-header-side">
      <span class="flow-status-chip">{{ status }}</span>
      <div v-if="$slots.actions" class="flow-page-actions">
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>

<script setup>
defineProps({
  eyebrow: { type: String, default: '食堂排队仿真流程' },
  title: { type: String, required: true },
  description: { type: String, required: true },
  status: { type: String, default: '未运行仿真' },
})
</script>
