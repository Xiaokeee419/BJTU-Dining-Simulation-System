<template>
  <main class="workspace-page">
    <section class="workspace-hero">
      <div>
        <p class="section-kicker">Workspace</p>
        <h1>仿真与分流优化工作台</h1>
        <p class="section-text">
          先读入 data 真源，再预览人流曲线，运行仿真，评估当前参数，最后启动随机搜索或模拟退火。
        </p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" :loading="booting || runningSimulation" @click="handleRunSimulation">
          运行仿真
        </el-button>
        <el-button :loading="evaluating" @click="handleEvaluateStrategy">单次评估</el-button>
        <el-button type="warning" :loading="optimizationRunning" @click="handleRunOptimization">
          启动优化
        </el-button>
      </div>
    </section>

    <section class="panel-grid panel-grid--triple">
      <article class="panel-card">
        <p class="panel-label">学生池</p>
        <h2>{{ studentPoolSummary?.totalStudents ?? '--' }}</h2>
        <p class="panel-subtitle">学生画像样本数</p>
        <div class="metric-row">
          <span>平均预算下界</span>
          <strong>{{ studentPoolSummary?.avgBudgetMin ?? '--' }}</strong>
        </div>
        <div class="metric-row">
          <span>平均预算上界</span>
          <strong>{{ studentPoolSummary?.avgBudgetMax ?? '--' }}</strong>
        </div>
        <div class="metric-row">
          <span>平均等待容忍</span>
          <strong>{{ studentPoolSummary?.avgWaitingToleranceMinutes ?? '--' }}</strong>
        </div>
      </article>

      <article class="panel-card">
        <p class="panel-label">到达规则</p>
        <h2>{{ arrivalRules.length }}</h2>
        <p class="panel-subtitle">meal period 规则数</p>
        <div class="tag-cloud">
          <span v-for="rule in arrivalRules" :key="rule.mealPeriod" class="tag-pill">
            {{ rule.mealPeriod }} · {{ rule.peaks.length }} peaks
          </span>
        </div>
      </article>

      <article class="panel-card">
        <p class="panel-label">标签映射</p>
        <h2>{{ tagMappingSummary?.totalMappings ?? '--' }}</h2>
        <p class="panel-subtitle">exact / keyword 统一规则</p>
        <div class="metric-row">
          <span>EXACT</span>
          <strong>{{ tagMappingSummary?.exactMappings ?? '--' }}</strong>
        </div>
        <div class="metric-row">
          <span>KEYWORD</span>
          <strong>{{ tagMappingSummary?.keywordMappings ?? '--' }}</strong>
        </div>
      </article>
    </section>

    <section class="panel-grid panel-grid--double">
      <article class="panel-card">
        <div class="card-head">
          <div>
            <p class="panel-label">仿真配置</p>
            <h3>最小输入</h3>
          </div>
          <el-button text :loading="previewing" @click="refreshPreview">刷新曲线预览</el-button>
        </div>
        <el-form label-position="top" class="form-grid">
          <el-form-item label="用户类型">
            <el-select v-model="simulationForm.userType">
              <el-option label="STUDENT" value="STUDENT" />
              <el-option label="HURRY" value="HURRY" />
              <el-option label="BUDGET_SENSITIVE" value="BUDGET_SENSITIVE" />
            </el-select>
          </el-form-item>
          <el-form-item label="餐段">
            <el-select v-model="simulationForm.mealPeriod">
              <el-option label="BREAKFAST" value="BREAKFAST" />
              <el-option label="LUNCH" value="LUNCH" />
              <el-option label="DINNER" value="DINNER" />
            </el-select>
          </el-form-item>
          <el-form-item label="日期类型">
            <el-select v-model="simulationForm.dayType">
              <el-option label="WEEKDAY" value="WEEKDAY" />
              <el-option label="WEEKEND" value="WEEKEND" />
            </el-select>
          </el-form-item>
          <el-form-item label="拥挤等级">
            <el-select v-model="simulationForm.crowdLevel">
              <el-option label="IDLE" value="IDLE" />
              <el-option label="NORMAL" value="NORMAL" />
              <el-option label="BUSY" value="BUSY" />
              <el-option label="EXTREME" value="EXTREME" />
            </el-select>
          </el-form-item>
          <el-form-item label="虚拟用户数">
            <el-input-number v-model="simulationForm.virtualUserCount" :min="50" :max="5000" :step="50" />
          </el-form-item>
          <el-form-item label="时长 / 步长">
            <div class="inline-pair">
              <el-input-number v-model="simulationForm.durationMinutes" :min="15" :max="180" :step="5" />
              <el-input-number v-model="simulationForm.stepMinutes" :min="1" :max="30" :step="1" />
            </div>
          </el-form-item>
          <el-form-item label="随机种子">
            <el-input-number v-model="simulationForm.randomSeed" :min="1" :max="999999999" />
          </el-form-item>
        </el-form>
        <p class="support-text">
          步长不会改变后端核心仿真结果；当前更接近展示粒度参数。
        </p>
      </article>

      <article class="panel-card">
        <div class="card-head">
          <div>
            <p class="panel-label">策略参数</p>
            <h3>分流规则可调</h3>
          </div>
          <el-button text @click="resetStrategyParameters">重置默认参数</el-button>
        </div>
        <div class="slider-grid">
          <label v-for="field in strategyFields" :key="field.key" class="slider-item">
            <span>{{ field.label }}</span>
            <el-input-number
              v-model="strategyParameters[field.key]"
              :min="field.min"
              :max="field.max"
              :step="field.step"
              :precision="field.precision"
            />
          </label>
        </div>
      </article>
    </section>

    <section class="panel-grid panel-grid--double">
      <article class="panel-card panel-card--chart">
        <div class="card-head">
          <div>
            <p class="panel-label">人流曲线</p>
            <h3>Preview / Run</h3>
          </div>
          <span class="muted-note">采样到达人数 + 规则权重</span>
        </div>
        <EChartPanel :option="arrivalChartOption" height="360px" />
      </article>

      <article class="panel-card panel-card--chart">
        <div class="card-head">
          <div>
            <p class="panel-label">分钟指标</p>
            <h3>排队与拥挤</h3>
          </div>
          <span class="muted-note">队列长度 / 平均等待 / 过载窗口数</span>
        </div>
        <EChartPanel :option="minuteMetricsOption" height="360px" />
      </article>
    </section>

    <section class="panel-grid panel-grid--double">
      <article class="panel-card panel-card--chart">
        <div class="card-head">
          <div>
            <p class="panel-label">窗口压力</p>
            <h3>峰值时刻 Top 窗口</h3>
          </div>
          <span class="muted-note">按 pressure score 排序</span>
        </div>
        <EChartPanel :option="windowPressureOption" height="360px" />
      </article>

      <article class="panel-card">
        <div class="card-head">
          <div>
            <p class="panel-label">高级指标</p>
            <h3>拥挤放大效果</h3>
          </div>
          <el-tag v-if="currentRun" type="success">Run #{{ currentRun.runId }}</el-tag>
        </div>
        <div class="metric-stack">
          <div class="metric-row"><span>overloadedWindowMinutes</span><strong>{{ advancedMetrics?.overloadedWindowMinutes ?? '--' }}</strong></div>
          <div class="metric-row"><span>extremeWindowMinutes</span><strong>{{ advancedMetrics?.extremeWindowMinutes ?? '--' }}</strong></div>
          <div class="metric-row"><span>peakWait10m</span><strong>{{ advancedMetrics?.peakWait10m ?? '--' }}</strong></div>
          <div class="metric-row"><span>queueImbalanceIndex</span><strong>{{ advancedMetrics?.queueImbalanceIndex ?? '--' }}</strong></div>
          <div class="metric-row"><span>acceptedDiversionCount</span><strong>{{ advancedMetrics?.acceptedDiversionCount ?? '--' }}</strong></div>
          <div class="metric-row"><span>benefitPerAcceptedDiversion</span><strong>{{ advancedMetrics?.benefitPerAcceptedDiversion ?? '--' }}</strong></div>
        </div>
      </article>
    </section>

    <section class="panel-grid panel-grid--double">
      <article class="panel-card">
        <div class="card-head">
          <div>
            <p class="panel-label">当前分流建议</p>
            <h3>Suggestion Snapshot</h3>
          </div>
          <el-button :disabled="!currentRun" :loading="generatingDiversion" @click="handleGenerateDiversion">生成建议</el-button>
        </div>
        <el-empty v-if="!diversionResult?.suggestions?.length" description="暂无分流建议" />
        <el-table v-else :data="diversionResult.suggestions" height="320">
          <el-table-column prop="fromWindowId" label="源窗口" width="86" />
          <el-table-column prop="toWindowId" label="目标窗口" width="86" />
          <el-table-column prop="suggestedUserCount" label="建议人数" width="96" />
          <el-table-column prop="acceptanceRate" label="接受率" width="90" />
          <el-table-column prop="estimatedAcceptedCount" label="估算接受" width="100" />
          <el-table-column prop="estimatedWaitReduction" label="减等待" width="90" />
        </el-table>
        <p class="support-text">{{ diversionResult?.reason }}</p>
      </article>

      <article class="panel-card">
        <div class="card-head">
          <div>
            <p class="panel-label">单次评估</p>
            <h3>Loss & Compare</h3>
          </div>
          <el-button :disabled="!currentRun" :loading="evaluating" @click="handleEvaluateStrategy">评估当前参数</el-button>
        </div>
        <div v-if="evaluationResult" class="metric-stack">
          <div class="metric-row"><span>loss</span><strong>{{ evaluationResult.loss }}</strong></div>
          <div class="metric-row"><span>avgWaitDelta</span><strong>{{ evaluationResult.lossBreakdown.avgWaitDelta }}</strong></div>
          <div class="metric-row"><span>maxQueueDelta</span><strong>{{ evaluationResult.lossBreakdown.maxQueueDelta }}</strong></div>
          <div class="metric-row"><span>extremeWindowMinutesDelta</span><strong>{{ evaluationResult.lossBreakdown.extremeWindowMinutesDelta }}</strong></div>
          <div class="metric-row"><span>acceptedBenefit</span><strong>{{ evaluationResult.lossBreakdown.acceptedDiversionBenefit }}</strong></div>
        </div>
        <el-empty v-else description="先做一次评估" />
      </article>
    </section>

    <section class="panel-grid panel-grid--double">
      <article class="panel-card panel-card--chart">
        <div class="card-head">
          <div>
            <p class="panel-label">优化过程</p>
            <h3>Iteration History</h3>
          </div>
          <div class="inline-pair inline-pair--compact">
            <el-select v-model="optimizationMethod" style="width: 170px">
              <el-option label="SIMULATED_ANNEALING" value="SIMULATED_ANNEALING" />
              <el-option label="RANDOM_SEARCH" value="RANDOM_SEARCH" />
            </el-select>
            <el-input-number v-model="optimizationIterations" :min="5" :max="120" :step="5" />
          </div>
        </div>
        <EChartPanel :option="optimizationChartOption" height="360px" />
      </article>

      <article class="panel-card">
        <div class="card-head">
          <div>
            <p class="panel-label">最佳结果</p>
            <h3>Best Candidate</h3>
          </div>
          <el-tag v-if="optimizationJob" :type="optimizationJob.status === 'COMPLETED' ? 'success' : 'warning'">
            {{ optimizationJob.status }}
          </el-tag>
        </div>
        <div v-if="optimizationBest?.evaluation" class="metric-stack">
          <div class="metric-row"><span>bestLoss</span><strong>{{ optimizationBest.evaluation.loss }}</strong></div>
          <div class="metric-row"><span>compareRunId</span><strong>{{ optimizationBest.evaluation.compareRunId ?? '--' }}</strong></div>
          <div class="metric-row"><span>estimatedAccepted</span><strong>{{ optimizationBest.evaluation.diversionResult.estimatedSystemBenefit.estimatedAcceptedCount }}</strong></div>
          <div class="metric-row"><span>avgWaitDelta</span><strong>{{ optimizationBest.evaluation.lossBreakdown.avgWaitDelta }}</strong></div>
          <div class="metric-row"><span>queueImbalanceIndex</span><strong>{{ optimizationBest.evaluation.compareAdvancedMetrics.queueImbalanceIndex }}</strong></div>
        </div>
        <el-empty v-else description="尚未得到最优解" />
      </article>
    </section>
  </main>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import EChartPanel from '../components/EChartPanel.vue'
