<template>
  <main class="page-shell page-stack">
    <PageHeader
      eyebrow="人流快照"
      title="仿真人流快照"
      description="本页仅展示仿真返回的 timePoint 快照，不再假设人员明细、标签画像或真实平面热力图。默认展示高峰分钟。"
    >
      <template #actions>
        <span class="status-badge">高峰分钟：{{ snapshot?.minute ?? '--' }}</span>
        <el-button plain @click="handleRefresh">重新运行仿真</el-button>
      </template>
    </PageHeader>

    <EmptyState
      v-if="!snapshot"
      title="请先运行仿真"
      description="当前没有可用于展示快照的数据。运行仿真后，这里会展示高峰分钟的餐厅负载、窗口排队和容量负载情况。"
    />

    <template v-else>
      <section class="card-grid-4">
        <StatCard
          :icon="Clock"
          label="快照分钟"
          :value="snapshot.minute"
          unit="分钟"
          caption="默认高峰时间点"
          :delta="snapshot.minuteLabel"
          delta-state="neutral"
        />
        <StatCard
          :icon="UserFilled"
          label="峰值负载人数"
          :value="snapshot.loadCount"
          unit="人"
          caption="timePoint.currentCount 聚合"
          :delta="snapshot.sampleNote"
          delta-state="neutral"
        />
        <StatCard
          :icon="Tickets"
          label="总排队人数"
          :value="snapshot.queueTotal"
          unit="人"
          caption="窗口 queueLength 汇总"
          :delta="`${snapshot.queueWindowCount} 个窗口在排队`"
          delta-state="neutral"
          tone="orange"
        />
        <StatCard
          :icon="Grid"
          label="开放窗口数"
          :value="snapshot.openWindowCount"
          unit="个"
          caption="营业窗口"
          :delta="`${snapshot.serviceTotal} 人正在服务中`"
          delta-state="neutral"
          tone="green"
        />
      </section>

      <section class="section-grid">
        <div class="restaurant-zone">
          <SectionCard
            title="餐厅负载快照"  
            subtitle="按餐厅展示 currentCount、queueLength、服务人数和容量负载率。这里的负载人数来自 A 的 currentCount 聚合，不表示真实在座人数。"
          >
            <div class="restaurant-grid">
              <article
                v-for="restaurant in snapshot.restaurants"
                :key="restaurant.restaurantId"
                class="restaurant-card"
              >
                <div class="restaurant-head">
                  <strong>{{ restaurant.name }}</strong>
                  <span class="crowd-chip" :class="crowdClass(restaurant.crowdLevel)">
                    {{ crowdLabel(restaurant.crowdLevel) }}
                  </span>
                </div>
                <div class="restaurant-metrics">
                  <div>
                    <span>负载人数</span>
                    <strong>{{ restaurant.currentCount }}</strong>
                  </div>
                  <div>
                    <span>排队人数</span>
                    <strong>{{ restaurant.queueTotal }}</strong>
                  </div>
                  <div>
                    <span>服务中</span>
                    <strong>{{ restaurant.servingTotal }}</strong>
                  </div>
                  <div>
                    <span>开放窗口</span>
                    <strong>{{ restaurant.openWindows }} / {{ restaurant.totalWindows }}</strong>
                  </div>
                </div>
                <div class="restaurant-foot">
                  <span>容量负载率 {{ restaurant.loadRate }}%</span>
                  <span>平均等待 {{ restaurant.avgWait }} 分钟</span>
                </div>
              </article>
            </div>
          </SectionCard>
        </div>

        <div class="crowd-zone">
          <ChartCard
            title="窗口拥挤等级分布"
            subtitle="统计当前快照下 IDLE、NORMAL、BUSY、EXTREME 四类窗口数量。"
            :empty="!snapshot.crowdBuckets.some((item) => item.value > 0)"
            empty-title="当前没有窗口快照"
            empty-description="请先运行仿真后再查看当前分钟窗口拥挤等级分布。"
          >
            <div ref="crowdChartRef" class="chart-surface"></div>
          </ChartCard>
        </div>
      </section>

      <section class="section-grid">
        <div class="trend-zone">
          <ChartCard
            title="负载变化趋势"
            subtitle="展示整轮仿真中负载人数、排队人数和服务中人数的时间变化。"
            :empty="!snapshot.trend.length"
          >
            <div ref="flowTrendRef" class="chart-surface"></div>
          </ChartCard>
        </div>

        <div class="window-zone">
          <SectionCard
            title="高压窗口列表"
            subtitle="按排队人数、等待时间排序，优先展示当前快照下压力最高的窗口。"
          >
            <div v-if="snapshot.busyWindows.length" class="window-list">
              <article
                v-for="window in snapshot.busyWindows"
                :key="window.windowId"
                class="window-item"
              >
                <div class="window-main">
                  <strong>{{ window.restaurantName }} / {{ window.name }}</strong>
                  <span class="crowd-chip" :class="crowdClass(window.crowdLevel)">
                    {{ crowdLabel(window.crowdLevel) }}
                  </span>
                </div>
                <div class="window-metrics">
                  <span>排队 {{ window.queueLength }} 人</span>
                  <span>服务中 {{ window.servingCount }} 人</span>
                  <span>等待 {{ window.waitMinutes }} 分钟</span>
                </div>
              </article>
            </div>
            <EmptyState
              v-else
              title="当前没有拥挤窗口"
              description="高峰分钟下所有营业窗口排队人数都为 0。"
            />
          </SectionCard>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import * as echarts from 'echarts'
