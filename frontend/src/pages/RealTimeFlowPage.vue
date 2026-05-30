<template>
  <main class="page-shell page-stack">
    <PageHeader
      eyebrow="人流快照"
      title="仿真人流快照"
      description="本页基于仿真 timePoint 快照展示各餐厅窗口排队与服务压力。负载人数由窗口排队人数和服务中人数汇总得到，不代表实际座位占用人数。"
    >
      <template #actions>
        <span v-if="snapshot" class="status-badge">高峰快照第 {{ snapshot.minute }} 分钟</span>
        <el-button plain @click="router.push('/config')">前往参数配置</el-button>
      </template>
    </PageHeader>

    <section v-if="!snapshot" class="empty-shell">
      <EmptyState
        title="暂无可查看的人流快照"
        description="请先到参数配置页运行一次未分流仿真，再返回本页查看高峰排队与服务压力。"
      />
      <el-button type="primary" @click="router.push('/config')">前往参数配置</el-button>
    </section>

    <template v-else>
      <section class="card-grid-4">
        <StatCard
          :icon="Clock"
          label="快照分钟"
          :value="snapshot.minute"
          unit="分钟"
          caption="默认高峰快照"
          :delta="snapshot.createdAtLabel"
          delta-state="neutral"
        />
        <StatCard
          :icon="UserFilled"
          label="高峰负载人数"
          :value="snapshot.loadCount"
          unit="人"
          caption="排队/服务负载"
          :delta="snapshot.loadNote"
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
            title="Top 5 排队压力最高餐厅"
            subtitle="按平均窗口等待、总排队人数和负载人数排序，优先展示最需要关注的餐厅。"
          >
            <div class="restaurant-list">
              <article
                v-for="restaurant in snapshot.topRestaurants"
                :key="restaurant.restaurantId"
                class="restaurant-card"
              >
                <div class="restaurant-head">
                  <div>
                    <strong>{{ restaurant.name }}</strong>
                    <span class="restaurant-sub">{{ restaurant.pressureLabel }}排队压力</span>
                  </div>
                  <span class="pressure-chip" :class="`pressure-${restaurant.pressureTone}`">
                    {{ restaurant.pressureLabel }}
                  </span>
                </div>
                <div class="restaurant-metrics">
                  <span>排队/服务负载 {{ restaurant.currentCount }} 人</span>
                  <span>排队人数 {{ restaurant.queueTotal }} 人</span>
                  <span>平均窗口等待 {{ restaurant.avgWait }} 分钟</span>
                  <span>开放窗口 {{ restaurant.openWindows }} / {{ restaurant.totalWindows }}</span>
                </div>
              </article>
            </div>
          </SectionCard>
        </div>

        <div class="window-zone">
          <SectionCard
            title="Top 5 等待时间最高窗口"
            subtitle="按等待时长和排队人数排序，优先展示最拥挤的窗口。"
          >
            <div class="window-list">
              <article v-for="window in snapshot.topWindows" :key="window.windowId" class="window-item">
                <div class="window-head">
                  <div>
                    <strong>{{ window.name }}</strong>
                    <span class="window-sub">{{ window.restaurantName }}</span>
                  </div>
                  <span class="crowd-chip" :class="`crowd-${window.crowdTone}`">
                    {{ window.crowdLabel }}
                  </span>
                </div>
                <div class="window-metrics">
                  <span>queueLength {{ window.queueLength }}</span>
                  <span>waitMinutes {{ window.waitMinutes }}</span>
                  <span>{{ window.crowdLevel }}</span>
                  <span>{{ window.status }}</span>
                </div>
              </article>
            </div>
          </SectionCard>
        </div>
      </section>

      <section class="section-grid">
        <div class="distribution-zone">
          <ChartCard
            title="窗口拥挤等级分布"
            subtitle="统计当前高峰快照中各拥挤等级窗口数量。"
            :empty="!snapshot.crowdBuckets.some((item) => item.value > 0)"
            empty-title="当前没有窗口快照"
            empty-description="请先运行仿真后再查看高峰快照的窗口拥挤分布。"
          >
            <div ref="crowdChartRef" class="chart-surface"></div>
          </ChartCard>
        </div>

        <div class="all-zone">
          <SectionCard
            title="查看全部餐厅快照"
            subtitle="默认折叠，避免全部餐厅信息干扰高峰排队压力判断。"
          >
            <el-collapse>
              <el-collapse-item name="all-restaurants" title="展开全部餐厅快照">
                <div class="restaurant-list">
                  <article
                    v-for="restaurant in snapshot.restaurants"
                    :key="`all-${restaurant.restaurantId}`"
                    class="restaurant-card compact"
                  >
                    <div class="restaurant-head">
                      <div>
                        <strong>{{ restaurant.name }}</strong>
                        <span class="restaurant-sub">{{ restaurant.pressureLabel }}排队压力</span>
                      </div>
                      <span class="pressure-chip" :class="`pressure-${restaurant.pressureTone}`">
                        {{ restaurant.pressureLabel }}
                      </span>
                    </div>
                    <div class="restaurant-metrics">
                      <span>排队/服务负载 {{ restaurant.currentCount }} 人</span>
                      <span>排队人数 {{ restaurant.queueTotal }} 人</span>
                      <span>平均窗口等待 {{ restaurant.avgWait }} 分钟</span>
                      <span>开放窗口 {{ restaurant.openWindows }} / {{ restaurant.totalWindows }}</span>
                    </div>
                  </article>
                </div>
              </el-collapse-item>
            </el-collapse>
          </SectionCard>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
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
  openWindowCount,
  queuePressureLevel,
  resolvePeakTimePoint,
  totalCurrentCount,
  totalQueueLength,
  totalServingCount,
} from '../utils/simulationStats'

