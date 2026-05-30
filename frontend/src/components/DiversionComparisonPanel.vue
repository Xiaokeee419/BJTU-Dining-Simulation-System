<template>
  <div v-if="hasComparison" class="panel-stack">
    <section class="run-strip">
      <span>Baseline Run #{{ comparison.baseRunId }}</span>
      <span>Compare Run #{{ comparison.compareRunId }}</span>
    </section>

    <section class="metric-grid">
      <article v-for="item in metricCards" :key="item.key" class="metric-card">
        <span class="metric-label">{{ item.label }}</span>
        <strong class="metric-value">{{ item.base }}</strong>
        <span class="metric-sub">未分流</span>
        <strong class="metric-value compare">{{ item.compare }}</strong>
        <span class="metric-sub">分流后</span>
        <span class="metric-delta" :class="item.deltaClass">{{ item.deltaText }}</span>
      </article>
    </section>

    <section class="chart-grid">
      <div class="chart-card">
        <div class="chart-head">
          <h3>核心指标对比</h3>
          <p>未分流 vs 分流后</p>
        </div>
        <div ref="compareChartRef" class="chart-surface"></div>
      </div>

      <div class="chart-card">
        <div class="chart-head">
          <h3>改善幅度</h3>
          <p>负值代表等待和拥挤指标下降</p>
        </div>
        <div ref="deltaChartRef" class="chart-surface"></div>
      </div>
    </section>

    <section v-if="hasTrend" class="chart-card trend-card">
      <div class="chart-head">
        <h3>趋势对比</h3>
        <p>总排队人数与总负载人数随时间变化</p>
      </div>
      <div ref="trendChartRef" class="chart-surface trend-surface"></div>
    </section>

    <section class="conclusion-card">
      <span class="conclusion-label">策略结论</span>
      <p>{{ comparison.conclusion }}</p>
    </section>
  </div>
  <EmptyState
    v-else
    title="尚未运行分流对比"
    description="请先保存 baseline，并点击运行分流对比。只有同时拿到 baseRunId 和 compareRunId 时，页面才会展示真实策略对比。"
  />
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import EmptyState from './EmptyState.vue'
import { totalCurrentCount, totalQueueLength } from '../utils/simulationStats'

const props = defineProps({
  comparison: {
    type: Object,
    default: null,
  },
  baseMetrics: {
    type: Object,
    default: null,
  },
  compareMetrics: {
    type: Object,
    default: null,
  },
  baselineRun: {
    type: Object,
    default: null,
  },
  compareRun: {
    type: Object,
    default: null,
  },
})

const compareChartRef = ref(null)
const deltaChartRef = ref(null)
const trendChartRef = ref(null)
let compareChart
let deltaChart
let trendChart

const hasComparison = computed(
  () =>
    Boolean(
      props.comparison &&
        props.baseMetrics &&
        props.compareMetrics &&
        props.comparison.baseRunId &&
        props.comparison.compareRunId,
    ),
)

const hasTrend = computed(
  () =>
    hasComparison.value &&
    Array.isArray(props.baselineRun?.timePoints) &&
    props.baselineRun.timePoints.length &&
    Array.isArray(props.compareRun?.timePoints) &&
    props.compareRun.timePoints.length,
)

const metricCards = computed(() => {
  if (!hasComparison.value) return []
  return [
    buildMetricCard('avgWait', '平均等待时长', props.baseMetrics.avgWaitMinutes, props.compareMetrics.avgWaitMinutes, props.comparison.avgWaitDelta, true, '分钟'),
    buildMetricCard('maxQueue', '最大排队长度', props.baseMetrics.maxQueueLength, props.compareMetrics.maxQueueLength, props.comparison.maxQueueDelta, true, '人'),
    buildMetricCard('busyWindows', '忙碌窗口数', props.baseMetrics.busyWindowCount, props.compareMetrics.busyWindowCount, props.comparison.busyWindowCountDelta, true, '个'),
    buildMetricCard('extremeWindows', '极端拥挤窗口数', props.baseMetrics.extremeWindowCount, props.compareMetrics.extremeWindowCount, props.comparison.extremeWindowCountDelta, true, '个'),
    buildMetricCard('servedUsers', '已服务人数', props.baseMetrics.servedUserCount, props.compareMetrics.servedUserCount, props.comparison.servedUserCountDelta, false, '人'),
    buildMetricCard('unservedUsers', '未服务人数', props.baseMetrics.unservedUserCount, props.compareMetrics.unservedUserCount, props.comparison.unservedUserCountDelta, true, '人'),
  ]
})

onMounted(() => {
  renderCharts()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  compareChart?.dispose()
  deltaChart?.dispose()
  trendChart?.dispose()
})

watch(
  () => [props.comparison, props.baseMetrics, props.compareMetrics, props.baselineRun, props.compareRun],
  () => nextTick(renderCharts),
  { deep: true },
)

function renderCharts() {
  if (!hasComparison.value) {
    disposeCharts()
    return
  }
  renderCompareChart()
  renderDeltaChart()
  renderTrendChart()
}

