<template>
  <section class="metrics-grid">
    <div class="metric-item wait">
      <span class="metric-label">平均等待</span>
      <strong>{{ metrics?.avgWaitMinutes ?? '-' }}</strong>
      <span class="metric-unit">分钟</span>
    </div>
    <div class="metric-item peak">
      <span class="metric-label">最长等待</span>
      <strong>{{ metrics?.maxWaitMinutes ?? '-' }}</strong>
      <span class="metric-unit">分钟</span>
    </div>
    <div class="metric-item queue">
      <span class="metric-label">最大排队</span>
      <strong>{{ metrics?.maxQueueLength ?? '-' }}</strong>
      <span class="metric-unit">人</span>
    </div>
    <div class="metric-item busy">
      <span class="metric-label">高拥挤窗口</span>
      <strong>{{ metrics?.busyWindowCount ?? '-' }}</strong>
      <span class="metric-unit">次</span>
    </div>
    <div class="metric-item served">
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
  position: relative;
  padding: 15px 16px 15px 18px;
  border: 1px solid #d9e0ea;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgb(23 32 51 / 7%);
  overflow: hidden;
}

.metric-item::before {
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  content: "";
  background: #64748b;
}

.metric-item.wait::before {
  background: #9f1239;
}

.metric-item.peak::before {
  background: #b45309;
}

.metric-item.queue::before {
  background: #0f766e;
}

.metric-item.busy::before {
  background: #7c2d12;
}

.metric-item.served::before {
  background: #166534;
}

.metric-label {
  display: block;
  color: #657084;
  font-size: 13px;
  font-weight: 700;
}

.metric-item strong {
  display: inline-block;
  margin-top: 8px;
  color: #172033;
  font-size: 28px;
  line-height: 1.1;
}

.metric-unit {
  margin-left: 4px;
  color: #657084;
  font-size: 13px;
  font-weight: 650;
}

@media (max-width: 920px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
