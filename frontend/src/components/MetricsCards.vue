<template>
  <section class="metrics-grid">
    <div class="metric-item">
      <span class="metric-label">平均等待</span>
      <strong>{{ metrics?.avgWaitMinutes ?? '-' }}</strong>
      <span class="metric-unit">分钟</span>
    </div>
    <div class="metric-item">
      <span class="metric-label">最长等待</span>
      <strong>{{ metrics?.maxWaitMinutes ?? '-' }}</strong>
      <span class="metric-unit">分钟</span>
    </div>
    <div class="metric-item">
      <span class="metric-label">最大排队</span>
      <strong>{{ metrics?.maxQueueLength ?? '-' }}</strong>
      <span class="metric-unit">人</span>
    </div>
    <div class="metric-item">
      <span class="metric-label">高拥挤窗口</span>
      <strong>{{ metrics?.busyWindowCount ?? '-' }}</strong>
      <span class="metric-unit">次</span>
    </div>
    <div class="metric-item">
      <span class="metric-label">已服务</span>
      <strong>{{ metrics?.servedUserCount ?? '-' }}</strong>
      <span class="metric-unit">人</span>
    </div>
  </section>
</template>

<script setup>
defineProps({
  metrics: {
    type: Object,
    default: null,
  },
})
</script>

<style scoped>
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.metric-item {
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid #d7dde8;
  border-radius: 8px;
  background: #ffffff;
}

.metric-label {
  display: block;
  color: #64748b;
  font-size: 13px;
}

.metric-item strong {
  display: inline-block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 26px;
  line-height: 1.1;
}

.metric-unit {
  margin-left: 4px;
  color: #64748b;
  font-size: 13px;
}

@media (max-width: 920px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
