<template>
  <section class="metrics-stack">
    <div class="metrics-grid">
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
    </div>

    <div class="metric-summary" :class="summary.level">
      <strong>{{ summary.title }}</strong>
      <span>{{ summary.text }}</span>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  metrics: {
    type: Object,
    default: null,
  },
})

const summary = computed(() => {
  const metrics = props.metrics
  if (!metrics) {
    return {
      level: 'idle',
      title: '等待仿真结果',
      text: '运行仿真后会根据平均等待、最大排队和拥挤窗口给出状态判断。',
    }
  }

  const avgWait = Number(metrics.avgWaitMinutes || 0)
  const maxQueue = Number(metrics.maxQueueLength || 0)
  const busy = Number(metrics.busyWindowCount || 0)
  const extreme = Number(metrics.extremeWindowCount || 0)
  const total = Number(metrics.totalVirtualUsers || 0)
  const served = Number(metrics.servedUserCount || 0)
  const servedRate = total ? Math.round((served / total) * 100) : null

  if (avgWait < 5 && maxQueue < 20 && extreme === 0) {
    return {
      level: 'good',
      title: '整体较顺畅',
      text: `平均等待 ${avgWait} 分钟，最大排队 ${maxQueue} 人，当前配置下窗口压力可控。${servedRate == null ? '' : `服务完成率约 ${servedRate}%。`}`,
    }
  }

  if (avgWait < 10 && maxQueue < 45) {
    return {
      level: 'warn',
      title: '存在局部排队',
      text: `已有 ${busy} 次高拥挤窗口记录，建议结合排队状态查看具体餐厅和窗口。`,
    }
  }

  return {
    level: 'danger',
    title: '高峰压力明显',
    text: `平均等待或最大排队偏高，建议降低客流、开放更多窗口，或调整分流策略后再做对比。`,
  }
})
</script>

<style scoped>
.metrics-stack {
  display: grid;
  gap: 10px;
}

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

.metric-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 10px 14px;
  border: 1px solid #d9e0ea;
  border-radius: 8px;
  color: #334155;
  background: #ffffff;
  box-shadow: 0 10px 24px rgb(23 32 51 / 5%);
}

.metric-summary strong {
  flex: 0 0 auto;
  color: #172033;
  font-size: 14px;
}

.metric-summary span {
  color: #657084;
  font-size: 13px;
  line-height: 1.55;
}

.metric-summary.good {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.metric-summary.warn {
  border-color: #fde68a;
  background: #fffbeb;
}

.metric-summary.danger {
  border-color: #fecaca;
  background: #fef2f2;
}

@media (max-width: 920px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-summary {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