function renderCompareChart() {
  if (!compareChartRef.value) return
  if (!compareChart) {
    compareChart = echarts.init(compareChartRef.value)
  }

  compareChart.setOption({
    color: ['#94a3b8', '#2563eb'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 48, right: 20, top: 40, bottom: 32 },
    xAxis: {
      type: 'category',
      data: ['平均等待', '最大排队', '忙碌窗口', '未服务人数'],
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: '未分流',
        type: 'bar',
        barMaxWidth: 24,
        data: [
          props.baseMetrics.avgWaitMinutes,
          props.baseMetrics.maxQueueLength,
          props.baseMetrics.busyWindowCount,
          props.baseMetrics.unservedUserCount,
        ],
      },
      {
        name: '分流后',
        type: 'bar',
        barMaxWidth: 24,
        data: [
          props.compareMetrics.avgWaitMinutes,
          props.compareMetrics.maxQueueLength,
          props.compareMetrics.busyWindowCount,
          props.compareMetrics.unservedUserCount,
        ],
      },
    ],
  })
}

function renderDeltaChart() {
  if (!deltaChartRef.value) return
  if (!deltaChart) {
    deltaChart = echarts.init(deltaChartRef.value)
  }

  const rows = [
    ['平均等待', props.comparison.avgWaitDelta, true],
    ['最大排队', props.comparison.maxQueueDelta, true],
    ['忙碌窗口', props.comparison.busyWindowCountDelta, true],
    ['未服务人数', props.comparison.unservedUserCountDelta, true],
  ]

  deltaChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 20, top: 26, bottom: 32 },
    xAxis: {
      type: 'category',
      data: rows.map((item) => item[0]),
    },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        barMaxWidth: 28,
        data: rows.map((item) => ({
          value: item[1],
          itemStyle: {
            color: isImproved(Number(item[1]), Boolean(item[2])) ? '#16a34a' : Number(item[1]) === 0 ? '#94a3b8' : '#dc2626',
          },
        })),
      },
    ],
  })
}

function renderTrendChart() {
  if (!hasTrend.value || !trendChartRef.value) {
    trendChart?.dispose()
    trendChart = null
    return
  }
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }

  const baseSeries = buildTrendSeries(props.baselineRun)
  const compareSeries = buildTrendSeries(props.compareRun)

  trendChart.setOption({
    color: ['#94a3b8', '#2563eb', '#f59e0b', '#14b8a6'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 48, right: 24, top: 42, bottom: 32 },
    xAxis: {
      type: 'category',
      data: baseSeries.labels,
      boundaryGap: false,
    },
    yAxis: { type: 'value', name: '人数' },
    series: [
      {
        name: '未分流排队人数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: baseSeries.queue,
      },
      {
        name: '分流后排队人数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: compareSeries.queue,
      },
      {
        name: '未分流负载人数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: baseSeries.load,
      },
      {
        name: '分流后负载人数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: compareSeries.load,
      },
    ],
  })
}

function buildMetricCard(key, label, baseValue, compareValue, delta, smallerIsBetter, unit) {
  const improved = isImproved(delta, smallerIsBetter)
  return {
    key,
    label,
    base: `${formatValue(baseValue)} ${unit}`,
    compare: `${formatValue(compareValue)} ${unit}`,
    deltaText: deltaText(delta, unit, smallerIsBetter),
    deltaClass: improved ? 'good' : Number(delta) === 0 ? 'neutral' : 'bad',
  }
}

function isImproved(delta, smallerIsBetter) {
  if (Number(delta) === 0) return false
  return smallerIsBetter ? Number(delta) < 0 : Number(delta) > 0
}

function deltaText(delta, unit, smallerIsBetter) {
  const value = Number(delta || 0)
  if (value === 0) return '变化 0'
  const direction = smallerIsBetter
    ? value < 0
      ? '改善'
      : '变差'
    : value > 0
      ? '改善'
      : '变差'
  const sign = value > 0 ? '+' : ''
  return `${direction} ${sign}${formatValue(value)} ${unit}`
}

function formatValue(value) {
  return Math.round(Number(value || 0) * 10) / 10
}

function buildTrendSeries(run) {
  const timePoints = Array.isArray(run?.timePoints) ? run.timePoints : []
  return {
    labels: timePoints.map((point) => `${point.minute} 分`),
    queue: timePoints.map(totalQueueLength),
    load: timePoints.map(totalCurrentCount),
  }
}

function resizeCharts() {
  compareChart?.resize()
  deltaChart?.resize()
  trendChart?.resize()
}

function disposeCharts() {
  compareChart?.dispose()
  deltaChart?.dispose()
  trendChart?.dispose()
  compareChart = null
  deltaChart = null
  trendChart = null
}
</script>

<style scoped>
.panel-stack {
  display: grid;
  gap: 18px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.run-strip {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.run-strip span {
  padding: 8px 12px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
}

.metric-card,
.chart-card,
.conclusion-card {
  padding: 18px;
  border: 1px solid #dbe4ee;
  border-radius: 20px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.metric-card {
  display: grid;
  gap: 4px;
}

.metric-label,
.metric-sub,
.chart-head p,
.conclusion-label {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.metric-value {
  color: #0f172a;
  font-size: 22px;
}

.metric-value.compare {
  color: #2563eb;
}

.metric-delta {
  margin-top: 6px;
  font-size: 13px;
  font-weight: 800;
}

.metric-delta.good {
  color: #15803d;
}

.metric-delta.bad {
  color: #dc2626;
}

.metric-delta.neutral {
  color: #475569;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.chart-head {
  display: grid;
  gap: 4px;
  margin-bottom: 8px;
}

.chart-head h3 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
}

.chart-head p,
.conclusion-card p {
  margin: 0;
}

.chart-surface {
  min-height: 280px;
}

.trend-surface {
  min-height: 320px;
}

.conclusion-card {
  display: grid;
  gap: 10px;
}

.conclusion-card p {
  color: #334155;
  line-height: 1.8;
}

@media (max-width: 1100px) {
  .metric-grid,
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
