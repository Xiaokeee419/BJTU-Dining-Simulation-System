<template>
  <main class="dashboard-page">
    <header class="dashboard-topbar">
      <div>
        <p class="eyebrow">聚焦排队拥挤缓解</p>
        <h1>食堂排队仿真与分流优化展示</h1>
        <p>
          先展示 baseline 高峰排队，再验证规则分流，最后用模拟退火寻找缓解拥挤更好的参数组合。
        </p>
      </div>
      <div class="dashboard-actions">
        <el-button type="primary" :loading="loading.simulation" @click="handleRun">
          运行基础仿真
        </el-button>
        <el-button :loading="loading.diversion" :disabled="!currentRun" @click="handleDiversion">
          生成分流建议
        </el-button>
        <el-button
          :loading="loading.comparison"
          :disabled="!diversionResult?.suggestions?.length"
          @click="handleComparison"
        >
          验证规则分流
        </el-button>
        <el-button
          type="success"
          :loading="loading.optimization"
          :disabled="!currentRun"
          @click="handleOptimization"
        >
          启动模拟退火优化
        </el-button>
      </div>
    </header>

    <div class="dashboard-canvas">
      <section class="dashboard-section">
        <SectionHeading
          index="01"
          title="基础场景与高峰队列"
          description="真实 CSV 人流驱动仿真；默认定位到高峰分钟，同时支持动态播放和手动选择具体时刻。"
        />

        <div class="input-grid">
          <section class="dashboard-panel input-panel">
            <header class="panel-heading">
              <div>
                <h2>运行参数</h2>
                <p>只保留和排队演化直接相关的几个控制项，界面更聚焦。</p>
              </div>
              <span class="status-chip">{{ requestStatus }}</span>
            </header>
            <div class="compact-form">
              <label>
                <span>就餐时段</span>
                <el-select v-model="form.mealPeriod" @change="handleMealPeriodChange">
                  <el-option label="早餐" value="BREAKFAST" />
                  <el-option label="午餐" value="LUNCH" />
                  <el-option label="晚餐" value="DINNER" />
                </el-select>
              </label>
              <label>
                <span>虚拟人数</span>
                <el-input-number
                  v-model="form.virtualUserCount"
                  :min="1"
                  :max="5000"
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
              <label class="form-wide">
                <span>随机种子</span>
                <el-input-number v-model="form.randomSeed" :min="1" :max="99999999" />
              </label>
            </div>
          </section>

          <section class="dashboard-panel source-panel">
            <header class="panel-heading">
              <div>
                <h2>CSV 数据基线</h2>
                <p>{{ dataOverview?.sourceDirectory || '正在读取数据目录' }}</p>
              </div>
              <span class="source-badge">REAL CSV</span>
            </header>
            <div class="source-stat-grid">
              <MetricBlock label="人群记录" :value="dataOverview?.studentCount" unit="条" />
              <MetricBlock label="餐厅" :value="dataOverview?.restaurantCount" unit="个" />
              <MetricBlock label="窗口" :value="dataOverview?.windowCount" unit="个" />
              <MetricBlock label="开放窗口" :value="dataOverview?.openWindowCount" unit="个" />
            </div>
            <dl class="source-details">
              <div>
                <dt>人流来源</dt>
                <dd>{{ flowCurve?.sourceFile || 'virtual_students.csv' }}</dd>
              </div>
              <div>
                <dt>支持人群</dt>
                <dd>{{ dataOverview?.userTypes?.join(' / ') || '--' }}</dd>
              </div>
            </dl>
          </section>
        </div>

        <div class="kpi-grid">
          <MetricBlock
            v-for="card in baselineKpiCards"
            :key="card.label"
            :label="card.label"
            :value="card.value"
            :unit="card.unit"
            :tone="card.tone"
          />
        </div>

        <div class="chart-grid chart-grid-wide">
          <DashboardChart
            title="CSV 原始人流入流曲线"
            subtitle="由 virtual_students.csv 的到达分钟聚合，展示客流在高峰前后的分布。"
            :option="flowCurveOption"
            :loading="loading.flow || loading.initialize"
            :error="flowCurveError"
            :empty="!flowCurve?.points?.length"
          />
          <DashboardChart
            title="当前窗口压力排行"
            :subtitle="windowLoadSubtitle"
            :option="windowLoadOption"
            :loading="loading.simulation"
            :empty="!windowLoadRows.length"
          />
        </div>

        <QueueOverview
          v-if="currentRun"
          :run="currentRun"
          v-model:minute="overviewMinute"
          :initial-minute="peakPoint?.minute"
        />
        <section v-else class="dashboard-panel empty-panel">
          <div class="empty-state">
            运行基础仿真后，这里会展示高峰时刻的餐厅与窗口排队快照。
          </div>
        </section>
      </section>

      <section class="dashboard-section">
        <SectionHeading
          index="02"
          title="规则分流缓解效果"
          description="先用规则分流验证：从最拥挤窗口向可承接窗口导流，看看拥堵是否真的缓解。"
        />

        <div class="comparison-kpi-grid">
          <MetricBlock
            v-for="card in comparisonKpiCards"
            :key="card.label"
            :label="card.label"
            :value="card.value"
            :unit="card.unit"
            :tone="card.tone"
          />
        </div>

        <section class="dashboard-panel">
          <header class="panel-heading">
            <div>
              <h2>分流路径与承接关系</h2>
              <p>
                {{ diversionResult?.reason || '完成基础仿真后生成分流建议，并在同一批到达人群上重放对比。' }}
              </p>
            </div>
            <span class="status-chip">
              {{ diversionResult?.suggestions?.length || 0 }} 条建议
            </span>
          </header>
          <div v-if="suggestionRows.length" class="route-grid">
            <article v-for="item in suggestionRows" :key="item.key" class="route-card">
              <div class="route-title">
                <div>
                  <small>来源窗口</small>
                  <strong>{{ item.sourceLabel }}</strong>
                </div>
                <span>→</span>
                <div>
                  <small>目标窗口</small>
                  <strong>{{ item.targetLabel }}</strong>
                </div>
              </div>
              <div class="route-metrics">
                <MetricBlock label="建议人数" :value="item.suggestedUserCount" unit="人" />
                <MetricBlock label="预计接受" :value="item.estimatedAcceptedCount" unit="人" />
                <MetricBlock label="接受率" :value="item.acceptancePercent" unit="%" />
                <MetricBlock label="预计等待下降" :value="item.waitReduction" unit="分钟" />
              </div>
              <div class="pressure-compare">
                <span>源窗口压力 {{ item.sourceBefore }} → {{ item.sourceAfter }}</span>
                <span>目标窗口压力 {{ item.targetBefore }} → {{ item.targetAfter }}</span>
              </div>
              <p>{{ item.reason }}</p>
            </article>
          </div>
          <div v-else class="empty-state">
            当前没有分流路径。请先运行基础仿真，或选择压力更高的场景。
          </div>
        </section>

        <div class="chart-grid">
          <DashboardChart
            title="规则分流前后等待对比"
            subtitle="先看等待是否下降，避免只转移人流但没有真正缓解体验。"
            :option="waitComparisonOption"
            :loading="loading.comparison"
            :empty="!comparison"
            :height="250"
          />
          <DashboardChart
            title="规则分流前后拥挤对比"
            subtitle="重点看最大队列、未服务人数和高压窗口总量。"
            :option="queueComparisonOption"
            :loading="loading.comparison"
            :empty="!comparison"
            :height="250"
          />
        </div>

        <section class="dashboard-panel">
          <header class="panel-heading">
            <div>
              <h2>餐厅队列变化</h2>
              <p>按分流发生分钟对齐 baseline 与 compare run，重点观察各餐厅总队列是否回落。</p>
            </div>
            <span class="status-chip">
              Base #{{ currentRun?.runId || '--' }} / Compare #{{ compareRun?.runId || '--' }}
            </span>
          </header>
          <el-table :data="restaurantLoadRows" stripe empty-text="运行分流对比后显示餐厅队列变化">
            <el-table-column prop="name" label="餐厅" min-width="180" />
            <el-table-column prop="baseQueue" label="分流前队列" width="130" />
            <el-table-column prop="compareQueue" label="分流后队列" width="130" />
            <el-table-column prop="queueDelta" label="队列变化" width="120" />
            <el-table-column prop="baseCount" label="分流前负载" width="130" />
            <el-table-column prop="compareCount" label="分流后负载" width="130" />
          </el-table>
        </section>
      </section>

      <section class="dashboard-section">
        <SectionHeading
          index="03"
          title="模拟退火优化分流"
          description="在规则分流可行的前提下，再用模拟退火搜索更优参数，目标是让拥挤缓解更明显。"
        />

        <section class="annealing-note">
          <strong>模拟退火展示说明</strong>
          <p>
            系统每轮扰动一组参数，并运行一次真实 compare 仿真。候选参数降低 loss 时直接接受；
            loss 上升时在高温阶段仍可能接受，以避免过早陷入局部最优。最终展示最优参数对应的拥挤缓解效果。
          </p>
          <div class="optimization-controls">
            <label>
              <span>迭代次数</span>
              <el-input-number
                v-model="optimizationSettings.iterationCount"
                :min="1"
                :max="60"
              />
            </label>
            <label>
              <span>优化随机种子</span>
              <el-input-number
                v-model="optimizationSettings.randomSeed"
                :min="1"
                :max="99999999"
              />
            </label>
          </div>
        </section>

        <div class="optimization-summary-grid">
          <ParameterPanel title="初始参数" :parameters="strategyParameters" />
          <ParameterPanel
            title="当前参数"
            :parameters="optimizationJob?.currentParameters"
            empty-text="尚未开始优化"
          />
          <ParameterPanel
            title="最优参数"
            :parameters="optimizationBest?.parameters || optimizationJob?.bestParameters"
            empty-text="尚未产生 best"
            tone="best"
          />
          <section class="dashboard-panel progress-panel">
            <h3>搜索进度</h3>
            <div class="progress-kpis">
              <MetricBlock
                label="当前迭代"
                :value="optimizationJob?.currentIteration"
                :unit="`/ ${optimizationJob?.totalIterations || optimizationSettings.iterationCount}`"
              />
              <MetricBlock
                label="当前温度"
                :value="formatNumber(optimizationJob?.currentTemperature, 2)"
              />
              <MetricBlock
                label="当前 Loss"
                :value="formatNumber(optimizationJob?.currentLoss, 2)"
              />
              <MetricBlock
                label="Best Loss"
                :value="formatNumber(optimizationJob?.bestLoss, 2)"
                tone="success"
              />
            </div>
            <el-progress
              :percentage="optimizationProgress"
              :status="optimizationJob?.status === 'FAILED' ? 'exception' : undefined"
            />
          </section>
        </div>

        <section class="dashboard-panel outcome-panel">
          <header class="panel-heading">
            <div>
              <h2>退火最优方案效果</h2>
              <p>{{ optimizationNarrative }}</p>
            </div>
            <span class="status-chip">
              {{
                optimizationBest?.compareRunId
                  ? `Best #${optimizationBest.compareRunId}`
                  : optimizationJob?.status || 'NOT_STARTED'
              }}
            </span>
          </header>
          <div class="outcome-kpi-grid">
            <MetricBlock
              v-for="card in optimizationOutcomeCards"
              :key="card.label"
              :label="card.label"
              :value="card.value"
              :unit="card.unit"
              :tone="card.tone"
            />
          </div>
          <el-table
            :data="optimizationMetricRows"
            stripe
            empty-text="启动模拟退火后显示基线、规则分流与退火最优的指标对比"
          >
            <el-table-column prop="label" label="指标" min-width="150" />
            <el-table-column prop="baseline" label="Baseline" width="110" />
            <el-table-column prop="strategy" label="规则分流" width="110" />
            <el-table-column prop="optimized" label="退火最优" width="110" />
            <el-table-column prop="vsBaseline" label="相对基线" min-width="130" />
            <el-table-column prop="vsStrategy" label="相对规则分流" min-width="140" />
          </el-table>
        </section>

        <div class="chart-grid">
          <DashboardChart
            title="优化过程 Loss 曲线"
            subtitle="loss 越低越好；绿色点表示成为新的 best。"
            :option="lossCurveOption"
            :loading="loading.optimization && !optimizationIterations.length"
            :empty="!optimizationIterations.length"
          />
          <DashboardChart
            title="迭代中的关键拥挤指标"
            subtitle="同步观察最大队列、未服务人数和高压窗口总量是否回落。"
            :option="optimizationMetricOption"
            :loading="loading.optimization && !optimizationIterations.length"
            :empty="!optimizationIterations.length"
          />
        </div>

        <section class="dashboard-panel">
          <header class="panel-heading">
            <div>
              <h2>迭代历史</h2>
              <p>每一行对应一次候选参数评估和 compare 仿真，方便复盘搜索过程。</p>
            </div>
            <span class="status-chip">{{ optimizationJob?.status || 'NOT_STARTED' }}</span>
          </header>
          <el-table
            :data="optimizationIterations"
            stripe
            max-height="460"
            empty-text="启动模拟退火后显示迭代过程"
          >
            <el-table-column prop="iteration" label="#" width="58" />
            <el-table-column prop="temperature" label="温度" width="82" />
            <el-table-column prop="loss" label="Loss" width="86" />
            <el-table-column label="接受" width="72">
              <template #default="{ row }">
                <el-tag :type="row.accepted ? 'success' : 'info'" size="small">
                  {{ row.accepted ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="高温接受" width="90">
              <template #default="{ row }">{{ row.acceptedWorseSolution ? '是' : '--' }}</template>
            </el-table-column>
            <el-table-column label="Best" width="70">
              <template #default="{ row }">{{ row.best ? '✓' : '' }}</template>
            </el-table-column>
            <el-table-column prop="metrics.avgWaitMinutes" label="平均等待" width="100" />
            <el-table-column prop="metrics.maxWaitMinutes" label="最大等待" width="100" />
            <el-table-column prop="metrics.maxQueueLength" label="最大队列" width="96" />
            <el-table-column prop="metrics.unservedUserCount" label="未服务" width="86" />
            <el-table-column prop="metrics.busyWindowCount" label="忙碌窗口" width="96" />
            <el-table-column prop="metrics.extremeWindowCount" label="极端窗口" width="96" />
            <el-table-column prop="compareRunId" label="Compare Run" min-width="120" />
          </el-table>
        </section>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, defineComponent, h, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import DashboardChart from '../components/DashboardChart.vue'
import QueueOverview from '../components/QueueOverview.vue'
import { useDashboardStore } from '../stores/dashboardStore'
import {
  avgWaitMinutes,
  busyWindowCount,
  extremeWindowCount,
  flattenWindows,
  interpolateTimePoint,
  maxWindowQueueLength,
  resolveTimePoint,
  totalCurrentCount,
  totalQueueLength,
} from '../utils/simulationStats'

const store = useDashboardStore()
const {
  dataOverview,
  flowCurve,
  form,
  strategyParameters,
  optimizationSettings,
  currentRun,
  compareRun,
  diversionResult,
  comparison,
  optimizationJob,
  optimizationIterations,
  optimizationBest,
  loading,
  requestStatus,
  lastError,
  peakPoint,
} = storeToRefs(store)

const SectionHeading = defineComponent({
  props: ['index', 'title', 'description'],
  setup: (props) => () =>
    h('header', { class: 'section-heading' }, [
      h('span', props.index),
      h('div', [
        h('h2', props.title),
        h('p', props.description),
      ]),
    ]),
})

const MetricBlock = defineComponent({
  props: ['label', 'value', 'unit', 'tone'],
  setup: (props) => () =>
    h('div', { class: ['metric-block', props.tone ? `tone-${props.tone}` : ''] }, [
      h('span', props.label),
      h('strong', props.value === null || props.value === undefined ? '--' : props.value),
      props.unit ? h('small', props.unit) : null,
    ]),
})

const ParameterPanel = defineComponent({
  props: ['title', 'parameters', 'emptyText', 'tone'],
  setup: (props) => () =>
    h('section', { class: ['dashboard-panel', 'parameter-panel', props.tone || ''] }, [
      h('h3', props.title),
      props.parameters
        ? h(
            'dl',
            parameterFields.map((field) =>
              h('div', [
                h('dt', field.label),
                h('dd', formatNumber(props.parameters[field.key], field.digits)),
              ]),
            ),
          )
        : h('p', { class: 'parameter-empty' }, props.emptyText || '暂无参数'),
    ]),
})

const parameterFields = [
  { key: 'sourcePressureScale', label: '源压力阈值倍率', digits: 3 },
  { key: 'targetPressureBufferScale', label: '目标压力缓冲倍率', digits: 3 },
  { key: 'transferScale', label: '转移规模倍率', digits: 3 },
  { key: 'maxTransferCount', label: '单条最大转移人数', digits: 0 },
  { key: 'acceptanceBias', label: '接受率偏置', digits: 3 },
  { key: 'waitReductionWeight', label: '等待改善权重', digits: 3 },
]

const overviewMinute = ref(null)

const flowCurveError = computed(() =>
  lastError.value?.endpoint?.includes('/data/flow-curves') ? lastError.value.message : '',
)

const overviewTimePoint = computed(() => {
  if (!currentRun.value) return null
  const minute = Number.isFinite(Number(overviewMinute.value))
    ? Number(overviewMinute.value)
    : Number(peakPoint.value?.minute || 0)
  return interpolateTimePoint(currentRun.value, minute)
})

const overviewQueueCount = computed(() =>
  overviewTimePoint.value ? totalQueueLength(overviewTimePoint.value) : null,
)
const overviewMaxWindowQueue = computed(() =>
  overviewTimePoint.value ? maxWindowQueueLength(overviewTimePoint.value) : null,
)
const overviewAvgWait = computed(() =>
  overviewTimePoint.value ? avgWaitMinutes(overviewTimePoint.value) : null,
)
const overviewCrowdedWindowCount = computed(() =>
  overviewTimePoint.value
    ? busyWindowCount(overviewTimePoint.value) + extremeWindowCount(overviewTimePoint.value)
    : null,
)
const overviewMinuteLabel = computed(() =>
  overviewTimePoint.value ? `第 ${overviewTimePoint.value.minute} 分钟` : '--',
)
const windowLoadSubtitle = computed(() =>
  overviewTimePoint.value
    ? `${overviewMinuteLabel.value} 的等待和排队综合压力，优先定位当前最拥堵窗口。`
    : '运行基础仿真后显示当前时刻压力最高的窗口。',
)

const baselineKpiCards = computed(() => [
  {
    label: 'Baseline Run',
    value: currentRun.value?.runId ?? '--',
  },
  {
    label: '当前总队列',
    value: overviewQueueCount.value ?? '--',
    unit: '人',
    tone: overviewQueueCount.value >= 80 ? 'danger' : 'warning',
  },
  {
    label: '最长窗口队列',
    value: overviewMaxWindowQueue.value ?? '--',
    unit: '人',
    tone: overviewMaxWindowQueue.value >= 28 ? 'danger' : 'warning',
  },
  {
    label: '窗口平均等待',
    value: formatNumber(overviewAvgWait.value, 1),
    unit: '分钟',
  },
  {
    label: '高压窗口',
    value: overviewCrowdedWindowCount.value ?? '--',
    unit: '个',
    tone: overviewCrowdedWindowCount.value > 0 ? 'warning' : 'success',
  },
  {
    label: '当前分钟',
    value: overviewTimePoint.value?.minute ?? '--',
    unit: 'min',
  },
])

const comparisonMinute = computed(
  () => comparison.value?.minute ?? diversionResult.value?.minute ?? peakPoint.value?.minute,
)
const baseComparisonPoint = computed(() =>
  resolveTimePoint(currentRun.value, comparisonMinute.value),
)
const compareComparisonPoint = computed(() =>
  resolveTimePoint(compareRun.value, comparisonMinute.value),
)

const comparisonSuggestedCount = computed(() =>
  (diversionResult.value?.suggestions || []).reduce(
    (sum, item) =>
      sum + Number(item.estimatedAcceptedCount ?? item.suggestedUserCount ?? 0),
    0,
  ),
)

const comparisonCrowdedDelta = computed(() => {
  const value = comparison.value
  if (!value) return null
  return Number(value.busyWindowCountDelta || 0) + Number(value.extremeWindowCountDelta || 0)
})

const comparisonKpiCards = computed(() => [
  {
    label: '建议承接人数',
    value: comparisonSuggestedCount.value,
    unit: '人',
  },
  {
    label: '平均等待变化',
    value: formatSignedNumber(comparison.value?.avgWaitDelta, 1),
    unit: '分钟',
    tone: lowerBetterTone(comparison.value?.avgWaitDelta),
  },
  {
    label: '最大队列变化',
    value: formatSignedNumber(comparison.value?.maxQueueDelta, 0),
    unit: '人',
    tone: lowerBetterTone(comparison.value?.maxQueueDelta),
  },
  {
    label: '高压窗口变化',
    value: formatSignedNumber(comparisonCrowdedDelta.value, 0),
    unit: '个',
    tone: lowerBetterTone(comparisonCrowdedDelta.value),
  },
])

const windowLoadRows = computed(() =>
  flattenWindows(overviewTimePoint.value)
    .filter(({ window }) => window.status !== 'CLOSED')
    .map(({ restaurantName, window }) => ({
      label: `${restaurantName} / ${window.name}`,
      queue: Number(window.queueLength || 0),
      wait: Number(window.waitMinutes || 0),
      pressure: pressureScore(window),
    }))
    .sort((left, right) => right.pressure - left.pressure)
    .slice(0, 10),
)

const suggestionRows = computed(() =>
  (diversionResult.value?.suggestions || []).map((suggestion) => {
    const sourceBefore = findWindow(baseComparisonPoint.value, suggestion.fromWindowId)
    const sourceAfter = findWindow(compareComparisonPoint.value, suggestion.fromWindowId)
    const targetBefore = findWindow(baseComparisonPoint.value, suggestion.toWindowId)
    const targetAfter = findWindow(compareComparisonPoint.value, suggestion.toWindowId)
    return {
      key: `${suggestion.fromWindowId}-${suggestion.toWindowId}`,
      sourceLabel: windowLabel(baseComparisonPoint.value, suggestion.fromWindowId),
      targetLabel: windowLabel(baseComparisonPoint.value, suggestion.toWindowId),
      suggestedUserCount: suggestion.suggestedUserCount,
      estimatedAcceptedCount: suggestion.estimatedAcceptedCount,
      acceptancePercent: Math.round(Number(suggestion.acceptanceRate || 0) * 100),
      waitReduction: formatNumber(suggestion.estimatedWaitReduction, 1),
      sourceBefore: formatNumber(pressureScore(sourceBefore), 2),
      sourceAfter: sourceAfter ? formatNumber(pressureScore(sourceAfter), 2) : '--',
      targetBefore: formatNumber(pressureScore(targetBefore), 2),
      targetAfter: targetAfter ? formatNumber(pressureScore(targetAfter), 2) : '--',
      reason: suggestion.reason,
    }
  }),
)

const restaurantLoadRows = computed(() => {
  if (!baseComparisonPoint.value || !compareComparisonPoint.value) return []
  const compareById = new Map(
    compareComparisonPoint.value.restaurants.map((item) => [item.restaurantId, item]),
  )

  return baseComparisonPoint.value.restaurants
    .map((base) => {
      const after = compareById.get(base.restaurantId)
      const baseQueue = totalQueueLength({ restaurants: [base] })
      const compareQueue = after ? totalQueueLength({ restaurants: [after] }) : 0
      return {
        name: base.name,
        baseCount: totalCurrentCount({ restaurants: [base] }),
        compareCount: after ? totalCurrentCount({ restaurants: [after] }) : 0,
        baseQueue,
        compareQueue,
        queueDelta: formatSignedNumber(compareQueue - baseQueue, 0),
      }
    })
    .filter((row) => row.baseQueue || row.compareQueue || row.baseCount || row.compareCount)
    .sort((left, right) => Math.abs(Number(right.queueDelta || 0)) - Math.abs(Number(left.queueDelta || 0)))
    .slice(0, 10)
})

const optimizationProgress = computed(() => {
  const total = Number(optimizationJob.value?.totalIterations || 0)
  const current = Number(optimizationJob.value?.currentIteration || 0)
  return total > 0 ? Math.round((current / total) * 100) : 0
})

const optimizationOutcomeCards = computed(() => {
  const base = currentRun.value?.metrics
  const best = optimizationBest.value?.metrics
  const strategy = compareRun.value?.metrics

  return [
    {
      label: 'Best Loss',
      value: formatNumber(optimizationBest.value?.loss, 2),
      tone: 'success',
    },
    {
      label: '最优 Compare Run',
      value: optimizationBest.value?.compareRunId ?? '--',
    },
    {
      label: '平均等待变化',
      value: formatSignedNumber(metricDelta(base?.avgWaitMinutes, best?.avgWaitMinutes), 1),
      unit: '分钟',
      tone: lowerBetterTone(metricDelta(base?.avgWaitMinutes, best?.avgWaitMinutes)),
    },
    {
      label: '最大队列变化',
      value: formatSignedNumber(metricDelta(base?.maxQueueLength, best?.maxQueueLength), 0),
      unit: '人',
      tone: lowerBetterTone(metricDelta(base?.maxQueueLength, best?.maxQueueLength)),
    },
    {
      label: '相对规则分流',
      value: formatSignedNumber(metricDelta(strategy?.maxQueueLength, best?.maxQueueLength), 0),
      unit: '人',
      tone: lowerBetterTone(metricDelta(strategy?.maxQueueLength, best?.maxQueueLength)),
    },
  ]
})

const optimizationMetricRows = computed(() => {
  const base = currentRun.value?.metrics
  const strategy = compareRun.value?.metrics
  const best = optimizationBest.value?.metrics

  return [
    metricRow('平均等待（分钟）', base?.avgWaitMinutes, strategy?.avgWaitMinutes, best?.avgWaitMinutes, 1, '分钟'),
    metricRow('最大等待（分钟）', base?.maxWaitMinutes, strategy?.maxWaitMinutes, best?.maxWaitMinutes, 1, '分钟'),
    metricRow('最大队列（人）', base?.maxQueueLength, strategy?.maxQueueLength, best?.maxQueueLength, 0, '人'),
    metricRow(
      '高压窗口（个）',
      crowdedMetricCount(base),
      crowdedMetricCount(strategy),
      crowdedMetricCount(best),
      0,
      '个',
    ),
    metricRow(
      '未服务人数（人）',
      base?.unservedUserCount,
      strategy?.unservedUserCount,
      best?.unservedUserCount,
      0,
      '人',
    ),
  ]
})

const optimizationNarrative = computed(() => {
  const base = currentRun.value?.metrics
  const strategy = compareRun.value?.metrics
  const best = optimizationBest.value

  if (!optimizationJob.value && !best) {
    return '模拟退火会在每一轮真实 compare 仿真后，根据拥挤缓解效果调整参数并记录 best。'
  }
  if (!best?.metrics || !base) {
    return optimizationJob.value?.message || '优化进行中，等待最优方案结果产生。'
  }

  const waitText = formatImprovementText(base.avgWaitMinutes, best.metrics.avgWaitMinutes, 1, '分钟')
  const queueText = formatImprovementText(base.maxQueueLength, best.metrics.maxQueueLength, 0, '人')
  const crowdedText = formatImprovementText(
    crowdedMetricCount(base),
    crowdedMetricCount(best.metrics),
    0,
    '个',
  )
  const extraQueueText = strategy
    ? `相较规则分流，最大队列${formatImprovementText(strategy.maxQueueLength, best.metrics.maxQueueLength, 0, '人')}。`
    : ''

  return `当前 best run #${best.compareRunId} 相比 baseline 平均等待${waitText}，最大队列${queueText}，高压窗口${crowdedText}。${extraQueueText}`
})

const flowCurveOption = computed(() => ({
  color: ['#0160a8', '#8fa8c7'],
  tooltip: { trigger: 'axis' },
  legend: { top: 8, data: ['每桶到达人数', '累计到达人数'] },
  grid: { left: 52, right: 54, top: 48, bottom: 40 },
  xAxis: {
    type: 'category',
    data: (flowCurve.value?.points || []).map((item) => `${item.minute}m`),
    axisLabel: { interval: 'auto' },
  },
  yAxis: [
    { type: 'value', name: '到达人数' },
    { type: 'value', name: '累计人数' },
  ],
  series: [
    {
      name: '每桶到达人数',
      type: 'line',
      smooth: true,
      symbol: 'none',
      data: (flowCurve.value?.points || []).map((item) => item.arrivals),
    },
    {
      name: '累计到达人数',
      type: 'line',
      yAxisIndex: 1,
      symbol: 'none',
      lineStyle: { type: 'dashed' },
      data: (flowCurve.value?.points || []).map((item) => item.cumulativeArrivals),
    },
  ],
}))

const windowLoadOption = computed(() => ({
  color: ['#0160a8'],
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 150, right: 30, top: 20, bottom: 30 },
  xAxis: { type: 'value', name: '压力分' },
  yAxis: {
    type: 'category',
    inverse: true,
    data: windowLoadRows.value.map((item) => item.label),
    axisLabel: { width: 135, overflow: 'truncate' },
  },
  series: [
    {
      type: 'bar',
      barMaxWidth: 18,
      data: windowLoadRows.value.map((item) => ({
        value: item.pressure,
        itemStyle: {
          color: item.wait >= 20 ? '#c81e1e' : item.wait >= 10 ? '#f97316' : '#0160a8',
        },
      })),
    },
  ],
}))

const waitComparisonOption = computed(() =>
  comparisonBarOption(
    ['平均等待', '最大等待'],
    [
      currentRun.value?.metrics?.avgWaitMinutes,
      currentRun.value?.metrics?.maxWaitMinutes,
    ],
    [
      compareRun.value?.metrics?.avgWaitMinutes,
      compareRun.value?.metrics?.maxWaitMinutes,
    ],
    '分钟',
  ),
)

const queueComparisonOption = computed(() =>
  comparisonBarOption(
    ['最大队列', '未服务人数', '高压窗口'],
    [
      currentRun.value?.metrics?.maxQueueLength,
      currentRun.value?.metrics?.unservedUserCount,
      crowdedMetricCount(currentRun.value?.metrics),
    ],
    [
      compareRun.value?.metrics?.maxQueueLength,
      compareRun.value?.metrics?.unservedUserCount,
      crowdedMetricCount(compareRun.value?.metrics),
    ],
    '数量',
  ),
)

const lossCurveOption = computed(() => ({
  color: ['#0160a8'],
  tooltip: { trigger: 'axis' },
  grid: { left: 52, right: 24, top: 28, bottom: 40 },
  xAxis: {
    type: 'category',
    data: optimizationIterations.value.map((item) => item.iteration),
    name: 'Iteration',
  },
  yAxis: { type: 'value', name: 'Loss' },
  series: [
    {
      type: 'line',
      smooth: true,
      data: optimizationIterations.value.map((item) => ({
        value: item.loss,
        symbolSize: item.best ? 12 : item.accepted ? 8 : 5,
        itemStyle: {
          color: item.best ? '#16a34a' : item.accepted ? '#0160a8' : '#94a3b8',
        },
      })),
    },
  ],
}))

const optimizationMetricOption = computed(() => ({
  color: ['#0160a8', '#f97316', '#c81e1e'],
  tooltip: { trigger: 'axis' },
  legend: { top: 4 },
  grid: { left: 52, right: 24, top: 48, bottom: 40 },
  xAxis: {
    type: 'category',
    data: optimizationIterations.value.map((item) => item.iteration),
  },
  yAxis: { type: 'value' },
  series: [
    {
      name: '最大队列',
      type: 'line',
      data: optimizationIterations.value.map((item) => item.metrics?.maxQueueLength ?? 0),
    },
    {
      name: '未服务人数',
      type: 'line',
      data: optimizationIterations.value.map((item) => item.metrics?.unservedUserCount ?? 0),
    },
    {
      name: '高压窗口',
      type: 'line',
      data: optimizationIterations.value.map((item) => crowdedMetricCount(item.metrics)),
    },
  ],
}))

onMounted(async () => {
  try {
    await store.initialize()
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') {
      ElMessage.error(error.message || '展示页初始化失败')
    }
  }
})

