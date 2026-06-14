<template>
  <main class="flow-page">
    <DashboardPageHeader
      title="规则分流效果验证"
      description="基于基础仿真的高峰快照生成保守分流建议，并验证来源高压窗口是否得到真实缓解。"
      :status="flowStatus"
    >
      <template #actions>
        <el-button :loading="loading.diversion" :disabled="!currentRun" @click="handleDiversion">
          生成规则分流建议
        </el-button>
        <el-button
          type="primary"
          :loading="loading.comparison"
          :disabled="!suggestions.length"
          @click="handleComparison"
        >
          验证规则分流
        </el-button>
      </template>
    </DashboardPageHeader>

    <section v-if="!currentRun" class="flow-panel flow-empty-state">
      <el-empty description="请先在“仿真运行与人流快照”页面运行基础仿真。">
        <RouterLink to="/simulation">
          <el-button type="primary">前往仿真运行</el-button>
        </RouterLink>
      </el-empty>
    </section>

    <template v-else>
      <section class="flow-panel">
        <header class="flow-panel-heading">
          <div>
            <h2>当前基础仿真</h2>
            <p>规则建议与对比运行均基于这一份 Baseline，不会重新生成另一套场景数据。</p>
          </div>
        </header>
        <div class="flow-metric-grid flow-metric-grid-4">
          <MetricCard label="Baseline Run" :value="currentRun.runId" />
          <MetricCard label="高峰分钟" :value="peakPoint?.minute" unit="分钟" />
          <MetricCard label="当前拥挤等级" :value="crowdLevelLabel(form.crowdLevel)" />
          <MetricCard label="目标承接等级" :value="crowdLevelLabel(targetCrowdLevel)" />
        </div>
      </section>

      <section class="flow-panel">
        <header class="flow-panel-heading">
          <div>
            <h2>高峰时刻分流建议</h2>
            <p>
              基于第 {{ comparisonMinute ?? '--' }} 分钟高峰快照生成，从高压窗口向低压且可承接窗口分流。
            </p>
          </div>
          <span class="flow-status-chip">{{ suggestions.length }} 条建议</span>
        </header>

        <div class="flow-metric-grid flow-metric-grid-4">
          <MetricCard label="建议路径数" :value="suggestions.length" unit="条" />
          <MetricCard label="建议分流人数" :value="suggestedDiversionCount" unit="人" />
          <MetricCard label="预计接受人数" :value="estimatedAcceptedCount" unit="人" />
          <MetricCard
            label="平均预计等待下降"
            :value="averageWaitReduction"
            unit="分钟"
            tone="success"
          />
        </div>

        <div v-if="topSuggestions.length" class="suggestion-grid">
          <article v-for="item in topSuggestions" :key="item.key" class="suggestion-card">
            <div class="suggestion-route">
              <strong>{{ item.sourceLabel }}</strong>
              <span>→</span>
              <strong>{{ item.targetLabel }}</strong>
            </div>
            <div class="suggestion-metrics">
              <span>建议 {{ item.suggestedUserCount ?? '--' }} 人</span>
              <span>预计接受 {{ item.estimatedAcceptedCount ?? '--' }} 人</span>
              <span>等待下降 {{ item.waitReduction }} 分钟</span>
            </div>
            <p>{{ item.reasonLabel }}</p>
          </article>
        </div>
        <el-empty v-else :image-size="72" description="尚未生成可执行的分流建议。" />

        <el-collapse v-if="remainingSuggestions.length" class="flow-collapse">
          <el-collapse-item
            :title="`查看其余 ${remainingSuggestions.length} 条分流路径`"
            name="remaining-suggestions"
          >
            <div class="suggestion-grid">
              <article
                v-for="item in remainingSuggestions"
                :key="item.key"
                class="suggestion-card"
              >
                <div class="suggestion-route">
                  <strong>{{ item.sourceLabel }}</strong>
                  <span>→</span>
                  <strong>{{ item.targetLabel }}</strong>
                </div>
                <div class="suggestion-metrics">
                  <span>建议 {{ item.suggestedUserCount ?? '--' }} 人</span>
                  <span>预计接受 {{ item.estimatedAcceptedCount ?? '--' }} 人</span>
                  <span>等待下降 {{ item.waitReduction }} 分钟</span>
                </div>
                <p>{{ item.reasonLabel }}</p>
              </article>
            </div>
          </el-collapse-item>
        </el-collapse>
      </section>

      <section class="flow-panel">
        <header class="flow-panel-heading">
          <div>
            <h2>规则分流前后瓶颈缓解对比</h2>
            <p>
              规则分流主要用于缓解被选中来源窗口的局部拥堵，因此重点观察来源排队、来源等待和总过载程度。
            </p>
          </div>
          <span class="flow-status-chip">
            {{ compareRun ? `Compare #${compareRun.runId}` : '等待验证' }}
          </span>
        </header>

        <div class="flow-metric-grid flow-metric-grid-4">
          <MetricCard label="建议分流人数" :value="suggestedDiversionCount" unit="人" />
          <MetricCard label="预计接受人数" :value="estimatedAcceptedCount" unit="人" />
          <MetricCard
            label="来源窗口排队变化"
            :value="transitionLabel(sourceQueueRow)"
            :detail="sourceQueueRow?.changeLabel"
            :tone="sourceQueueRow?.tone"
          />
          <MetricCard
            label="总过载程度变化"
            :value="transitionLabel(overloadRow)"
            :detail="overloadRow?.percentLabel"
            :tone="overloadRow?.tone"
          />
        </div>

        <DashboardChart
          title="核心瓶颈指标改善率"
          subtitle="统一使用 (Baseline - Compare) / Baseline × 100%，正值表示拥堵得到缓解。"
          :option="improvementRateOption"
          :loading="loading.comparison"
          :empty="!compareRun"
          empty-text="验证规则分流后显示来源排队、来源等待和总过载程度的改善率"
          :height="310"
        />

        <div class="comparison-card-grid">
          <article
            v-for="row in bottleneckRows"
            :key="row.key"
            :class="['comparison-card', row.tone ? `tone-${row.tone}` : '']"
          >
            <span>{{ row.label }}</span>
            <strong>{{ row.baselineLabel }} → {{ row.compareLabel }}</strong>
            <small>{{ row.changeLabel }} · {{ row.percentLabel }}</small>
          </article>
        </div>

        <p class="flow-diagnostic">
          如果“来源窗口总排队人数”在分流后几乎没有变化，说明 compareRun
          可能没有真正执行分流建议，或者分流强度过低，需要进一步检查后端
          runSimulationWithDiversion / diversion-comparison 逻辑。
        </p>
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
import { useDashboardStore } from '../stores/dashboardStore'
import {
  buildBottleneckMetrics,
  buildFiveMetricRows,
  findWindow,
  formatNumber,
  resolveSourceWindowIds,
  sumSuggestionField,
  windowLabel,
} from '../utils/dashboardMetrics'