import { Clock, Grid, Tickets, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChartCard from '../components/ChartCard.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import SectionCard from '../components/SectionCard.vue'
import StatCard from '../components/StatCard.vue'
import { useSimulationStore } from '../stores/simulationStore'
import {
  avgWaitMinutes,
  flattenWindows,
  resolvePeakTimePoint,
  resolveTimePoint,
  totalCurrentCount,
  totalQueueLength,
} from '../utils/simulationStats'

const store = useSimulationStore()
const { currentRun, currentMinute, profiles, scenarios } = storeToRefs(store)

const flowTrendRef = ref(null)
const crowdChartRef = ref(null)
let flowTrendChart
let crowdChart

const snapshot = computed(() => {
  const point = resolvePeakTimePoint(currentRun.value) || resolveTimePoint(currentRun.value, currentMinute.value)
  if (!point) return null

  const windows = flattenWindows(point)
  const openWindows = windows.filter((entry) => entry.window.status !== 'CLOSED')
  const serviceTotal = openWindows.reduce(
    (sum, entry) => sum + Number(entry.window.servingCount || 0),
    0,
  )
  const restaurants = (point.restaurants || [])
    .map((restaurant) => {
      const restaurantWindows = restaurant.windows || []
      const queueTotal = restaurantWindows.reduce(
        (sum, window) => sum + Number(window.queueLength || 0),
        0,
      )
      const servingTotal = restaurantWindows.reduce(
        (sum, window) => sum + Number(window.servingCount || 0),
        0,
      )
      const totalWindows = restaurantWindows.length
      const openWindowCount = restaurantWindows.filter((window) => window.status !== 'CLOSED').length
      const capacity = Number(restaurant.capacity || 0)
      const currentCount = Number(restaurant.currentCount || 0)
      return {
        restaurantId: restaurant.restaurantId,
        name: restaurant.name,
        crowdLevel: restaurant.crowdLevel,
        currentCount,
        queueTotal,
        servingTotal,
        openWindows: openWindowCount,
        totalWindows,
        loadRate: capacity > 0 ? round((currentCount / capacity) * 100, 1) : 0,
        avgWait: round(avgWaitMinutes({ restaurants: [{ ...restaurant }] }), 1),
      }
    })
    .sort((left, right) => right.currentCount - left.currentCount)

  const crowdBuckets = ['IDLE', 'NORMAL', 'BUSY', 'EXTREME'].map((level) => ({
    label: crowdLabel(level),
    value: openWindows.filter((entry) => entry.window.crowdLevel === level).length,
  }))

  const trend = (currentRun.value?.timePoints || []).map((timePoint) => {
    const entries = flattenWindows(timePoint)
    return {
      label: `${timePoint.minute} 分`,
      load: totalCurrentCount(timePoint),
      queue: totalQueueLength(timePoint),
      serving: entries.reduce((sum, entry) => sum + Number(entry.window.servingCount || 0), 0),
    }
  })
  
  return {
    minute: point.minute,
    minuteLabel: `createdAt ${formatTime(currentRun.value?.createdAt)}`,
    loadCount: totalCurrentCount(point),
    queueTotal: totalQueueLength(point),
    queueWindowCount: openWindows.filter((entry) => Number(entry.window.queueLength || 0) > 0).length,
    openWindowCount: openWindows.length,
    serviceTotal,
    sampleNote: currentRun.value?.metrics?.totalVirtualUsers
      ? `样本 ${currentRun.value.metrics.totalVirtualUsers} 人`
      : '',
    restaurants,
    crowdBuckets,
    trend,
    busyWindows: openWindows
      .map((entry) => ({
        restaurantName: entry.restaurantName,
        windowId: entry.window.windowId,
        name: entry.window.name,
        queueLength: Number(entry.window.queueLength || 0),
        servingCount: Number(entry.window.servingCount || 0),
        waitMinutes: Number(entry.window.waitMinutes || 0),
        crowdLevel: entry.window.crowdLevel,
      }))
      .sort((left, right) => {
        if (right.queueLength !== left.queueLength) return right.queueLength - left.queueLength
        return right.waitMinutes - left.waitMinutes
      })
      .slice(0, 8),
  }
})

onMounted(async () => {
  try {
    await store.initializeDashboard()
    if (!currentRun.value && profiles.value.length && scenarios.value.length) {
      await store.runCurrentSimulation()
    }
    renderCharts()
    window.addEventListener('resize', resizeCharts)
  } catch (error) {
    ElMessage.error(error.message || '人流快照页初始化失败')
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  flowTrendChart?.dispose()
  crowdChart?.dispose()
})

watch(
  () => snapshot.value,
  () => nextTick(renderCharts),
  { deep: true },
)

async function handleRefresh() {
  try {
    await store.runCurrentSimulation()
  } catch (error) {
    ElMessage.error(error.message || '重新运行仿真失败')
  }
}

function renderCharts() {
  renderFlowTrend()
  renderCrowdChart()
}

function renderFlowTrend() {
  if (!flowTrendRef.value || !snapshot.value?.trend?.length) {
    flowTrendChart?.dispose()
    flowTrendChart = null
    return
  }
  if (!flowTrendChart) {
    flowTrendChart = echarts.init(flowTrendRef.value)
  }

  flowTrendChart.setOption({
    color: ['#2563eb', '#f97316', '#14b8a6'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 42, right: 24, top: 42, bottom: 32 },
    xAxis: {
      type: 'category',
      data: snapshot.value.trend.map((item) => item.label),
      boundaryGap: false,
    },
    yAxis: {
      type: 'value',
      name: '人数',
    },
    series: [
      {
        name: '负载人数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: snapshot.value.trend.map((item) => item.load),
      },
      {
        name: '排队人数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: snapshot.value.trend.map((item) => item.queue),
      },
      {
        name: '服务中人数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: snapshot.value.trend.map((item) => item.serving),
      },
    ],
  })
}

function renderCrowdChart() {
  if (!crowdChartRef.value || !snapshot.value) {
    crowdChart?.dispose()
    crowdChart = null
    return
  }
  if (!crowdChart) {
    crowdChart = echarts.init(crowdChartRef.value)
  }

  crowdChart.setOption({
    color: ['#94a3b8', '#2563eb', '#f59e0b', '#ef4444'],
    tooltip: { trigger: 'axis' },
    grid: { left: 28, right: 12, top: 20, bottom: 28 },
    xAxis: {
      type: 'category',
      data: snapshot.value.crowdBuckets.map((item) => item.label),
    },
    yAxis: {
      type: 'value',
      name: '窗口数',
      minInterval: 1,
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 42,
        data: snapshot.value.crowdBuckets.map((item) => item.value),
      },
    ],
  })
}

function resizeCharts() {
  flowTrendChart?.resize()
  crowdChart?.resize()
}

function crowdLabel(level) {
  return (
    {
      IDLE: '空闲',
      NORMAL: '正常',
      BUSY: '繁忙',
      EXTREME: '极拥挤',
    }[level] || level
  )
}

function crowdClass(level) {
  return `crowd-${String(level || '').toLowerCase()}`
}

function formatTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function round(value, digits) {
  const base = 10 ** digits
  return Math.round(Number(value || 0) * base) / base
}
</script>

<style scoped>
.restaurant-zone {
  grid-column: span 7;
}

.crowd-zone {
  grid-column: span 5;
}

.trend-zone {
  grid-column: span 7;
}

.window-zone {
  grid-column: span 5;
}

.restaurant-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.restaurant-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.restaurant-head,
.window-main,
.restaurant-foot,
.window-metrics {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.restaurant-head strong,
.window-main strong {
  color: #0f172a;
  font-size: 15px;
}

.restaurant-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.restaurant-metrics div {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 14px;
  background: #f8fafc;
}

.restaurant-metrics span,
.restaurant-foot,
.window-metrics {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.restaurant-metrics strong {
  color: #1e3a8a;
  font-size: 18px;
}

.crowd-chip {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.crowd-idle {
  color: #475569;
  background: #e2e8f0;
}

.crowd-normal {
  color: #1d4ed8;
  background: #dbeafe;
}

.crowd-busy {
  color: #b45309;
  background: #fef3c7;
}

.crowd-extreme {
  color: #b91c1c;
  background: #fee2e2;
}

.window-list {
  display: grid;
  gap: 12px;
}

.window-item {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

@media (max-width: 1100px) {
  .restaurant-zone,
  .crowd-zone,
  .trend-zone,
  .window-zone {
    grid-column: auto;
  }

  .restaurant-grid {
    grid-template-columns: 1fr;
  }
}
</style>
