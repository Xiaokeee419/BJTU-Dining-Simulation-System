<template>
  <main class="page-shell page-stack">
    <PageHeader
      eyebrow="参数配置"
      title="未分流仿真参数配置"
      description="本页用于设置本轮未分流仿真的主导人群画像和场景参数，运行后生成 baseline 仿真结果。"
    >
      <template #actions>
        <el-button @click="store.resetForms">重置参数</el-button>
        <el-button type="primary" :loading="running" @click="handleRun">运行未分流仿真</el-button>
        <el-button :disabled="!hasFinishedRun" @click="store.saveBaselineRun">
          使用当前结果作为 baseline
        </el-button>
      </template>
    </PageHeader>

    <section class="config-grid">
      <div class="config-zone">
        <ProfilePanel
          v-model="profileForm"
          :profiles="profiles"
          :selected-profile-id="selectedProfileId"
          @preset-change="store.applyProfilePreset"
        />
      </div>

      <div class="config-zone">
        <ScenarioPanel
          v-model="scenarioForm"
          :scenarios="scenarios"
          :windows="windows"
          :selected-scenario-id="selectedScenarioId"
          @preset-change="store.applyScenarioPreset"
        />
      </div>
    </section>

    <SectionCard
      title="当前未分流结果"
      subtitle="这里展示最近一次未分流仿真的核心结果，便于确认 baseline 是否已经生成。"
    >
      <EmptyState
        v-if="!hasFinishedRun"
        title="尚未生成 baseline 仿真结果"
        description="请先运行一次未分流仿真，再进入人流快照、统计分析和分流对比页面。"
      />

      <section v-else class="card-grid-4">
        <StatCard
          :icon="Tickets"
          label="Run ID"
          :value="currentRun.runId"
          caption="当前未分流结果"
          :delta="baselineRun ? `已保存基准 #${baselineRun.runId}` : '尚未保存为基准'"
          :delta-state="baselineRun ? 'neutral' : 'danger'"
        />
        <StatCard
          :icon="Clock"
          label="高峰分钟"
          :value="peakMinute"
          unit="分钟"
          caption="默认用于人流快照和分流建议"
          :delta="`创建时间 ${createdAtLabel}`"
          delta-state="neutral"
          tone="teal"
        />
        <StatCard
          :icon="UserFilled"
          label="高峰负载人数"
          :value="peakLoad"
          unit="人"
          caption="排队/服务负载"
          :delta="`总排队 ${peakQueue} 人`"
          delta-state="neutral"
          tone="orange"
        />
        <StatCard
          :icon="Grid"
          label="开放窗口数"
          :value="peakOpenWindows"
          unit="个"
          caption="高峰快照"
          :delta="`平均排队 ${currentRun.metrics?.avgWaitMinutes ?? '--'} 分钟`"
          delta-state="neutral"
          tone="green"
        />
      </section>
    </SectionCard>
  </main>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { Clock, Grid, Tickets, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import ProfilePanel from '../components/ProfilePanel.vue'
import ScenarioPanel from '../components/ScenarioPanel.vue'
import SectionCard from '../components/SectionCard.vue'
import StatCard from '../components/StatCard.vue'
import { useSimulationStore } from '../stores/simulationStore'
import {
  openWindowCount,
  resolvePeakTimePoint,
  totalCurrentCount,
  totalQueueLength,
} from '../utils/simulationStats'

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
  baselineRun,
  running,
} = storeToRefs(store)

const hasFinishedRun = computed(() => currentRun.value?.status === 'FINISHED')
const peakPoint = computed(() => resolvePeakTimePoint(currentRun.value))
const peakMinute = computed(() => peakPoint.value?.minute ?? '--')
const peakLoad = computed(() => (peakPoint.value ? totalCurrentCount(peakPoint.value) : '--'))
const peakQueue = computed(() => (peakPoint.value ? totalQueueLength(peakPoint.value) : '--'))
const peakOpenWindows = computed(() => (peakPoint.value ? openWindowCount(peakPoint.value) : '--'))
const createdAtLabel = computed(() => formatDateTime(currentRun.value?.createdAt))

onMounted(async () => {
  try {
    await store.initializeDashboard()
  } catch (error) {
    ElMessage.error(error.message || '参数页初始化失败')
  }
})

async function handleRun() {
  try {
    await store.runCurrentSimulation()
  } catch (error) {
    ElMessage.error(error.message || '运行未分流仿真失败')
  }
}

function formatDateTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.config-zone {
  min-width: 0;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(420px, 1fr));
  gap: 16px;
  align-items: start;
}

@media (max-width: 1100px) {
  .config-grid {
    grid-template-columns: 1fr;
  }
}
</style>