const store = useDashboardStore()
const {
  form,
  currentRun,
  compareRun,
  diversionResult,
  comparison,
  loading,
  requestStatus,
  targetCrowdLevel,
  peakPoint,
} = storeToRefs(store)

const suggestions = computed(() =>
  Array.isArray(diversionResult.value?.suggestions)
    ? diversionResult.value.suggestions
    : [],
)
const comparisonMinute = computed(
  () => comparison.value?.minute ?? diversionResult.value?.minute ?? peakPoint.value?.minute,
)
const sourceWindowIds = computed(() => resolveSourceWindowIds(suggestions.value))
const baselineMetrics = computed(() =>
  buildBottleneckMetrics(currentRun.value, comparisonMinute.value, sourceWindowIds.value),
)
const compareMetrics = computed(() =>
  buildBottleneckMetrics(compareRun.value, comparisonMinute.value, sourceWindowIds.value),
)
const bottleneckRows = computed(() =>
  buildFiveMetricRows(baselineMetrics.value, compareMetrics.value),
)
const sourceQueueRow = computed(() =>
  bottleneckRows.value.find((row) => row.key === 'sourceQueue'),
)
const overloadRow = computed(() =>
  bottleneckRows.value.find((row) => row.key === 'totalOverload'),
)

const flowStatus = computed(() => {
  if (!currentRun.value) return '未运行基础仿真'
  if (loading.value.comparison) return '正在验证规则分流'
  if (compareRun.value) return '已完成规则分流验证'
  if (suggestions.value.length) return '已生成规则分流建议'
  return requestStatus.value
})

const suggestedDiversionCount = computed(() =>
  sumSuggestionField(suggestions.value, 'suggestedUserCount'),
)
const estimatedAcceptedCount = computed(() =>
  sumSuggestionField(suggestions.value, 'estimatedAcceptedCount'),
)
const averageWaitReduction = computed(() => {
  const values = suggestions.value
    .map((item) => Number(item?.estimatedWaitReduction))
    .filter(Number.isFinite)
  return values.length
    ? formatNumber(values.reduce((sum, value) => sum + value, 0) / values.length, 1)
    : '--'
})

