<template>
  <main class="page-shell page-stack">
    <section class="hero-panel panel">
      <div class="hero-copy">
        <p class="eyebrow">系统首页</p>
        <h1>食堂运营仿真平台</h1>
        <p class="hero-subtitle">仿真模拟 · 数据分析 · 决策支持</p>
        <div class="hero-line"></div>
        <p class="hero-description">
          基于仿真技术与数据分析，辅助食堂运营管理与资源优化配置，提升就餐体验与运营效率。
        </p>
      </div>
      <div class="hero-scene" aria-hidden="true">
        <div class="scene-grid"></div>
        <div class="scene-orb orb-primary"></div>
        <div class="scene-orb orb-secondary"></div>
        <div class="scene-stack">
          <div class="scene-card scene-card-top"></div>
          <div class="scene-card scene-card-middle"></div>
          <div class="scene-card scene-card-bottom"></div>
          <div class="scene-outline"></div>
        </div>
      </div>
    </section>

    <section class="section-grid home-content">
      <section class="feature-panel">
        <div class="feature-grid">
          <FeatureEntryCard
            :icon="UserFilled"
            title="仿真人流快照"
            description="查看高峰分钟的餐厅负载、排队窗口和容量负载情况。"
            tone="primary"
            @open="router.push('/flow')"
          />
          <FeatureEntryCard
            :icon="Setting"
            title="参数配置"
            description="调整仿真场景、用户画像和运营策略并重新运行仿真。"
            tone="teal"
            @open="router.push('/config')"
          />
          <FeatureEntryCard
            :icon="Histogram"
            title="统计分析"
            description="查看 timePoints 与 metrics 生成的趋势图和负载统计。"
            tone="cyan"
            @open="router.push('/statistics')"
          />
          <FeatureEntryCard
            :icon="DataAnalysis"
            title="推荐结果"
            description="查看推荐分数、理由、等待时长和分流建议明细。"
            tone="primary"
            @open="router.push('/recommendation')"
          />
        </div>
      </section>

      <section class="recent-panel panel">
        <div class="panel-body">
          <RecentSimulationCard :run-meta="lastRunMeta" @open="router.push('/statistics')" />
        </div>
      </section>
    </section>

    <section class="card-grid-4">
      <StatCard
        :icon="UserFilled"
        label="峰值负载人数"
        :value="overview.currentPeople"
        unit="人"
        :caption="overview.peopleCaption"
        :delta="overview.peopleNote"
        delta-state="neutral"
      />
      <StatCard
        :icon="Clock"
        label="平均排队时长"
        :value="overview.avgWait"
        unit="分钟"
        :caption="overview.waitCaption"
        :delta="overview.waitNote"
        delta-state="neutral"
        tone="teal"
      />
      <StatCard
        :icon="OfficeBuilding"
        label="容量负载率"
        :value="overview.seatUtilization"
        unit="%"
        :caption="overview.seatCaption"
        :delta="overview.seatNote"
        delta-state="neutral"
        tone="orange"
      />
      <StatCard
        :icon="Grid"
        label="窗口开放数"
        :value="overview.openWindows"
        :caption="overview.windowCaption"
        :delta="overview.windowNote"
        delta-state="neutral"
        tone="green"
      />
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  Clock,
  DataAnalysis,
  Grid,
  Histogram,
  OfficeBuilding,
  Setting,
  UserFilled,
} from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import FeatureEntryCard from '../components/FeatureEntryCard.vue'
import RecentSimulationCard from '../components/RecentSimulationCard.vue'
import StatCard from '../components/StatCard.vue'
import { useSimulationStore } from '../stores/simulationStore'
import {
  resolvePeakTimePoint,
  resolveTimePoint,
  seatUtilizationRate,
  totalCurrentCount,
} from '../utils/simulationStats'

const router = useRouter()
const store = useSimulationStore()
const { currentRun, currentMinute, windows, lastRunMeta, profiles, scenarios } = storeToRefs(store)

const overview = computed(() => {
  const peakPoint = resolvePeakTimePoint(currentRun.value)
  const fallbackPoint = resolveTimePoint(currentRun.value, currentMinute.value)
  const point = peakPoint || fallbackPoint

  const currentPeople = point ? totalCurrentCount(point) : '--'
  const avgWait = currentRun.value?.metrics?.avgWaitMinutes ?? '--'
  const seatUtilization = point ? seatUtilizationRate(point) : '--'
  const peakMinute = point?.minute
  const seatCapacity = point ? totalSeatCapacity(point) : 0
  const servedUserCount = currentRun.value?.metrics?.servedUserCount
  const totalVirtualUsers =
    currentRun.value?.metrics?.totalVirtualUsers ?? currentRun.value?.scenario?.virtualUserCount
  const openWindows = point ? countOpenWindows(point) : windows.value.filter(isWindowOpen).length
  const totalWindows = point ? countTotalWindows(point) : windows.value.length

  return {
    currentPeople,
    avgWait,
    seatUtilization,
    openWindows,
    peopleCaption: peakMinute != null ? `高峰快照 ${peakMinute} 分钟` : '请先运行仿真',
    peopleNote: Number.isFinite(totalVirtualUsers) ? `仿真样本 ${totalVirtualUsers} 人` : '',
    waitCaption: '当前仿真',
    waitNote: Number.isFinite(servedUserCount) ? `已服务 ${servedUserCount} 人` : '',
    seatCaption: peakMinute != null ? `高峰快照 ${peakMinute} 分钟` : '请先运行仿真',
    seatNote: seatCapacity ? `总容量 ${seatCapacity}` : '',
    windowCaption: '开放 / 总数',
    windowNote: totalWindows ? `${openWindows} / ${totalWindows}` : '',
  }
})