const router = useRouter()
const store = useSimulationStore()
const { currentRun } = storeToRefs(store)

const crowdChartRef = ref(null)
let crowdChart

const snapshot = computed(() => {
  if (currentRun.value?.status !== 'FINISHED') {
    return null
  }
  const point = resolvePeakTimePoint(currentRun.value)
  if (!point) {
    return null
  }

  const windowEntries = flattenWindows(point).filter(({ window }) => window.status !== 'CLOSED')
  const restaurants = (point.restaurants || [])
    .map((restaurant) => {
      const restaurantPoint = { restaurants: [restaurant] }
      const avgWait = avgWaitMinutes(restaurantPoint)
      const pressure = queuePressureLevel(avgWait)
      const queueTotal = (restaurant.windows || []).reduce(
        (sum, window) => sum + Number(window.queueLength || 0),
        0,
      )
      return {
        restaurantId: restaurant.restaurantId,
        name: restaurant.name,
        currentCount: Number(restaurant.currentCount || 0),
        queueTotal,
        avgWait,
        openWindows: (restaurant.windows || []).filter((window) => window.status !== 'CLOSED').length,
        totalWindows: (restaurant.windows || []).length,
        pressureLabel: pressure.label,
        pressureTone: pressure.tone,
      }
    })
    .sort((left, right) => {
      if (right.avgWait !== left.avgWait) return right.avgWait - left.avgWait
      if (right.queueTotal !== left.queueTotal) return right.queueTotal - left.queueTotal
      return right.currentCount - left.currentCount
    })

  const topWindows = windowEntries
    .map(({ restaurantName, window }) => ({
      restaurantName,
      windowId: window.windowId,
      name: window.name || `窗口 ${window.windowId}`,
      queueLength: Number(window.queueLength || 0),
      waitMinutes: Number(window.waitMinutes || 0),
      crowdLevel: window.crowdLevel,
      crowdLabel: crowdLabel(window.crowdLevel),
      crowdTone: crowdTone(window.crowdLevel),
      status: window.status,
    }))
    .sort((left, right) => {
      if (right.waitMinutes !== left.waitMinutes) return right.waitMinutes - left.waitMinutes
      return right.queueLength - left.queueLength
    })
    .slice(0, 5)

  const crowdBuckets = ['IDLE', 'NORMAL', 'BUSY', 'EXTREME'].map((level) => ({
    label: crowdLabel(level),
    value: windowEntries.filter(({ window }) => window.crowdLevel === level).length,
  }))

  return {
    minute: point.minute,
    createdAtLabel: `生成于 ${formatTime(currentRun.value?.createdAt)}`,
    loadCount: totalCurrentCount(point),
    queueTotal: totalQueueLength(point),
    queueWindowCount: windowEntries.filter(({ window }) => Number(window.queueLength || 0) > 0).length,
    openWindowCount: openWindowCount(point),
    serviceTotal: totalServingCount(point),
    loadNote: currentRun.value?.metrics?.totalVirtualUsers
      ? `仿真样本 ${currentRun.value.metrics.totalVirtualUsers} 人`
      : '',
    restaurants,
    topRestaurants: restaurants.slice(0, 5),
    topWindows,
    crowdBuckets,
  }
})

onMounted(async () => {
  try {
    await store.initializeDashboard()
    renderCrowdChart()
    window.addEventListener('resize', resizeCharts)
  } catch (error) {
    ElMessage.error(error.message || '人流快照页初始化失败')
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  crowdChart?.dispose()
})

watch(
  () => snapshot.value,
  () => nextTick(renderCrowdChart),
  { deep: true },
)

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
    grid: { left: 30, right: 12, top: 20, bottom: 30 },
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
        barMaxWidth: 40,
        data: snapshot.value.crowdBuckets.map((item) => item.value),
      },
    ],
  })
}

function resizeCharts() {
  crowdChart?.resize()
}

function crowdLabel(level) {
  return (
    {
      IDLE: '空闲',
      NORMAL: '正常',
      BUSY: '繁忙',
      EXTREME: '极端拥挤',
    }[level] || level
  )
}

function crowdTone(level) {
  return String(level || '').toLowerCase()
}

function formatTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.empty-shell {
  display: grid;
  justify-items: center;
  gap: 12px;
}

.restaurant-zone,
.distribution-zone {
  grid-column: span 7;
}

.window-zone,
.all-zone {
  grid-column: span 5;
}

.restaurant-list,
.window-list {
  display: grid;
  gap: 12px;
}

.restaurant-card,
.window-item {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.restaurant-card.compact {
  padding: 14px;
}

.restaurant-head,
.window-head,
.restaurant-metrics,
.window-metrics {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.restaurant-head strong,
.window-head strong {
  color: #0f172a;
  font-size: 15px;
}

.restaurant-sub,
.window-sub,
.restaurant-metrics,
.window-metrics {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.restaurant-head > div,
.window-head > div {
  display: grid;
  gap: 4px;
}

.pressure-chip,
.crowd-chip {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.pressure-low {
  color: #475569;
  background: #e2e8f0;
}

.pressure-medium {
  color: #1d4ed8;
  background: #dbeafe;
}

.pressure-high {
  color: #b45309;
  background: #fef3c7;
}

.pressure-extreme,
.crowd-extreme {
  color: #b91c1c;
  background: #fee2e2;
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

.chart-surface {
  min-height: 280px;
}

@media (max-width: 1100px) {
  .restaurant-zone,
  .window-zone,
  .distribution-zone,
  .all-zone {
    grid-column: auto;
  }
}
</style>
