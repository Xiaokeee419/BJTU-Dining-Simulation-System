<template>
  <main class="page-shell page-stack">
    <PageHeader
      eyebrow="参数配置"
      title="仿真参数配置"
      description="配置仿真模型的基础参数、人群偏好与运营策略，保存方案并运行仿真以预测食堂运营效果。"
    >
      <template #actions>
        <el-button @click="store.resetForms">重置参数</el-button>
        <el-button @click="store.saveCurrentScheme">保存方案</el-button>
        <el-button type="primary" :loading="running" @click="handleRun">运行仿真</el-button>
      </template>
    </PageHeader>

    <section class="section-grid config-layout">
      <div class="config-main page-stack">
        <SectionCard title="基础参数" subtitle="基于现有场景参数接口组织基础配置项。">
          <div class="basic-grid">
            <div class="param-card">
              <span>学生人数（人）</span>
              <el-input-number
                :model-value="scenarioForm.virtualUserCount"
                :min="100"
                :max="4000"
                :step="50"
                controls-position="right"
                @update:model-value="updateScenario('virtualUserCount', $event)"
              />
              <el-slider
                :model-value="scenarioForm.virtualUserCount"
                :min="100"
                :max="4000"
                :step="50"
                @update:model-value="updateScenario('virtualUserCount', $event)"
              />
            </div>

            <div class="param-card">
              <span>窗口数量（个）</span>
              <el-input-number
                :model-value="activeWindowCount"
                :min="1"
                :max="Math.max(1, windows.length)"
                :step="1"
                controls-position="right"
                @update:model-value="setActiveWindowCount"
              />
              <el-slider
                :model-value="activeWindowCount"
                :min="1"
                :max="Math.max(1, windows.length)"
                :step="1"
                @update:model-value="setActiveWindowCount"
              />
            </div>

            <div class="param-card">
              <span>座位数量（个）</span>
              <el-input-number :model-value="totalCapacity" disabled controls-position="right" />
              <el-slider :model-value="totalCapacity" :min="0" :max="Math.max(totalCapacity, 1)" disabled />
            </div>

            <div class="param-card">
              <span>仿真时长（分钟）</span>
              <el-input-number
                :model-value="scenarioForm.durationMinutes"
                :min="15"
                :max="240"
                :step="15"
                controls-position="right"
                @update:model-value="updateScenario('durationMinutes', $event)"
              />
              <el-slider
                :model-value="scenarioForm.durationMinutes"
                :min="15"
                :max="240"
                :step="15"
                @update:model-value="updateScenario('durationMinutes', $event)"
              />
            </div>
          </div>
        </SectionCard>

        <SectionCard title="人群偏好参数" subtitle="在不改后端接口的前提下，用偏好按钮映射当前画像的 tasteTags。">
          <div class="preference-grid">
            <div v-for="item in preferenceItems" :key="item.tag" class="preference-card">
              <span>{{ item.label }}</span>
              <div class="preference-levels">
                <button
                  v-for="level in preferenceLevelsOptions"
                  :key="level.value"
                  type="button"
                  class="level-button"
                  :class="{ active: preferenceLevels[item.tag] === level.value }"
                  @click="setPreferenceLevel(item.tag, level.value)"
                >
                  <strong>{{ level.label }}</strong>
                  <small>{{ level.caption }}</small>
                </button>
              </div>
            </div>
          </div>
        </SectionCard>

        <SectionCard title="运营策略" subtitle="作为前端可保存方案项，用于记录下一轮仿真的运营思路。">
          <div class="strategy-grid">
            <div class="strategy-item">
              <span>分时开放窗口</span>
              <el-select v-model="strategyForm.stagedWindowOpening">
                <el-option label="启用（按时段动态调整）" value="ENABLED" />
                <el-option label="固定开放窗口" value="FIXED" />
                <el-option label="关闭" value="DISABLED" />
              </el-select>
            </div>
            <div class="strategy-item">
              <span>热门菜补货策略</span>
              <el-select v-model="strategyForm.popularDishRestock">
                <el-option label="智能补货（基于销量预测）" value="SMART" />
                <el-option label="人工观察补货" value="MANUAL" />
                <el-option label="不启用补货策略" value="OFF" />
              </el-select>
            </div>
            <div class="strategy-item">
              <span>高峰分流策略</span>
              <el-select v-model="strategyForm.peakDiversion">
                <el-option label="启用（推荐与引导分流）" value="ENABLED" />
                <el-option label="仅广播提醒" value="NOTICE" />
                <el-option label="关闭" value="DISABLED" />
              </el-select>
            </div>
          </div>
        </SectionCard>

        <section class="section-grid advanced-grid">
          <div class="advanced-zone">
            <ProfilePanel
              v-model="profileForm"
              :profiles="profiles"
              :selected-profile-id="selectedProfileId"
              @preset-change="store.applyProfilePreset"
            />
          </div>
          <div class="advanced-zone">
            <ScenarioPanel
              v-model="scenarioForm"
              :scenarios="scenarios"
              :windows="windows"
              :selected-scenario-id="selectedScenarioId"
              @preset-change="store.applyScenarioPreset"
            />
          </div>
        </section>
      </div>

      <div class="config-preview">
        <SectionCard title="仿真预览" subtitle="这里展示当前配置下的预估结果，运行后会切换为真实仿真值。">
          <div class="preview-metrics">
            <div class="preview-item">
              <span>预计就餐人数</span>
              <strong>{{ previewSummary.expectedDiners }} 人</strong>
            </div>
            <div class="preview-item">
              <span>平均排队时长</span>
              <strong>{{ previewSummary.avgWait }} 分钟</strong>
            </div>
            <div class="preview-item">
              <span>座位利用率</span>
              <strong>{{ previewSummary.seatUtilization }}%</strong>
            </div>
            <div class="preview-item">
              <span>预计高峰时段</span>
              <strong>{{ previewSummary.peakPeriod }}</strong>
            </div>
          </div>

          <div ref="previewChartRef" class="preview-chart"></div>

          <div class="preview-note">
            预览数据基于当前参数配置生成；运行仿真后，首页、实时人流、统计分析和推荐结果页会读取真实 Run 数据。
          </div>
        </SectionCard>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import ProfilePanel from '../components/ProfilePanel.vue'