onBeforeUnmount(() => store.stopOptimizationPolling(true))

async function handleMealPeriodChange() {
  try {
    await store.refreshFlowCurve()
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') ElMessage.error(error.message)
  }
}

async function handleRun() {
  try {
    await store.runBaseline()
    ElMessage.success(`基础仿真完成 #${currentRun.value?.runId}`)
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') ElMessage.error(error.message)
  }
}

async function handleDiversion() {
  try {
    await store.generateDiversion()
    ElMessage.success('分流建议已刷新')
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') ElMessage.error(error.message)
  }
}

async function handleComparison() {
  try {
    const result = await store.runComparison()
    if (result?.status === 'NO_SUGGESTION') {
      ElMessage.warning(result.diversionResult?.reason || '当前没有可执行建议')
    } else {
      ElMessage.success('规则分流对比已完成')
    }
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') ElMessage.error(error.message)
  }
}

async function handleOptimization() {
  try {
    await store.startOptimization()
    ElMessage.success('模拟退火任务已启动')
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') ElMessage.error(error.message)
  }
}

function comparisonBarOption(labels, baseValues, compareValues, unit) {
  return {
    color: ['#9aa5b5', '#0160a8'],
    tooltip: { trigger: 'axis' },
    legend: { top: 4, data: ['Baseline', 'Compare'] },
    grid: { left: 48, right: 20, top: 46, bottom: 34 },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value', name: unit },
    series: [
      { name: 'Baseline', type: 'bar', data: baseValues.map(numberOrZero), barMaxWidth: 28 },
      { name: 'Compare', type: 'bar', data: compareValues.map(numberOrZero), barMaxWidth: 28 },
    ],
  }
}

