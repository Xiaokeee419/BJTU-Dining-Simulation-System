<template>
  <main class="flow-page">
    <DashboardPageHeader
      title="仿真运行与人流快照"
      description="配置当前用餐场景，运行基础仿真，并沿时间轴观察餐厅与窗口排队如何演化。"
      :status="flowStatus"
    >
      <template #actions>
        <el-button type="primary" :loading="loading.simulation" @click="handleRun">
          运行基础仿真
        </el-button>
      </template>
    </DashboardPageHeader>

    <section class="flow-grid flow-grid-config">
      <section class="flow-panel">
        <header class="flow-panel-heading">
          <div>
            <h2>场景参数配置</h2>
            <p>参数变化后，到达曲线会自动刷新；运行仿真后生成完整排队时间序列。</p>
          </div>
        </header>
        <div class="flow-form-grid">
          <label>
            <span>用户画像</span>
            <el-select v-model="form.userType">
              <el-option
                v-for="profile in profiles"
                :key="profile.userType"
                :label="profileLabel(profile.userType)"
                :value="profile.userType"
              />
            </el-select>
          </label>
          <label>
            <span>用餐时段</span>
            <el-select v-model="form.mealPeriod">
              <el-option label="早餐" value="BREAKFAST" />
              <el-option label="午餐" value="LUNCH" />
              <el-option label="晚餐" value="DINNER" />
            </el-select>
          </label>
          <label>
            <span>拥挤程度</span>
            <el-select v-model="form.crowdLevel">
              <el-option label="空闲" value="IDLE" />
              <el-option label="正常" value="NORMAL" />
              <el-option label="较忙" value="BUSY" />
              <el-option label="拥挤" value="EXTREME" />
            </el-select>
          </label>
          <label>
            <span>天气系数</span>
            <el-input-number
              v-model="form.weatherFactor"
              :min="0.5"
              :max="2"
              :step="0.05"
              :precision="2"
            />
          </label>
          <label>
            <span>活动系数</span>
            <el-input-number
              v-model="form.eventFactor"
              :min="0.5"
              :max="2"
              :step="0.05"
              :precision="2"
            />
          </label>
          <label>
            <span>仿真人数</span>
            <el-input-number
              v-model="form.virtualUserCount"
              :min="1"
              :max="10000"
              :step="100"
            />
          </label>
          <label>
            <span>仿真时长</span>
            <el-input-number
              v-model="form.durationMinutes"
              :min="5"
              :max="180"
              :step="5"
            />
          </label>
          <label>
            <span>随机种子</span>
            <el-input-number v-model="form.randomSeed" :min="1" :max="99999999" />
          </label>
        </div>
      </section>

      <section class="flow-panel">
        <header class="flow-panel-heading">
          <div>
            <h2>基础数据概览</h2>
            <p>{{ dataOverview?.sourceDirectory || '正在读取数据目录' }}</p>
          </div>
          <span class="flow-status-chip">REAL CSV</span>
        </header>
        <div class="flow-metric-grid flow-metric-grid-2">
          <MetricCard label="人群记录" :value="dataOverview?.studentCount" unit="条" />
          <MetricCard label="餐厅" :value="dataOverview?.restaurantCount" unit="个" />
          <MetricCard label="窗口" :value="dataOverview?.windowCount" unit="个" />
          <MetricCard
            label="当前时段开放窗口"
            :value="scenarioOpenWindowCount"
            unit="个"
            :detail="scenarioWindowRuleLabel"
          />
        </div>
        <div class="flow-data-note">
          <span>到达模型</span>
          <strong>arrival_rules.csv + 当前场景参数</strong>
        </div>
      </section>
    </section>

    <DashboardChart
      title="当前场景人流到达曲线"
      subtitle="根据仿真人数、用餐时段、拥挤程度、天气和活动系数生成预计到达分布。"
      :option="flowCurveOption"
      :loading="loading.flow || loading.initialize"
      :error="flowCurveError"
      :empty="!arrivalCurvePoints.length"
      :height="340"
    >
      <template #extra>
        <span class="flow-status-chip">{{ arrivalCurveModeLabel }}</span>
      </template>
    </DashboardChart>

    <QueueOverview
      v-if="currentRun"
      :run="currentRun"
      v-model:minute="currentMinute"
      :initial-minute="peakPoint?.minute"
    />
    <section v-else class="flow-panel flow-empty-state">
      <el-empty description="运行基础仿真后，这里会显示动态时间轴、餐厅概览和窗口队列柱状图。" />
    </section>

    <section class="flow-panel">
      <header class="flow-panel-heading">
        <div>
          <h2>仿真高峰摘要</h2>
          <p>根据当前基础仿真的完整 timePoints 自动定位排队峰值。</p>
        </div>
      </header>
      <div class="flow-metric-grid flow-metric-grid-4">
        <MetricCard label="高峰出现时间" :value="peakPoint?.minute" unit="分钟" />
        <MetricCard label="峰值总排队" :value="peakTotalQueue" unit="人" tone="warning" />
        <MetricCard
          label="峰值餐厅"
          :value="peakRestaurant?.name"
          :detail="peakRestaurant ? `排队 ${peakRestaurant.queue} 人` : ''"
        />
        <MetricCard
          label="峰值窗口"
          :value="peakWindow?.name"
          :detail="peakWindow ? `${peakWindow.restaurantName} · 排队 ${peakWindow.queue} 人` : ''"
          tone="danger"
        />
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import DashboardChart from '../components/DashboardChart.vue'
import QueueOverview from '../components/QueueOverview.vue'
import DashboardPageHeader from '../components/dashboard/DashboardPageHeader.vue'
import MetricCard from '../components/dashboard/MetricCard.vue'
import { useDashboardStore } from '../stores/dashboardStore'
import { flattenWindows, totalQueueLength } from '../utils/simulationStats'