import {
  evaluateOptimization,
  generateDiversion,
  getAdvancedMetrics,
  getArrivalCurve,
  getArrivalRules,
  getDefaultStrategyParameters,
  getLossConfig,
  getMinuteMetrics,
  getOptimizationBest,
  getOptimizationIterations,
  getOptimizationJob,
  getStudentPoolSummary,
  getTagMappingsSummary,
  getWindowPressure,
  previewArrivalCurve,
  runOptimization,
  runSimulation,
} from '../api/v04'

const booting = ref(true)
const previewing = ref(false)
const runningSimulation = ref(false)
const generatingDiversion = ref(false)
const evaluating = ref(false)
const optimizationRunning = ref(false)

const studentPoolSummary = ref(null)
const arrivalRules = ref([])
const tagMappingSummary = ref(null)
const lossConfig = ref(null)

const currentRun = ref(null)
const previewCurve = ref(null)
const runArrivalCurve = ref(null)
const minuteMetrics = ref(null)
const windowPressure = ref(null)
const advancedMetrics = ref(null)
const diversionResult = ref(null)
const evaluationResult = ref(null)
const optimizationJob = ref(null)
const optimizationIterationsData = ref([])
const optimizationBest = ref(null)

const optimizationMethod = ref('SIMULATED_ANNEALING')
const optimizationIterations = ref(24)
let pollTimer = null
let previewTimer = null

