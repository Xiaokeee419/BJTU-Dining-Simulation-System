<template>
  <main class="cockpit-page">
    <header class="cockpit-statusbar">
      <div class="cockpit-title">
        <h1>食堂排队驾驶舱</h1>
        <span></span>
      </div>

      <div class="simulation-status-list">
        <div class="simulation-status-item status-running">
          <i></i>
          <span>仿真状态：</span>
          <strong>{{ statusBar.status }}</strong>
        </div>
        <div class="simulation-status-item">
          <span>仿真时刻：</span>
          <strong>第 {{ statusBar.minute }} 分钟</strong>
        </div>
        <div class="simulation-status-item">
          <span>当前场景：</span>
          <strong>{{ statusBar.scene }}</strong>
        </div>
        <div class="simulation-status-item">
          <span>虚拟学生规模：</span>
          <strong>{{ statusBar.studentCount }} 人</strong>
        </div>
        <span class="phase-badge">{{ statusBar.phase }}</span>
      </div>
    </header>

    <div class="cockpit-canvas">
      <SimulationPlaybackBar
        :minute="playbackMinute"
        :start-minute="playbackStartMinute"
        :end-minute="playbackEndMinute"
        :rate="playbackRate"
        :has-run="Boolean(currentRun)"
        :is-playing="isPlaying"
        :running="running"
        :current-flow="liveSummary.currentFlow"
        :current-queue="liveSummary.currentQueue"
        @primary="handlePrimaryPlayback"
        @restart="restartPlayback"
        @rerun="rerunSimulation"
        @seek="seekPlayback"
      />

      <section class="cockpit-metrics" aria-label="核心仿真指标">
        <MetricCard
          v-for="item in metricCards"
          :key="item.key"
          :label="item.label"
          :value="item.value"
          :unit="item.unit"
          :status="item.status"
          :tone="item.tone"
          :status-tone="item.statusTone"
        />
      </section>

      <section class="cockpit-main-grid">
        <QueueOverview
          v-if="currentRun"
          :run="currentRun"
          v-model:minute="playbackMinute"
          :show-controls="false"
        />
        <section v-else class="stitch-panel queue-empty-panel">
          <EmptyState
            title="暂无排队快照"
            description="请先运行一次真实仿真，驾驶舱会在这里展示餐厅和窗口的实时排队情况。"
          />
        </section>

        <div class="cockpit-side-stack">
          <section class="stitch-panel pressure-panel">
            <header class="stitch-panel-header">
              <div class="stitch-panel-title">
                <span class="stitch-panel-symbol" aria-hidden="true">▥</span>
                <h2>高压窗口预警榜</h2>
              </div>
              <span class="update-note">优先关注最拥堵的窗口</span>
            </header>

            <div class="pressure-list">
              <template v-if="pressureWindows.length">
                <PressureBar
                  v-for="window in pressureWindows"
                  :key="window.id"
                  :name="window.name"
                  :queue-count="window.queueCount"
                  :wait-minutes="window.waitMinutes"
                  :percentage="window.percentage"
                  :status="window.status"
                  :tone="window.tone"
                />
              </template>
              <EmptyState
                v-else
                title="暂无窗口压力数据"
                description="完成一次真实仿真后，这里会显示当前时刻排队压力最高的窗口。"
              />
            </div>
          </section>

          <StrategySuggestionCard
            :available="strategySuggestion.available"
            :source="strategySuggestion.source"
            :target="strategySuggestion.target"
            :suggested-count="strategySuggestion.suggestedCount"
            :wait-reduction="strategySuggestion.waitReduction"
            :max-queue-reduction="strategySuggestion.maxQueueReduction"
            :reason="strategySuggestion.reason"
            @open="router.push('/recommendation')"
          />
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import EmptyState from '../components/EmptyState.vue'
import MetricCard from '../components/MetricCard.vue'
import PressureBar from '../components/PressureBar.vue'
import QueueOverview from '../components/QueueOverview.vue'
import SimulationPlaybackBar from '../components/SimulationPlaybackBar.vue'
import StrategySuggestionCard from '../components/StrategySuggestionCard.vue'
import { useSimulationStore } from '../stores/simulationStore'
import {
  avgWaitMinutes,
  busyWindowCount,
  extremeWindowCount,
  flattenWindows,
  interpolateTimePoint,
  maxWindowQueueLength,
  openWindowCount,
  totalCurrentCount,
  totalQueueLength,
} from '../utils/simulationStats'