const store = useDashboardStore()
const {
  profiles,
  dataOverview,
  flowCurve,
  flowCurveError,
  form,
  currentRun,
  loading,
  requestStatus,
  scenarioOpenWindowCount,
  scenarioWindowRuleLabel,
  peakPoint,
} = storeToRefs(store)

const currentMinute = ref(null)
let flowCurveRefreshTimer = null

const flowStatus = computed(() => {
  if (loading.value.simulation) return '正在运行基础仿真'
  if (currentRun.value) return `已生成 Baseline #${currentRun.value.runId}`
  return requestStatus.value === 'ERROR' ? '初始化失败' : '未运行仿真'
})

const actualArrivalCurvePoints = computed(() => {
  const curve =
    currentRun.value?.arrivalCurve ??
    currentRun.value?.arrivalTimeSeries ??
    currentRun.value?.arrivalPoints
  const points = Array.isArray(curve) ? curve : curve?.points
  return runMatchesCurrentScenario(currentRun.value) && Array.isArray(points) ? points : []
})

const arrivalCurveModeLabel = computed(() =>
  actualArrivalCurvePoints.value.length ? '本次仿真实际曲线' : '预计到达曲线',
)

const arrivalCurvePoints = computed(() => {
  const source = actualArrivalCurvePoints.value.length
    ? actualArrivalCurvePoints.value
    : flowCurve.value?.points || []
  let cumulativeArrivals = 0
  return source.map((item) => {
    const arrivals = Number(
      item?.arrivals ?? item?.arrivalCount ?? item?.count ?? item?.value ?? 0,
    )
    cumulativeArrivals =
      item?.cumulativeArrivals == null
        ? cumulativeArrivals + arrivals
        : Number(item.cumulativeArrivals)
    return {
      minute: Number(item?.minute || 0),
      arrivals,
      cumulativeArrivals,
    }
  })
})

const arrivalCurvePopulation = computed(
  () =>
    Number(
      runMatchesCurrentScenario(currentRun.value)
        ? currentRun.value?.scenario?.virtualUserCount
        : flowCurve.value?.totalArrivals,
    ) || Number(form.value.virtualUserCount || 0),
)

