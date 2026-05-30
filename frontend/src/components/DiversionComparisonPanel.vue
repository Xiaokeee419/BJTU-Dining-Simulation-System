<template>
  <div v-if="hasComparison" class="panel-stack">
    <section class="run-strip">
      <span>Baseline Run #{{ comparison.baseRunId }}</span>
      <span>Compare Run #{{ comparison.compareRunId }}</span>
    </section>

    <section class="metric-note">
      <strong>指标口径说明</strong>
      <p>
        平均等待和最大等待反映的是用户选择窗口时的预计等待值；判断分流是否真正缓解高峰压力，请优先结合最大队列长度、忙碌窗口数、极端拥挤窗口数和未服务人数一起看。
      </p>
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
          <p>负值代表排队压力类指标下降，正值代表已服务人数增加</p>
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
  <EmptyState v-else :title="emptyTitle" :description="emptyDescription" />
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
  status: {
    type: String,
    default: 'IDLE',
  },
  statusMessage: {
    type: String,
    default: '',
  },
})

const compareChartRef = ref(null)
const deltaChartRef = ref(null)
const trendChartRef = ref(null)
let compareChart
let deltaChart
let trendChart

const hasComparison = computed(() =>
  Boolean(
    props.comparison &&
      props.baseMetrics &&
      props.compareMetrics &&
      props.comparison.baseRunId &&
      props.comparison.compareRunId &&
      props.compareRun?.runId === props.comparison.compareRunId,
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
    buildMetricCard(
      'maxQueue',
      '最大队列长度',
      props.baseMetrics.maxQueueLength,
      props.compareMetrics.maxQueueLength,
      props.comparison.maxQueueDelta,
      true,
      '人',
    ),
    buildMetricCard(
      'busyWindows',
      '忙碌窗口数',
      props.baseMetrics.busyWindowCount,
      props.compareMetrics.busyWindowCount,
      props.comparison.busyWindowCountDelta,
      true,
      '个',
    ),
    buildMetricCard(
      'extremeWindows',
      '极端拥挤窗口数',
      props.baseMetrics.extremeWindowCount,
      props.compareMetrics.extremeWindowCount,
      props.comparison.extremeWindowCountDelta,
      true,
      '个',
    ),
    buildMetricCard(
      'unservedUsers',
      '未服务人数',
      props.baseMetrics.unservedUserCount,
      props.compareMetrics.unservedUserCount,
      props.comparison.unservedUserCountDelta,
      true,
      '人',
    ),
    buildMetricCard(
      'avgWait',
      '平均预计等待',
      props.baseMetrics.avgWaitMinutes,
      props.compareMetrics.avgWaitMinutes,
      props.comparison.avgWaitDelta,
      true,
      '分钟',
    ),
    buildMetricCard(
      'servedUsers',
      '已服务人数',
      props.baseMetrics.servedUserCount,
      props.compareMetrics.servedUserCount,
      props.comparison.servedUserCountDelta,
      false,
      '人',
    ),
  ]
})

const emptyTitle = computed(() => {
  if (props.status === 'RUNNING') return '正在生成分流对比'
  if (props.status === 'ERROR') return '分流对比失败'
  if (props.status === 'MOCK_UNSUPPORTED') return 'Mock 模式暂不支持真实分流对比'
  if (props.status === 'NO_SUGGESTION') return '当前分钟暂无可执行的分流建议'
  return '尚未运行分流对比'
})

const emptyDescription = computed(() => {
  if (props.status === 'RUNNING') {
    return '系统正在生成分流建议、运行 compare 仿真并计算策略对比，请稍候。'
  }
  if (props.status === 'ERROR') {
    return props.statusMessage || '分流对比失败，请检查后端响应或日志。'
  }
  if (props.status === 'MOCK_UNSUPPORTED') {
    return 'Mock 模式暂不支持真实分流对比，请切换到真实后端接口后再运行。'
  }
  if (props.status === 'NO_SUGGESTION') {
    return props.statusMessage || '当前高峰分钟没有可行分流建议，因此不会生成 compareRun。'
  }
  return '尚未运行分流对比。请先保存 baseline，并点击运行分流对比。'
})

onMounted(() => {
  renderCharts()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  disposeCharts()
})