const router = useRouter()
const store = useSimulationStore()
const {
  currentRun,
  scenarioForm,
  windows,
  recommendation,
  diversionResult,
  strategyComparison,
  running,
} = storeToRefs(store)

const playbackRate = 2
const playbackMinute = ref(0)
const isPlaying = ref(false)
let playbackTimer = null

const statusBar = computed(() => {
  const scenario = currentRun.value?.scenario || scenarioForm.value || {}
  const duration = playbackEndMinute.value || Number(scenario.durationMinutes || 20)
  const minute = currentRun.value ? playbackMinute.value : 0

  return {
    status: running.value
      ? '计算中'
      : isPlaying.value
        ? '运行中'
        : currentRun.value && minute >= playbackEndMinute.value
        ? '已完成'
        : currentRun.value
          ? '已暂停'
          : '待启动',
    minute,
    scene: resolveScenarioName(scenario),
    studentCount: pickNumber(
      currentRun.value?.metrics?.totalVirtualUsers,
      scenario.virtualUserCount,
      '--',
    ),
    phase: currentRun.value ? resolvePhase(minute, duration) : '等待仿真',
  }
})

const playbackStartMinute = computed(
  () => Number(currentRun.value?.timePoints?.[0]?.minute || 0),
)

const playbackEndMinute = computed(() => {
  const points = currentRun.value?.timePoints || []
  return Number(
    points[points.length - 1]?.minute ||
      currentRun.value?.scenario?.durationMinutes ||
      scenarioForm.value?.durationMinutes ||
      20,
  )
})

const activeTimePoint = computed(() =>
  currentRun.value
    ? interpolateTimePoint(currentRun.value, playbackMinute.value)
    : null,
)

const liveSummary = computed(() => ({
  currentFlow: activeTimePoint.value ? totalCurrentCount(activeTimePoint.value) : 0,
  currentQueue: activeTimePoint.value ? totalQueueLength(activeTimePoint.value) : 0,
}))

const metricCards = computed(() => {
  const metrics = currentRun.value?.metrics || {}
  const point = activeTimePoint.value
  if (!currentRun.value || !point) {
    return [
      {
        key: 'current-queue',
        label: '当前总队列',
        value: '--',
        unit: '人',
        status: '等待仿真',
        tone: 'primary',
        statusTone: 'neutral',
      },
      {
        key: 'max-queue',
        label: '最长窗口队列',
        value: '--',
        unit: '人',
        status: '等待仿真',
        tone: 'warning',
        statusTone: 'neutral',
      },
      {
        key: 'avg-wait',
        label: '窗口平均等待',
        value: '--',
        unit: '分钟',
        status: '等待仿真',
        tone: 'primary',
        statusTone: 'neutral',
      },
      {
        key: 'busy-windows',
        label: '高压窗口数',
        value: '--',
        unit: '个',
        status: '等待仿真',
        tone: 'warning',
        statusTone: 'neutral',
      },
    ]
  }
  const previousPoint = resolvePreviousPoint()
  const pointWait = point ? avgWaitMinutes(point) : null
  const previousWait = previousPoint ? avgWaitMinutes(previousPoint) : null
  const queueTotal = totalQueueLength(point)
  const previousQueueTotal = previousPoint ? totalQueueLength(previousPoint) : null
  const waitDelta =
    pointWait != null && previousWait != null ? round(pointWait - previousWait, 1) : 0
  const queueDelta =
    queueTotal != null && previousQueueTotal != null
      ? queueTotal - previousQueueTotal
      : 0
  const maxQueue = maxWindowQueueLength(point)
  const crowdedWindowCount = busyWindowCount(point) + extremeWindowCount(point)
  const openWindows = openWindowCount(point)

  return [
    {
      key: 'current-queue',
      label: '当前总队列',
      value: queueTotal,
      unit: '人',
      status:
        queueDelta === 0
          ? '与上一时刻持平'
          : `较上一时刻 ${queueDelta > 0 ? '+' : ''}${queueDelta} 人`,
      tone: queueTotal >= 80 ? 'danger' : queueTotal >= 35 ? 'warning' : 'primary',
      statusTone: queueDelta > 0 ? 'danger' : 'success',
    },
    {
      key: 'max-queue',
      label: '最长窗口队列',
      value: maxQueue,
      unit: '人',
      status: maxQueue >= 28 ? '压力上升' : '压力可控',
      tone: maxQueue >= 28 ? 'danger' : 'warning',
      statusTone: maxQueue >= 28 ? 'danger' : 'success',
    },
    {
      key: 'avg-wait',
      label: '窗口平均等待',
      value: pickNumber(pointWait, metrics.avgWaitMinutes, '--'),
      unit: '分钟',
      status:
        waitDelta === 0
          ? '等待稳定'
          : `较上一时刻 ${waitDelta > 0 ? '+' : ''}${waitDelta} 分钟`,
      tone: waitDelta > 0 ? 'warning' : 'primary',
      statusTone: waitDelta > 0 ? 'danger' : 'success',
    },
    {
      key: 'busy-windows',
      label: '高压窗口数',
      value: crowdedWindowCount,
      unit: '个',
      status: `开放窗口 ${openWindows} 个`,
      tone: crowdedWindowCount > 0 ? 'warning' : 'success',
      statusTone: crowdedWindowCount > 0 ? 'warning' : 'success',
    }
  ]
})

