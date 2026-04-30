<template>
  <main v-loading="loading" class="dashboard">
    <header class="dashboard-header">
      <div>
        <p class="eyebrow">BJTU Dining Simulation</p>
        <h1>就餐仿真控制台</h1>
      </div>
      <div class="header-actions">
        <el-button
          type="primary"
          :icon="VideoPlay"
          :loading="running"
          @click="handleRun"
        >
          运行仿真
        </el-button>
        <el-button
          :icon="Flag"
          :disabled="!currentRun"
          @click="store.setBaseRun"
        >
          设为基准
        </el-button>
        <el-button
          type="success"
          :icon="DataLine"
          :disabled="!baseRun"
          :loading="comparing"
          @click="handleCompare"
        >
          运行对比
        </el-button>
      </div>
    </header>

    <MetricsCards :metrics="currentRun?.metrics" />

    <section class="section-grid config-grid">
      <ProfilePanel
        v-model="profileForm"
        class="profile-area"
        :profiles="profiles"
        :selected-profile-id="selectedProfileId"
        @preset-change="store.applyProfilePreset"
      />
      <ScenarioPanel
        v-model="scenarioForm"
        class="scenario-area"
        :scenarios="scenarios"
        :windows="windows"
        :selected-scenario-id="selectedScenarioId"
        @preset-change="store.applyScenarioPreset"
      />
      <RecommendationPanel class="recommendation-area" :recommendation="recommendation" />
    </section>

    <section v-if="currentRun" class="timeline-panel panel">
      <div class="panel-header">
        <h2 class="panel-title">时间轴</h2>
        <div class="timeline-actions">
          <span class="muted">Run {{ currentRun.runId }}</span>
          <el-button
            size="small"
            :icon="playing ? VideoPause : VideoPlay"
            @click="togglePlayback"
          >
            {{ playing ? '暂停' : '播放' }}
          </el-button>
          <el-button size="small" :icon="DArrowRight" @click="goNextMinute">
            下一步
          </el-button>
          <el-button size="small" :icon="RefreshLeft" @click="goFirstMinute">
            回到开始
          </el-button>
        </div>
      </div>
      <div class="panel-body timeline-body">
        <el-slider
          :model-value="currentMinute"
          :min="0"
          :max="maxMinute"
          :step="scenarioForm.stepMinutes || 5"
          :marks="timelineMarks"
          @change="handleMinuteChange"
        />
      </div>
    </section>

    <section class="section-grid result-grid">
      <QueueOverview class="queue-area" :time-point="currentTimePoint" />
      <ComparePanel
        class="compare-area"
        :base-run="baseRun"
        :compare-run="compareRun"
        :comparison="comparison"
        @clear="store.clearComparison"
      />
    </section>

    <TrendCharts :time-points="currentRun?.timePoints || []" />
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import {
  DArrowRight,
  DataLine,
  Flag,
  RefreshLeft,
  VideoPause,
  VideoPlay,
} from '@element-plus/icons-vue'
import ComparePanel from '../components/ComparePanel.vue'
import MetricsCards from '../components/MetricsCards.vue'
import ProfilePanel from '../components/ProfilePanel.vue'
import QueueOverview from '../components/QueueOverview.vue'
import RecommendationPanel from '../components/RecommendationPanel.vue'
import ScenarioPanel from '../components/ScenarioPanel.vue'
import TrendCharts from '../components/TrendCharts.vue'
import { useSimulationStore } from '../stores/simulationStore'

const store = useSimulationStore()
const {
  profiles,
  scenarios,
  windows,
  selectedProfileId,
  selectedScenarioId,
  profileForm,
  scenarioForm,
  currentRun,
  baseRun,
  compareRun,
  recommendation,
  comparison,
  currentMinute,
  currentTimePoint,
  maxMinute,
  loading,
  running,
  comparing,
} = storeToRefs(store)

const playing = ref(false)
let playbackTimer = null
let playbackRefreshing = false

const timelineMarks = computed(() => {
  if (!currentRun.value?.timePoints?.length) return {}
  const first = currentRun.value.timePoints[0]
  const last = currentRun.value.timePoints.at(-1)
  return {
    [first.minute]: '0',
    [last.minute]: `${last.minute}分`,
  }
})

const minuteStep = computed(() => Number(scenarioForm.value.stepMinutes || 5))

onMounted(async () => {
  try {
    await store.initializeDashboard()
    await store.runCurrentSimulation()
  } catch (error) {
    ElMessage.error(error.message || '初始化失败')
  }
})

onBeforeUnmount(() => {
  stopPlayback()
})

async function handleRun() {
  stopPlayback()
  try {
    await store.runCurrentSimulation()
  } catch (error) {
    ElMessage.error(error.message || '仿真运行失败')
  }
}

async function handleCompare() {
  stopPlayback()
  try {
    await store.runCompareSimulation()
  } catch (error) {
    ElMessage.error(error.message || '场景对比失败')
  }
}

async function handleMinuteChange(minute) {
  store.setCurrentMinute(minute)
  try {
    await store.refreshRecommendation()
  } catch (error) {
    ElMessage.error(error.message || '推荐刷新失败')
  }
}

function togglePlayback() {
  if (playing.value) {
    stopPlayback()
    return
  }
  startPlayback()
}

function startPlayback() {
  if (!currentRun.value) return
  if (currentMinute.value >= maxMinute.value) {
    store.setCurrentMinute(0)
  }
  playing.value = true
  playbackTimer = window.setInterval(playNextFrame, 1100)
}

function stopPlayback() {
  playing.value = false
  if (playbackTimer) {
    window.clearInterval(playbackTimer)
    playbackTimer = null
  }
}

async function playNextFrame() {
  if (playbackRefreshing) return
  playbackRefreshing = true
  try {
    await goNextMinute()
  } finally {
    playbackRefreshing = false
  }
}

async function goNextMinute() {
  if (!currentRun.value) return
  const nextMinute = Math.min(maxMinute.value, currentMinute.value + minuteStep.value)
  await handleMinuteChange(nextMinute)
  if (nextMinute >= maxMinute.value) {
    stopPlayback()
  }
}

async function goFirstMinute() {
  stopPlayback()
  await handleMinuteChange(0)
}
</script>

<style scoped>
.dashboard {
  display: grid;
  gap: 16px;
  width: min(1500px, 100%);
  min-height: 100vh;
  margin: 0 auto;
  padding: 24px;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 10px 24px rgb(15 23 42 / 6%);
}

.eyebrow {
  margin: 0 0 4px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.dashboard h1 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
  line-height: 1.2;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.config-grid {
  align-items: start;
}

.profile-area {
  grid-column: span 3;
}

.scenario-area {
  grid-column: span 4;
}

.recommendation-area {
  grid-column: span 5;
}

.timeline-body {
  padding: 10px 30px 18px;
}

.timeline-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.queue-area {
  grid-column: span 8;
}

.compare-area {
  grid-column: span 4;
}

@media (max-width: 1100px) {
  .dashboard {
    padding: 14px;
  }

  .dashboard-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .profile-area,
  .scenario-area,
  .recommendation-area,
  .queue-area,
  .compare-area {
    grid-column: auto;
  }
}
</style>