watch(
  () => [
    props.comparison,
    props.baseMetrics,
    props.compareMetrics,
    props.baselineRun,
    props.compareRun,
  ],
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
    grid: { left: 48, right: 20, top: 40, bottom: 40 },
    xAxis: {
      type: 'category',
      axisLabel: { interval: 0, rotate: 18 },
      data: ['最大队列', '忙碌窗口', '极端拥挤窗口', '未服务人数', '平均预计等待', '已服务人数'],
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: '未分流',
        type: 'bar',
        barMaxWidth: 24,
        data: [
          props.baseMetrics.maxQueueLength,
          props.baseMetrics.busyWindowCount,
          props.baseMetrics.extremeWindowCount,
          props.baseMetrics.unservedUserCount,
          props.baseMetrics.avgWaitMinutes,
          props.baseMetrics.servedUserCount,
        ],
      },
      {
        name: '分流后',
        type: 'bar',
        barMaxWidth: 24,
        data: [
          props.compareMetrics.maxQueueLength,
          props.compareMetrics.busyWindowCount,
          props.compareMetrics.extremeWindowCount,
          props.compareMetrics.unservedUserCount,
          props.compareMetrics.avgWaitMinutes,
          props.compareMetrics.servedUserCount,
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
    ['最大队列', props.comparison.maxQueueDelta, true],
    ['忙碌窗口', props.comparison.busyWindowCountDelta, true],
    ['极端拥挤窗口', props.comparison.extremeWindowCountDelta, true],
    ['未服务人数', props.comparison.unservedUserCountDelta, true],
    ['平均预计等待', props.comparison.avgWaitDelta, true],
    ['已服务人数', props.comparison.servedUserCountDelta, false],
  ]

  deltaChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 20, top: 26, bottom: 40 },
    xAxis: {
      type: 'category',
      axisLabel: { interval: 0, rotate: 18 },
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
            color: isImproved(Number(item[1]), Boolean(item[2]))
              ? '#16a34a'
              : Number(item[1]) === 0
                ? '#94a3b8'
                : '#dc2626',
          },
        })),
      },
    ],
  })
}

function renderTrendChart() {
  if (!hasTrend.value || !trendChartRef.value) {
    if (trendChart) {
      trendChart.dispose()
      trendChart = null
    }
    return
  }
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }

  const trendSeries = buildTrendSeries(props.baselineRun, props.compareRun)

  trendChart.setOption({
    color: ['#94a3b8', '#2563eb', '#f59e0b', '#14b8a6'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 48, right: 24, top: 42, bottom: 32 },
    xAxis: {
      type: 'category',
      data: trendSeries.labels,
      boundaryGap: false,
    },
    yAxis: { type: 'value', name: '人数' },
    series: [
      {
        name: '未分流排队人数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trendSeries.baseQueue,
      },
      {
        name: '分流后排队人数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trendSeries.compareQueue,
      },
      {
        name: '未分流负载人数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trendSeries.baseLoad,
      },
      {
        name: '分流后负载人数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trendSeries.compareLoad,
      },
    ],
  })
}

function buildMetricCard(key, label, baseValue, compareValue, delta, smallerIsBetter, unit) {
  const numericDelta = Number(delta || 0)
  const improved = isImproved(numericDelta, smallerIsBetter)
  return {
    key,
    label,
    base: `${formatValue(baseValue)} ${unit}`,
    compare: `${formatValue(compareValue)} ${unit}`,
    deltaText: deltaText(numericDelta, unit, smallerIsBetter),
    deltaClass: improved ? 'good' : numericDelta === 0 ? 'neutral' : 'bad',
  }
}

function buildTrendSeries(baseRun, compareRun) {
  const basePoints = Array.isArray(baseRun?.timePoints) ? baseRun.timePoints : []
  const comparePoints = Array.isArray(compareRun?.timePoints) ? compareRun.timePoints : []
  const baseIndex = new Map(
    basePoints.map((point) => [point.minute, { queue: totalQueueLength(point), load: totalCurrentCount(point) }]),
  )
  const compareIndex = new Map(
    comparePoints.map((point) => [point.minute, { queue: totalQueueLength(point), load: totalCurrentCount(point) }]),
  )

  const labels = [...new Set([...baseIndex.keys(), ...compareIndex.keys()])]
    .sort((left, right) => left - right)

  return {
    labels: labels.map((minute) => `${minute} 分钟`),
    baseQueue: labels.map((minute) => baseIndex.get(minute)?.queue ?? 0),
    compareQueue: labels.map((minute) => compareIndex.get(minute)?.queue ?? 0),
    baseLoad: labels.map((minute) => baseIndex.get(minute)?.load ?? 0),
    compareLoad: labels.map((minute) => compareIndex.get(minute)?.load ?? 0),
  }
}

function isImproved(delta, smallerIsBetter) {
  if (delta === 0) return false
  return smallerIsBetter ? delta < 0 : delta > 0
}

function deltaText(delta, unit, smallerIsBetter) {
  if (delta === 0) return '变化 0'
  const direction = smallerIsBetter ? (delta < 0 ? '改善' : '变差') : delta > 0 ? '改善' : '变差'
  return `${direction} ${formatValue(Math.abs(delta))} ${unit}`
}

function formatValue(value) {
  return Math.round(Number(value || 0) * 10) / 10
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

.metric-note,
.metric-card,
.chart-card,
.conclusion-card {
  padding: 18px;
  border: 1px solid #dbe4ee;
  border-radius: 20px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.metric-note {
  display: grid;
  gap: 8px;
}

.metric-note strong,
.metric-label,
.metric-sub,
.chart-head p,
.conclusion-label {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.metric-note p,
.conclusion-card p {
  margin: 0;
  color: #334155;
  line-height: 1.8;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  display: grid;
  gap: 4px;
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

@media (max-width: 1100px) {
  .metric-grid,
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