const pressureWindows = computed(() => {
  const entries = flattenWindows(activeTimePoint.value)
    .filter(({ window }) => window.status !== 'CLOSED')
    .map(({ restaurantName, window }) => {
      const queueCount = Number(window.queueLength || 0)
      const waitMinutes = Number(window.waitMinutes || 0)
      return {
        id: window.windowId ?? `${restaurantName}-${window.name}`,
        name: `${restaurantName} / ${window.name || `窗口 ${window.windowId}`}`,
        queueCount,
        waitMinutes,
        score: queueCount + waitMinutes * 2.5,
        ...resolveWindowPressure(queueCount, waitMinutes),
      }
    })
    .sort((left, right) => right.score - left.score)
    .slice(0, 6)

  if (!entries.length) return []

  const maxScore = Math.max(...entries.map((item) => item.score), 1)
  return entries.map((item) => ({
    ...item,
    percentage: Math.round(Math.max(12, (item.score / maxScore) * 92)),
  }))
})

const strategySuggestion = computed(() => {
  const suggestion = diversionResult.value?.suggestions?.[0]
  const comparison = strategyComparison.value
  const fallbackReason =
    typeof recommendation.value?.diversionSuggestion === 'string'
      ? recommendation.value.diversionSuggestion
      : '基于当前窗口压力与目标窗口承载能力生成的分流建议。'

  if (!suggestion) {
    return {
      available: false,
      source: '',
      target: '',
      suggestedCount: 0,
      waitReduction: null,
      maxQueueReduction: null,
      reason:
        diversionResult.value?.reason ||
        (currentRun.value
          ? '当前时刻没有达到分流触发条件的拥堵窗口，建议继续观察。'
          : '完成一次真实仿真后，这里会展示基于窗口压力生成的分流建议。'),
    }
  }

  return {
    available: true,
    source: resolveWindowLabel(suggestion.fromRestaurantId, suggestion.fromWindowId),
    target: resolveWindowLabel(suggestion.toRestaurantId, suggestion.toWindowId),
    suggestedCount: pickNumber(
      suggestion.suggestedUserCount,
      suggestion.estimatedAcceptedCount,
      0,
    ),
    waitReduction: round(
      pickNumber(
        suggestion.estimatedWaitReduction,
        comparison?.avgWaitDelta < 0 ? Math.abs(comparison.avgWaitDelta) : null,
        0,
      ),
      1,
    ),
    maxQueueReduction:
      comparison?.maxQueueDelta < 0 ? Math.round(Math.abs(comparison.maxQueueDelta)) : null,
    reason: suggestion.reason || diversionResult.value?.reason || fallbackReason,
  }
})

