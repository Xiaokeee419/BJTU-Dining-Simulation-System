<template>
  <section class="panel trend-panel">
    <div class="panel-header">
      <h2 class="panel-title">趋势图</h2>
      <span class="muted">{{ timePoints.length }} 个时间点</span>
    </div>
    <div class="panel-body chart-grid">
      <div ref="flowChartRef" class="chart"></div>
      <div ref="waitChartRef" class="chart"></div>
    </div>
  </section>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  timePoints: {
    type: Array,
    default: () => [],
  },
})

const flowChartRef = ref(null)
const waitChartRef = ref(null)
let flowChart
let waitChart

onMounted(() => {
  flowChart = echarts.init(flowChartRef.value)
  waitChart = echarts.init(waitChartRef.value)
  renderCharts()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  flowChart?.dispose()
  waitChart?.dispose()
})

watch(
  () => props.timePoints,
  () => nextTick(renderCharts),
  { deep: true },
)

function renderCharts() {
  if (!flowChart || !waitChart) return
  const labels = props.timePoints.map((point) => `${point.minute}分`)
  const restaurantNames = props.timePoints[0]?.restaurants?.map((restaurant) => restaurant.name) || []
  const flowSeries = restaurantNames.map((name) => ({
    name,
    type: 'line',
    smooth: true,
    symbolSize: 6,
    data: props.timePoints.map((point) => {
      const restaurant = point.restaurants.find((item) => item.name === name)
      return restaurant?.currentCount || 0
    }),
  }))

  flowChart.setOption({
    color: ['#2563eb', '#16a34a', '#d97706', '#7c3aed'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 42, right: 18, top: 42, bottom: 32 },
    xAxis: { type: 'category', data: labels, boundaryGap: false },
    yAxis: { type: 'value', name: '人数' },
    series: flowSeries,
  })

  waitChart.setOption({
    color: ['#dc2626', '#0891b2'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 42, right: 18, top: 42, bottom: 32 },
    xAxis: { type: 'category', data: labels, boundaryGap: false },
    yAxis: { type: 'value' },
    series: [
      {
        name: '平均等待',
        type: 'bar',
        data: props.timePoints.map(avgWaitMinutes),
      },
      {
        name: '最大排队',
        type: 'line',
        smooth: true,
        data: props.timePoints.map(maxQueueLength),
      },
    ],
  })
}

function avgWaitMinutes(point) {
  const windows = point.restaurants.flatMap((restaurant) => restaurant.windows)
  const total = windows.reduce((sum, window) => sum + window.waitMinutes, 0)
  return Math.round((total / windows.length) * 10) / 10
}

function maxQueueLength(point) {
  const windows = point.restaurants.flatMap((restaurant) => restaurant.windows)
  return Math.max(...windows.map((window) => window.queueLength))
}

function resizeCharts() {
  flowChart?.resize()
  waitChart?.resize()
}
</script>

<style scoped>
.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.chart {
  width: 100%;
  min-width: 0;
  height: 320px;
}

@media (max-width: 920px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
