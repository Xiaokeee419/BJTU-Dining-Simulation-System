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
                <el-select v-model="form.mealPeriod">
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
                <dt>到达模型</dt>
                <dd>arrival_rules.csv + 当前场景参数</dd>
              </div>
              <div>
                <dt>支持人群</dt>
                <dd>{{ dataOverview?.userTypes?.join(' / ') || '--' }}</dd>
              </div>
            </dl>
          </section>
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
            title="当前场景人流到达曲线"
            subtitle="根据仿真人数、用餐时段、拥挤程度、天气和活动系数生成预计到达分布。"
            :option="flowCurveOption"
            :loading="loading.flow || loading.initialize"
            :empty="!arrivalCurvePoints.length"
          >
            <template #extra>
              <span v-if="flowCurveError" class="curve-error-chip">
                人流曲线预览加载失败
              </span>
              <span v-else class="curve-mode-chip">{{ arrivalCurveModeLabel }}</span>
            </template>
          </DashboardChart>
          <DashboardChart
            title="当前窗口压力排行"
            :subtitle="windowLoadSubtitle"
            :option="windowLoadOption"
            :loading="loading.simulation"
            :empty="!windowLoadRows.length"
          />
        </div>
      </section>

      <section class="dashboard-section">
        <SectionHeading
          index="02"
          title="规则分流缓解效果"
          description="先用规则分流验证：从最拥挤窗口向可承接窗口导流，看看拥堵是否真的缓解。"
        />

        <section class="dashboard-panel bottleneck-panel">
          <header class="panel-heading">
            <div>
              <h2>规则分流前后瓶颈缓解对比</h2>
              <p>
                分流策略主要用于缓解高峰期局部窗口拥堵，因此重点比较来源窗口排队、最大队列和过载程度变化。
              </p>
            </div>
            <span class="status-chip">
              {{ compareRun ? `Compare #${compareRun.runId}` : '等待验证分流' }}
            </span>
          </header>

          <div class="bottleneck-summary-grid">
            <article
              v-for="card in bottleneckSummaryCards"
              :key="card.label"
              class="bottleneck-summary-card"
              :class="card.tone ? `tone-${card.tone}` : ''"
            >
              <span>{{ card.label }}</span>
              <strong>{{ card.value }}</strong>
              <small>{{ card.detail }}</small>
            </article>
          </div>

          <DashboardChart
            class="bottleneck-chart"
            title="五项核心瓶颈指标"
            subtitle="同一口径下对比 Baseline 与 Compare；数值越低表示拥堵缓解越明显。"
            :option="bottleneckComparisonOption"
            :loading="loading.comparison"
            :empty="!compareRun"
            :height="320"
          />

          <div class="bottleneck-change-grid">
            <article
              v-for="item in bottleneckMetricRows"
              :key="item.key"
              :class="['bottleneck-change-item', item.tone ? `tone-${item.tone}` : '']"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.baselineLabel }} → {{ item.compareLabel }}</strong>
              <small>{{ item.changeLabel }}</small>
            </article>
          </div>

          <p class="bottleneck-diagnostic">
            如果“来源窗口总排队人数”在分流后几乎没有变化，说明 compareRun
            可能没有真正执行分流建议，或者分流强度过低，需要进一步检查后端
            runSimulationWithDiversion / diversion-comparison 逻辑。
          </p>
        </section>

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
                :max="200"
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
          <section class="annealing-further-panel">
            <div class="annealing-further-heading">
              <h3>退火相对规则分流的进一步优化</h3>
              <p>基于真实规则分流 Run 与退火 Best Run，展示退火搜索带来的增量改善。</p>
            </div>
            <div class="annealing-further-grid">
              <article
                v-for="card in annealingFurtherImprovementCards"
                :key="card.label"
                :class="['annealing-further-card', card.tone ? `tone-${card.tone}` : '']"
              >
                <span>{{ card.label }}</span>
                <strong>{{ card.transition }}</strong>
                <small>{{ card.detail }}</small>
              </article>
            </div>
          </section>
          <p class="stage-comparison-note">
            规则分流主要用于快速缓解来源高压窗口；模拟退火在规则分流基础上进一步搜索参数，使瓶颈缓解、过载程度和综合 Loss 继续下降。
          </p>
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
            subtitle="候选 Loss 展示真实搜索波动；历史 Best Loss 展示当前最优值的阶梯式下降。"
            :option="lossCurveOption"
            :loading="loading.optimization && !optimizationIterations.length"
            :empty="!optimizationIterations.length"
          />
          <DashboardChart
            title="迭代中的关键拥挤指标"
            subtitle="与规则分流采用同一组五项瓶颈指标；峰值总排队使用右侧坐标轴。"
            :option="optimizationMetricOption"
            :loading="loading.optimization && !optimizationIterations.length"
            :empty="!optimizationBottleneckSeriesReady"
            empty-text="当前后端未返回迭代瓶颈指标，请重启后端后重新运行模拟退火"
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
            <el-table-column
              prop="bottleneckMetrics.sourceWindowQueueTotal"
              label="来源队列"
              width="96"
            />
            <el-table-column
              prop="bottleneckMetrics.sourceWindowAverageWait"
              label="来源等待"
              width="96"
            />
            <el-table-column
              prop="bottleneckMetrics.maxSingleWindowQueue"
              label="最大单窗"
              width="96"
            />
            <el-table-column
              prop="bottleneckMetrics.peakTotalQueue"
              label="峰值总排队"
              width="108"
            />
            <el-table-column
              prop="bottleneckMetrics.totalOverload"
              label="总过载"
              width="88"
            />
            <el-table-column prop="compareRunId" label="Compare Run" min-width="120" />
          </el-table>
        </section>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, defineComponent, h, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
  flowCurveError,
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
  optimizationBestRun,
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
  { key: 'pressureWaitWeight', label: '压力等待权重', digits: 3 },
  { key: 'pressureQueueWeight', label: '压力队列权重', digits: 3 },
  { key: 'crossRestaurantPenalty', label: '跨餐厅惩罚', digits: 2 },
]

