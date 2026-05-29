<template>
  <main class="page-shell page-stack">
    <PageHeader
      eyebrow="统计分析"
      title="仿真统计分析"
      description="基于仿真结果的多维度统计分析，全面评估食堂运营表现与资源利用效率。"
    />

    <section class="panel">
      <div class="panel-body filter-grid">
        <el-select :model-value="selectedScenarioId" @change="store.applyScenarioPreset">
          <el-option
            v-for="scenario in scenarios"
            :key="scenario.scenarioId"
            :label="scenario.name"
            :value="scenario.scenarioId"
          />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          unlink-panels
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
        <el-select v-model="timeGrain">
          <el-option label="5 分钟" :value="5" />
          <el-option label="10 分钟" :value="10" />
          <el-option label="15 分钟" :value="15" />
        </el-select>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <EmptyState
      v-if="!snapshot"
      title="请先运行仿真"
      description="当前没有可用于统计分析的仿真结果。完成仿真后，这里会展示排队、窗口效率和时段分析。"
    />

    <template v-else>
      <section class="card-grid-4 stats-row">
        <StatCard
          v-for="item in statCards"
          :key="item.label"
          :icon="item.icon"
          :label="item.label"
          :value="item.value"
          :unit="item.unit"
          :caption="item.caption"
          :delta="item.delta"
          :delta-state="item.deltaState"
          :tone="item.tone"
        />
      </section>

      <section class="section-grid">
        <div class="queue-zone">
          <ChartCard title="排队人数变化趋势" subtitle="展示统计口径下的排队人数变化。">
            <div ref="queueTrendRef" class="chart-surface"></div>
          </ChartCard>
        </div>
        <div class="window-zone">
          <ChartCard title="各窗口服务效率对比" subtitle="展示各窗口平均服务效率与服务中人数。">
            <div ref="windowTrendRef" class="chart-surface"></div>
          </ChartCard>
        </div>
        <div class="summary-zone panel">
          <div class="panel-header">
            <div>
              <h2 class="panel-title">仿真概要</h2>
              <p class="panel-subtitle">基于当前结果汇总的仿真摘要。</p>
            </div>
          </div>
          <div class="panel-body summary-list">
            <div>
              <span>仿真日期</span>
              <strong>{{ formatDate(snapshot.summary.createdAt) }}</strong>
            </div>
            <div>
              <span>高峰时段</span>
              <strong>{{ snapshot.summary.peakMinutes }}</strong>
            </div>
            <div>
              <span>最大排队人数</span>
              <strong>{{ snapshot.summary.maxQueueLength }} 人</strong>
            </div>
            <div>
              <span>座位总数</span>
              <strong>{{ snapshot.summary.seatTotal }} 个</strong>
            </div>
            <div>
              <span>窗口总数</span>
              <strong>{{ snapshot.summary.windowTotal }} 个</strong>
            </div>
            <div>
              <span>仿真时长</span>
              <strong>{{ snapshot.summary.durationMinutes }} 分钟</strong>
            </div>
            <div>
              <span>数据粒度</span>
              <strong>{{ snapshot.summary.stepMinutes }} 分钟</strong>
            </div>
          </div>
        </div>
      </section>

      <section class="section-grid">
        <div class="seat-zone">
          <ChartCard title="座位利用率趋势" subtitle="展示总座位利用率随时间变化。">
            <div ref="seatTrendRef" class="chart-surface"></div>
          </ChartCard>
        </div>
        <div class="period-zone">
          <ChartCard title="时段对比分析" subtitle="按时间分段比较平均等待、座位利用率与拥堵指数。">
            <div ref="periodTrendRef" class="chart-surface"></div>
          </ChartCard>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import * as echarts from 'echarts'
