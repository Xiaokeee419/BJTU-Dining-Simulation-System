<template>
  <section class="panel queue-panel">
    <div class="panel-header queue-header">
      <div>
        <h2 class="panel-title">餐厅与窗口排队总览</h2>
        <p class="panel-subtitle">
          {{ hasTimeline ? '支持动态播放，也可以拖动时间轴查看任意时刻的排队快照。' : '当前展示单个时刻的排队快照。' }}
        </p>
      </div>
      <div class="header-meta">
        <span class="muted">第 {{ displayTimePoint?.minute ?? 0 }} 分钟</span>
        <div v-if="hasTimeline && showControls" class="header-actions">
          <el-button text :disabled="isAtStart" @click="stepMinute(-stepMinutesValue)">
            上一段
          </el-button>
          <el-button text @click="togglePlayback">
            {{ isPlaying ? '暂停' : '播放' }}
          </el-button>
          <el-button text :disabled="isAtEnd" @click="stepMinute(stepMinutesValue)">
            下一段
          </el-button>
        </div>
      </div>
    </div>

    <div v-if="hasTimeline && showControls" class="timeline-panel">
      <div class="timeline-meta">
        <span>当前查看第 {{ currentMinuteValue }} 分钟</span>
        <div class="timeline-actions">
          <button type="button" class="timeline-link" @click="jumpToMinute(startMinute)">
            回到起点
          </button>
          <button
            v-if="peakMinute !== null"
            type="button"
            class="timeline-link"
            @click="jumpToMinute(peakMinute)"
          >
            跳到高峰
          </button>
          <button type="button" class="timeline-link" @click="jumpToMinute(endMinute)">
            查看结束
          </button>
        </div>
      </div>

      <el-slider
        :model-value="currentMinuteValue"
        :min="startMinute"
        :max="endMinute"
        :step="1"
        @update:model-value="handleMinuteInput"
      />

      <div class="timeline-scale">
        <span>起点 {{ startMinute }} 分钟</span>
        <span v-if="peakMinute !== null">高峰 {{ peakMinute }} 分钟</span>
        <span>结束 {{ endMinute }} 分钟</span>
      </div>
    </div>

    <div class="panel-body queue-body">
      <div class="restaurant-pane">
        <div class="subhead">
          <span>餐厅列表</span>
          <span class="muted">点击餐厅查看当前时间点下的窗口排队详情</span>
        </div>

        <div class="restaurant-overview-list">
          <button
            v-for="restaurant in restaurants"
            :key="restaurant.restaurantId"
            type="button"
            class="restaurant-overview-card"
            :class="{ active: restaurant.restaurantId === selectedRestaurant?.restaurantId }"
            @click="selectedRestaurantId = restaurant.restaurantId"
          >
            <div class="restaurant-overview-head">
              <div class="restaurant-overview-title">
                <strong>{{ restaurant.name }}</strong>
              </div>
              <el-tag
                :type="tagType(getRestaurantCrowdLevel(restaurant))"
                effect="light"
                size="small"
              >
                {{ crowdLabel(getRestaurantCrowdLevel(restaurant)) }}
              </el-tag>
            </div>

            <div class="restaurant-overview-body">
              <div class="restaurant-overview-metric">
                <span>总排队人数</span>
                <strong>{{ getRestaurantQueueTotal(restaurant) }} 人</strong>
              </div>
              <div class="restaurant-overview-extra">
                <span>开放窗口 {{ openWindowCount(restaurant) }}</span>
                <span>平均等待 {{ averageWaitMinutes(restaurant) }} 分</span>
              </div>
            </div>

            <div class="restaurant-overview-bar" aria-hidden="true">
              <i
                :class="[
                  'restaurant-overview-bar-fill',
                  `tone-${restaurantQueueBarTone(restaurant)}`,
                ]"
                :style="{ width: restaurantQueueBarWidth(restaurant) }"
              ></i>
            </div>
          </button>

          <el-empty
            v-if="!restaurants.length"
            :image-size="72"
            description="当前时刻暂无餐厅数据"
          />
        </div>
      </div>

      <div class="window-pane">
        <template v-if="selectedRestaurant">
          <div class="subhead detail-subhead">
            <div>
              <strong class="detail-title">{{ selectedRestaurant.name }}</strong>
            </div>
            <el-tag
              class="density-tag"
              :type="tagType(getRestaurantCrowdLevel(selectedRestaurant))"
              effect="light"
            >
              {{ crowdLabel(getRestaurantCrowdLevel(selectedRestaurant)) }}
            </el-tag>
          </div>

          <div class="summary-strip">
            <div class="summary-item">
              <span>开放窗口</span>
              <strong>{{ openWindowCount(selectedRestaurant) }}</strong>
            </div>
            <div class="summary-item">
              <span>总排队人数</span>
              <strong>{{ getRestaurantQueueTotal(selectedRestaurant) }} 人</strong>
            </div>
            <div class="summary-item">
              <span>平均等待</span>
              <strong>{{ averageWaitMinutes(selectedRestaurant) }} 分</strong>
            </div>
            <div class="summary-item">
              <span>最大排队人数</span>
              <strong>{{ maxQueueLength(selectedRestaurant) }} 人</strong>
            </div>
          </div>

          <div class="window-chart-panel">
            <div
              v-if="selectedWindows.length"
              ref="windowQueueChartRef"
              class="window-queue-chart"
            ></div>
            <p v-if="selectedWindows.length" class="window-chart-note">
              当前窗口排队人数会随时间轴同步更新，关闭窗口会以灰色显示。
            </p>
            <el-empty
              v-else
              :image-size="72"
              description="当前餐厅在这个时刻没有窗口数据"
            />
          </div>
        </template>

        <el-empty v-else :image-size="72" description="暂无餐厅数据" />
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { interpolateTimePoint, resolvePeakTimePoint } from '../utils/simulationStats'