import ScenarioPanel from '../components/ScenarioPanel.vue'
import SectionCard from '../components/SectionCard.vue'
import { useSimulationStore } from '../stores/simulationStore'
import { seatUtilizationRate, totalCurrentCount } from '../utils/simulationStats'

const store = useSimulationStore()
const {
  profiles,
  scenarios,
  windows,
  restaurants,
  selectedProfileId,
  selectedScenarioId,
  profileForm,
  scenarioForm,
  strategyForm,
  currentRun,
  running,
} = storeToRefs(store)

const preferenceItems = [
  { label: '辣味偏好', tag: '偏辣' },
  { label: '清淡偏好', tag: '清淡' },
  { label: '米饭偏好', tag: '米饭' },
  { label: '面食偏好', tag: '面食' },
  { label: '减脂需求', tag: '轻食' },
]
const preferenceLevelsOptions = [
  { label: '低', value: 'low', caption: '20%' },
  { label: '中', value: 'medium', caption: '50%' },
  { label: '高', value: 'high', caption: '30%' },
]
const preferenceLevels = reactive({})
const previewChartRef = ref(null)
let previewChart

const totalCapacity = computed(() =>
  restaurants.value.reduce((sum, restaurant) => sum + Number(restaurant.capacity || 0), 0),
)
const activeWindowCount = computed(() =>
  Math.max(1, windows.value.length - (scenarioForm.value.closedWindowIds?.length || 0)),
)
const previewSummary = computed(() => {
  const sourcePoint = currentRun.value?.timePoints?.at?.(-1)
  if (sourcePoint) {
    return {
      expectedDiners: totalCurrentCount(sourcePoint),
      avgWait: currentRun.value.metrics?.avgWaitMinutes ?? 0,
      seatUtilization: seatUtilizationRate(sourcePoint),
      peakPeriod: inferPeakPeriod(),
    }
  }

  const crowdFactor =
    {
      IDLE: 0.62,
      NORMAL: 0.78,
      BUSY: 0.94,
      EXTREME: 1.08,
    }[scenarioForm.value.crowdLevel] || 0.8
  const expectedDiners = Math.round(Number(scenarioForm.value.virtualUserCount || 0) * 0.96)
  const avgWait = Math.min(
    18,
    Math.round(
      (2.4 +
        expectedDiners / 240 +
        Math.max(0, windows.value.length - activeWindowCount.value) * 0.45 +
        crowdFactor * 3.2) *
        10,
    ) / 10,
  )
  const seatUtilization = totalCapacity.value
    ? Math.min(100, Math.round((expectedDiners / totalCapacity.value) * crowdFactor * 58))
    : 0
  return {
    expectedDiners,
    avgWait,
    seatUtilization,
    peakPeriod: inferPeakPeriod(),
  }
})

onMounted(async () => {
  try {
    await store.initializeDashboard()
    syncPreferences()
    renderPreviewChart()
    window.addEventListener('resize', resizePreviewChart)
  } catch (error) {
    ElMessage.error(error.message || '参数页初始化失败')
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizePreviewChart)
  previewChart?.dispose()
})

watch(
  () => profileForm.value.tasteTags,
  () => syncPreferences(),
  { deep: true },
)

watch(
  () => [previewSummary.value, scenarioForm.value.virtualUserCount, activeWindowCount.value],
  () => nextTick(renderPreviewChart),
  { deep: true },
)

async function handleRun() {
  try {
    await store.runCurrentSimulation()
  } catch (error) {
    ElMessage.error(error.message || '运行仿真失败')
  }
}