function metricRow(label, baseline, strategy, optimized, digits, unit) {
  return {
    label,
    baseline: formatNumber(baseline, digits),
    strategy: formatNumber(strategy, digits),
    optimized: formatNumber(optimized, digits),
    vsBaseline: formatImprovementText(baseline, optimized, digits, unit),
    vsStrategy: formatImprovementText(strategy, optimized, digits, unit),
  }
}

function metricDelta(before, after) {
  if (!Number.isFinite(Number(before)) || !Number.isFinite(Number(after))) return null
  return Number(after) - Number(before)
}

function crowdedMetricCount(metrics) {
  if (!metrics) return null
  return Number(metrics.busyWindowCount || 0) + Number(metrics.extremeWindowCount || 0)
}

function lowerBetterTone(delta) {
  if (!Number.isFinite(Number(delta))) return ''
  if (Number(delta) < 0) return 'success'
  if (Number(delta) > 0) return 'danger'
  return ''
}

function findWindow(point, windowId) {
  return flattenWindows(point).find(
    ({ window }) => Number(window.windowId) === Number(windowId),
  )?.window
}

function windowLabel(point, windowId) {
  const item = flattenWindows(point).find(
    ({ window }) => Number(window.windowId) === Number(windowId),
  )
  return item ? `${item.restaurantName} / ${item.window.name}` : `窗口 ${windowId}`
}