const props = defineProps({
  run: {
    type: Object,
    default: null,
  },
  timePoint: {
    type: Object,
    default: null,
  },
  minute: {
    type: Number,
    default: null,
  },
  initialMinute: {
    type: Number,
    default: null,
  },
  showControls: {
    type: Boolean,
    default: true,
  },
  playIntervalMs: {
    type: Number,
    default: 700,
  },
})

const emit = defineEmits(['update:minute', 'minute-change'])

const selectedRestaurantId = ref(null)
const localMinute = ref(null)
const isPlaying = ref(false)
const windowQueueChartRef = ref(null)

let playTimer = null
let windowQueueChart = null

const timePoints = computed(() =>
  [...(Array.isArray(props.run?.timePoints) ? props.run.timePoints : [])].sort(
    (left, right) => Number(left.minute || 0) - Number(right.minute || 0),
  ),
)

const hasTimeline = computed(() => timePoints.value.length > 1)

const startMinute = computed(() => Number(timePoints.value[0]?.minute || props.timePoint?.minute || 0))
const endMinute = computed(() => {
  const lastPoint = timePoints.value[timePoints.value.length - 1]
  return Number(lastPoint?.minute || props.timePoint?.minute || 0)
})

const peakMinute = computed(() => {
  if (!props.run) return finiteMinute(props.timePoint?.minute)
  const peakPoint = resolvePeakTimePoint(props.run)
  return finiteMinute(peakPoint?.minute)
})

const stepMinutesValue = computed(() => {
  if (timePoints.value.length < 2) return 1
  const firstDelta = Number(timePoints.value[1].minute || 0) - Number(timePoints.value[0].minute || 0)
  return Math.max(1, firstDelta || 1)
})

const currentMinuteValue = computed(() => {
  const preferredMinute = finiteMinute(props.minute)
  if (preferredMinute !== null) {
    return clampMinute(preferredMinute)
  }

  const localValue = finiteMinute(localMinute.value)
  if (localValue !== null) {
    return clampMinute(localValue)
  }

  return resolveInitialMinute()
})