const overviewMinute = ref(null)

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

const overloadThreshold = 10

const diversionSuggestions = computed(() =>
  Array.isArray(diversionResult.value?.suggestions)
    ? diversionResult.value.suggestions
    : [],
)

const sourceWindowIds = computed(() =>
  [
    ...new Set(
      diversionSuggestions.value
        .map(resolveSuggestionSourceWindowId)
        .filter((windowId) => windowId !== null),
    ),
  ],
)

const suggestedDiversionCount = computed(() =>
  sumSuggestionField(diversionSuggestions.value, 'suggestedUserCount'),
)

const estimatedAcceptedCount = computed(() =>
  sumSuggestionField(diversionSuggestions.value, 'estimatedAcceptedCount'),
)

const baselineBottleneckMetrics = computed(() =>
  buildBottleneckMetrics(
    currentRun.value,
    baseComparisonPoint.value,
    sourceWindowIds.value,
  ),
)

const strategyBottleneckMetrics = computed(() =>
  buildBottleneckMetrics(
    compareRun.value,
    compareComparisonPoint.value,
    sourceWindowIds.value,
  ),
)

const optimizationComparisonMinute = computed(
  () => optimizationBest.value?.minute ?? comparisonMinute.value,
)

const optimizedComparisonPoint = computed(() =>
  resolveTimePoint(optimizationBestRun.value, optimizationComparisonMinute.value),
)

const optimizedBottleneckMetrics = computed(() => {
  if (optimizationBestRun.value) {
    const runMetrics = buildBottleneckMetrics(
      optimizationBestRun.value,
      optimizedComparisonPoint.value,
      sourceWindowIds.value,
    )
    const backendMetrics = normalizeOptimizationBottleneckMetrics(
      optimizationBest.value?.bottleneckMetrics,
      optimizationBest.value?.metrics,
    )
    return {
      ...runMetrics,
      targetOverload: backendMetrics.targetOverload,
      loadImbalance: backendMetrics.loadImbalance ?? runMetrics.loadImbalance,
    }
  }
  return normalizeOptimizationBottleneckMetrics(
    optimizationBest.value?.bottleneckMetrics,
    optimizationBest.value?.metrics,
  )
})

const bottleneckMetricRows = computed(() => {
  const baseline = baselineBottleneckMetrics.value
  const compare = strategyBottleneckMetrics.value

  return [
    bottleneckMetricRow(
      'sourceQueue',
      '来源窗口总排队',
      baseline.sourceQueue,
      compare.sourceQueue,
      0,
      '人',
    ),
    bottleneckMetricRow(
      'sourceWait',
      '来源窗口平均等待',
      baseline.sourceWait,
      compare.sourceWait,
      1,
      '分钟',
    ),
    bottleneckMetricRow(
      'maxWindowQueue',
      '最大单窗排队',
      baseline.maxWindowQueue,
      compare.maxWindowQueue,
      0,
      '人',
    ),
    bottleneckMetricRow(
      'peakTotalQueue',
      '峰值总排队',
      baseline.peakTotalQueue,
      compare.peakTotalQueue,
      0,
      '人',
    ),
    bottleneckMetricRow(
      'totalOverload',
      '总过载程度',
      baseline.totalOverload,
      compare.totalOverload,
      0,
      '',
    ),
  ]
})

const sourceQueueMetric = computed(() =>
  bottleneckMetricRows.value.find((item) => item.key === 'sourceQueue'),
)

const overloadMetric = computed(() =>
  bottleneckMetricRows.value.find((item) => item.key === 'totalOverload'),
)