function pressureScore(window) {
  if (!window) return 0
  return Number(
    (
      Number(window.waitMinutes || 0) / 7 +
      Number(window.queueLength || 0) / 8 +
      crowdScore(window.crowdLevel)
    ).toFixed(2),
  )
}

function crowdScore(level) {
  return { IDLE: 0, NORMAL: 0.4, BUSY: 1, EXTREME: 1.6 }[level] || 0
}

function numberOrZero(value) {
  return Number.isFinite(Number(value)) ? Number(value) : 0
}

function formatNumber(value, digits = 1) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '--'
  return Number(value).toFixed(digits).replace(/\.0+$/, '')
}

function formatSignedNumber(value, digits = 1) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '--'
  const numericValue = Number(value)
  if (numericValue === 0) return '0'
  const formatted = formatNumber(Math.abs(numericValue), digits)
  return `${numericValue > 0 ? '+' : '-'}${formatted}`
}

function formatImprovementText(before, after, digits, unit) {
  if (!Number.isFinite(Number(before)) || !Number.isFinite(Number(after))) return '--'
  const delta = round(Number(after) - Number(before), digits)
  if (delta === 0) return '持平'
  const absolute = formatNumber(Math.abs(delta), digits)
  return delta < 0 ? `下降 ${absolute} ${unit}` : `上升 ${absolute} ${unit}`
}