const simulationForm = reactive({
  userType: 'STUDENT',
  mealPeriod: 'LUNCH',
  dayType: 'WEEKDAY',
  crowdLevel: 'BUSY',
  virtualUserCount: 800,
  durationMinutes: 60,
  stepMinutes: 3,
  randomSeed: 20260611,
})

const strategyParameters = reactive({})

const strategyFields = [
  { key: 'pressureWaitWeight', label: 'pressureWaitWeight', min: 0.1, max: 3, step: 0.05, precision: 2 },
  { key: 'pressureQueueWeight', label: 'pressureQueueWeight', min: 0.1, max: 3, step: 0.05, precision: 2 },
  { key: 'pressureLoadWeight', label: 'pressureLoadWeight', min: 0.05, max: 2, step: 0.05, precision: 2 },
  { key: 'pressureCrowdWeight', label: 'pressureCrowdWeight', min: 0.05, max: 2, step: 0.05, precision: 2 },
  { key: 'sourcePressureMargin', label: 'sourcePressureMargin', min: 0.05, max: 1, step: 0.02, precision: 2 },
  { key: 'strictTargetPressureBuffer', label: 'strictTargetPressureBuffer', min: 0.02, max: 0.5, step: 0.01, precision: 2 },
  { key: 'sameRestaurantBonus', label: 'sameRestaurantBonus', min: 0.5, max: 20, step: 0.5, precision: 1 },
  { key: 'crossRestaurantPenalty', label: 'crossRestaurantPenalty', min: 0.5, max: 20, step: 0.5, precision: 1 },
  { key: 'tagSimilarityWeight', label: 'tagSimilarityWeight', min: 0.05, max: 1, step: 0.02, precision: 2 },
  { key: 'serviceRateWeight', label: 'serviceRateWeight', min: 0.05, max: 1, step: 0.02, precision: 2 },
  { key: 'pressureGapTransferWeight', label: 'pressureGapTransferWeight', min: 0.2, max: 6, step: 0.1, precision: 1 },
  { key: 'queueGapTransferWeight', label: 'queueGapTransferWeight', min: 0.1, max: 3, step: 0.05, precision: 2 },
  { key: 'sourceExcessTransferWeight', label: 'sourceExcessTransferWeight', min: 0.1, max: 3, step: 0.05, precision: 2 },
  { key: 'maxTransferCount', label: 'maxTransferCount', min: 5, max: 120, step: 1, precision: 0 },
  { key: 'acceptanceBaseRate', label: 'acceptanceBaseRate', min: 0.05, max: 0.8, step: 0.01, precision: 2 },
  { key: 'waitReductionAcceptanceWeight', label: 'waitReductionAcceptanceWeight', min: 0.005, max: 0.1, step: 0.005, precision: 3 },
  { key: 'pressureGapAcceptanceWeight', label: 'pressureGapAcceptanceWeight', min: 0.005, max: 0.2, step: 0.005, precision: 3 },
  { key: 'tagSimilarityAcceptanceWeight', label: 'tagSimilarityAcceptanceWeight', min: 0.05, max: 0.8, step: 0.01, precision: 2 },
]