const displayTimePoint = computed(() => {
  if (props.run) {
    return interpolateTimePoint(props.run, currentMinuteValue.value)
  }
  return props.timePoint
})

const restaurants = computed(() =>
  [...(displayTimePoint.value?.restaurants || [])].sort((left, right) => {
    const queueDelta = getRestaurantQueueTotal(right) - getRestaurantQueueTotal(left)
    if (queueDelta !== 0) return queueDelta

    const loadDelta = getRestaurantLoadRate(right) - getRestaurantLoadRate(left)
    if (loadDelta !== 0) return loadDelta

    return getRestaurantCurrentCount(right) - getRestaurantCurrentCount(left)
  }),
)

const selectedRestaurant = computed(
  () =>
    restaurants.value.find((restaurant) => restaurant.restaurantId === selectedRestaurantId.value) ||
    restaurants.value[0] ||
    null,
)

const selectedWindows = computed(() =>
  [...(selectedRestaurant.value?.windows || [])].sort((left, right) => {
    const statusDelta = Number(isWindowOpen(right)) - Number(isWindowOpen(left))
    if (statusDelta !== 0) return statusDelta

    const queueDelta = Number(right.queueLength || 0) - Number(left.queueLength || 0)
    if (queueDelta !== 0) return queueDelta

    const waitDelta = Number(right.waitMinutes || 0) - Number(left.waitMinutes || 0)
    if (waitDelta !== 0) return waitDelta

    return String(left.name || '').localeCompare(String(right.name || ''), 'zh-CN')
  }),
)

const maxRestaurantQueueTotal = computed(() =>
  restaurants.value.length
    ? Math.max(...restaurants.value.map((restaurant) => getRestaurantQueueTotal(restaurant)), 0)
    : 0,
)

watch(
  () => props.run,
  () => {
    if (finiteMinute(props.minute) !== null) return
    localMinute.value = resolveInitialMinute()
    stopPlayback()
  },
  { immediate: true },
)

watch(
  () => props.initialMinute,
  () => {
    if (finiteMinute(props.minute) !== null) return
    localMinute.value = resolveInitialMinute()
  },
)

watch(
  restaurants,
  (list) => {
    if (!list.length) {
      selectedRestaurantId.value = null
      return
    }

    const exists = list.some((restaurant) => restaurant.restaurantId === selectedRestaurantId.value)
    if (!exists) {
      selectedRestaurantId.value = list[0].restaurantId
    }
  },
  { immediate: true, deep: true },
)

watch(
  () => ({
    restaurantId: selectedRestaurant.value?.restaurantId ?? null,
    minute: currentMinuteValue.value,
    windows: selectedWindows.value.map((window) => [
      window.windowId,
      window.name,
      window.queueLength,
      window.waitMinutes,
      window.status,
      window.isOpen,
      window.open,
      window.crowdLevel,
    ]),
  }),
  () => nextTick(renderWindowQueueChart),
  { immediate: true, deep: true },
)

onMounted(() => {
  window.addEventListener('resize', resizeWindowQueueChart)
  nextTick(renderWindowQueueChart)
})

onBeforeUnmount(() => {
  stopPlayback()
  window.removeEventListener('resize', resizeWindowQueueChart)
  windowQueueChart?.dispose()
  windowQueueChart = null
})

const isAtStart = computed(() => currentMinuteValue.value <= startMinute.value)
const isAtEnd = computed(() => currentMinuteValue.value >= endMinute.value)

function handleMinuteInput(value) {
  jumpToMinute(value)
}

function stepMinute(delta) {
  jumpToMinute(currentMinuteValue.value + Number(delta || 0))
}

function jumpToMinute(value) {
  const nextMinute = clampMinute(value)
  if (finiteMinute(props.minute) === null) {
    localMinute.value = nextMinute
  }
  emit('update:minute', nextMinute)
  emit('minute-change', nextMinute)
}

