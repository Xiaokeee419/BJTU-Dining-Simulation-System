<template>
  <section class="dashboard-chart">
    <header class="dashboard-chart-head">
      <div>
        <h3>{{ title }}</h3>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <slot name="extra" />
    </header>
    <div v-if="loading" class="chart-state">
      <el-skeleton :rows="4" animated />
    </div>
    <div v-else-if="error" class="chart-state chart-error">{{ error }}</div>
    <div v-else-if="empty" class="chart-state">{{ emptyText }}</div>
    <div v-else ref="chartRef" class="dashboard-chart-surface"></div>
  </section>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  option: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  empty: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无可展示数据' },
  height: { type: Number, default: 300 },
})

const chartRef = ref(null)
let chart = null

onMounted(() => {
  render()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})

watch(
  () => [props.option, props.loading, props.error, props.empty],
  () => nextTick(render),
  { deep: true },
)

function render() {
  if (props.loading || props.error || props.empty || !chartRef.value) {
    chart?.dispose()
    chart = null
    return
  }
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  chart.setOption(props.option, true)
}

function resize() {
  chart?.resize()
}
</script>

<style scoped>
.dashboard-chart {
  min-width: 0;
  border: 1px solid var(--color-outline);
  border-radius: var(--radius-panel);
  background: #fff;
  box-shadow: var(--shadow-card);
  overflow-anchor: none;
  overflow: hidden;
}

.dashboard-chart-head {
  display: flex;
  min-height: 66px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-outline);
}

.dashboard-chart-head h3 {
  margin: 0;
  color: var(--color-primary);
  font-size: 16px;
}

.dashboard-chart-head p {
  margin: 5px 0 0;
  color: var(--color-subtle);
  font-size: 12px;
  line-height: 1.5;
}

.dashboard-chart-surface,
.chart-state {
  height: v-bind("`${height}px`");
  min-height: v-bind("`${height}px`");
}

.chart-state {
  display: grid;
  align-items: center;
  padding: 24px;
  color: var(--color-subtle);
  text-align: center;
}

.chart-error {
  color: var(--color-danger);
  background: #fff7f7;
}
</style>
