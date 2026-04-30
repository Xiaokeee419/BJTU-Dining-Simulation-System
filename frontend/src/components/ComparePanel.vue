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
        <div v-if="baseRun && compareRun" ref="chartRef" class="compare-chart"></div>
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
          <div class="delta-item">
            <span>极拥挤窗口变化</span>
            <strong :class="deltaClass(comparison.extremeWindowCountDelta)">
              {{ signed(comparison.extremeWindowCountDelta) }} 次
            </strong>
          </div>
          <div class="delta-item">
            <span>已服务人数变化</span>
            <strong :class="deltaClass(-comparison.servedUserCountDelta)">
              {{ signed(comparison.servedUserCountDelta) }} 人
            </strong>
          </div>
        </div>
        <p v-if="comparison" class="conclusion">{{ comparison.conclusion }}</p>
      </template>
    </div>
  </section>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
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

const chartRef = ref(null)
let chart

onMounted(() => {
  renderChart()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
})

watch(
  () => [props.baseRun, props.compareRun],
  () => nextTick(renderChart),
  { deep: true },
)

function signed(value) {
  if (value == null) return '-'
  if (value > 0) return `+${value}`
  return String(value)
}

function deltaClass(value) {
  if (value == null) return ''
  if (value < 0) return 'good'
  if (value > 0) return 'bad'
  return ''
}

function renderChart() {
  if (
    !chartRef.value ||
    !props.baseRun?.timePoints?.length ||
    !props.compareRun?.timePoints?.length
  ) {
    chart?.dispose()
    chart = null
    return
  }
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  const labels = props.baseRun.timePoints.map((point) => `${point.minute}分`)
  chart.setOption({
    color: ['#2563eb', '#16a34a', '#dc2626', '#d97706'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 42, right: 18, top: 42, bottom: 28 },
    xAxis: { type: 'category', data: labels, boundaryGap: false },
    yAxis: { type: 'value' },
    series: [
      {
        name: '基准平均等待',
        type: 'line',
        smooth: true,
        data: props.baseRun.timePoints.map(avgWaitMinutes),
      },
      {
        name: '对比平均等待',
        type: 'line',
        smooth: true,
        data: props.compareRun.timePoints.map(avgWaitMinutes),
      },
      {
        name: '基准最大排队',
        type: 'line',
        smooth: true,
        lineStyle: { type: 'dashed' },
        data: props.baseRun.timePoints.map(maxQueueLength),
      },
      {
        name: '对比最大排队',
        type: 'line',
        smooth: true,
        lineStyle: { type: 'dashed' },
        data: props.compareRun.timePoints.map(maxQueueLength),
      },
    ],
  })
}

function avgWaitMinutes(point) {
  const windows = point.restaurants.flatMap((restaurant) => restaurant.windows)
  if (!windows.length) return 0
  const total = windows.reduce((sum, window) => sum + window.waitMinutes, 0)
  return Math.round((total / windows.length) * 10) / 10
}

function maxQueueLength(point) {
  const windows = point.restaurants.flatMap((restaurant) => restaurant.windows)
  return windows.length ? Math.max(...windows.map((window) => window.queueLength)) : 0
}

function resizeChart() {
  chart?.resize()
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
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 10px;
  margin-top: 8px;
}

.compare-chart {
  width: 100%;
  height: 260px;
  margin: 12px 0;
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