function togglePlayback() {
  if (isPlaying.value) {
    stopPlayback()
    return
  }

  if (isAtEnd.value) {
    jumpToMinute(startMinute.value)
  }

  isPlaying.value = true
  playTimer = window.setInterval(() => {
    const nextMinute = clampMinute(currentMinuteValue.value + 1)
    jumpToMinute(nextMinute)
    if (nextMinute >= endMinute.value) {
      stopPlayback()
    }
  }, props.playIntervalMs)
}

function stopPlayback() {
  isPlaying.value = false
  if (playTimer != null) {
    window.clearInterval(playTimer)
    playTimer = null
  }
}

function resolveInitialMinute() {
  const explicitMinute = finiteMinute(props.initialMinute)
  if (explicitMinute !== null) {
    return clampMinute(explicitMinute)
  }

  const timePointMinute = finiteMinute(props.timePoint?.minute)
  if (timePointMinute !== null) {
    return timePointMinute
  }

  return clampMinute(startMinute.value)
}

function clampMinute(value) {
  const numericValue = finiteMinute(value)
  if (numericValue === null) return startMinute.value
  return Math.min(endMinute.value, Math.max(startMinute.value, numericValue))
}

function finiteMinute(value) {
  return Number.isFinite(Number(value)) ? Number(value) : null
}

function formatWaitMinutes(value) {
  const numericValue = Number(value || 0)
  return `${numericValue.toFixed(1).replace(/\.0$/, '')} 分`
}

function getRestaurantCurrentCount(restaurant) {
  return Number(
    restaurant?.currentCount ??
      restaurant?.currentPeople ??
      restaurant?.currentUserCount ??
      restaurant?.currentUsers ??
      0,
  )
}

function getRestaurantLoadRate(restaurant) {
  const capacity = Number(restaurant?.capacity ?? restaurant?.totalCapacity ?? 0)
  if (!capacity) return 0
  return getRestaurantCurrentCount(restaurant) / capacity
}

function getRestaurantQueueTotal(restaurant) {
  return (
    restaurant?.windows?.reduce((sum, window) => sum + Number(window.queueLength || window.queueCount || 0), 0) ||
    0
  )
}

function restaurantQueueBarWidth(restaurant) {
  const value = getRestaurantQueueTotal(restaurant)
  if (value <= 0) return '0%'
  const maxValue = Math.max(1, maxRestaurantQueueTotal.value)
  return `${Math.max(10, Math.round((value / maxValue) * 100))}%`
}

function restaurantQueueBarTone(restaurant) {
  return toneKey(getRestaurantCrowdLevel(restaurant))
}

function isWindowOpen(window) {
  if (typeof window?.isOpen === 'boolean') return window.isOpen
  if (typeof window?.open === 'boolean') return window.open
  return window?.status !== 'CLOSED'
}

function openWindowCount(restaurant) {
  return restaurant?.windows?.filter((window) => isWindowOpen(window)).length || 0
}

function averageWaitMinutes(restaurant) {
  const windows = restaurant?.windows?.filter((window) => isWindowOpen(window)) || []
  if (!windows.length) return 0
  const total = windows.reduce((sum, window) => sum + Number(window.waitMinutes || 0), 0)
  return Math.round((total / windows.length) * 10) / 10
}

function maxQueueLength(restaurant) {
  const queues =
    restaurant?.windows?.map((window) => Number(window.queueLength || window.queueCount || 0)) || []
  return queues.length ? Math.max(...queues) : 0
}

function resolveCrowdLevel({ level, queue, loadRate }) {
  if (['IDLE', 'NORMAL', 'BUSY', 'EXTREME'].includes(level)) {
    return level
  }

  if (Number.isFinite(loadRate) && loadRate > 0) {
    if (loadRate >= 0.9) return 'EXTREME'
    if (loadRate >= 0.7) return 'BUSY'
    if (loadRate >= 0.35) return 'NORMAL'
    return 'IDLE'
  }

  if (queue <= 5) return 'IDLE'
  if (queue <= 15) return 'NORMAL'
  if (queue <= 30) return 'BUSY'
  return 'EXTREME'
}

