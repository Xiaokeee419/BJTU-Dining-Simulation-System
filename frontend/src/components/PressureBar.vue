<template>
  <div class="pressure-row" :class="`pressure-row-${tone}`">
    <div class="pressure-row-head">
      <div>
        <strong>{{ name }}</strong>
        <span>{{ queueCount }} 人排队 · 预计等待 {{ waitMinutes }} 分钟</span>
      </div>
      <span class="pressure-state">{{ status }}</span>
    </div>
    <div class="pressure-track" aria-hidden="true">
      <i :style="{ width: `${boundedPercentage}%` }"></i>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: {
    type: String,
    required: true,
  },
  queueCount: {
    type: Number,
    default: 0,
  },
  waitMinutes: {
    type: Number,
    default: 0,
  },
  percentage: {
    type: Number,
    default: 0,
  },
  status: {
    type: String,
    default: '正常',
  },
  tone: {
    type: String,
    default: 'success',
  },
})

const boundedPercentage = computed(() => Math.min(100, Math.max(4, props.percentage)))
</script>

<style scoped>
.pressure-row {
  --pressure-color: var(--color-success);
  display: grid;
  gap: 10px;
}

.pressure-row-warning {
  --pressure-color: var(--color-warning);
}

.pressure-row-danger {
  --pressure-color: var(--color-danger);
}

.pressure-row-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.pressure-row-head > div {
  display: grid;
  gap: 4px;
}

.pressure-row-head strong {
  color: var(--color-primary);
  font-size: 16px;
}

.pressure-row-head span:not(.pressure-state) {
  color: var(--color-subtle);
  font-size: 12px;
}

.pressure-state {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 3px;
  color: #fff;
  background: var(--pressure-color);
  font-size: 11px;
  font-weight: 750;
}

.pressure-track {
  height: 12px;
  border-radius: 999px;
  background: var(--color-surface-container);
  overflow: hidden;
}

.pressure-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--pressure-color);
  transition: width 0.55s ease;
}
</style>