function round(value, digits = 0) {
  const factor = 10 ** digits
  return Math.round(Number(value || 0) * factor) / factor
}
</script>

<style scoped>
.dashboard-page {
  min-height: 100%;
}

.dashboard-topbar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  min-height: 82px;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 14px 24px;
  border-bottom: 1px solid var(--color-outline);
  background: rgb(250 248 255 / 96%);
  backdrop-filter: blur(14px);
}

.dashboard-topbar h1 {
  margin: 0;
  color: var(--color-primary);
  font-size: 24px;
}

.dashboard-topbar p {
  margin: 5px 0 0;
  color: var(--color-subtle);
  font-size: 12px;
}

.dashboard-topbar .eyebrow {
  margin: 0 0 4px;
  color: var(--color-secondary);
}

.dashboard-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.dashboard-canvas {
  display: grid;
  gap: 34px;
  width: min(1520px, calc(100% - 48px));
  margin: 0 auto;
  padding: 28px 0 44px;
}

.dashboard-section {
  display: grid;
  gap: 18px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.section-heading > span {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 4px;
  color: #fff;
  background: var(--color-secondary);
  font-family: var(--font-data);
  font-size: 12px;
}

.section-heading h2 {
  margin: 0;
  color: var(--color-primary);
  font-size: 21px;
}

.section-heading p {
  margin: 5px 0 0;
  color: var(--color-subtle);
  font-size: 13px;
}

.input-grid,
.chart-grid,
.optimization-summary-grid,
.kpi-grid,
.comparison-kpi-grid,
.outcome-kpi-grid,
.route-metrics,
.progress-kpis,
.source-stat-grid {
  display: grid;
  gap: 12px;
}

.input-grid {
  grid-template-columns: minmax(480px, 1.25fr) minmax(360px, 0.75fr);
  gap: 16px;
}

.chart-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.chart-grid-wide {
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
}

.kpi-grid {
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.comparison-kpi-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.outcome-kpi-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  padding: 18px;
  border-bottom: 1px solid var(--color-outline);
}

.optimization-summary-grid {
  grid-template-columns: repeat(3, minmax(220px, 0.75fr)) minmax(300px, 1.25fr);
  gap: 16px;
}

.dashboard-panel,
.annealing-note {
  min-width: 0;
  border: 1px solid var(--color-outline);
  border-radius: var(--radius-panel);
  background: #fff;
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.panel-heading {
  display: flex;
  min-height: 66px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-outline);
}

.panel-heading h2,
.parameter-panel h3,
.progress-panel h3 {
  margin: 0;
  color: var(--color-primary);
  font-size: 16px;
}

.panel-heading p {
  margin: 5px 0 0;
  color: var(--color-subtle);
  font-size: 12px;
  line-height: 1.65;
}

.status-chip,
.source-badge {
  padding: 5px 9px;
  border-radius: 999px;
  color: var(--color-secondary);
  background: var(--color-secondary-soft);
  font-family: var(--font-data);
  font-size: 10px;
  font-weight: 800;
}

.source-badge {
  color: #166534;
  background: #dcfce7;
}

.compact-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding: 18px;
}

