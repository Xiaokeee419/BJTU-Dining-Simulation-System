<template>
  <main class="flow-page">
    <DashboardPageHeader
      title="模拟退火策略优化"
      description="在真实 compare 仿真的基础上搜索分流参数，观察 Loss、温度与瓶颈指标如何逐步改善。"
      :status="flowStatus"
    >
      <template #actions>
        <el-button
          type="success"
          :loading="loading.optimization"
          :disabled="!currentRun"
          @click="handleOptimization"
        >
          启动模拟退火优化
        </el-button>
        <el-button v-if="loading.optimization" @click="stopPolling">停止轮询</el-button>
      </template>
    </DashboardPageHeader>

    <section v-if="!currentRun" class="flow-panel flow-empty-state">
      <el-empty description="请先完成基础仿真，再启动模拟退火优化。">
        <RouterLink to="/simulation">
          <el-button type="primary">前往仿真运行</el-button>
        </RouterLink>
      </el-empty>
    </section>

    <template v-else>
      <el-alert
        v-if="!compareRun"
        title="建议先完成规则分流验证，再查看退火相对规则分流的提升；当前仍可直接启动优化。"
        type="warning"
        :closable="false"
        show-icon
      />

      <section class="flow-grid flow-grid-controls">
        <section class="flow-panel">
          <header class="flow-panel-heading">
            <div>
              <h2>优化控制</h2>
              <p>每轮参数都会触发真实 compare 仿真，最多支持 200 次迭代。</p>
            </div>
          </header>
          <div class="annealing-control-row">
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

        <section class="flow-panel">
          <header class="flow-panel-heading">
            <div>
              <h2>搜索进度</h2>
              <p>{{ optimizationJob?.message || '尚未启动优化任务' }}</p>
            </div>
          </header>
          <div class="flow-metric-grid flow-metric-grid-5">
            <MetricCard
              label="当前迭代"
              :value="optimizationJob?.currentIteration"
              :detail="`总计 ${optimizationJob?.totalIterations || optimizationSettings.iterationCount} 次`"
            />
            <MetricCard
              label="当前温度"
              :value="formatNumber(optimizationJob?.currentTemperature, 2)"
            />
            <MetricCard
              label="当前 Loss"
              :value="formatNumber(optimizationJob?.currentLoss, 2)"
            />
            <MetricCard
              label="Best Loss"
              :value="formatNumber(bestLoss, 2)"
              tone="success"
            />
            <MetricCard label="当前状态" :value="optimizationJob?.status || 'NOT_STARTED'" />
          </div>
          <el-progress
            class="annealing-progress"
            :percentage="optimizationProgress"
            :status="optimizationJob?.status === 'FAILED' ? 'exception' : undefined"
          />
        </section>
      </section>

      <div class="flow-grid flow-grid-3">
        <ParameterPanel title="初始参数" :parameters="strategyParameters" />
        <ParameterPanel
          title="当前参数"
          :parameters="optimizationJob?.currentParameters"
          empty-text="尚未开始优化"
        />
        <ParameterPanel
          title="最优参数"
          :parameters="optimizationBest?.parameters || optimizationJob?.bestParameters"
          empty-text="尚未产生 Best"
          tone="best"
        />
      </div>

      <section class="flow-grid flow-grid-2">
        <DashboardChart
          title="优化过程 Loss 曲线"
          subtitle="候选 Loss 展示真实搜索波动，历史 Best Loss 展示当前最优值的阶梯式下降。"
          :option="lossCurveOption"
          :loading="loading.optimization && !optimizationIterations.length"
          :empty="!optimizationIterations.length"
          :height="330"
        />
        <DashboardChart
          title="迭代中的关键瓶颈指标"
          subtitle="仅观察来源窗口排队、来源窗口等待和总过载程度；等待时间使用右侧坐标轴。"
          :option="optimizationMetricOption"
          :loading="loading.optimization && !optimizationIterations.length"
          :empty="!optimizationBottleneckSeriesReady"
          empty-text="当前后端尚未返回迭代瓶颈指标"
          :height="330"
        />
      </section>

      <section class="flow-panel">
        <header class="flow-panel-heading">
          <div>
            <h2>退火最优方案效果</h2>
            <p>
              规则分流用于快速缓解来源高压窗口；模拟退火在此基础上继续搜索，使瓶颈缓解、过载程度和综合 Loss 下降。
            </p>
          </div>
          <span class="flow-status-chip">
            {{
              optimizationBest?.compareRunId
                ? `Best #${optimizationBest.compareRunId}`
                : optimizationJob?.status || 'NOT_STARTED'
            }}
          </span>
        </header>

        <div class="flow-metric-grid flow-metric-grid-3">
          <MetricCard
            v-for="card in outcomeCards"
            :key="card.label"
            :label="card.label"
            :value="card.value"
            :detail="card.detail"
            :tone="card.tone"
          />
        </div>

        <section class="flow-grid flow-grid-2 stage-chart-grid">
          <DashboardChart
            title="Baseline / 规则分流 / 退火最优三阶段对比"
            subtitle="展示从基础仿真到规则分流，再到模拟退火优化后，三个核心瓶颈指标的连续变化；各指标保留自身原始单位。"
            :option="threeStageRawOption"
            :empty="!compareRun"
            empty-text="请先完成规则分流验证"
            :height="330"
          >
            <template #extra>
              <span v-if="!optimizationBest" class="flow-status-chip">
                请先完成模拟退火优化
              </span>
            </template>
          </DashboardChart>
          <DashboardChart
            title="三阶段核心改善率对比"
            subtitle="分别比较规则分流和退火最优相对 Baseline 的改善率，正值表示拥堵下降。"
            :option="threeStageImprovementOption"
            :empty="!compareRun"
            empty-text="请先完成规则分流验证"
            :height="330"
          >
            <template #extra>
              <span v-if="!optimizationBest" class="flow-status-chip">
                请先完成模拟退火优化
              </span>
            </template>
          </DashboardChart>
        </section>

        <div class="flow-subsection">
          <h3>退火相对规则分流的进一步优化</h3>
          <p>以下变化均由真实规则分流 Run 与退火 Best Run 重新计算，不对后端指标做展示修正。</p>
          <div class="comparison-card-grid">
            <article
              v-for="card in furtherImprovementCards"
              :key="card.label"
              :class="['comparison-card', card.tone ? `tone-${card.tone}` : '']"
            >
              <span>{{ card.label }}</span>
              <strong>{{ card.transition }}</strong>
              <small>{{ card.detail }}</small>
            </article>
          </div>
        </div>

        <el-table
          :data="threeStageRows"
          stripe
          empty-text="优化完成后显示 Baseline、规则分流与退火最优的指标对比"
        >
          <el-table-column prop="label" label="指标" min-width="170" />
          <el-table-column prop="baseline" label="Baseline" width="125" />
          <el-table-column prop="strategy" label="规则分流" width="125" />
          <el-table-column prop="optimized" label="退火最优" width="125" />
          <el-table-column prop="vsBaseline" label="相对 Baseline" min-width="145" />
          <el-table-column prop="vsStrategy" label="相对规则分流" min-width="145" />
        </el-table>
      </section>

      <section class="flow-panel">
        <el-collapse class="flow-collapse">
          <el-collapse-item
            :title="`迭代历史（${optimizationIterations.length} 条）`"
            name="iteration-history"
          >
            <el-table
              :data="optimizationIterations"
              stripe
              max-height="480"
              empty-text="启动模拟退火后显示迭代过程"
            >
              <el-table-column prop="iteration" label="#" width="62" />
              <el-table-column prop="temperature" label="温度" width="90" />
              <el-table-column prop="loss" label="Loss" width="94" />
              <el-table-column label="接受" width="76">
                <template #default="{ row }">
                  <el-tag :type="row.accepted ? 'success' : 'info'" size="small">
                    {{ row.accepted ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="Best" width="72">
                <template #default="{ row }">{{ row.best ? '是' : '' }}</template>
              </el-table-column>
              <el-table-column
                prop="bottleneckMetrics.sourceWindowQueueTotal"
                label="来源排队"
                width="100"
              />
              <el-table-column
                prop="bottleneckMetrics.sourceWindowAverageWait"
                label="来源等待"
                width="100"
              />
              <el-table-column
                prop="bottleneckMetrics.totalOverload"
                label="总过载"
                width="90"
              />
              <el-table-column prop="compareRunId" label="Compare Run" min-width="120" />
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import { RouterLink } from 'vue-router'
import DashboardChart from '../components/DashboardChart.vue'
import DashboardPageHeader from '../components/dashboard/DashboardPageHeader.vue'
import MetricCard from '../components/dashboard/MetricCard.vue'
import ParameterPanel from '../components/dashboard/ParameterPanel.vue'
import { useDashboardStore } from '../stores/dashboardStore'
import {
  buildBottleneckMetrics,
  buildFurtherImprovementCard,
  buildThreeStageRow,
  finiteMetricOrNull,
  formatImprovementText,
  formatMetricValue,
  formatNumber,
  formatPercentChange,
  improvementPercent,
  isFiniteMetric,
  lowerBetterTone,
  metricDelta,
  normalizeOptimizationMetrics,
  resolveSourceWindowIds,
} from '../utils/dashboardMetrics'

const store = useDashboardStore()
const {
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
  peakPoint,
} = storeToRefs(store)

const suggestions = computed(() =>
  Array.isArray(diversionResult.value?.suggestions)
    ? diversionResult.value.suggestions
    : [],
)
const sourceWindowIds = computed(() => resolveSourceWindowIds(suggestions.value))
const comparisonMinute = computed(
  () => comparison.value?.minute ?? diversionResult.value?.minute ?? peakPoint.value?.minute,
)
const optimizedMinute = computed(
  () => optimizationBest.value?.minute ?? comparisonMinute.value,
)

const baselineMetrics = computed(() =>
  buildBottleneckMetrics(currentRun.value, comparisonMinute.value, sourceWindowIds.value),
)
const strategyMetrics = computed(() =>
  buildBottleneckMetrics(compareRun.value, comparisonMinute.value, sourceWindowIds.value),
)
const optimizedMetrics = computed(() => {
  const backendMetrics = normalizeOptimizationMetrics(
    optimizationBest.value?.bottleneckMetrics,
    optimizationBest.value?.metrics,
  )
  if (!optimizationBestRun.value) return backendMetrics
  const runMetrics = buildBottleneckMetrics(
    optimizationBestRun.value,
    optimizedMinute.value,
    sourceWindowIds.value,
  )
  return {
    ...runMetrics,
    targetOverload: backendMetrics.targetOverload,
    loadImbalance: backendMetrics.loadImbalance ?? runMetrics.loadImbalance,
  }
})

const bestLoss = computed(
  () => optimizationBest.value?.loss ?? optimizationJob.value?.bestLoss,
)
const strategyLoss = computed(() =>
  firstFiniteMetric(
    comparison.value?.loss,
    comparison.value?.compareLoss,
    comparison.value?.strategyLoss,
    comparison.value?.ruleLoss,
    comparison.value?.compareMetrics?.loss,
    compareRun.value?.metrics?.loss,
  ),
)

const flowStatus = computed(() => {
  if (!currentRun.value) return '未运行基础仿真'
  if (loading.value.optimization) return '模拟退火运行中'
  if (optimizationBest.value) return '已完成模拟退火优化'
  if (compareRun.value) return '已完成规则分流验证'
  return requestStatus.value
})

const optimizationProgress = computed(() => {
  const total = Number(optimizationJob.value?.totalIterations || 0)
  const current = Number(optimizationJob.value?.currentIteration || 0)
  return total > 0 ? Math.min(100, Math.round((current / total) * 100)) : 0
})

const lossCurvePoints = computed(() => {
  let runningBest = null
  return optimizationIterations.value.map((item) => {
    const candidateLoss = finiteMetricOrNull(item.loss)
    if (candidateLoss !== null) {
      runningBest =
        runningBest === null ? candidateLoss : Math.min(runningBest, candidateLoss)
    }
    return { candidateLoss, bestLoss: runningBest }
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
  grid: { left: 56, right: 28, top: 48, bottom: 42 },
  xAxis: {
    type: 'category',
    data: optimizationIterations.value.map((item) => item.iteration),
    name: 'Iteration',
  },
  yAxis: { type: 'value', name: 'Loss', scale: true },
  series: [
    {
      name: '候选 Loss',
      type: 'line',
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
      lineStyle: { width: 3, color: '#16a34a' },
      data: lossCurvePoints.value.map((item) => item.bestLoss),
    },
  ],
}))

const optimizationBottleneckSeriesReady = computed(() =>
  optimizationIterations.value.some((item) =>
    [
      item?.bottleneckMetrics?.sourceWindowQueueTotal,
      item?.bottleneckMetrics?.sourceWindowAverageWait,
      item?.bottleneckMetrics?.totalOverload,
    ].some(isFiniteMetric),
  ),
)

const optimizationMetricOption = computed(() => ({
  color: ['#0160a8', '#7c3aed', '#16a34a'],
  tooltip: { trigger: 'axis' },
  legend: { top: 4 },
  grid: { left: 58, right: 64, top: 58, bottom: 42 },
  xAxis: {
    type: 'category',
    data: optimizationIterations.value.map((item) => item.iteration),
  },
  yAxis: [
    { type: 'value', name: '排队 / 过载', min: 0 },
    { type: 'value', name: '等待（分钟）', min: 0, splitLine: { show: false } },
  ],
  series: [
    {
      name: '来源窗口总排队',
      type: 'line',
      data: optimizationIterations.value.map((item) =>
        finiteMetricOrNull(item.bottleneckMetrics?.sourceWindowQueueTotal),
      ),
    },
    {
      name: '来源窗口平均等待',
      type: 'line',
      yAxisIndex: 1,
      data: optimizationIterations.value.map((item) =>
        finiteMetricOrNull(item.bottleneckMetrics?.sourceWindowAverageWait),
      ),
    },
    {
      name: '总过载程度',
      type: 'line',
      data: optimizationIterations.value.map((item) =>
        finiteMetricOrNull(item.bottleneckMetrics?.totalOverload),
      ),
    },
  ],
}))

const outcomeCards = computed(() => {
  const baseline = baselineMetrics.value
  const strategy = strategyMetrics.value
  const optimized = optimizedMetrics.value
  const relativeRates = [
    improvementPercent(strategy.sourceQueue, optimized.sourceQueue),
    improvementPercent(strategy.sourceWait, optimized.sourceWait),
    improvementPercent(strategy.totalOverload, optimized.totalOverload),
  ].filter(isFiniteMetric)
  const combinedImprovement = relativeRates.length
    ? relativeRates.reduce((sum, value) => sum + Number(value), 0) / relativeRates.length
    : null

  return [
    { label: 'Best Loss', value: formatNumber(bestLoss.value, 2), tone: 'success' },
    {
      label: 'Loss 下降率',
      value: formatPercentChange(improvementPercent(strategyLoss.value, bestLoss.value)),
      tone: lowerBetterTone(metricDelta(strategyLoss.value, bestLoss.value)),
    },
    {
      label: '来源窗口排队相对规则再下降',
      value: formatImprovementText(strategy.sourceQueue, optimized.sourceQueue, 0, '人'),
      tone: lowerBetterTone(metricDelta(strategy.sourceQueue, optimized.sourceQueue)),
    },
    {
      label: '来源窗口等待相对规则再下降',
      value: formatImprovementText(strategy.sourceWait, optimized.sourceWait, 1, '分钟'),
      tone: lowerBetterTone(metricDelta(strategy.sourceWait, optimized.sourceWait)),
    },
    {
      label: '总过载程度相对规则再下降',
      value: formatImprovementText(strategy.totalOverload, optimized.totalOverload, 0, ''),
      tone: lowerBetterTone(metricDelta(strategy.totalOverload, optimized.totalOverload)),
    },
    {
      label: '相对规则分流综合改善',
      value: formatPercentChange(combinedImprovement),
      detail: '来源排队、来源等待与总过载改善率的平均值',
      tone:
        isFiniteMetric(combinedImprovement) && Number(combinedImprovement) > 0
          ? 'success'
          : isFiniteMetric(combinedImprovement) && Number(combinedImprovement) < 0
            ? 'danger'
            : '',
    },
  ]
})

const furtherImprovementCards = computed(() => [
  buildFurtherImprovementCard(
    '来源窗口总排队',
    strategyMetrics.value.sourceQueue,
    optimizedMetrics.value.sourceQueue,
    0,
    '人',
  ),
  buildFurtherImprovementCard(
    '来源窗口平均等待',
    strategyMetrics.value.sourceWait,
    optimizedMetrics.value.sourceWait,
    1,
    '分钟',
  ),
  buildFurtherImprovementCard(
    '总过载程度',
    strategyMetrics.value.totalOverload,
    optimizedMetrics.value.totalOverload,
    0,
    '',
  ),
  buildFurtherImprovementCard('Loss', strategyLoss.value, bestLoss.value, 2, ''),
])

const stageMetricRows = computed(() => [
  {
    key: 'sourceQueue',
    label: '来源窗口总排队',
    baseline: baselineMetrics.value.sourceQueue,
    strategy: strategyMetrics.value.sourceQueue,
    optimized: optimizedMetrics.value.sourceQueue,
    digits: 0,
    unit: '人',
  },
  {
    key: 'sourceWait',
    label: '来源窗口平均等待',
    baseline: baselineMetrics.value.sourceWait,
    strategy: strategyMetrics.value.sourceWait,
    optimized: optimizedMetrics.value.sourceWait,
    digits: 1,
    unit: '分钟',
  },
  {
    key: 'totalOverload',
    label: '总过载程度',
    baseline: baselineMetrics.value.totalOverload,
    strategy: strategyMetrics.value.totalOverload,
    optimized: optimizedMetrics.value.totalOverload,
    digits: 0,
    unit: '',
  },
])

const threeStageRows = computed(() => [
  ...stageMetricRows.value.map((row) =>
    buildThreeStageRow(
      row.label,
      row.baseline,
      row.strategy,
      row.optimized,
      row.digits,
      row.unit,
    ),
  ),
  {
    label: 'Loss',
    baseline: '--',
    strategy: formatMetricValue(strategyLoss.value, 2, ''),
    optimized: formatMetricValue(bestLoss.value, 2, ''),
    vsBaseline: '--',
    vsStrategy: formatLossImprovement(strategyLoss.value, bestLoss.value),
  },
])

const threeStageRawOption = computed(() => ({
  color: ['#94a3b8', '#0160a8', '#16a34a'],
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    formatter: (params) => {
      const row = stageMetricRows.value[Number(params?.[0]?.dataIndex ?? -1)]
      if (!row) return ''
      return [
        row.label,
        `Baseline：${formatMetricValue(row.baseline, row.digits, row.unit)}`,
        `规则分流：${formatMetricValue(row.strategy, row.digits, row.unit)}`,
        `退火最优：${formatMetricValue(row.optimized, row.digits, row.unit)}`,
      ].join('<br/>')
    },
  },
  legend: { top: 4, data: ['Baseline', '规则分流', '退火最优'] },
  grid: { left: 52, right: 28, top: 48, bottom: 58 },
  xAxis: {
    type: 'category',
    data: stageMetricRows.value.map((row) => row.label),
    axisLabel: { interval: 0 },
  },
  yAxis: {
    type: 'value',
    name: '原始值',
    min: 0,
    splitLine: { lineStyle: { color: '#e8edf4' } },
  },
  series: [
    {
      name: 'Baseline',
      type: 'bar',
      barMaxWidth: 34,
      data: stageMetricRows.value.map((row) => finiteMetricOrNull(row.baseline)),
      itemStyle: { color: '#94a3b8', borderRadius: [6, 6, 0, 0] },
    },
    {
      name: '规则分流',
      type: 'bar',
      barMaxWidth: 34,
      data: stageMetricRows.value.map((row) => finiteMetricOrNull(row.strategy)),
      itemStyle: { color: '#0160a8', borderRadius: [6, 6, 0, 0] },
    },
    {
      name: '退火最优',
      type: 'bar',
      barMaxWidth: 34,
      data: stageMetricRows.value.map((row) => finiteMetricOrNull(row.optimized)),
      itemStyle: { color: '#16a34a', borderRadius: [6, 6, 0, 0] },
    },
  ],
}))

const threeStageImprovementOption = computed(() => ({
  color: ['#0160a8', '#16a34a'],
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    formatter: (params) => {
      const row = stageMetricRows.value[Number(params?.[0]?.dataIndex ?? -1)]
      if (!row) return ''
      const strategyRate = improvementPercent(row.baseline, row.strategy)
      const optimizedRate = improvementPercent(row.baseline, row.optimized)
      const relativeRuleRate = improvementPercent(row.strategy, row.optimized)
      return [
        row.label,
        `规则分流相对 Baseline：${formatRateTooltip(strategyRate)}`,
        `退火最优相对 Baseline：${formatRateTooltip(optimizedRate)}`,
        `相对规则分流再提升：${formatRateTooltip(relativeRuleRate)}`,
      ].join('<br/>')
    },
  },
  legend: { top: 4, data: ['规则分流相对 Baseline', '退火最优相对 Baseline'] },
  grid: { left: 52, right: 28, top: 48, bottom: 62 },
  xAxis: {
    type: 'category',
    data: stageMetricRows.value.map((row) => `${row.label}下降率`),
    axisLabel: { interval: 0, rotate: 12 },
  },
  yAxis: {
    type: 'value',
    name: '改善率（%）',
    axisLabel: { formatter: '{value}%' },
    splitLine: { lineStyle: { color: '#e8edf4' } },
  },
  series: [
    {
      name: '规则分流相对 Baseline',
      type: 'bar',
      barMaxWidth: 38,
      data: stageMetricRows.value.map((row) =>
        finiteMetricOrNull(improvementPercent(row.baseline, row.strategy)),
      ),
      itemStyle: { color: '#0160a8', borderRadius: [6, 6, 0, 0] },
    },
    {
      name: '退火最优相对 Baseline',
      type: 'bar',
      barMaxWidth: 38,
      data: stageMetricRows.value.map((row) =>
        finiteMetricOrNull(improvementPercent(row.baseline, row.optimized)),
      ),
      itemStyle: { color: '#16a34a', borderRadius: [6, 6, 0, 0] },
    },
  ],
}))

onMounted(async () => {
  try {
    await store.initialize()
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') {
      ElMessage.error(error?.message || '页面初始化失败')
    }
  }
})

async function handleOptimization() {
  try {
    await store.startOptimization()
    ElMessage.success('模拟退火任务已启动')
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') {
      ElMessage.error(error?.message || '模拟退火启动失败')
    }
  }
}

function stopPolling() {
  store.stopOptimizationPolling(true)
  ElMessage.info('已停止前端轮询')
}

function firstFiniteMetric(...values) {
  return values.find(isFiniteMetric) ?? null
}

function formatLossImprovement(before, after) {
  if (!isFiniteMetric(before) || !isFiniteMetric(after)) return '--'
  const change = formatImprovementText(before, after, 2, '')
  const rate = formatPercentChange(improvementPercent(before, after))
  return `${change}，${rate}`
}

function formatRateTooltip(value) {
  if (!isFiniteMetric(value)) return '--'
  return `${Number(value) >= 0 ? '下降' : '上升'} ${formatNumber(
    Math.abs(Number(value)),
    1,
  )}%`
}
</script>

<style src="./dashboard-flow.css"></style>
