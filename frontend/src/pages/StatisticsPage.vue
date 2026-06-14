<template>
  <main class="page-shell page-stack">
    <PageHeader
      eyebrow="统计分析"
      title="baseline 仿真统计分析"
      description="本页只展示当前未分流仿真的整体统计，用于观察 baseline 的排队趋势和窗口负载，不替代分流对比页。"
    >
      <template #actions>
        <el-button plain @click="router.push('/config')">前往参数配置</el-button>
      </template>
    </PageHeader>

    <section v-if="!snapshot" class="empty-shell">
      <EmptyState
        title="暂无可分析的 baseline 仿真结果"
        description="请先到参数配置页运行一次未分流仿真，再返回本页查看整体统计。"
      />
      <el-button type="primary" @click="router.push('/config')">前往参数配置</el-button>
    </section>

    <template v-else>
      <section class="card-grid-4">
        <StatCard
          :icon="Clock"
          label="平均排队时长"
          :value="cardMap.avgWait.value"
          :unit="cardMap.avgWait.unit"
          caption="metrics.avgWaitMinutes"
          :delta="cardMap.avgWait.note"
          delta-state="neutral"
          tone="teal"
        />
        <StatCard
          :icon="Tickets"
          label="最大排队人数"
          :value="cardMap.maxQueue.value"
          :unit="cardMap.maxQueue.unit"
          caption="metrics.maxQueueLength"
          :delta="cardMap.maxQueue.note"
          delta-state="neutral"
          tone="orange"
        />
        <StatCard
          :icon="UserFilled"
          label="仿真样本人数"
          :value="cardMap.sampleCount.value"
          :unit="cardMap.sampleCount.unit"
          caption="baseline 样本规模"
          :delta="cardMap.sampleCount.note"
          delta-state="neutral"
        />
        <StatCard
          :icon="Warning"
          label="未服务人数"
          :value="cardMap.unserved.value"
          :unit="cardMap.unserved.unit"
          caption="高峰后仍未完成服务"
          :delta="cardMap.unserved.note"
          delta-state="neutral"
          tone="green"
        />
      </section>

      <section class="section-grid">
        <div class="queue-zone">
          <ChartCard title="排队人数趋势" subtitle="按 timePoints 统计总排队人数。">
            <div ref="queueTrendRef" class="chart-surface"></div>
          </ChartCard>
        </div>

        <div class="window-zone">
          <ChartCard
            title="窗口排队/负载对比"
            subtitle="按窗口统计平均排队人数与平均排队/服务负载，默认展示压力最高的窗口。"
          >
            <div ref="windowCompareRef" class="chart-surface"></div>
          </ChartCard>
        </div>
      </section>

      <section class="section-grid">
        <div class="load-zone">
          <ChartCard title="负载率趋势" subtitle="按高峰负载人数 / 餐厅容量计算的容量负载率走势。">
            <div ref="loadRateRef" class="chart-surface"></div>
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
import { Clock, Tickets, UserFilled, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import ChartCard from '../components/ChartCard.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatCard from '../components/StatCard.vue'
import { useSimulationStore } from '../stores/simulationStore'
import { buildStatisticsSnapshot } from '../utils/simulationStats'

const router = useRouter()
const store = useSimulationStore()
const { currentRun } = storeToRefs(store)

const queueTrendRef = ref(null)
const windowCompareRef = ref(null)
const loadRateRef = ref(null)
let queueChart
let windowChart
let loadRateChart

const snapshot = computed(() =>
  buildStatisticsSnapshot(currentRun.value?.status === 'FINISHED' ? currentRun.value : null),
)
const cardMap = computed(() => {
  const map = {}
  ;(snapshot.value?.cards || []).forEach((item) => {
    map[item.key] = item
  })
  return map
})

onMounted(async () => {
  try {
    await store.initializeDashboard()
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
  loadRateChart?.dispose()
})

watch(
  () => snapshot.value,
  () => nextTick(renderCharts),
  { deep: true },
)

function renderCharts() {
  if (!snapshot.value) {
    disposeCharts()
    return
  }
  renderQueueChart()
  renderWindowChart()
  renderLoadRateChart()
}

function renderQueueChart() {
  if (!queueTrendRef.value) return
  if (!queueChart) {
    queueChart = echarts.init(queueTrendRef.value)
  }

  queueChart.setOption({
    color: ['#2563eb'],
    tooltip: { trigger: 'axis' },
    grid: { left: 42, right: 20, top: 26, bottom: 32 },
    xAxis: {
      type: 'category',
      data: snapshot.value.series.labels,
      boundaryGap: false,
    },
    yAxis: { type: 'value', name: '人数' },
    series: [
      {
        name: '总排队人数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: snapshot.value.series.queueSeries,
      },
    ],
  })
}

function renderWindowChart() {
  if (!windowCompareRef.value) return
  if (!windowChart) {
    windowChart = echarts.init(windowCompareRef.value)
  }

  const rows = snapshot.value.series.windowQueueLoadRows.slice(0, 8)
  windowChart.setOption({
    color: ['#f97316', '#14b8a6'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 42, right: 20, top: 44, bottom: 42 },
    xAxis: {
      type: 'category',
      data: rows.map((item) => `${item.restaurantName} / ${item.name}`),
      axisLabel: { interval: 0, rotate: 18 },
    },
    yAxis: { type: 'value', name: '人数' },
    series: [
      {
        name: '平均排队人数',
        type: 'bar',
        barMaxWidth: 26,
        data: rows.map((item) => item.averageQueue),
      },
      {
        name: '平均排队/服务负载',
        type: 'bar',
        barMaxWidth: 26,
        data: rows.map((item) => item.averageLoad),
      },
    ],
  })
}

function renderLoadRateChart() {
  if (!loadRateRef.value) return
  if (!loadRateChart) {
    loadRateChart = echarts.init(loadRateRef.value)
  }

  loadRateChart.setOption({
    color: ['#14b8a6'],
    tooltip: { trigger: 'axis' },
    grid: { left: 42, right: 20, top: 26, bottom: 32 },
    xAxis: {
      type: 'category',
      data: snapshot.value.series.labels,
      boundaryGap: false,
    },
    yAxis: { type: 'value', name: '负载率', max: 100 },
    series: [
      {
        name: '负载率',
        type: 'line',
        smooth: true,
        areaStyle: { color: 'rgb(20 184 166 / 12%)' },
        data: snapshot.value.series.loadRateSeries,
      },
    ],
  })
}

function resizeCharts() {
  queueChart?.resize()
  windowChart?.resize()
  loadRateChart?.resize()
}

function disposeCharts() {
  queueChart?.dispose()
  windowChart?.dispose()
  loadRateChart?.dispose()
  queueChart = null
  windowChart = null
  loadRateChart = null
}
</script>

<style scoped>
.empty-shell {
  display: grid;
  justify-items: center;
  gap: 12px;
}

.queue-zone,
.window-zone,
.load-zone {
  grid-column: span 6;
}

.chart-surface {
  min-height: 300px;
}

@media (max-width: 1100px) {
  .queue-zone,
  .window-zone,
  .load-zone {
    grid-column: auto;
  }
}
</style>