.compact-form label,
.optimization-controls label {
  display: grid;
  gap: 7px;
  color: var(--color-on-surface-variant);
  font-size: 12px;
  font-weight: 700;
}

.form-wide {
  grid-column: 1 / -1;
}

.source-stat-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 18px;
}

.metric-block {
  position: relative;
  display: grid;
  align-content: center;
  min-height: 82px;
  padding: 12px 14px;
  border: 1px solid #e1e6ef;
  border-radius: 6px;
  background: #f8f9fc;
}

.metric-block span {
  color: var(--color-subtle);
  font-size: 11px;
}

.metric-block strong {
  margin-top: 6px;
  color: var(--color-primary);
  font-family: var(--font-data);
  font-size: 24px;
  line-height: 1;
}

.metric-block small {
  position: absolute;
  right: 12px;
  bottom: 12px;
  color: var(--color-subtle);
  font-size: 10px;
}

.metric-block.tone-danger strong {
  color: var(--color-danger);
}

.metric-block.tone-warning strong {
  color: var(--color-warning-strong);
}

.metric-block.tone-success strong {
  color: var(--color-success);
}

.source-details {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0 18px 18px;
}

.source-details div {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid #edf0f5;
}

.source-details dt {
  color: var(--color-subtle);
  font-size: 11px;
}

