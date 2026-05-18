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
        <div v-if="verdict" class="compare-verdict" :class="verdict.level">
          <strong>{{ verdict.title }}</strong>
          <span>{{ verdict.text }}</span>
        </div>
        <div v-if="scenarioDiffs.length" class="scenario-diffs">
          <span class="diff-label">本次调整</span>
          <div class="diff-list">
            <span v-for="item in scenarioDiffs" :key="item.label">
              {{ item.label }}：{{ item.before }} → {{ item.after }}
            </span>
          </div>
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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

const verdict = computed(() => {
  const comparison = props.comparison
  if (!comparison) return null

  let score = 0
  score += comparison.avgWaitDelta < 0 ? 2 : comparison.avgWaitDelta > 0 ? -2 : 0
  score += comparison.maxQueueDelta < 0 ? 1 : comparison.maxQueueDelta > 0 ? -1 : 0
  score += comparison.busyWindowCountDelta < 0 ? 1 : comparison.busyWindowCountDelta > 0 ? -1 : 0
  score += comparison.extremeWindowCountDelta < 0 ? 1 : comparison.extremeWindowCountDelta > 0 ? -1 : 0
  score += comparison.servedUserCountDelta > 0 ? 1 : comparison.servedUserCountDelta < 0 ? -1 : 0

  if (score >= 3) {
    return {
      level: 'good',
      title: '调整有效',
      text: '核心排队压力指标整体改善，可以作为较优策略继续观察。',
    }
  }
  if (score <= -3) {
    return {
      level: 'bad',
      title: '压力上升',
      text: '对比场景使等待或排队压力变大，建议回退参数或增加分流/开放窗口策略。',
    }
  }
  return {
    level: 'neutral',
    title: '变化不明显',
    text: '指标有升有降，建议结合具体窗口排队和推荐结果进一步判断。',
  }
})

const scenarioDiffs = computed(() => {
  const base = props.baseRun?.scenario
  const current = props.compareRun?.scenario
  if (!base || !current) return []

  return [
    diffItem('就餐时段', base.mealPeriod, current.mealPeriod, mealLabel),
    diffItem('日期类型', base.dayType, current.dayType, dayLabel),
    diffItem('拥挤等级', base.crowdLevel, current.crowdLevel, crowdLabel),
    diffItem('仿真人数', base.virtualUserCount, current.virtualUserCount, (value) => `${value} 人`),
    diffItem('天气系数', base.weatherFactor, current.weatherFactor),
    diffItem('活动系数', base.eventFactor, current.eventFactor),
    diffItem(
      '关闭窗口',
      base.closedWindowIds?.length || 0,
      current.closedWindowIds?.length || 0,
      (value) => `${value} 个`,
    ),
  ].filter(Boolean)
})

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

function diffItem(label, before, after, formatter = formatValue) {
  if (String(before) === String(after)) return null
  return {
    label,
    before: formatter(before),
    after: formatter(after),
  }
}

function formatValue(value) {
  if (value == null) return '-'
  return String(value)
}

function mealLabel(value) {
  return (
    {
      BREAKFAST: '早餐',
      LUNCH: '午餐',
      DINNER: '晚餐',
    }[value] || formatValue(value)
  )
}

function dayLabel(value) {
  return (
    {
      WEEKDAY: '工作日',
      WEEKEND: '周末',
    }[value] || formatValue(value)
  )
}

function crowdLabel(value) {
  return (
    {
      IDLE: '空闲',
      NORMAL: '正常',
      BUSY: '繁忙',
      EXTREME: '极拥挤',
    }[value] || formatValue(value)
  )
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
  padding: 9px 0;
  color: #657084;
  font-size: 13px;
  font-weight: 700;
}

.run-row strong {
  color: #172033;
}

.compare-verdict {
  display: grid;
  gap: 4px;
  margin: 10px 0;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.compare-verdict strong {
  color: #172033;
  font-size: 15px;
}

.compare-verdict span {
  color: #657084;
  font-size: 13px;
  line-height: 1.5;
}

.compare-verdict.good {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.compare-verdict.bad {
  border-color: #fecaca;
  background: #fef2f2;
}

.scenario-diffs {
  display: grid;
  gap: 8px;
  margin: 10px 0 12px;
}

.diff-label {
  color: #657084;
  font-size: 13px;
  font-weight: 800;
}

.diff-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.diff-list span {
  padding: 5px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  color: #475569;
  background: #ffffff;
  font-size: 12px;
  font-weight: 700;
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
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.delta-item span {
  display: block;
  margin-bottom: 6px;
  color: #657084;
  font-size: 13px;
  font-weight: 700;
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
  color: #134e4a;
  background: #f0fdfa;
  line-height: 1.6;
}

@media (max-width: 780px) {
  .delta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