const bottleneckSummaryCards = computed(() => [
  {
    label: '建议分流人数',
    value: formatMetricValue(suggestedDiversionCount.value, 0, '人'),
    detail: diversionSuggestions.value.length
      ? `${diversionSuggestions.value.length} 条分流建议`
      : '等待生成分流建议',
  },
  {
    label: '预计接受人数',
    value: formatMetricValue(estimatedAcceptedCount.value, 0, '人'),
    detail: diversionSuggestions.value.length ? '按建议接受率估算' : '等待生成分流建议',
  },
  {
    label: '来源窗口排队变化',
    value: metricTransitionLabel(sourceQueueMetric.value),
    detail: sourceQueueMetric.value?.changeLabel || '--',
    tone: sourceQueueMetric.value?.tone,
  },
  {
    label: '总过载程度变化',
    value: metricTransitionLabel(overloadMetric.value),
    detail: overloadMetric.value?.percentLabel || overloadMetric.value?.changeLabel || '--',
    tone: overloadMetric.value?.tone,
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

const initialOptimizationLoss = computed(() => {
  const firstIteration = optimizationIterations.value.find(
    (item) => Number(item.iteration) === 1,
  )
  return isFiniteMetric(firstIteration?.loss) ? Number(firstIteration.loss) : null
})

const optimizationBottleneckSeriesReady = computed(() =>
  optimizationIterations.value.some((item) =>
    [
      item?.bottleneckMetrics?.sourceWindowQueueTotal,
      item?.bottleneckMetrics?.sourceWindowAverageWait,
      item?.bottleneckMetrics?.maxSingleWindowQueue,
      item?.bottleneckMetrics?.peakTotalQueue,
      item?.bottleneckMetrics?.totalOverload,
      item?.bottleneckMetrics?.extremeOverloadSeverity,
    ].some(isFiniteMetric),
  ),
)

const optimizationOutcomeCards = computed(() => {
  const baseline = baselineBottleneckMetrics.value
  const strategy = strategyBottleneckMetrics.value
  const optimized = optimizedBottleneckMetrics.value
  const bestLoss = optimizationBest.value?.loss ?? optimizationJob.value?.bestLoss
  const lossReductionRate = improvementPercent(initialOptimizationLoss.value, bestLoss)

  return [
    {
      label: 'Best Loss',
      value: formatNumber(bestLoss, 2),
      tone: 'success',
    },
    {
      label: 'Loss 下降率',
      value: formatPercentChange(lossReductionRate),
      tone: improvementTone(lossReductionRate),
    },
    {
      label: '来源窗口排队下降',
      value: formatReductionSummary(baseline.sourceQueue, optimized.sourceQueue, 0, '人'),
      tone: lowerBetterTone(metricDelta(baseline.sourceQueue, optimized.sourceQueue)),
    },
    {
      label: '最大单窗排队下降',
      value: formatReductionSummary(
        baseline.maxWindowQueue,
        optimized.maxWindowQueue,
        0,
        '人',
      ),
      tone: lowerBetterTone(
        metricDelta(baseline.maxWindowQueue, optimized.maxWindowQueue),
      ),
    },
    {
      label: '总过载程度下降',
      value: formatReductionSummary(
        baseline.totalOverload,
        optimized.totalOverload,
        0,
        '',
      ),
      tone: lowerBetterTone(metricDelta(baseline.totalOverload, optimized.totalOverload)),
    },
    {
      label: '相对规则分流改善',
      value: formatPercentChange(
        improvementPercent(strategy.totalOverload, optimized.totalOverload),
      ),
      tone: improvementTone(
        improvementPercent(strategy.totalOverload, optimized.totalOverload),
      ),
    },
  ]
})

const annealingFurtherImprovementCards = computed(() => {
  const strategy = strategyBottleneckMetrics.value
  const optimized = optimizedBottleneckMetrics.value
  const strategyLoss = initialOptimizationLoss.value
  const optimizedLoss = optimizationBest.value?.loss ?? optimizationJob.value?.bestLoss

  return [
    furtherImprovementCard(
      '来源窗口总排队',
      strategy.sourceQueue,
      optimized.sourceQueue,
      0,
      '人',
    ),
    furtherImprovementCard(
      '来源窗口平均等待',
      strategy.sourceWait,
      optimized.sourceWait,
      1,
      '分钟',
    ),
    furtherImprovementCard(
      '总过载程度',
      strategy.totalOverload,
      optimized.totalOverload,
      0,
      '',
    ),
    furtherImprovementCard('Loss', strategyLoss, optimizedLoss, 2, ''),
    furtherImprovementCard(
      '极端过载程度',
      strategy.extremeOverload,
      optimized.extremeOverload,
      0,
      '',
    ),
  ]
})

const optimizationMetricRows = computed(() => {
  const baseline = baselineBottleneckMetrics.value
  const strategy = strategyBottleneckMetrics.value
  const optimized = optimizedBottleneckMetrics.value

  return [
    metricRow(
      '来源窗口总排队（人）',
      baseline.sourceQueue,
      strategy.sourceQueue,
      optimized.sourceQueue,
      0,
      '人',
    ),
    metricRow(
      '来源窗口平均等待（分钟）',
      baseline.sourceWait,
      strategy.sourceWait,
      optimized.sourceWait,
      1,
      '分钟',
    ),
    metricRow(
      '最大单窗口排队（人）',
      baseline.maxWindowQueue,
      strategy.maxWindowQueue,
      optimized.maxWindowQueue,
      0,
      '人',
    ),
    metricRow(
      '峰值总排队人数（人）',
      baseline.peakTotalQueue,
      strategy.peakTotalQueue,
      optimized.peakTotalQueue,
      0,
      '人',
    ),
    metricRow(
      '总过载程度',
      baseline.totalOverload,
      strategy.totalOverload,
      optimized.totalOverload,
      0,
      '',
    ),
    metricRow(
      '极端过载程度',
      baseline.extremeOverload,
      strategy.extremeOverload,
      optimized.extremeOverload,
      0,
      '',
    ),
    metricRow(
      '未服务人数（人）',
      baseline.unservedUsers,
      strategy.unservedUsers,
      optimized.unservedUsers,
      0,
      '人',
    ),
    metricRow(
      'Loss',
      null,
      initialOptimizationLoss.value,
      optimizationBest.value?.loss ?? optimizationJob.value?.bestLoss,
      2,
      '',
    ),
  ]
})

const optimizationNarrative = computed(() => {
  const baseline = baselineBottleneckMetrics.value
  const strategy = strategyBottleneckMetrics.value
  const optimized = optimizedBottleneckMetrics.value
  const best = optimizationBest.value

  if (!optimizationJob.value && !best) {
    return '模拟退火会在每一轮真实 compare 仿真后，根据拥挤缓解效果调整参数并记录 best。'
  }
  if (!best || !isFiniteMetric(optimized.totalOverload)) {
    return optimizationJob.value?.message || '优化进行中，等待最优方案结果产生。'
  }

  const sourceQueueText = formatImprovementText(
    baseline.sourceQueue,
    optimized.sourceQueue,
    0,
    '人',
  )
  const maxQueueText = formatImprovementText(
    baseline.maxWindowQueue,
    optimized.maxWindowQueue,
    0,
    '人',
  )
  const overloadText = formatImprovementText(
    baseline.totalOverload,
    optimized.totalOverload,
    0,
    '',
  )
  const extraOverloadText = isFiniteMetric(strategy.totalOverload)
    ? `相较规则分流，总过载程度${formatImprovementText(strategy.totalOverload, optimized.totalOverload, 0, '')}。`
    : ''

  return `当前 best run #${best.compareRunId} 相比 baseline 来源窗口排队${sourceQueueText}，最大单窗排队${maxQueueText}，总过载程度${overloadText}。${extraOverloadText}`
})

const actualArrivalCurvePoints = computed(() => {
  const runCurve =
    currentRun.value?.arrivalCurve ??
    currentRun.value?.arrivalTimeSeries ??
    currentRun.value?.arrivalPoints
  const runPoints = Array.isArray(runCurve) ? runCurve : runCurve?.points
  return runMatchesCurrentScenario(currentRun.value) && Array.isArray(runPoints)
    ? runPoints
    : []
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
    ) || Number(form.virtualUserCount || 0),
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
  grid: { left: 52, right: 54, top: 48, bottom: 40 },
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

const bottleneckComparisonOption = computed(() => ({
  color: ['#94a3b8', '#0160a8'],
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    formatter: (params) => {
      const index = Number(params?.[0]?.dataIndex ?? -1)
      const row = bottleneckMetricRows.value[index]
      if (!row) return ''
      return [
        row.label,
        `Baseline：${row.baselineLabel}`,
        `Compare：${row.compareLabel}`,
        `变化：${row.changeLabel}`,
      ].join('<br/>')
    },
  },
  legend: { top: 6, data: ['Baseline', 'Compare'] },
  grid: { left: 58, right: 28, top: 48, bottom: 70 },
  xAxis: {
    type: 'category',
    data: bottleneckMetricRows.value.map((item) => item.label),
    axisLabel: {
      interval: 0,
      rotate: 18,
      color: '#475569',
    },
  },
  yAxis: {
    type: 'value',
    name: '指标值',
    min: 0,
    splitLine: { lineStyle: { color: '#e8edf4' } },
  },
  series: [
    {
      name: 'Baseline',
      type: 'bar',
      barMaxWidth: 34,
      data: bottleneckMetricRows.value.map((item) => item.baseline),
      itemStyle: {
        color: '#94a3b8',
        borderRadius: [6, 6, 0, 0],
      },
    },
    {
      name: 'Compare',
      type: 'bar',
      barMaxWidth: 34,
      data: bottleneckMetricRows.value.map((item) => ({
        value: item.compare,
        itemStyle: {
          color:
            item.tone === 'success'
              ? '#16a34a'
              : item.tone === 'danger'
                ? '#dc2626'
                : '#0160a8',
          borderRadius: [6, 6, 0, 0],
        },
      })),
    },
  ],
}))

const lossCurvePoints = computed(() => {
  let runningBest = null
  return optimizationIterations.value.map((item) => {
    const candidateLoss = finiteMetricOrNull(item.loss)
    if (candidateLoss !== null) {
      runningBest = runningBest === null
        ? candidateLoss
        : Math.min(runningBest, candidateLoss)
    }
    return {
      candidateLoss,
      bestLoss: runningBest,
    }
  })
})

const lossCurveOption = computed(() => ({
  color: ['#0160a8', '#16a34a'],
  tooltip: {
    trigger: 'axis',
    formatter: (params) => {
      const index = Number(params?.[0]?.dataIndex ?? -1)
      const iteration = optimizationIterations.value[index]
      if (!iteration) return ''
      return [
        `迭代 ${iteration.iteration}`,
        `候选 Loss：${formatNumber(iteration.loss, 2)}`,
        `历史 Best：${formatNumber(lossCurvePoints.value[index]?.bestLoss, 2)}`,
        `温度：${formatNumber(iteration.temperature, 2)}`,
        `状态：${iteration.best ? '新 Best' : iteration.acceptedWorseSolution ? '接受较差解' : iteration.accepted ? '接受' : '拒绝'}`,
      ].join('<br/>')
    },
  },
  legend: { top: 4, data: ['候选 Loss', '历史 Best Loss'] },
  grid: { left: 52, right: 24, top: 28, bottom: 40 },
  xAxis: {
    type: 'category',
    data: optimizationIterations.value.map((item) => item.iteration),
    name: 'Iteration',
  },
  yAxis: {
    type: 'value',
    name: 'Loss',
    scale: true,
  },
  series: [
    {
      name: '候选 Loss',
      type: 'line',
      smooth: false,
      data: optimizationIterations.value.map((item, index) => ({
        value: lossCurvePoints.value[index]?.candidateLoss,
        symbolSize: item.best ? 12 : item.accepted ? 8 : 5,
        itemStyle: {
          color: item.best
            ? '#16a34a'
            : item.acceptedWorseSolution
              ? '#f97316'
              : item.accepted
                ? '#0160a8'
                : '#94a3b8',
        },
      })),
    },
    {
      name: '历史 Best Loss',
      type: 'line',
      step: 'end',
      symbol: 'none',
      lineStyle: {
        width: 3,
        color: '#16a34a',
      },
      data: lossCurvePoints.value.map((item) => item.bestLoss),
    },
  ],
}))

const optimizationMetricOption = computed(() => ({
  color: ['#0160a8', '#7c3aed', '#f97316', '#64748b', '#16a34a'],
  tooltip: {
    trigger: 'axis',
    formatter: (params) => {
      const iteration = params?.[0]?.axisValue ?? '--'
      return [
        `迭代 ${iteration}`,
        ...(params || []).map((item) => `${item.marker}${item.seriesName}：${item.value ?? '--'}`),
      ].join('<br/>')
    },
  },
  legend: { top: 4 },
  grid: { left: 58, right: 62, top: 58, bottom: 40 },
  xAxis: {
    type: 'category',
    data: optimizationIterations.value.map((item) => item.iteration),
  },
  yAxis: [
    {
      type: 'value',
      name: '排队 / 等待 / 过载',
      min: 0,
    },
    {
      type: 'value',
      name: '峰值总排队',
      min: 0,
      splitLine: { show: false },
    },
  ],
  series: [
    {
      name: '来源窗口总排队',
      type: 'line',
      connectNulls: false,
      data: optimizationIterations.value.map(
        (item) => finiteMetricOrNull(item.bottleneckMetrics?.sourceWindowQueueTotal),
      ),
    },
    {
      name: '来源窗口平均等待',
      type: 'line',
      connectNulls: false,
      data: optimizationIterations.value.map(
        (item) => finiteMetricOrNull(item.bottleneckMetrics?.sourceWindowAverageWait),
      ),
    },
    {
      name: '最大单窗口排队',
      type: 'line',
      connectNulls: false,
      data: optimizationIterations.value.map(
        (item) => finiteMetricOrNull(item.bottleneckMetrics?.maxSingleWindowQueue),
      ),
    },
    {
      name: '峰值总排队人数',
      type: 'line',
      yAxisIndex: 1,
      connectNulls: false,
      data: optimizationIterations.value.map(
        (item) => finiteMetricOrNull(item.bottleneckMetrics?.peakTotalQueue),
      ),
    },
    {
      name: '总过载程度',
      type: 'line',
      connectNulls: false,
      data: optimizationIterations.value.map(
        (item) => finiteMetricOrNull(item.bottleneckMetrics?.totalOverload),
      ),
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

let flowCurveRefreshTimer = null

watch(
  () => [
    form.virtualUserCount,
    form.mealPeriod,
    form.dayType,
    form.crowdLevel,
    form.weatherFactor ?? 1,
    form.eventFactor ?? 1,
    form.durationMinutes,
    form.randomSeed,
  ],
  () => {
    if (flowCurveRefreshTimer != null) {
      window.clearTimeout(flowCurveRefreshTimer)
    }
    flowCurveRefreshTimer = window.setTimeout(() => {
      flowCurveRefreshTimer = null
      store.refreshFlowCurve()
    }, 250)
  },
)

onBeforeUnmount(() => {
  if (flowCurveRefreshTimer != null) {
    window.clearTimeout(flowCurveRefreshTimer)
  }
  store.stopOptimizationPolling(true)
})

function runMatchesCurrentScenario(run) {
  const scenario = run?.scenario
  if (!scenario) return false
  return (
    String(scenario.mealPeriod) === String(form.mealPeriod) &&
    String(scenario.dayType) === String(form.dayType) &&
    String(scenario.crowdLevel) === String(form.crowdLevel) &&
    Number(scenario.weatherFactor ?? 1) === Number(form.weatherFactor ?? 1) &&
    Number(scenario.eventFactor ?? 1) === Number(form.eventFactor ?? 1) &&
    Number(scenario.virtualUserCount) === Number(form.virtualUserCount) &&
    Number(scenario.durationMinutes) === Number(form.durationMinutes) &&
    Number(scenario.randomSeed) === Number(form.randomSeed)
  )
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

function resolveSuggestionSourceWindowId(suggestion) {
  const value =
    suggestion?.fromWindowId ??
    suggestion?.sourceWindowId ??
    suggestion?.sourceWindow?.windowId ??
    suggestion?.sourceWindow?.id
  return value === null || value === undefined || value === '' ? null : Number(value)
}

function sumSuggestionField(suggestions, field) {
  if (!suggestions.length) return null
  const values = suggestions
    .map((suggestion) => suggestion?.[field])
    .filter((value) => value !== null && value !== undefined && Number.isFinite(Number(value)))
  return values.length ? values.reduce((sum, value) => sum + Number(value), 0) : null
}

function buildBottleneckMetrics(run, comparisonPoint, windowIds) {
  const timePoints = Array.isArray(run?.timePoints) ? run.timePoints : []
  const comparisonWindows = windowIds
    .map((windowId) => findWindow(comparisonPoint, windowId))
    .filter(Boolean)
  const sourceWaitValues = comparisonWindows
    .map(resolveWindowWait)
    .filter((value) => value !== null)

  let maxWindowQueue = null
  let peakTotalQueue = null
  let totalOverload = null
  let extremeOverload = null
  let loadImbalance = null

  if (timePoints.length) {
    maxWindowQueue = 0
    peakTotalQueue = 0
    totalOverload = 0
    extremeOverload = 0
    loadImbalance = 0
    timePoints.forEach((point) => {
      const windows = flattenWindows(point).map((entry) => entry.window)
      const totalQueue = windows.reduce((sum, window) => sum + windowQueueLength(window), 0)
      peakTotalQueue = Math.max(peakTotalQueue, totalQueue)
      windows.forEach((window) => {
        const queueLength = windowQueueLength(window)
        maxWindowQueue = Math.max(maxWindowQueue, queueLength)
        totalOverload += Math.max(queueLength - overloadThreshold, 0)
        extremeOverload += Math.max(queueLength - 15, 0) ** 2
      })
      ;(point.restaurants || []).forEach((restaurant) => {
        const restaurantQueues = (restaurant.windows || []).map(windowQueueLength)
        if (!restaurantQueues.length) return
        const averageQueue =
          restaurantQueues.reduce((sum, queue) => sum + queue, 0) / restaurantQueues.length
        restaurantQueues.forEach((queue) => {
          loadImbalance += (queue - averageQueue) ** 2
        })
      })
    })
  }

  return {
    sourceQueue:
      windowIds.length && comparisonWindows.length
        ? comparisonWindows.reduce((sum, window) => sum + windowQueueLength(window), 0)
        : null,
    sourceWait: sourceWaitValues.length
      ? sourceWaitValues.reduce((sum, value) => sum + value, 0) / sourceWaitValues.length
      : null,
    maxWindowQueue,
    peakTotalQueue,
    totalOverload,
    extremeOverload,
    targetOverload: null,
    loadImbalance,
    unservedUsers: isFiniteMetric(run?.metrics?.unservedUserCount)
      ? Number(run.metrics.unservedUserCount)
      : null,
  }
}

function normalizeOptimizationBottleneckMetrics(metrics, fallbackMetrics) {
  if (!metrics) {
    return {
      sourceQueue: null,
      sourceWait: null,
      maxWindowQueue: isFiniteMetric(fallbackMetrics?.maxQueueLength)
        ? Number(fallbackMetrics.maxQueueLength)
        : null,
      peakTotalQueue: null,
      totalOverload: null,
      extremeOverload: null,
      targetOverload: null,
      loadImbalance: null,
      unservedUsers: isFiniteMetric(fallbackMetrics?.unservedUserCount)
        ? Number(fallbackMetrics.unservedUserCount)
        : null,
    }
  }
  return {
    sourceQueue: finiteMetricOrNull(metrics.sourceWindowQueueTotal),
    sourceWait: finiteMetricOrNull(metrics.sourceWindowAverageWait),
    maxWindowQueue: finiteMetricOrNull(metrics.maxSingleWindowQueue),
    peakTotalQueue: finiteMetricOrNull(metrics.peakTotalQueue),
    totalOverload: finiteMetricOrNull(metrics.totalOverload),
    extremeOverload: finiteMetricOrNull(metrics.extremeOverloadSeverity),
    targetOverload: finiteMetricOrNull(metrics.targetWindowOverload),
    loadImbalance: finiteMetricOrNull(metrics.loadImbalancePenalty),
    unservedUsers: finiteMetricOrNull(
      metrics.unservedUserCount ?? fallbackMetrics?.unservedUserCount,
    ),
  }
}

function bottleneckMetricRow(key, label, baseline, compare, digits, unit) {
  const hasValues = isFiniteMetric(baseline) && isFiniteMetric(compare)
  const delta = hasValues ? round(Number(compare) - Number(baseline), digits) : null
  const percent =
    hasValues && Number(baseline) !== 0
      ? round(((Number(baseline) - Number(compare)) / Number(baseline)) * 100, 1)
      : null

  return {
    key,
    label,
    baseline: isFiniteMetric(baseline) ? Number(baseline) : null,
    compare: isFiniteMetric(compare) ? Number(compare) : null,
    baselineLabel: formatMetricValue(baseline, digits, unit),
    compareLabel: formatMetricValue(compare, digits, unit),
    changeLabel: formatBottleneckChange(delta, digits, unit),
    percentLabel: formatBottleneckPercent(delta, percent),
    tone: lowerBetterTone(delta),
  }
}

function resolveWindowWait(window) {
  const value =
    window?.waitMinutes ??
    window?.avgWaitTime ??
    window?.averageWaitTime ??
    window?.avgWaitMinutes
  return isFiniteMetric(value) ? Number(value) : null
}

function windowQueueLength(window) {
  const value = window?.queueLength ?? window?.queueCount
  return isFiniteMetric(value) ? Number(value) : 0
}

function isFiniteMetric(value) {
  return value !== null && value !== undefined && Number.isFinite(Number(value))
}

function formatMetricValue(value, digits, unit) {
  if (!isFiniteMetric(value)) return '--'
  const suffix = unit ? ` ${unit}` : ''
  return `${formatNumber(value, digits)}${suffix}`
}

function formatBottleneckChange(delta, digits, unit) {
  if (!isFiniteMetric(delta)) return '--'
  if (Number(delta) === 0) return '持平'
  const suffix = unit ? ` ${unit}` : ''
  const amount = `${formatNumber(Math.abs(delta), digits)}${suffix}`
  return Number(delta) < 0 ? `减少 ${amount}` : `增加 ${amount}`
}

function formatBottleneckPercent(delta, percent) {
  if (!isFiniteMetric(delta)) return '--'
  if (Number(delta) === 0) return '持平'
  if (!isFiniteMetric(percent)) return Number(delta) < 0 ? '下降' : '上升'
  return Number(delta) < 0
    ? `下降 ${formatNumber(Math.abs(percent), 1)}%`
    : `上升 ${formatNumber(Math.abs(percent), 1)}%`
}

function metricTransitionLabel(metric) {
  return metric ? `${metric.baselineLabel} → ${metric.compareLabel}` : '--'
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

function furtherImprovementCard(label, before, after, digits, unit) {
  const delta = metricDelta(before, after)
  const percent = improvementPercent(before, after)
  const transition =
    isFiniteMetric(before) && isFiniteMetric(after)
      ? `${formatMetricValue(before, digits, unit)} → ${formatMetricValue(after, digits, unit)}`
      : '--'
  let detail = '--'
  if (isFiniteMetric(delta)) {
    if (Number(delta) === 0) {
      detail = '持平'
    } else {
      const amount = formatMetricValue(Math.abs(delta), digits, unit)
      const percentText = isFiniteMetric(percent)
        ? `（${formatNumber(Math.abs(percent), 1)}%）`
        : ''
      detail = Number(delta) < 0
        ? `再降 ${amount}${percentText}`
        : `增加 ${amount}${percentText}`
    }
  }
  return {
    label,
    transition,
    detail,
    tone: lowerBetterTone(delta),
  }
}

function metricDelta(before, after) {
  if (!Number.isFinite(Number(before)) || !Number.isFinite(Number(after))) return null
  return Number(after) - Number(before)
}

function improvementPercent(before, after) {
  if (!isFiniteMetric(before) || !isFiniteMetric(after)) return null
  if (Number(before) === 0) return Number(after) === 0 ? 0 : null
  return ((Number(before) - Number(after)) / Math.abs(Number(before))) * 100
}

function improvementTone(value) {
  if (!isFiniteMetric(value)) return ''
  if (Number(value) > 0) return 'success'
  if (Number(value) < 0) return 'danger'
  return ''
}

function formatPercentChange(value) {
  if (!isFiniteMetric(value)) return '--'
  if (Number(value) === 0) return '持平'
  const direction = Number(value) > 0 ? '下降' : '上升'
  return `${direction} ${formatNumber(Math.abs(value), 1)}%`
}

function formatReductionSummary(before, after, digits, unit) {
  const delta = metricDelta(before, after)
  if (!isFiniteMetric(delta)) return '--'
  if (Number(delta) === 0) return '持平'
  const suffix = unit ? ` ${unit}` : ''
  const amount = `${formatNumber(Math.abs(delta), digits)}${suffix}`
  return Number(delta) < 0 ? `减少 ${amount}` : `增加 ${amount}`
}

function finiteMetricOrNull(value) {
  return isFiniteMetric(value) ? Number(value) : null
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
  const suffix = unit ? ` ${unit}` : ''
  return delta < 0 ? `下降 ${absolute}${suffix}` : `上升 ${absolute}${suffix}`
}

function round(value, digits = 0) {
  const factor = 10 ** digits
  return Math.round(Number(value || 0) * factor) / factor
}
</script>

<style scoped>
.dashboard-page {
  min-height: 100%;
  overflow-anchor: none;
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

.bottleneck-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 18px 18px 0;
}

.bottleneck-summary-card {
  display: grid;
  gap: 7px;
  min-width: 0;
  padding: 15px 16px;
  border: 1px solid #e1e6ef;
  border-radius: 8px;
  background: #f8f9fc;
}

.bottleneck-summary-card > span {
  color: var(--color-subtle);
  font-size: 11px;
  font-weight: 700;
}

.bottleneck-summary-card strong {
  overflow: hidden;
  color: var(--color-primary);
  font-family: var(--font-data);
  font-size: 19px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bottleneck-summary-card small {
  color: var(--color-subtle);
  font-size: 11px;
}

.bottleneck-summary-card.tone-success strong,
.bottleneck-summary-card.tone-success small {
  color: var(--color-success);
}

.bottleneck-summary-card.tone-danger strong,
.bottleneck-summary-card.tone-danger small {
  color: var(--color-danger);
}

.bottleneck-chart {
  width: calc(100% - 36px);
  margin: 18px;
  box-shadow: none;
}

.bottleneck-change-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  padding: 0 18px 18px;
}

.bottleneck-change-item {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 12px;
  border: 1px solid #e5eaf2;
  border-radius: 6px;
  background: #fff;
}

.bottleneck-change-item span,
.bottleneck-change-item small {
  color: var(--color-subtle);
  font-size: 10px;
}

.bottleneck-change-item strong {
  overflow: hidden;
  color: var(--color-primary);
  font-family: var(--font-data);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bottleneck-change-item.tone-success {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.bottleneck-change-item.tone-success small {
  color: var(--color-success);
  font-weight: 800;
}

.bottleneck-change-item.tone-danger {
  border-color: #fecaca;
  background: #fff7f7;
}

.bottleneck-change-item.tone-danger small {
  color: var(--color-danger);
  font-weight: 800;
}

.bottleneck-diagnostic {
  margin: 0;
  padding: 12px 18px;
  border-top: 1px solid #fed7aa;
  color: #9a3412;
  background: #fff7ed;
  font-size: 11px;
  line-height: 1.65;
}

.outcome-kpi-grid {
  grid-template-columns: repeat(6, minmax(0, 1fr));
  padding: 18px;
  border-bottom: 1px solid var(--color-outline);
}

.annealing-further-panel {
  margin: 18px;
  border: 1px solid #bbf7d0;
  border-radius: 10px;
  background: linear-gradient(135deg, #f0fdf4 0%, #ffffff 65%);
}

.annealing-further-heading {
  padding: 15px 16px 10px;
}

.annealing-further-heading h3 {
  margin: 0;
  color: var(--color-primary);
  font-size: 16px;
}

.annealing-further-heading p,
.stage-comparison-note {
  margin: 5px 0 0;
  color: var(--color-subtle);
  font-size: 12px;
  line-height: 1.6;
}

.annealing-further-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  padding: 0 16px 16px;
}

.annealing-further-card {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 12px;
  border: 1px solid #dbe5ef;
  border-radius: 8px;
  background: #fff;
}

.annealing-further-card span,
.annealing-further-card small {
  color: var(--color-subtle);
  font-size: 11px;
}

.annealing-further-card strong {
  color: var(--color-primary);
  font-family: var(--font-data);
  font-size: 15px;
}

.annealing-further-card.tone-success {
  border-color: #86efac;
  background: #f0fdf4;
}

.annealing-further-card.tone-success small {
  color: var(--color-success);
  font-weight: 700;
}

.annealing-further-card.tone-danger {
  border-color: #fecaca;
  background: #fef2f2;
}

.annealing-further-card.tone-danger small {
  color: var(--color-danger);
  font-weight: 700;
}

.stage-comparison-note {
  margin: 0;
  padding: 0 18px 14px;
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

.curve-error-chip {
  padding: 5px 9px;
  border-radius: 999px;
  color: #b45309;
  background: #fff7ed;
  font-size: 10px;
  font-weight: 800;
  white-space: nowrap;
}

.curve-mode-chip {
  padding: 5px 9px;
  border-radius: 999px;
  color: var(--color-secondary);
  background: var(--color-secondary-soft);
  font-size: 10px;
  font-weight: 800;
  white-space: nowrap;
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

  .bottleneck-change-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .outcome-kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .annealing-further-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1280px) {
  .optimization-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .annealing-further-grid {
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
  .bottleneck-summary-grid,
  .bottleneck-change-grid,
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
  .bottleneck-summary-grid,
  .bottleneck-change-grid,
  .outcome-kpi-grid,
  .annealing-further-grid,
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