.source-details dd {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-data);
  font-size: 12px;
  text-align: right;
}

.route-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding: 18px;
}

.route-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid #dfe5ef;
  border-left: 4px solid var(--color-secondary);
  border-radius: 6px;
  background: #fbfcff;
}

.route-title {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 12px;
}

.route-title div {
  display: grid;
  gap: 5px;
}

.route-title small {
  color: var(--color-subtle);
}

.route-title strong {
  color: var(--color-primary);
  font-size: 14px;
}

.route-title > span {
  color: var(--color-secondary);
  font-size: 22px;
}

.route-metrics {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.route-card .metric-block {
  min-height: 68px;
  padding: 10px;
}

.route-card .metric-block strong {
  font-size: 18px;
}

.pressure-compare {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  color: #004378;
  background: #e7f2ff;
  font-family: var(--font-data);
  font-size: 11px;
}

.route-card > p {
  margin: 0;
  color: var(--color-on-surface-variant);
  font-size: 12px;
  line-height: 1.7;
}

.empty-state {
  padding: 30px 18px;
  color: var(--color-subtle);
  text-align: center;
}

.empty-panel {
  display: grid;
  min-height: 180px;
  place-items: center;
}

.annealing-note {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 18px;
  border-left: 4px solid var(--color-secondary);
}

.annealing-note strong {
  flex: 0 0 auto;
  color: var(--color-primary);
}

.annealing-note p {
  flex: 1;
  margin: 0;
  color: var(--color-on-surface-variant);
  font-size: 12px;
  line-height: 1.75;
}

.optimization-controls {
  display: grid;
  grid-template-columns: repeat(2, 150px);
  gap: 10px;
}

.parameter-panel,
.progress-panel {
  padding: 18px;
}

.parameter-panel.best {
  border-top: 3px solid var(--color-success);
}

.parameter-panel dl {
  display: grid;
  gap: 8px;
  margin: 14px 0 0;
}

.parameter-panel dl div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 7px;
  border-bottom: 1px solid #edf0f5;
}