function getRestaurantCrowdLevel(restaurant) {
  return resolveCrowdLevel({
    level: restaurant?.crowdLevel,
    queue: getRestaurantQueueTotal(restaurant),
    loadRate: getRestaurantLoadRate(restaurant),
  })
}

function getWindowCrowdLevel(window) {
  return resolveCrowdLevel({
    level: window?.crowdLevel,
    queue: Number(window?.queueLength || window?.queueCount || 0),
    loadRate: Number(window?.loadRate),
  })
}

function renderWindowQueueChart() {
  if (!selectedWindows.value.length) {
    windowQueueChart?.dispose()
    windowQueueChart = null
    return
  }

  if (!windowQueueChartRef.value) return

  if (!windowQueueChart) {
    windowQueueChart = echarts.init(windowQueueChartRef.value)
  }

  const rows = selectedWindows.value.map((window) => ({
    ...window,
    queueValue: Number(window.queueLength || window.queueCount || 0),
    waitValue: Number(window.waitMinutes || 0),
    openState: isWindowOpen(window),
  }))

  windowQueueChart.setOption({
    animationDuration: 260,
    color: ['#2563eb'],
    grid: { left: 36, right: 20, top: 24, bottom: 92, containLabel: true },
    tooltip: {
      trigger: 'item',
      formatter: ({ data }) => {
        const row = data?.meta
        if (!row) return ''
        return [
          row.name,
          `排队人数：${row.queueValue} 人`,
          `平均等待：${formatWaitMinutes(row.waitValue)}`,
          `状态：${row.openState ? '开放' : '关闭'}`,
        ].join('<br/>')
      },
    },
    xAxis: {
      type: 'category',
      data: rows.map((row) => row.name),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#d7dfeb' } },
      axisLabel: {
        interval: 0,
        rotate: 35,
        color: '#475569',
        fontSize: 12,
        formatter: wrapAxisLabel,
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#e9eef5' } },
      axisLabel: {
        color: '#64748b',
      },
      name: '排队人数',
      nameTextStyle: {
        color: '#64748b',
        padding: [0, 0, 8, 0],
      },
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 34,
        data: rows.map((row) => ({
          value: row.queueValue,
          meta: row,
          itemStyle: {
            color: resolveWindowBarColor(row),
            borderRadius: [10, 10, 0, 0],
          },
        })),
        label: {
          show: true,
          position: 'top',
          color: '#334155',
          fontWeight: 700,
          formatter: ({ data }) => `${data.value}`,
        },
      },
    ],
  })

  resizeWindowQueueChart()
}

function wrapAxisLabel(value) {
  const text = String(value || '')
  if (text.length <= 8) return text
  const parts = []
  for (let index = 0; index < text.length; index += 8) {
    parts.push(text.slice(index, index + 8))
  }
  return parts.join('\n')
}

function resolveWindowBarColor(window) {
  if (!window.openState) return '#cbd5e1'

  switch (toneKey(getWindowCrowdLevel(window))) {
    case 'danger':
      return '#dc2626'
    case 'warning':
      return '#f59e0b'
    case 'primary':
      return '#2563eb'
    default:
      return '#60a5fa'
  }
}

function resizeWindowQueueChart() {
  windowQueueChart?.resize()
}

function crowdLabel(level) {
  return (
    {
      IDLE: '空闲',
      NORMAL: '正常',
      BUSY: '较忙',
      EXTREME: '拥挤',
    }[level] || '正常'
  )
}

function tagType(level) {
  return toneKey(level)
}

function toneKey(level) {
  return (
    {
      IDLE: 'success',
      NORMAL: 'primary',
      BUSY: 'warning',
      EXTREME: 'danger',
    }[level] || 'info'
  )
}
</script>

<style scoped>
.queue-header {
  align-items: flex-start;
}

.header-meta {
  display: grid;
  justify-items: end;
  gap: 8px;
}