const arrivalChartOption = computed(() => {
  const previewPoints = previewCurve.value?.points ?? []
  const runPoints = runArrivalCurve.value?.points ?? []
  const source = runPoints.length ? runPoints : previewPoints
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['arrivals', 'weight'], textStyle: { color: '#dbeafe' } },
    grid: { left: 46, right: 24, top: 40, bottom: 32 },
    xAxis: {
      type: 'category',
      data: source.map((item) => item.minute),
      axisLabel: { color: '#9fb0c4' },
      axisLine: { lineStyle: { color: '#37506e' } },
    },
    yAxis: [
      { type: 'value', axisLabel: { color: '#9fb0c4' }, splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } } },
      { type: 'value', axisLabel: { color: '#9fb0c4' }, splitLine: { show: false } },
    ],
    series: [
      {
        name: 'arrivals',
        type: 'bar',
        data: source.map((item) => item.arrivals),
        itemStyle: { color: '#ff9f5c' },
      },
      {
        name: 'weight',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: source.map((item) => item.weight),
        lineStyle: { color: '#7dd3fc', width: 3 },
        symbol: 'none',
      },
    ],
  }
})

const minuteMetricsOption = computed(() => {
  const points = minuteMetrics.value?.points ?? []
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['queue', 'avgWait', 'overloaded'], textStyle: { color: '#dbeafe' } },
    grid: { left: 46, right: 24, top: 40, bottom: 32 },
    xAxis: {
      type: 'category',
      data: points.map((item) => item.minute),
      axisLabel: { color: '#9fb0c4' },
      axisLine: { lineStyle: { color: '#37506e' } },
    },
    yAxis: [
      { type: 'value', axisLabel: { color: '#9fb0c4' }, splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } } },
      { type: 'value', axisLabel: { color: '#9fb0c4' }, splitLine: { show: false } },
    ],
    series: [
      { name: 'queue', type: 'bar', data: points.map((item) => item.totalQueueLength), itemStyle: { color: '#f97316' } },
      { name: 'avgWait', type: 'line', data: points.map((item) => item.avgWindowWait), smooth: true, symbol: 'none', lineStyle: { color: '#60a5fa', width: 3 } },
      { name: 'overloaded', type: 'line', yAxisIndex: 1, data: points.map((item) => item.overloadedWindowCount), smooth: true, symbol: 'none', lineStyle: { color: '#facc15', width: 2 } },
    ],
  }
})