function updateScenario(key, value) {
  scenarioForm.value = {
    ...scenarioForm.value,
    [key]: value,
  }
}

function setActiveWindowCount(value) {
  const target = Math.max(1, Math.min(Number(value || 1), windows.value.length || 1))
  const toClose = Math.max(0, windows.value.length - target)
  const closable = [...windows.value].sort((left, right) => Number(right.windowId || 0) - Number(left.windowId || 0))
  updateScenario(
    'closedWindowIds',
    closable.slice(0, toClose).map((item) => item.windowId),
  )
}

function syncPreferences() {
  const selected = new Set(profileForm.value.tasteTags || [])
  preferenceItems.forEach((item) => {
    const current = preferenceLevels[item.tag]
    if (selected.has(item.tag)) {
      preferenceLevels[item.tag] = current && current !== 'low' ? current : 'medium'
    } else {
      preferenceLevels[item.tag] = 'low'
    }
  })
}

function setPreferenceLevel(tag, level) {
  preferenceLevels[tag] = level
  const set = new Set(profileForm.value.tasteTags || [])
  if (level === 'low') {
    set.delete(tag)
  } else {
    set.add(tag)
  }
  profileForm.value = {
    ...profileForm.value,
    tasteTags: [...set],
  }
}

function renderPreviewChart() {
  if (!previewChartRef.value) return
  if (!previewChart) {
    previewChart = echarts.init(previewChartRef.value)
  }

  const series = buildPreviewSeries()
  previewChart.setOption({
    color: ['#14b8a6'],
    tooltip: { trigger: 'axis' },
    grid: { left: 28, right: 10, top: 16, bottom: 26 },
    xAxis: {
      type: 'category',
      data: series.map((item) => item.label),
      boundaryGap: false,
    },
    yAxis: {
      type: 'value',
      name: '人数',
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbolSize: 6,
        areaStyle: {
          color: 'rgb(20 184 166 / 12%)',
        },
        data: series.map((item) => item.value),
      },
    ],
  })
}

function resizePreviewChart() {
  previewChart?.resize()
}

function buildPreviewSeries() {
  if (currentRun.value?.timePoints?.length) {
    return currentRun.value.timePoints.map((point) => ({
      label: `${point.minute}分`,
      value: totalCurrentCount(point),
    }))
  }

  const step = 15
  const totalMinutes = Number(scenarioForm.value.durationMinutes || 120)
  const labels = []
  for (let minute = 0; minute <= totalMinutes; minute += step) {
    const progress = totalMinutes ? minute / totalMinutes : 0
    const wave = 0.2 + Math.sin(progress * Math.PI) * 0.8
    labels.push({
      label: `${10 + Math.floor(minute / 60)}:${String(minute % 60).padStart(2, '0')}`,
      value: Math.max(40, Math.round(previewSummary.value.expectedDiners * wave * 0.72)),
    })
  }
  return labels
}

function inferPeakPeriod() {
  return (
    {
      BREAKFAST: '07:30 - 08:30',
      LUNCH: '11:30 - 12:30',
      DINNER: '17:30 - 18:30',
    }[scenarioForm.value.mealPeriod] || '11:30 - 12:30'
  )
}
</script>

<style scoped>
.config-layout {
  align-items: start;
}

.config-main {
  grid-column: span 8;
}

.config-preview {
  grid-column: span 4;
}

.basic-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.param-card {
  display: grid;
  gap: 12px;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.param-card span {
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.preference-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.preference-card {
  display: grid;
  gap: 12px;
}

.preference-card span {
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.preference-levels {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.level-button {
  display: grid;
  gap: 4px;
  place-items: center;
  padding: 10px 6px;
  border: 1px solid #dbe2ec;
  border-radius: 16px;
  color: #475569;
  background: #ffffff;
  cursor: pointer;
}

.level-button strong {
  font-size: 14px;
}

.level-button small {
  color: #64748b;
  font-size: 12px;
}

.level-button.active {
  border-color: #14b8a6;
  color: #ffffff;
  background: linear-gradient(180deg, #14b8a6 0%, #0f9488 100%);
}

.level-button.active small {
  color: rgb(255 255 255 / 88%);
}

.strategy-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.strategy-item {
  display: grid;
  gap: 10px;
}

.strategy-item span {
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.advanced-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.advanced-zone {
  min-width: 0;
}

.preview-metrics {
  display: grid;
  gap: 12px;
}

.preview-item {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #e2e8f0;
}

.preview-item span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.preview-item strong {
  color: #0f172a;
  font-size: 18px;
  text-align: right;
}

.preview-chart {
  width: 100%;
  height: 260px;
  margin-top: 16px;
}

.preview-note {
  margin-top: 14px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 1280px) {
  .preference-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .strategy-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1100px) {
  .config-main,
  .config-preview {
    grid-column: auto;
  }

  .basic-grid,
  .advanced-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .preference-grid {
    grid-template-columns: 1fr;
  }
}
</style>