.header-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.timeline-panel {
  padding: 14px 20px 10px;
  border-bottom: 1px solid var(--color-outline);
  background: linear-gradient(180deg, #fbfcff 0%, #ffffff 100%);
}

.timeline-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.timeline-actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.timeline-link {
  padding: 0;
  border: 0;
  color: var(--color-secondary);
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  background: transparent;
}

.timeline-link:hover {
  color: var(--color-primary-container);
}

.timeline-scale {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: #64748b;
  font-size: 11px;
}

.queue-body {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 18px;
}

.restaurant-pane,
.window-pane {
  min-width: 0;
}

.subhead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-subhead {
  align-items: flex-start;
}

.restaurant-overview-list {
  display: grid;
  gap: 12px;
  height: 540px;
  max-height: 540px;
  overflow-anchor: none;
  overflow-y: auto;
  padding-right: 6px;
}

.restaurant-overview-card {
  display: grid;
  gap: 14px;
  width: 100%;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  text-align: left;
  cursor: pointer;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.restaurant-overview-card:hover {
  border-color: #bfd1ea;
  box-shadow: 0 12px 28px rgb(37 99 235 / 8%);
  transform: translateY(-1px);
}

.restaurant-overview-card.active {
  border-color: #2563eb;
  background: linear-gradient(180deg, #f5f9ff 0%, #ffffff 100%);
  box-shadow: 0 0 0 1px rgb(37 99 235 / 12%);
}

.restaurant-overview-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.restaurant-overview-title {
  display: grid;
  gap: 6px;
}

.restaurant-overview-title strong {
  color: #172033;
  font-size: 16px;
  line-height: 1.3;
}

.restaurant-overview-title span {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.restaurant-overview-body {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.restaurant-overview-bar {
  height: 10px;
  border-radius: 999px;
  overflow: hidden;
  background: linear-gradient(180deg, #eef3f8 0%, #e5ebf3 100%);
}

.restaurant-overview-bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  transition: width 0.24s ease;
}

.restaurant-overview-bar-fill.tone-primary {
  background: linear-gradient(90deg, #60a5fa 0%, #2563eb 100%);
}

.restaurant-overview-bar-fill.tone-warning {
  background: linear-gradient(90deg, #fbbf24 0%, #f59e0b 100%);
}

.restaurant-overview-bar-fill.tone-danger {
  background: linear-gradient(90deg, #fb7185 0%, #dc2626 100%);
}

.restaurant-overview-bar-fill.tone-success {
  background: linear-gradient(90deg, #86efac 0%, #22c55e 100%);
}

.restaurant-overview-metric span,
.restaurant-overview-extra span {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.restaurant-overview-metric strong {
  display: block;
  margin-top: 6px;
  color: #172033;
  font-size: 24px;
  line-height: 1;
}

.restaurant-overview-extra {
  display: grid;
  gap: 6px;
  justify-items: end;
}

.detail-title {
  display: block;
  margin-bottom: 4px;
  font-size: 18px;
  color: #172033;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.summary-item {
  padding: 13px 12px;
  border: 1px solid #e8edf4;
  border-radius: 12px;
  background: linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
}

.summary-item span {
  display: block;
  margin-bottom: 6px;
  color: #657084;
  font-size: 12px;
  font-weight: 700;
}

.summary-item strong {
  color: #172033;
  font-size: 20px;
  line-height: 1.1;
}

.window-chart-panel {
  padding: 14px 16px 10px;
  border: 1px solid #e8edf4;
  border-radius: 14px;
  background: #fff;
}

.window-queue-chart {
  height: 360px;
}

.window-chart-note {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

@media (max-width: 1080px) {
  .queue-header,
  .timeline-meta,
  .restaurant-overview-body {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-meta {
    justify-items: start;
  }

  .restaurant-overview-extra {
    justify-items: start;
  }
}

@media (max-width: 900px) {
  .queue-body {
    grid-template-columns: 1fr;
  }

  .restaurant-overview-list {
    max-height: 420px;
  }

  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .timeline-scale {
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .subhead {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-strip {
    grid-template-columns: 1fr;
  }

  .window-queue-chart {
    height: 320px;
  }
}
</style>
