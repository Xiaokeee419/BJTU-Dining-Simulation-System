<template>
  <main class="page-shell page-stack">
    <PageHeader
      eyebrow="实时人流"
      title="食堂实时人流监测"
      description="实时展示各区域人流分布与用户偏好标签，辅助运营决策与资源调度。"
    >
      <template #actions>
        <span class="status-badge">最后更新：{{ updateLabel }}</span>
        <el-button plain @click="handleRefresh">刷新数据</el-button>
      </template>
    </PageHeader>

    <EmptyState
      v-if="!snapshot"
      title="请先运行仿真"
      description="当前还没有可用于展示实时人流的仿真结果。运行仿真后，这里会展示区域热力图、标签分布和人群样本。"
    />

    <template v-else>
      <section class="card-grid-4">
        <StatCard
          :icon="UserFilled"
          label="当前在场人数"
          :value="snapshot.currentPeople"
          unit="人"
          caption="较上一时间点"
          :delta="formatSigned(snapshot.peopleDelta)"
          :delta-state="snapshot.peopleDelta >= 0 ? 'positive' : 'negative'"
        />
        <StatCard
          :icon="Promotion"
          label="新进入人数"
          :value="snapshot.newEntries"
          unit="人"
          caption="本时间步"
          :delta="formatSigned(snapshot.newEntries)"
          delta-state="positive"
          tone="teal"
        />
        <StatCard
          :icon="Clock"
          label="平均停留时长"
          :value="snapshot.avgStayMinutes"
          unit="分钟"
          caption="估算值"
          :delta="`${currentMinute} 分钟快照`"
          delta-state="neutral"
          tone="orange"
        />
        <StatCard
          :icon="Grid"
          label="排队窗口数"
          :value="snapshot.queuedWindowCount"
          unit="个"
          caption="当前时间点"
          :delta="`${queueTotal} 人排队`"
          delta-state="neutral"
          tone="green"
        />
      </section>

      <section class="section-grid">
        <div class="flow-zone">
          <FlowHeatmapPanel :zones="snapshot.zoneCounts" />
        </div>
        <div class="tag-zone">
          <TagDistributionPanel :tags="snapshot.tagBreakdown" />
        </div>
      </section>

      <section class="section-grid">
        <div class="trend-zone">
          <ChartCard
            title="近 60 分钟人流变化"
            subtitle="基于当前仿真时间序列展示总在场人数变化。"
            :empty="!snapshot.trend.length"
          >
            <div ref="flowTrendRef" class="chart-surface"></div>
          </ChartCard>
        </div>
        <div class="table-zone">
          <UserProfileTable :items="snapshot.profiles" />
        </div>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import * as echarts from 'echarts'
import { Clock, Grid, Promotion, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChartCard from '../components/ChartCard.vue'
import EmptyState from '../components/EmptyState.vue'
import FlowHeatmapPanel from '../components/FlowHeatmapPanel.vue'
import PageHeader from '../components/PageHeader.vue'
import StatCard from '../components/StatCard.vue'
import TagDistributionPanel from '../components/TagDistributionPanel.vue'
import UserProfileTable from '../components/UserProfileTable.vue'
import { useSimulationStore } from '../stores/simulationStore'
import { buildRealtimeFlowSnapshot } from '../utils/realtimeFlow'
import { resolveTimePoint, totalCurrentCount, totalQueueLength } from '../utils/simulationStats'

const store = useSimulationStore()
const { currentRun, currentMinute, currentTimePoint, profileForm, profiles, scenarios, recommendation } = storeToRefs(store)

const displayMinute = computed(() => {
  const timePoints = currentRun.value?.timePoints || []
  if (!timePoints.length) return currentMinute.value

  const selectedPoint = resolveTimePoint(currentRun.value, currentMinute.value)
  if (selectedPoint && totalCurrentCount(selectedPoint) > 0) {
    return currentMinute.value
  }

  const peakPoint = timePoints.reduce((peak, point) =>
    totalCurrentCount(point) > totalCurrentCount(peak) ? point : peak,
  )
  return peakPoint?.minute ?? currentMinute.value
})

const snapshot = computed(() =>
  buildRealtimeFlowSnapshot(
    currentRun.value,
    profileForm.value,
    displayMinute.value,
    recommendation.value,
  ),
)
const queueTotal = computed(() =>
  snapshot.value ? totalQueueLength(resolveTimePoint(currentRun.value, displayMinute.value)) : 0,
)
const updateLabel = computed(() => {
  const raw = snapshot.value?.lastUpdated
  if (!raw) return '暂无数据'
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return raw
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`
})

const flowTrendRef = ref(null)
let flowTrendChart

onMounted(async () => {
  try {
    await store.initializeDashboard()
    if (!currentRun.value && profiles.value.length && scenarios.value.length) {
      await store.runCurrentSimulation()
    }
    renderChart()
    window.addEventListener('resize', resizeChart)
  } catch (error) {
    ElMessage.error(error.message || '实时人流页初始化失败')
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  flowTrendChart?.dispose()
})

watch(
  () => snapshot.value?.trend,
  () => nextTick(renderChart),
  { deep: true },
)

async function handleRefresh() {
  try {
    await store.runCurrentSimulation()
  } catch (error) {
    ElMessage.error(error.message || '刷新实时人流失败')
  }
}

function renderChart() {
  if (!flowTrendRef.value || !snapshot.value?.trend?.length) {
    flowTrendChart?.dispose()
    flowTrendChart = null
    return
  }
  if (!flowTrendChart) {
    flowTrendChart = echarts.init(flowTrendRef.value)
  }

  flowTrendChart.setOption({
    color: ['#2563eb'],
    tooltip: { trigger: 'axis' },
    grid: { left: 42, right: 24, top: 20, bottom: 32 },
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
        name: '在场人数',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        areaStyle: {
          color: 'rgb(37 99 235 / 10%)',
        },
        data: snapshot.value.trend.map((item) => item.count),
      },
    ],
  })
}

function resizeChart() {
  flowTrendChart?.resize()
}

function formatSigned(value) {
  const number = Number(value || 0)
  if (number > 0) return `+${number}`
  if (number < 0) return `${number}`
  return '0'
}
</script>

<style scoped>
.flow-zone {
  grid-column: span 8;
}

.tag-zone {
  grid-column: span 4;
}

.trend-zone {
  grid-column: span 8;
}

.table-zone {
  grid-column: span 4;
}

@media (max-width: 1100px) {
  .flow-zone,
  .tag-zone,
  .trend-zone,
  .table-zone {
    grid-column: auto;
  }
}
</style>