onMounted(async () => {
  try {
    await store.initializeDashboard()
    syncPlaybackToRun(currentRun.value)
  } catch (error) {
    ElMessage.warning(error.message || '仿真参数加载失败')
  }
})

onBeforeUnmount(stopPlayback)

watch(
  () => currentRun.value,
  (run) => syncPlaybackToRun(run),
)

function resolvePreviousPoint() {
  if (!currentRun.value) return null
  return interpolateTimePoint(
    currentRun.value,
    Math.max(playbackStartMinute.value, playbackMinute.value - playbackRate),
  )
}

function resolveScenarioName(scenario) {
  if (scenario?.name) return scenario.name
  const day = { WEEKDAY: '工作日', WEEKEND: '周末' }[scenario?.dayType] || '工作日'
  const meal =
    { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' }[scenario?.mealPeriod] || '午餐'
  const crowd =
    { IDLE: '平峰', NORMAL: '常态', BUSY: '高峰', EXTREME: '极端高峰' }[
      scenario?.crowdLevel
    ] || '高峰'
  return `${day}${meal}${crowd}`
}

function resolvePhase(minute, duration) {
  const progress = duration > 0 ? minute / duration : 0
  if (progress <= 0.65) return '高峰上升期'
  if (progress <= 0.85) return '高峰持续期'
  return '高峰回落期'
}

function resolveWindowPressure(queueCount, waitMinutes) {
  if (waitMinutes >= 8 || queueCount >= 22) {
    return { status: '拥堵', tone: 'danger' }
  }
  if (waitMinutes >= 4 || queueCount >= 10) {
    return { status: '压力较高', tone: 'warning' }
  }
  return { status: '正常', tone: 'success' }
}

function resolveWindowLabel(restaurantId, windowId) {
  const restaurant = activeTimePoint.value?.restaurants?.find(
    (item) => Number(item.restaurantId) === Number(restaurantId),
  )
  const snapshotWindow = restaurant?.windows?.find(
    (item) => Number(item.windowId) === Number(windowId),
  )
  const parameterWindow = windows.value.find(
    (item) => Number(item.windowId) === Number(windowId),
  )
  const restaurantName = restaurant?.name || `食堂 ${restaurantId}`
  const windowName = snapshotWindow?.name || parameterWindow?.name || `窗口 ${windowId}`
  return `${restaurantName}${windowName}`
}

async function handlePrimaryPlayback() {
  if (!currentRun.value) {
    await rerunSimulation()
    return
  }
  if (isPlaying.value) {
    stopPlayback()
    return
  }
  if (playbackMinute.value >= playbackEndMinute.value) {
    playbackMinute.value = playbackStartMinute.value
  }
  startPlayback()
}

async function rerunSimulation() {
  stopPlayback()
  try {
    await store.runCurrentSimulation()
    playbackMinute.value = playbackStartMinute.value
    startPlayback()
  } catch (error) {
    ElMessage.error(error.message || '仿真运行失败')
  }
}

function restartPlayback() {
  stopPlayback()
  playbackMinute.value = playbackStartMinute.value
  startPlayback()
}

function seekPlayback(minute) {
  stopPlayback()
  playbackMinute.value = clampMinute(minute)
}

function startPlayback() {
  if (!currentRun.value || isPlaying.value) return
  isPlaying.value = true
  playbackTimer = window.setInterval(() => {
    const nextMinute = Math.min(
      playbackEndMinute.value,
      playbackMinute.value + playbackRate,
    )
    playbackMinute.value = nextMinute
    if (nextMinute >= playbackEndMinute.value) {
      stopPlayback()
    }
  }, 1000)
}

function stopPlayback() {
  isPlaying.value = false
  if (playbackTimer != null) {
    window.clearInterval(playbackTimer)
    playbackTimer = null
  }
}

function syncPlaybackToRun(run) {
  stopPlayback()
  if (!run?.timePoints?.length) {
    playbackMinute.value = 0
    return
  }
  playbackMinute.value = Number(run.timePoints[0].minute || 0)
}

function clampMinute(minute) {
  return Math.min(
    playbackEndMinute.value,
    Math.max(playbackStartMinute.value, Number(minute || 0)),
  )
}

function pickNumber(...values) {
  const value = values.find((item) => item !== null && item !== undefined && Number.isFinite(Number(item)))
  return value == null ? 0 : Number(value)
}

function round(value, digits = 0) {
  const factor = 10 ** digits
  return Math.round(Number(value || 0) * factor) / factor
}
</script>

<style scoped>
.cockpit-page {
  min-height: 100%;
}

.cockpit-statusbar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  min-height: 64px;
  align-items: center;
  gap: 24px;
  padding: 0 24px;
  border-bottom: 1px solid var(--color-outline);
  background: rgb(250 248 255 / 96%);
  backdrop-filter: blur(14px);
}