onMounted(async () => {
  try {
    await store.initializeDashboard()
    if (!currentRun.value && profiles.value.length && scenarios.value.length) {
      await store.runCurrentSimulation()
    }
  } catch (error) {
    ElMessage.error(error.message || '首页初始化失败')
  }
})

function countOpenWindows(point) {
  return point.restaurants
    .flatMap((restaurant) => restaurant.windows || [])
    .filter((window) => isWindowOpen(window)).length
}

function countTotalWindows(point) {
  return point.restaurants.flatMap((restaurant) => restaurant.windows || []).length
}

function totalSeatCapacity(point) {
  return point.restaurants.reduce((sum, restaurant) => sum + Number(restaurant.capacity || 0), 0)
}

function isWindowOpen(window) {
  return window.status !== 'CLOSED'
}
</script>

<style scoped>
.hero-panel {
  position: relative;
  display: grid;
  grid-template-columns: 1.12fr 0.88fr;
  align-items: stretch;
  min-height: 240px;
  padding: 0;
  overflow: hidden;
  background:
    radial-gradient(circle at 12% 16%, rgb(255 255 255 / 98%), rgb(255 255 255 / 92%) 28%, transparent 62%),
    linear-gradient(135deg, #f8fbff 0%, #edf4ff 45%, #eef8ff 100%);
}

.hero-copy {
  display: grid;
  align-content: center;
  gap: 10px;
  padding: 34px 38px;
  z-index: 1;
}

.hero-copy h1 {
  margin: 0;
  color: #14316f;
  font-size: 38px;
  line-height: 1.18;
}

.hero-subtitle {
  margin: 0;
  color: #334155;
  font-size: 21px;
  font-weight: 600;
}

.hero-line {
  width: 48px;
  height: 4px;
  border-radius: 999px;
  background: #2563eb;
}

.hero-description {
  max-width: 560px;
  margin: 0;
  color: #475569;
  font-size: 15px;
  line-height: 1.8;
}

.hero-scene {
  position: relative;
  min-height: 240px;
  overflow: hidden;
}

.scene-grid {
  position: absolute;
  inset: 22px 28px 22px 40px;
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgb(255 255 255 / 0.7), rgb(255 255 255 / 0.05)),
    repeating-linear-gradient(0deg, transparent, transparent 22px, rgb(148 163 184 / 9%) 22px, rgb(148 163 184 / 9%) 23px),
    repeating-linear-gradient(90deg, transparent, transparent 22px, rgb(148 163 184 / 9%) 22px, rgb(148 163 184 / 9%) 23px);
  border: 1px solid rgb(191 219 254 / 40%);
}

.scene-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
}

.orb-primary {
  top: 32px;
  right: 96px;
  width: 156px;
  height: 156px;
  background: radial-gradient(circle, rgb(59 130 246 / 18%) 0%, rgb(59 130 246 / 4%) 68%, transparent 100%);
}

.orb-secondary {
  right: 34px;
  bottom: 24px;
  width: 118px;
  height: 118px;
  background: radial-gradient(circle, rgb(20 184 166 / 16%) 0%, rgb(20 184 166 / 3%) 68%, transparent 100%);
}

.scene-stack {
  position: absolute;
  right: 52px;
  bottom: 34px;
  width: 280px;
  height: 148px;
}

.scene-card {
  position: absolute;
  border-radius: 22px;
  border: 1px solid rgb(191 219 254 / 50%);
  background: linear-gradient(180deg, rgb(255 255 255 / 92%) 0%, rgb(239 246 255 / 74%) 100%);
  box-shadow: 0 16px 36px rgb(37 99 235 / 10%);
}

.scene-card-top {
  top: 0;
  right: 0;
  width: 172px;
  height: 74px;
}

.scene-card-middle {
  top: 32px;
  right: 92px;
  width: 128px;
  height: 60px;
}

.scene-card-bottom {
  right: 28px;
  bottom: 0;
  width: 216px;
  height: 88px;
}

.scene-outline {
  position: absolute;
  right: 8px;
  top: 14px;
  width: 204px;
  height: 112px;
  border: 1px solid rgb(37 99 235 / 12%);
  border-radius: 28px;
}

.home-content {
  align-items: stretch;
}

.feature-panel {
  grid-column: span 9;
  min-width: 0;
}

.recent-panel {
  grid-column: span 3;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.recent-panel :deep(.panel-body) {
  height: 100%;
}

@media (max-width: 1400px) {
  .hero-copy h1 {
    font-size: 34px;
  }

  .scene-stack {
    right: 34px;
    width: 244px;
  }
}

@media (max-width: 1280px) {
  .hero-panel {
    grid-template-columns: 1fr;
  }

  .hero-scene {
    min-height: 180px;
  }

  .feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .feature-panel,
  .recent-panel {
    grid-column: auto;
  }

  .feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .hero-copy {
    padding: 26px 22px;
  }

  .hero-copy h1 {
    font-size: 30px;
  }

  .hero-subtitle {
    font-size: 18px;
  }

  .hero-scene {
    min-height: 156px;
  }

  .scene-stack {
    right: 18px;
    bottom: 18px;
    width: 190px;
    height: 110px;
  }

  .scene-card-top {
    width: 122px;
    height: 54px;
  }

  .scene-card-middle {
    right: 64px;
    width: 96px;
    height: 44px;
  }

  .scene-card-bottom {
    width: 150px;
    height: 62px;
  }

  .feature-grid {
    grid-template-columns: 1fr;
  }
}
</style>