const flowCurveOption = computed(() => ({
  color: ['#0160a8', '#8fa8c7'],
  tooltip: {
    trigger: 'axis',
    formatter: (params) => {
      const minute = params?.[0]?.axisValueLabel || params?.[0]?.name || '0m'
      const values = Object.fromEntries(
        (params || []).map((item) => [item.seriesName, Number(item.value || 0)]),
      )
      return [
        `第 ${String(minute).replace('m', '')} 分钟`,
        `每分钟到达人数：${values['每分钟到达人数'] ?? 0} 人`,
        `累计到达人数：${values['累计到达人数'] ?? 0} 人`,
        `当前仿真人数配置：${arrivalCurvePopulation.value} 人`,
      ].join('<br/>')
    },
  },
  legend: { top: 8, data: ['每分钟到达人数', '累计到达人数'] },
  grid: { left: 58, right: 64, top: 52, bottom: 42 },
  xAxis: {
    type: 'category',
    data: arrivalCurvePoints.value.map((item) => `${item.minute}m`),
    axisLabel: { interval: 'auto' },
  },
  yAxis: [
    { type: 'value', name: '每分钟到达人数' },
    { type: 'value', name: '累计人数' },
  ],
  series: [
    {
      name: '每分钟到达人数',
      type: 'line',
      smooth: true,
      symbol: 'none',
      data: arrivalCurvePoints.value.map((item) => item.arrivals),
    },
    {
      name: '累计到达人数',
      type: 'line',
      yAxisIndex: 1,
      symbol: 'none',
      lineStyle: { type: 'dashed' },
      data: arrivalCurvePoints.value.map((item) => item.cumulativeArrivals),
    },
  ],
}))

const peakTotalQueue = computed(() =>
  peakPoint.value ? totalQueueLength(peakPoint.value) : null,
)

const peakRestaurant = computed(() => {
  if (!peakPoint.value?.restaurants?.length) return null
  return peakPoint.value.restaurants
    .map((restaurant) => ({
      name: restaurant.name,
      queue: totalQueueLength({ restaurants: [restaurant] }),
    }))
    .sort((left, right) => right.queue - left.queue)[0]
})

const peakWindow = computed(() => {
  const rows = flattenWindows(peakPoint.value)
    .map(({ restaurantName, window }) => ({
      name: window.name,
      restaurantName,
      queue: Number(window.queueLength ?? window.queueCount ?? 0),
    }))
    .sort((left, right) => right.queue - left.queue)
  return rows[0] || null
})

onMounted(async () => {
  try {
    await store.initialize()
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') {
      ElMessage.error(error?.message || '页面初始化失败')
    }
  }
})

watch(
  () => [
    form.value.userType,
    form.value.virtualUserCount,
    form.value.mealPeriod,
    form.value.dayType,
    form.value.crowdLevel,
    form.value.weatherFactor,
    form.value.eventFactor,
    form.value.durationMinutes,
    form.value.randomSeed,
  ],
  () => {
    if (flowCurveRefreshTimer != null) window.clearTimeout(flowCurveRefreshTimer)
    flowCurveRefreshTimer = window.setTimeout(() => {
      flowCurveRefreshTimer = null
      store.refreshFlowCurve()
    }, 250)
  },
)

watch(
  () => currentRun.value?.runId,
  () => {
    currentMinute.value = peakPoint.value?.minute ?? 0
  },
)

onBeforeUnmount(() => {
  if (flowCurveRefreshTimer != null) window.clearTimeout(flowCurveRefreshTimer)
})

async function handleRun() {
  try {
    await store.runBaseline()
    ElMessage.success(`基础仿真完成 #${currentRun.value?.runId}`)
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') {
      ElMessage.error(error?.message || '基础仿真运行失败')
    }
  }
}

function runMatchesCurrentScenario(run) {
  const scenario = run?.scenario
  if (!scenario) return false
  return (
    String(scenario.mealPeriod) === String(form.value.mealPeriod) &&
    String(scenario.dayType) === String(form.value.dayType) &&
    String(scenario.crowdLevel) === String(form.value.crowdLevel) &&
    Number(scenario.weatherFactor ?? 1) === Number(form.value.weatherFactor ?? 1) &&
    Number(scenario.eventFactor ?? 1) === Number(form.value.eventFactor ?? 1) &&
    Number(scenario.virtualUserCount) === Number(form.value.virtualUserCount) &&
    Number(scenario.durationMinutes) === Number(form.value.durationMinutes) &&
    Number(scenario.randomSeed) === Number(form.value.randomSeed)
  )
}

function profileLabel(userType) {
  return (
    {
      STUDENT: '普通学生',
      BUDGET_SENSITIVE: '预算敏感型',
      HURRY: '时间敏感型',
    }[userType] || userType
  )
}
</script>

<style src="./dashboard-flow.css"></style>