.cockpit-title {
  display: flex;
  align-items: center;
  gap: 20px;
  flex: 0 0 auto;
}

.cockpit-title h1 {
  margin: 0;
  color: var(--color-primary);
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 800;
}

.cockpit-title span {
  width: 1px;
  height: 24px;
  background: var(--color-outline);
}

.simulation-status-list {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 22px;
  flex-wrap: wrap;
}

.simulation-status-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-on-surface-variant);
  font-size: 13px;
  white-space: nowrap;
}

.simulation-status-item strong {
  color: var(--color-on-surface);
  font-family: var(--font-data);
  font-weight: 650;
}

.status-running i {
  width: 8px;
  height: 8px;
  margin-right: 5px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 3px rgb(34 197 94 / 10%);
}

.phase-badge {
  padding: 5px 10px;
  border-radius: 3px;
  color: #004378;
  background: #a2c9ff;
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.cockpit-canvas {
  display: grid;
  gap: 24px;
  width: min(1480px, calc(100% - 48px));
  margin: 0 auto;
  padding: 30px 0 44px;
}

.cockpit-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.cockpit-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.75fr) minmax(320px, 0.9fr);
  gap: 16px;
  align-items: start;
}

.cockpit-side-stack {
  display: grid;
  gap: 16px;
  align-content: start;
}

.stitch-panel {
  min-width: 0;
  border: 1px solid var(--color-outline);
  border-radius: var(--radius-panel);
  background: #fff;
  box-shadow: var(--shadow-card);
}

.pressure-panel {
  padding: 26px 30px 30px;
}

.stitch-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 30px;
}

.stitch-panel-title {
  display: flex;
  align-items: center;
  gap: 11px;
}

.stitch-panel-title h2 {
  margin: 0;
  color: var(--color-primary);
  font-family: var(--font-heading);
  font-size: 19px;
}

.stitch-panel-symbol {
  color: var(--color-secondary);
  font-size: 21px;
  font-weight: 800;
}

.update-note {
  color: var(--color-subtle);
  font-size: 11px;
}

.pressure-list {
  display: grid;
  gap: 22px;
}

.queue-empty-panel {
  display: grid;
  min-height: 520px;
  place-items: center;
  padding: 24px;
}

@media (max-width: 1320px) {
  .cockpit-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1080px) {
  .cockpit-statusbar {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
    padding-top: 14px;
    padding-bottom: 14px;
  }

  .cockpit-title span {
    display: none;
  }

  .cockpit-main-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 780px) {
  .cockpit-canvas {
    width: min(100%, calc(100% - 28px));
    padding-top: 18px;
  }

  .cockpit-metrics {
    grid-template-columns: 1fr;
  }

  .simulation-status-list {
    gap: 10px 16px;
  }

  .pressure-panel {
    padding: 20px 18px 24px;
  }
}

@media (max-width: 520px) {
  .cockpit-statusbar {
    position: static;
    padding-right: 14px;
    padding-left: 14px;
  }

  .simulation-status-list {
    display: grid;
    grid-template-columns: 1fr;
  }

  .stitch-panel-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
