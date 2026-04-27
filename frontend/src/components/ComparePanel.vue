<template>
  <section class="panel compare-panel">
    <div class="panel-header">
      <h2 class="panel-title">场景对比</h2>
      <el-button v-if="comparison" size="small" @click="$emit('clear')">清空</el-button>
    </div>
    <div class="panel-body">
      <el-empty
        v-if="!baseRun && !comparison"
        :image-size="72"
        description="未设置基准场景"
      />
      <template v-else>
        <div class="run-row">
          <span>基准</span>
          <strong>{{ baseRun?.runId || '-' }}</strong>
        </div>
        <div class="run-row">
          <span>对比</span>
          <strong>{{ compareRun?.runId || '-' }}</strong>
        </div>
        <div v-if="comparison" class="delta-grid">
          <div class="delta-item">
            <span>平均等待变化</span>
            <strong :class="deltaClass(comparison.avgWaitDelta)">
              {{ signed(comparison.avgWaitDelta) }} 分
            </strong>
          </div>
          <div class="delta-item">
            <span>最大排队变化</span>
            <strong :class="deltaClass(comparison.maxQueueDelta)">
              {{ signed(comparison.maxQueueDelta) }} 人
            </strong>
          </div>
          <div class="delta-item">
            <span>高拥挤窗口变化</span>
            <strong :class="deltaClass(comparison.busyWindowCountDelta)">
              {{ signed(comparison.busyWindowCountDelta) }} 次
            </strong>
          </div>
        </div>
        <p v-if="comparison" class="conclusion">{{ comparison.conclusion }}</p>
      </template>
    </div>
  </section>
</template>

<script setup>
defineProps({
  baseRun: {
    type: Object,
    default: null,
  },
  compareRun: {
    type: Object,
    default: null,
  },
  comparison: {
    type: Object,
    default: null,
  },
})

defineEmits(['clear'])

function signed(value) {
  if (value > 0) return `+${value}`
  return String(value)
}

function deltaClass(value) {
  if (value < 0) return 'good'
  if (value > 0) return 'bad'
  return ''
}
</script>

<style scoped>
.run-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  color: #64748b;
}

.run-row strong {
  color: #0f172a;
}

.delta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 8px;
}

.delta-item {
  padding: 12px;
  border: 1px solid #e5eaf2;
  border-radius: 8px;
  background: #f8fafc;
}

.delta-item span {
  display: block;
  margin-bottom: 6px;
  color: #64748b;
  font-size: 13px;
}

.delta-item strong {
  color: #334155;
  font-size: 20px;
}

.delta-item strong.good {
  color: #15803d;
}

.delta-item strong.bad {
  color: #b91c1c;
}

.conclusion {
  margin: 14px 0 0;
  padding: 12px;
  border-radius: 8px;
  color: #365314;
  background: #ecfccb;
  line-height: 1.6;
}

@media (max-width: 780px) {
  .delta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
