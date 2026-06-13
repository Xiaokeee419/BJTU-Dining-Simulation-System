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
          <span class="muted">点击柱子查看餐厅窗口排队</span>
        </div>
        <div class="restaurant-chart">
          <button
            v-for="restaurant in restaurants"
            :key="restaurant.restaurantId"
            type="button"
            class="restaurant-bar"
            :class="{ active: restaurant.restaurantId === selectedRestaurant?.restaurantId }"
            @click="selectedRestaurantId = restaurant.restaurantId"
          >
            <div class="restaurant-bar-value">
              {{ restaurantQueueCount(restaurant) }}
            </div>
            <div class="restaurant-bar-track" aria-hidden="true">
              <i
                :class="['restaurant-bar-fill', `tone-${restaurantBarTone(restaurant)}`]"
                :style="{ height: restaurantBarHeight(restaurant) }"
              ></i>
            </div>
            <div class="restaurant-bar-name">
              {{ restaurant.name }}
            </div>
            <div class="restaurant-bar-meta">
              总排队人数
            </div>
          </button>
        </div>
      </div>

      <div class="window-pane">
        <template v-if="selectedRestaurant">
          <div class="subhead">
            <div>
              <strong class="detail-title">{{ selectedRestaurant.name }}</strong>
              <span class="muted">
                当前人数 {{ selectedRestaurant.currentCount }} / {{ selectedRestaurant.capacity }}
              </span>
            </div>
            <el-tag
              class="density-tag"
              :type="tagType(selectedRestaurant.crowdLevel)"
              effect="light"
            >
              {{ crowdLabel(selectedRestaurant.crowdLevel) }}
            </el-tag>
          </div>

          <div class="summary-strip">
            <div class="summary-item">
              <span>开放窗口</span>
              <strong>{{ openWindowCount(selectedRestaurant) }}</strong>
            </div>
            <div class="summary-item">
              <span>总排队</span>
              <strong>{{ restaurantQueueCount(selectedRestaurant) }} 人</strong>
            </div>
            <div class="summary-item">
              <span>平均等待</span>
              <strong>{{ averageWaitMinutes(selectedRestaurant) }} 分</strong>
            </div>
            <div class="summary-item">
              <span>最大排队</span>
              <strong>{{ maxQueueLength(selectedRestaurant) }} 人</strong>
            </div>
          </div>

          <div class="window-chart-panel">
            <div
              v-if="openSelectedWindows.length"
              ref="windowQueueChartRef"
              class="window-queue-chart"
            ></div>
            <p v-if="openSelectedWindows.length" class="window-chart-note">
              按当前窗口排队人数排序，优先关注该餐厅最拥堵窗口。
            </p>
            <div v-if="openSelectedWindows.length" class="window-bar-list">
              <article
                v-for="row in openSelectedWindows"
                :key="row.windowId"
                class="window-bar-card"
              >
                <div class="window-bar-head">
                  <strong>{{ row.name }}</strong>
                  <span>{{ row.queueLength }} 人</span>
                </div>
                <div class="window-bar-track" aria-hidden="true">
                  <i
                    :class="['window-bar-fill', `tone-${windowBarTone(row)}`]"
                    :style="{ width: queueBarWidth(row) }"
                  ></i>
                </div>
                <div class="window-bar-foot">
                  <span>等待 {{ formatWaitMinutes(row.waitMinutes) }}</span>
                  <el-tag :type="tagType(row.crowdLevel)" effect="light" size="small">
                    {{ crowdLabel(row.crowdLevel) }}
                  </el-tag>
                </div>
              </article>
            </div>
            <el-empty
              v-else
              :image-size="72"
              description="当前时刻没有开放窗口"
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
    const queueDelta = restaurantQueueCount(right) - restaurantQueueCount(left)
    if (queueDelta !== 0) return queueDelta

    const waitDelta = averageWaitMinutes(right) - averageWaitMinutes(left)
    if (waitDelta !== 0) return waitDelta

    return Number(right.currentCount || 0) - Number(left.currentCount || 0)
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
    const statusDelta = windowStatusRank(left) - windowStatusRank(right)
    if (statusDelta !== 0) return statusDelta

    const queueDelta = Number(right.queueLength || 0) - Number(left.queueLength || 0)
    if (queueDelta !== 0) return queueDelta

    const waitDelta = Number(right.waitMinutes || 0) - Number(left.waitMinutes || 0)
    if (waitDelta !== 0) return waitDelta

    return Number(right.servingCount || 0) - Number(left.servingCount || 0)
  }),
)