const windowPressureOption = computed(() => {
  const peak = selectPeakPressurePoint(windowPressure.value?.points ?? [])
  const windows = (peak?.windows ?? []).slice().sort((a, b) => b.pressureScore - a.pressureScore).slice(0, 12)
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 100, right: 24, top: 20, bottom: 32 },
    xAxis: {
      type: 'value',
      axisLabel: { color: '#9fb0c4' },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
    },
    yAxis: {
      type: 'category',
      data: windows.map((item) => `${item.windowId} · ${item.windowName}`),
      axisLabel: { color: '#9fb0c4' },
      axisLine: { lineStyle: { color: '#37506e' } },
    },
    series: [
      {
        type: 'bar',
        data: windows.map((item) => item.pressureScore),
        itemStyle: {
          color: '#fb7185',
        },
      },
    ],
  }
})

const optimizationChartOption = computed(() => {
  const items = optimizationIterationsData.value ?? []
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['loss', 'temperature'], textStyle: { color: '#dbeafe' } },
    grid: { left: 46, right: 24, top: 40, bottom: 32 },
    xAxis: {
      type: 'category',
      data: items.map((item) => item.iteration),
      axisLabel: { color: '#9fb0c4' },
      axisLine: { lineStyle: { color: '#37506e' } },
    },
    yAxis: [
      { type: 'value', axisLabel: { color: '#9fb0c4' }, splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } } },
      { type: 'value', axisLabel: { color: '#9fb0c4' }, splitLine: { show: false } },
    ],
    series: [
      { name: 'loss', type: 'line', smooth: true, data: items.map((item) => item.loss), lineStyle: { color: '#f97316', width: 3 }, symbol: 'none' },
      { name: 'temperature', type: 'line', yAxisIndex: 1, smooth: true, data: items.map((item) => item.temperature), lineStyle: { color: '#38bdf8', width: 2 }, symbol: 'none' },
    ],
  }
})