.parameter-panel dt {
  color: var(--color-subtle);
  font-size: 11px;
}

.parameter-panel dd {
  margin: 0;
  color: var(--color-primary);
  font-family: var(--font-data);
  font-size: 12px;
  font-weight: 700;
}

.parameter-empty {
  color: var(--color-subtle);
  font-size: 12px;
}

.progress-panel {
  display: grid;
  gap: 16px;
}

.progress-kpis {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.progress-panel .metric-block {
  min-height: 70px;
}

.progress-panel .metric-block strong {
  font-size: 18px;
}

.outcome-panel :deep(.el-table),
.dashboard-panel :deep(.el-table) {
  width: 100%;
}

@media (max-width: 1400px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .outcome-kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1280px) {
  .optimization-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1050px) {
  .dashboard-topbar,
  .annealing-note {
    align-items: flex-start;
    flex-direction: column;
  }

  .input-grid,
  .chart-grid,
  .chart-grid-wide,
  .route-grid {
    grid-template-columns: 1fr;
  }

  .comparison-kpi-grid,
  .progress-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .dashboard-canvas {
    width: min(100%, calc(100% - 24px));
  }

  .compact-form,
  .kpi-grid,
  .comparison-kpi-grid,
  .outcome-kpi-grid,
  .source-stat-grid,
  .route-metrics,
  .progress-kpis,
  .optimization-summary-grid {
    grid-template-columns: 1fr;
  }

  .form-wide {
    grid-column: auto;
  }

  .optimization-controls {
    grid-template-columns: 1fr;
  }
}
</style>