const openSelectedWindows = computed(() =>
  selectedWindows.value.filter((window) => window.status !== 'CLOSED'),
)

const selectedWindowMaxQueue = computed(() => {
  const values = openSelectedWindows.value.map((window) => Number(window.queueLength || 0))
  return values.length ? Math.max(...values, 0) : 0
})

const maxRestaurantQueueCount = computed(() => {
  const values = restaurants.value.map((restaurant) => restaurantQueueCount(restaurant))
  return values.length ? Math.max(...values, 0) : 0
})

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
  () => [selectedRestaurant.value?.restaurantId, openSelectedWindows.value, currentMinuteValue.value],
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

function restaurantQueueCount(restaurant) {
  return restaurant?.windows?.reduce(
    (sum, window) => sum + Number(window.queueLength || 0),
    0,
  ) || 0
}

function restaurantBarHeight(restaurant) {
  const value = restaurantQueueCount(restaurant)
  if (value <= 0) return '0%'
  const maxQueue = Math.max(1, maxRestaurantQueueCount.value)
  return `${Math.max(8, Math.round((value / maxQueue) * 100))}%`
}

function restaurantBarTone(restaurant) {
  if (restaurant?.crowdLevel === 'EXTREME') return 'danger'
  if (restaurant?.crowdLevel === 'BUSY') return 'warning'
  return 'primary'
}

function openWindowCount(restaurant) {
  return restaurant?.windows?.filter((window) => window.status !== 'CLOSED').length || 0
}

function averageWaitMinutes(restaurant) {
  const windows = restaurant?.windows?.filter((window) => window.status !== 'CLOSED') || []
  if (!windows.length) return 0
  const total = windows.reduce((sum, window) => sum + Number(window.waitMinutes || 0), 0)
  return Math.round((total / windows.length) * 10) / 10
}

function maxQueueLength(restaurant) {
  const queues = restaurant?.windows?.map((window) => Number(window.queueLength || 0)) || []
  return queues.length ? Math.max(...queues) : 0
}

function queueBarWidth(window) {
  const value = Number(window?.queueLength || 0)
  if (value <= 0) return '0%'
  const maxQueue = Math.max(1, selectedWindowMaxQueue.value)
  return `${Math.max(8, Math.round((value / maxQueue) * 100))}%`
}

function windowBarTone(window) {
  if (window?.crowdLevel === 'EXTREME') return 'danger'
  if (window?.crowdLevel === 'BUSY') return 'warning'
  return 'primary'
}

function windowStatusRank(window) {
  return window?.status === 'CLOSED' ? 1 : 0
}

function renderWindowQueueChart() {
  if (!openSelectedWindows.value.length) {
    windowQueueChart?.dispose()
    windowQueueChart = null
    return
  }

  if (!windowQueueChartRef.value) return

  if (!windowQueueChart) {
    windowQueueChart = echarts.init(windowQueueChartRef.value)
  }

  const rows = [...openSelectedWindows.value].sort((left, right) => {
    const queueDelta = Number(right.queueLength || 0) - Number(left.queueLength || 0)
    if (queueDelta !== 0) return queueDelta
    return Number(right.waitMinutes || 0) - Number(left.waitMinutes || 0)
  })

  windowQueueChart.setOption({
    animationDuration: 260,
    color: ['#2563eb'],
    grid: { left: 18, right: 28, top: 8, bottom: 12, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const row = params?.[0]?.data?.meta
        if (!row) return ''
        return [
          row.name,
          `排队 ${row.queueLength} 人`,
          `等待 ${formatWaitMinutes(row.waitMinutes)}`,
        ].join('<br/>')
      },
    },
    xAxis: {
      type: 'value',
      name: '排队人数',
      minInterval: 1,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#e5ebf3' } },
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: rows.map((row) => row.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: '#172033',
        fontWeight: 700,
        width: 170,
        overflow: 'truncate',
      },
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 18,
        label: {
          show: true,
          position: 'right',
          color: '#475569',
          fontWeight: 700,
          formatter: ({ data }) => `${data.value} 人`,
        },
        data: rows.map((row) => ({
          value: Number(row.queueLength || 0),
          meta: row,
          itemStyle: {
            color:
              row.crowdLevel === 'EXTREME'
                ? '#dc2626'
                : row.crowdLevel === 'BUSY'
                  ? '#2563eb'
                  : '#60a5fa',
            borderRadius: [0, 999, 999, 0],
          },
        })),
      },
    ],
  })
}