onMounted(async () => {
  try {
    const [pool, rules, mappings, defaults, loss] = await Promise.all([
      getStudentPoolSummary(),
      getArrivalRules(),
      getTagMappingsSummary(),
      getDefaultStrategyParameters(),
      getLossConfig(),
    ])
    studentPoolSummary.value = pool
    arrivalRules.value = rules
    tagMappingSummary.value = mappings
    lossConfig.value = loss
    applyStrategyParameters(defaults)
    await refreshPreview()
  } catch (error) {
    ElMessage.error(error.message || '初始化失败')
  } finally {
    booting.value = false
  }
})

watch(
  () => ({ ...simulationForm }),
  () => {
    if (!booting.value) {
      queuePreviewRefresh()
    }
  },
  { deep: true },
)

onBeforeUnmount(() => {
  stopPolling()
  clearPreviewTimer()
})

function buildSimulationPayload() {
  return {
    profile: {
      userType: simulationForm.userType,
    },
    scenario: {
      mealPeriod: simulationForm.mealPeriod,
      dayType: simulationForm.dayType,
      crowdLevel: simulationForm.crowdLevel,
      virtualUserCount: simulationForm.virtualUserCount,
      durationMinutes: simulationForm.durationMinutes,
      stepMinutes: simulationForm.stepMinutes,
      randomSeed: simulationForm.randomSeed,
    },
  }
}

function currentStrategyPayload() {
  return JSON.parse(JSON.stringify(strategyParameters))
}

function deriveTargetCrowdLevel() {
  return simulationForm.crowdLevel === 'EXTREME' ? 'BUSY' : 'NORMAL'
}

function queuePreviewRefresh() {
  clearPreviewTimer()
  previewTimer = window.setTimeout(() => {
    refreshPreview()
  }, 180)
}

function clearPreviewTimer() {
  if (previewTimer) {
    window.clearTimeout(previewTimer)
    previewTimer = null
  }
}

async function refreshPreview() {
  previewing.value = true
  try {
    previewCurve.value = await previewArrivalCurve(buildSimulationPayload())
  } catch (error) {
    ElMessage.error(error.message || '人流曲线预览失败')
  } finally {
    previewing.value = false
  }
}

async function handleRunSimulation() {
  runningSimulation.value = true
  try {
    const run = await runSimulation(buildSimulationPayload())
    currentRun.value = run
    await loadRunArtifacts(run.runId)
    diversionResult.value = null
    evaluationResult.value = null
    ElMessage.success(`仿真完成 #${run.runId}`)
  } catch (error) {
    ElMessage.error(error.message || '仿真运行失败')
  } finally {
    runningSimulation.value = false
  }
}

async function loadRunArtifacts(runId) {
  const [curve, metrics, pressure, advanced] = await Promise.all([
    getArrivalCurve(runId),
    getMinuteMetrics(runId),
    getWindowPressure(runId),
    getAdvancedMetrics(runId),
  ])
  runArrivalCurve.value = curve
  minuteMetrics.value = metrics
  windowPressure.value = pressure
  advancedMetrics.value = advanced
}