const suggestionRows = computed(() =>
  suggestions.value.map((suggestion, index) => {
    const sourceWindowId =
      suggestion.fromWindowId ?? suggestion.sourceWindowId ?? suggestion.sourceWindow?.windowId
    const targetWindowId =
      suggestion.toWindowId ?? suggestion.targetWindowId ?? suggestion.targetWindow?.windowId
    const point = currentRun.value
      ? currentRun.value.timePoints?.find(
          (item) => Number(item.minute) === Number(comparisonMinute.value),
        ) ?? peakPoint.value
      : null
    return {
      key: `${sourceWindowId}-${targetWindowId}-${index}`,
      sourceLabel: windowLabel(point, sourceWindowId),
      targetLabel: windowLabel(point, targetWindowId),
      suggestedUserCount: suggestion.suggestedUserCount,
      estimatedAcceptedCount: suggestion.estimatedAcceptedCount,
      waitReduction: formatNumber(suggestion.estimatedWaitReduction, 1),
      reasonLabel: translateReason(suggestion.reason),
      sourceWindow: findWindow(point, sourceWindowId),
    }
  }),
)
const topSuggestions = computed(() => suggestionRows.value.slice(0, 3))
const remainingSuggestions = computed(() => suggestionRows.value.slice(3))

const primaryRows = computed(() =>
  ['sourceQueue', 'sourceWait', 'totalOverload']
    .map((key) => bottleneckRows.value.find((row) => row.key === key))
    .filter(Boolean),
)

const improvementRateOption = computed(() => ({
  color: ['#16a34a'],
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    formatter: (params) => {
      const row = primaryRows.value[Number(params?.[0]?.dataIndex ?? -1)]
      if (!row) return ''
      return [
        row.label,
        `Baseline：${row.baselineLabel}`,
        `Compare：${row.compareLabel}`,
        `改善率：${row.percentLabel}`,
      ].join('<br/>')
    },
  },
  grid: { left: 48, right: 30, top: 28, bottom: 58 },
  xAxis: {
    type: 'category',
    data: primaryRows.value.map((row) => row.label),
    axisLabel: { interval: 0 },
  },
  yAxis: {
    type: 'value',
    name: '改善率（%）',
    axisLabel: { formatter: '{value}%' },
    splitLine: { lineStyle: { color: '#e8edf4' } },
  },
  series: [
    {
      type: 'bar',
      barMaxWidth: 52,
      data: primaryRows.value.map((row) => ({
        value: row.percent,
        itemStyle: {
          color: Number(row.percent) >= 0 ? '#16a34a' : '#dc2626',
          borderRadius: Number(row.percent) >= 0 ? [7, 7, 0, 0] : [0, 0, 7, 7],
        },
      })),
      label: {
        show: true,
        position: 'top',
        formatter: ({ value }) =>
          Number.isFinite(Number(value)) ? `${formatNumber(value, 1)}%` : '--',
      },
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

async function handleDiversion() {
  try {
    await store.generateDiversion()
    ElMessage.success('规则分流建议已刷新')
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') {
      ElMessage.error(error?.message || '分流建议生成失败')
    }
  }
}

async function handleComparison() {
  try {
    const result = await store.runComparison()
    if (result?.status === 'NO_SUGGESTION') {
      ElMessage.warning(result.diversionResult?.reason || '当前没有可执行建议')
    } else {
      ElMessage.success('规则分流验证完成')
    }
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') {
      ElMessage.error(error?.message || '规则分流验证失败')
    }
  }
}

function transitionLabel(row) {
  return row ? `${row.baselineLabel} → ${row.compareLabel}` : '--'
}

function crowdLevelLabel(level) {
  return (
    {
      IDLE: '空闲',
      NORMAL: '正常',
      BUSY: '较忙',
      EXTREME: '拥挤',
    }[level] || level || '--'
  )
}

function translateReason(reason) {
  if (!reason) return '依据来源压力、目标承接能力和用户偏好生成'
  const text = String(reason)
  const chineseCount = (text.match(/[\u4e00-\u9fff]/g) || []).length
  if (chineseCount >= Math.max(3, text.length * 0.25)) return text
  return '依据来源压力、目标承接能力和用户偏好生成'
}
</script>

<style src="./dashboard-flow.css"></style>