function resizeWindowQueueChart() {
  windowQueueChart?.resize()
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

function tagType(level) {
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
  grid-template-columns: minmax(280px, 0.9fr) minmax(480px, 1.35fr);
  gap: 16px;
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

.restaurant-chart {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  max-height: 420px;
  min-height: 420px;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 10px 4px 2px 0;
}

.restaurant-bar {
  display: grid;
  grid-template-rows: auto minmax(220px, 1fr) auto auto;
  gap: 8px;
  align-items: end;
  flex: 0 0 74px;
  height: 100%;
  padding: 10px 8px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  cursor: pointer;
  background: #fff;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.restaurant-bar:hover {
  border-color: #cbd5e1;
  box-shadow: 0 10px 20px rgb(23 32 51 / 6%);
}

.restaurant-bar.active {
  border-color: #9f1239;
  background: linear-gradient(180deg, #fff8fb 0%, #ffffff 100%);
  box-shadow: 0 0 0 1px rgb(159 18 57 / 12%);
}

.restaurant-bar-value {
  color: #172033;
  font-size: 20px;
  font-weight: 800;
  line-height: 1;
  text-align: center;
}

.restaurant-bar-track {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  height: 100%;
  border-radius: 999px 999px 6px 6px;
  background: linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
}

.restaurant-bar-fill {
  display: block;
  width: 28px;
  min-height: 0;
  border-radius: 999px 999px 6px 6px;
  transition: height 0.24s ease;
}

.restaurant-bar-fill.tone-primary {
  background: linear-gradient(180deg, #60a5fa 0%, #2563eb 100%);
}

.restaurant-bar-fill.tone-warning {
  background: linear-gradient(180deg, #fbbf24 0%, #f97316 100%);
}

.restaurant-bar-fill.tone-danger {
  background: linear-gradient(180deg, #fb7185 0%, #dc2626 100%);
}

.restaurant-bar-name {
  color: #172033;
  font-size: 12px;
  font-weight: 800;
  line-height: 1.35;
  text-align: center;
  word-break: break-all;
}

.restaurant-bar-meta {
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  text-align: center;
}

.detail-title {
  display: block;
  margin-bottom: 4px;
  font-size: 16px;
  color: #172033;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.summary-item {
  padding: 12px 10px;
  border: 1px solid #e8edf4;
  border-radius: 8px;
  background: #f8fafc;
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
  font-size: 18px;
}

.window-chart-panel {
  display: grid;
  gap: 14px;
}

.window-queue-chart {
  height: 320px;
}

.window-chart-note {
  margin: -4px 0 0;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.window-bar-list {
  display: none;
}

.window-bar-card {
  padding: 12px 14px;
  border: 1px solid #e8edf4;
  border-radius: 8px;
  background: #fff;
}

.window-bar-head,
.window-bar-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.window-bar-head {
  margin-bottom: 10px;
}

.window-bar-head strong {
  color: #172033;
  font-size: 14px;
}

.window-bar-head span,
.window-bar-foot span {
  color: #657084;
  font-size: 12px;
  font-weight: 700;
}

.window-bar-track {
  height: 12px;
  border-radius: 999px;
  background: #edf2f7;
  overflow: hidden;
}

.window-bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  transition: width 0.24s ease;
}

.window-bar-fill.tone-primary {
  background: linear-gradient(90deg, #60a5fa 0%, #2563eb 100%);
}

.window-bar-fill.tone-warning {
  background: linear-gradient(90deg, #fbbf24 0%, #f97316 100%);
}

.window-bar-fill.tone-danger {
  background: linear-gradient(90deg, #fb7185 0%, #dc2626 100%);
}

.window-bar-foot {
  margin-top: 10px;
}

@media (max-width: 1080px) {
  .queue-header,
  .timeline-meta {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-meta {
    justify-items: start;
  }
}

@media (max-width: 900px) {
  .queue-body {
    grid-template-columns: 1fr;
  }

  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .timeline-scale {
    flex-wrap: wrap;
  }

  .restaurant-chart {
    min-height: 320px;
    max-height: 320px;
  }

  .restaurant-bar {
    flex-basis: 68px;
    grid-template-rows: auto minmax(150px, 1fr) auto auto;
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
}
</style>
