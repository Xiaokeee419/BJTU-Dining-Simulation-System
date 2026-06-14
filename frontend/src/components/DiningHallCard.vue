<template>
  <article class="hall-card" :class="`hall-card-${tone}`">
    <header class="hall-card-header">
      <strong>{{ name }}</strong>
      <span class="hall-status">{{ status }}</span>
    </header>

    <div class="hall-card-body">
      <div class="hall-count-row">
        <span>当前人数 / 容量</span>
        <strong>{{ currentCount }} / {{ capacity }}</strong>
      </div>

      <div class="hall-progress" aria-hidden="true">
        <i :style="{ width: `${boundedOccupancy}%` }"></i>
      </div>

      <dl class="hall-metrics">
        <div>
          <dt>座位占用率</dt>
          <dd>{{ occupancyRate }}%</dd>
        </div>
        <div>
          <dt>当前排队人数</dt>
          <dd>{{ queueCount }} 人</dd>
        </div>
        <div>
          <dt>平均等待</dt>
          <dd>{{ avgWait }} 分钟</dd>
        </div>
      </dl>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: {
    type: String,
    required: true,
  },
  currentCount: {
    type: Number,
    default: 0,
  },
  capacity: {
    type: Number,
    default: 0,
  },
  occupancyRate: {
    type: Number,
    default: 0,
  },
  queueCount: {
    type: Number,
    default: 0,
  },
  avgWait: {
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

const boundedOccupancy = computed(() => Math.min(100, Math.max(0, props.occupancyRate)))
</script>

<style scoped>
.hall-card {
  --hall-color: var(--color-success);
  --hall-header: #f1fbf5;
  min-width: 0;
  border: 1px solid var(--color-outline);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.hall-card-warning {
  --hall-color: var(--color-warning);
  --hall-header: #fff8ef;
}

.hall-card-danger {
  --hall-color: var(--color-danger);
  --hall-header: #fff4f2;
}

.hall-card-neutral {
  --hall-color: #64748b;
  --hall-header: #f3f4f7;
}

.hall-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 58px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-outline);
  background: var(--hall-header);
}

.hall-card-header strong {
  color: var(--color-primary);
  font-size: 17px;
}

.hall-status {
  padding: 4px 8px;
  border-radius: 3px;
  color: #fff;
  background: var(--hall-color);
  font-size: 11px;
  font-weight: 750;
}

.hall-card-body {
  display: grid;
  gap: 13px;
  padding: 17px 18px 18px;
}

.hall-count-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--color-subtle);
  font-size: 12px;
}

.hall-count-row strong {
  color: var(--color-on-surface);
  font-family: var(--font-data);
  font-size: 13px;
}

.hall-progress {
  height: 7px;
  border-radius: 999px;
  background: var(--color-surface-container);
  overflow: hidden;
}

.hall-progress i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--hall-color);
  transition: width 0.5s ease;
}

.hall-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.hall-metrics div {
  min-width: 0;
}

.hall-metrics dt {
  color: var(--color-subtle);
  font-size: 11px;
}

.hall-metrics dd {
  margin: 5px 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-data);
  font-size: 13px;
  font-weight: 650;
}

@media (max-width: 520px) {
  .hall-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