async function handleGenerateDiversion() {
  if (!currentRun.value) return
  generatingDiversion.value = true
  try {
    diversionResult.value = await generateDiversion({
      runId: currentRun.value.runId,
      minute: peakMinute(),
      targetCrowdLevel: deriveTargetCrowdLevel(),
      profile: {
        userType: simulationForm.userType,
      },
      strategyParameters: currentStrategyPayload(),
    })
    ElMessage.success('分流建议已生成')
  } catch (error) {
    ElMessage.error(error.message || '分流建议生成失败')
  } finally {
    generatingDiversion.value = false
  }
}

async function handleEvaluateStrategy() {
  if (!currentRun.value) return
  evaluating.value = true
  try {
    evaluationResult.value = await evaluateOptimization({
      baseRunId: currentRun.value.runId,
      minute: peakMinute(),
      targetCrowdLevel: deriveTargetCrowdLevel(),
      strategyParameters: currentStrategyPayload(),
    })
    diversionResult.value = evaluationResult.value.diversionResult
    ElMessage.success('参数评估完成')
  } catch (error) {
    ElMessage.error(error.message || '参数评估失败')
  } finally {
    evaluating.value = false
  }
}

async function handleRunOptimization() {
  if (!currentRun.value) return
  optimizationRunning.value = true
  try {
    optimizationJob.value = await runOptimization({
      baseRunId: currentRun.value.runId,
      minute: peakMinute(),
      targetCrowdLevel: deriveTargetCrowdLevel(),
      method: optimizationMethod.value,
      iterationCount: optimizationIterations.value,
      randomSeed: simulationForm.randomSeed + 101,
      initialParameters: currentStrategyPayload(),
    })
    optimizationIterationsData.value = []
    optimizationBest.value = null
    startPolling(optimizationJob.value.jobId)
  } catch (error) {
    optimizationRunning.value = false
    ElMessage.error(error.message || '优化任务启动失败')
  }
}

function startPolling(jobId) {
  stopPolling()
  pollTimer = window.setInterval(() => refreshOptimization(jobId), 1200)
  refreshOptimization(jobId)
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function refreshOptimization(jobId) {
  try {
    const [job, iterations, best] = await Promise.all([
      getOptimizationJob(jobId),
      getOptimizationIterations(jobId),
      getOptimizationBest(jobId).catch(() => null),
    ])
    optimizationJob.value = job
    optimizationIterationsData.value = iterations
    if (best) {
      optimizationBest.value = best
    }
    if (job.status === 'COMPLETED' || job.status === 'FAILED') {
      optimizationRunning.value = false
      stopPolling()
      if (job.status === 'COMPLETED') {
        ElMessage.success('优化任务完成')
      } else {
        ElMessage.error(job.message || '优化任务失败')
      }
    }
  } catch (error) {
    optimizationRunning.value = false
    stopPolling()
    ElMessage.error(error.message || '优化任务查询失败')
  }
}

function peakMinute() {
  const points = minuteMetrics.value?.points ?? []
  if (!points.length) return 0
  return points.reduce((best, item) => {
    const bestScore = best.totalQueueLength + best.overloadedWindowCount * 4
    const itemScore = item.totalQueueLength + item.overloadedWindowCount * 4
    return itemScore >= bestScore ? item : best
  }).minute
}

function selectPeakPressurePoint(points) {
  if (!points.length) return null
  return points.reduce((best, item) => {
    const bestMax = Math.max(...best.windows.map((window) => window.pressureScore), 0)
    const itemMax = Math.max(...item.windows.map((window) => window.pressureScore), 0)
    return itemMax >= bestMax ? item : best
  })
}

function resetStrategyParameters() {
  getDefaultStrategyParameters().then((defaults) => {
    applyStrategyParameters(defaults)
    ElMessage.success('策略参数已重置')
  })
}

function applyStrategyParameters(defaults) {
  strategyFields.forEach((field) => {
    strategyParameters[field.key] = defaults[field.key]
  })
}
</script>