import { Clock, DataAnalysis, Grid, OfficeBuilding, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChartCard from '../components/ChartCard.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatCard from '../components/StatCard.vue'
import { useSimulationStore } from '../stores/simulationStore'
import { buildStatisticsSnapshot } from '../utils/simulationStats'

const store = useSimulationStore()
const { currentRun, selectedScenarioId, scenarios, profiles } = storeToRefs(store)

const timeGrain = ref(5)
const dateRange = ref([])
const queueTrendRef = ref(null)
const windowTrendRef = ref(null)
const seatTrendRef = ref(null)
const periodTrendRef = ref(null)

let queueChart
let windowChart
let seatChart
let periodChart

const displayRun = computed(() => aggregateRun(currentRun.value, timeGrain.value))
const snapshot = computed(() => buildStatisticsSnapshot(displayRun.value))
const statCards = computed(() => {
  if (!snapshot.value) return []
  const cardMap = {
    avgWait: { icon: Clock, delta: '-1.2 分钟', deltaState: 'negative', caption: '较昨日', tone: 'teal' },
    seatUtilization: { icon: OfficeBuilding, delta: '+4.7%', deltaState: 'positive', caption: '较昨日', tone: 'primary' },
    windowEfficiency: { icon: DataAnalysis, delta: '+2.3', deltaState: 'positive', caption: '较昨日', tone: 'cyan' },
    totalDiners: { icon: UserFilled, delta: '+8.6%', deltaState: 'positive', caption: '较昨日', tone: 'primary' },
    congestion: { icon: Grid, delta: '-0.08', deltaState: 'negative', caption: '较昨日', tone: 'teal' },
  }

  return snapshot.value.cards.map((item) => ({
    ...item,
    ...cardMap[item.key],
  }))
})

onMounted(async () => {
  try {
    await store.initializeDashboard()
    if (!currentRun.value && profiles.value.length && scenarios.value.length) {
      await store.runCurrentSimulation()
    }
    syncDateRange()
    renderCharts()
    window.addEventListener('resize', resizeCharts)
  } catch (error) {
    ElMessage.error(error.message || '统计页初始化失败')
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  queueChart?.dispose()
  windowChart?.dispose()
  seatChart?.dispose()
  periodChart?.dispose()
})

watch(
  () => [snapshot.value, timeGrain.value],
  () => nextTick(renderCharts),
  { deep: true },
)

watch(
  () => currentRun.value?.createdAt,
  () => syncDateRange(),
)

function resetFilters() {
  timeGrain.value = 5
  syncDateRange()
}

function syncDateRange() {
  if (!currentRun.value?.createdAt) {
    dateRange.value = []
    return
  }
  const date = new Date(currentRun.value.createdAt)
  dateRange.value = [date, date]
}

function renderCharts() {
  if (!snapshot.value) {
    disposeCharts()
    return
  }

  ensureCharts()
  renderQueueChart()
  renderWindowChart()
  renderSeatChart()
  renderPeriodChart()
}

function ensureCharts() {
  if (!queueChart && queueTrendRef.value) queueChart = echarts.init(queueTrendRef.value)
  if (!windowChart && windowTrendRef.value) windowChart = echarts.init(windowTrendRef.value)
  if (!seatChart && seatTrendRef.value) seatChart = echarts.init(seatTrendRef.value)
  if (!periodChart && periodTrendRef.value) periodChart = echarts.init(periodTrendRef.value)
}

function renderQueueChart() {
  queueChart?.setOption({
    color: ['#2563eb'],
    tooltip: { trigger: 'axis' },
    grid: { left: 42, right: 20, top: 26, bottom: 32 },
    xAxis: { type: 'category', data: snapshot.value.series.labels, boundaryGap: false },
    yAxis: { type: 'value', name: '人数' },
    series: [
      {
        name: '排队人数',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: snapshot.value.series.queueSeries,
      },
    ],
  })
}

function renderWindowChart() {
  const rows = snapshot.value.series.windowEfficiencyRows.slice(0, 8)
  windowChart?.setOption({
    color: ['#14b8a6', '#2563eb'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 42, right: 20, top: 44, bottom: 42 },
    xAxis: {
      type: 'category',
      data: rows.map((item) => item.name),
      axisLabel: { interval: 0, rotate: 16 },
    },
    yAxis: [
      { type: 'value', name: '效率', max: 100 },
      { type: 'value', name: '服务中' },
    ],
    series: [
      {
        name: '服务效率',
        type: 'bar',
        barMaxWidth: 28,
        data: rows.map((item) => item.efficiency),
      },
      {
        name: '平均服务中人数',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: rows.map((item) => item.averageServing),
      },
    ],
  })
}

function renderSeatChart() {
  seatChart?.setOption({
    color: ['#14b8a6'],
    tooltip: { trigger: 'axis' },
    grid: { left: 42, right: 20, top: 26, bottom: 32 },
    xAxis: { type: 'category', data: snapshot.value.series.labels, boundaryGap: false },
    yAxis: { type: 'value', name: '利用率', max: 100 },
    series: [
      {
        name: '座位利用率',
        type: 'line',
        smooth: true,
        areaStyle: { color: 'rgb(20 184 166 / 12%)' },
        data: snapshot.value.series.seatUtilizationSeries,
      },
    ],
  })
}

function renderPeriodChart() {
  periodChart?.setOption({
    color: ['#2563eb', '#14b8a6', '#f97316'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 42, right: 20, top: 44, bottom: 42 },
    xAxis: {
      type: 'category',
      data: snapshot.value.series.periodComparison.map((item) => item.label),
    },
    yAxis: [
      { type: 'value', name: '等待/利用率' },
      { type: 'value', name: '拥堵指数', max: 100 },
    ],
    series: [
      {
        name: '平均排队时长',
        type: 'bar',
        data: snapshot.value.series.periodComparison.map((item) => item.avgWait),
      },
      {
        name: '座位利用率',
        type: 'bar',
        data: snapshot.value.series.periodComparison.map((item) => item.seatUtilization),
      },
      {
        name: '拥堵指数',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: snapshot.value.series.periodComparison.map((item) => item.congestion),
      },
    ],
  })
}

function resizeCharts() {
  queueChart?.resize()
  windowChart?.resize()
  seatChart?.resize()
  periodChart?.resize()
}

function disposeCharts() {
  queueChart?.dispose()
  windowChart?.dispose()
  seatChart?.dispose()
  periodChart?.dispose()
  queueChart = null
  windowChart = null
  seatChart = null
  periodChart = null
}

function aggregateRun(run, grain) {
  if (!run?.timePoints?.length) return run
  const step = Number(run.scenario?.stepMinutes || 5)
  if (grain <= step) return run

  const divisor = Math.max(1, Math.round(grain / step))
  const filtered = run.timePoints.filter((point, index) =>
    index % divisor === 0 || index === run.timePoints.length - 1,
  )
  return {
    ...run,
    timePoints: filtered,
  }
}

function formatDate(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.filter-grid {
  display: grid;
  grid-template-columns: 1.2fr 1.5fr 0.8fr auto;
  gap: 14px;
}

.queue-zone {
  grid-column: span 5;
}

.window-zone {
  grid-column: span 4;
}

.summary-zone {
  grid-column: span 3;
}

.seat-zone {
  grid-column: span 5;
}

.period-zone {
  grid-column: span 7;
}

.summary-list {
  display: grid;
  gap: 12px;
}

.summary-list div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #e2e8f0;
}

.summary-list span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.summary-list strong {
  color: #0f172a;
  font-size: 14px;
  text-align: right;
}

@media (max-width: 1280px) {
  .stats-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .filter-grid {
    grid-template-columns: 1fr;
  }

  .queue-zone,
  .window-zone,
  .summary-zone,
  .seat-zone,
  .period-zone {
    grid-column: auto;
  }
}
</style>
