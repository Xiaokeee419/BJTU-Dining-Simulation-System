<template>
  <main class="page-shell page-stack">
    <PageHeader
      eyebrow="分流决策"
      title="分流决策与效果对比"
      description="基于当前仿真高峰快照生成分流建议，并通过对比分流前后的仿真指标，评估分流策略对排队压力和服务效率的改善效果。"
    >
      <template #actions>
        <el-button :disabled="!currentRun" @click="handleSaveBaseline">保存为未分流基准</el-button>
        <el-button
          type="success"
          :disabled="!currentRun && !baselineRun"
          :loading="comparing"
          @click="handleRunComparison"
        >
          运行分流对比
        </el-button>
        <el-button plain :disabled="!hasAnyComparisonState" @click="store.clearComparison">
          清除对比
        </el-button>
      </template>
    </PageHeader>

    <EmptyState
      v-if="!currentRun"
      title="请先运行仿真"
      description="推荐页依赖真实仿真 runId。完成一次仿真后，这里会展示分流建议、策略对比和辅助推荐。"
    />

    <template v-else>
      <section class="card-grid-4">
        <StatCard
          :icon="Tickets"
          label="基准方案"
          :value="baselineRun ? `#${baselineRun.runId}` : '未保存'"
          caption="未执行分流"
          :delta="baselineSummary"
          :delta-state="baselineRun ? 'neutral' : 'danger'"
        />
        <StatCard
          :icon="Clock"
          label="推荐时间点"
          :value="selectedMinuteLabel"
          caption="默认使用高峰快照"
          :delta="generatedAtLabel"
          delta-state="neutral"
          tone="teal"
        />
        <StatCard
          :icon="Share"
          label="分流建议数"
          :value="suggestionCount"
          unit="条"
          caption="基于基准方案生成"
          :delta="diversionReason"
          delta-state="neutral"
          tone="orange"
        />
        <StatCard
          :icon="TrendCharts"
          label="对比状态"
          :value="comparisonStatusLabel"
          caption="需要 baseRunId 和 compareRunId"
          :delta="comparisonStatusDetail"
          :delta-state="comparisonStatusState"
          tone="green"
        />
      </section>

      <SectionCard
        title="分析时间点"
        subtitle="默认选择高峰分钟。拖动滑块会刷新当前基准 run 的推荐与分流建议，并清空旧的 compare 结果。"
      >
        <el-slider
          :model-value="currentMinute"
          :min="0"
          :max="maxMinute"
          :step="timeStep"
          :format-tooltip="formatMinute"
          @change="handleMinuteChange"
        />
      </SectionCard>

      <SectionCard
        title="分流建议列表"
        subtitle="展示从哪个窗口分流到哪个窗口、建议分流人数、接受率和预计等待改善。"
      >
        <DiversionSuggestionList
          :suggestions="diversionResult?.suggestions || []"
          :reason="diversionReason"
          :resolve-window-label="resolveWindowLabel"
        />
      </SectionCard>

      <SectionCard
        title="分流前后效果对比"
        subtitle="只有拿到 baseline 与 compare 两轮真实仿真结果后，才会展示指标对比、趋势图和策略结论。"
      >
        <DiversionComparisonPanel
          :comparison="strategyComparison"
          :base-metrics="effectiveBaseMetrics"
          :compare-metrics="effectiveCompareMetrics"
          :baseline-run="baselineRun"
          :compare-run="compareRun"
        />
      </SectionCard>

      <SectionCard
        title="辅助推荐详情"
        subtitle="餐厅、窗口、菜品推荐保留为辅助信息，不作为本页主线。"
      >
        <div class="recommendation-grid">
          <article class="recommendation-panel">
            <div class="panel-head">
              <h3>餐厅推荐</h3>
              <span>{{ recommendation?.restaurants?.length || 0 }} 条</span>
            </div>
            <div v-if="recommendation?.restaurants?.length" class="recommendation-list">
              <div
                v-for="item in recommendation.restaurants"
                :key="`restaurant-${item.targetId}`"
                class="recommendation-item"
              >
                <div class="item-head">
                  <strong>{{ item.name }}</strong>
                  <span class="item-rank">#{{ item.rank }}</span>
                </div>
                <p>{{ item.reason }}</p>
                <div class="item-meta">
                  <span>预计等待 {{ item.estimatedWaitMinutes }} 分钟</span>
                  <span>{{ crowdLabel(item.crowdLevel) }}</span>
                </div>
              </div>
            </div>
            <EmptyState v-else title="暂无餐厅推荐" description="当前分钟没有返回餐厅推荐结果。" />
          </article>

          <article class="recommendation-panel">
            <div class="panel-head">
              <h3>窗口推荐</h3>
              <span>{{ recommendation?.windows?.length || 0 }} 条</span>
            </div>
            <div v-if="recommendation?.windows?.length" class="recommendation-list">
              <div
                v-for="item in recommendation.windows"
                :key="`window-${item.targetId}`"
                class="recommendation-item"
              >
                <div class="item-head">
                  <strong>{{ item.name }}</strong>
                  <span class="item-rank">#{{ item.rank }}</span>
                </div>
                <p>{{ item.reason }}</p>
                <div class="item-meta">
                  <span>{{ relatedRestaurantName(item.relatedRestaurantId) }}</span>
                  <span>预计等待 {{ item.estimatedWaitMinutes }} 分钟</span>
                </div>
              </div>
            </div>
            <EmptyState v-else title="暂无窗口推荐" description="当前分钟没有返回窗口推荐结果。" />
          </article>

          <article class="recommendation-panel">
            <div class="panel-head">
              <h3>菜品推荐</h3>
              <span>{{ recommendation?.dishes?.length || 0 }} 条</span>
            </div>
            <div v-if="recommendation?.dishes?.length" class="recommendation-list">
              <div
                v-for="item in recommendation.dishes"
                :key="`dish-${item.targetId}`"
                class="recommendation-item"
              >
                <div class="item-head">
                  <strong>{{ item.name }}</strong>
                  <span class="item-rank">#{{ item.rank }}</span>
                </div>
                <p>{{ item.reason }}</p>
                <div class="item-meta">
                  <span>{{ relatedWindowName(item.relatedWindowId) }}</span>
                  <span>预计等待 {{ item.estimatedWaitMinutes }} 分钟</span>
                </div>
              </div>
            </div>
            <EmptyState v-else title="暂无菜品推荐" description="当前分钟没有返回菜品推荐结果。" />
          </article>
        </div>
      </SectionCard>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { Clock, Share, Tickets, TrendCharts } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import DiversionComparisonPanel from '../components/DiversionComparisonPanel.vue'
import DiversionSuggestionList from '../components/DiversionSuggestionList.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import SectionCard from '../components/SectionCard.vue'
import StatCard from '../components/StatCard.vue'
import { useSimulationStore } from '../stores/simulationStore'

const store = useSimulationStore()
const {
  currentRun,
  baselineRun,
  compareRun,
  recommendation,
  diversionResult,
  strategyComparison,
  baseMetrics,
  compareMetrics,
  diversionComparisonStatus,
  selectedCompareMinute,
  comparisonError,
  currentMinute,
  maxMinute,
  scenarioForm,
  profiles,
  scenarios,
  windows,
  comparing,
} = storeToRefs(store)

const timeStep = computed(() => Number(currentRun.value?.scenario?.stepMinutes || scenarioForm.value.stepMinutes || 5))

const suggestionCount = computed(() => diversionResult.value?.suggestions?.length || 0)

const diversionReason = computed(
  () => diversionResult.value?.reason || '尚未生成分流建议',
)

const selectedMinuteLabel = computed(() => {
  const minute = selectedCompareMinute.value ?? currentMinute.value ?? 0
  return `第 ${minute} 分钟`
})

const generatedAtLabel = computed(() => {
  const raw = recommendation.value?.generatedAt
  if (!raw) return '默认使用高峰快照'
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return String(raw)
  return `推荐更新时间 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
})

const effectiveBaseMetrics = computed(() => baseMetrics.value || baselineRun.value?.metrics || null)
const effectiveCompareMetrics = computed(() => compareMetrics.value || compareRun.value?.metrics || null)

const hasCompletedComparison = computed(
  () =>
    diversionComparisonStatus.value === 'COMPLETED' &&
    strategyComparison.value &&
    compareRun.value?.runId &&
    strategyComparison.value.compareRunId,
)

const comparisonStatusLabel = computed(() => {
  if (hasCompletedComparison.value) return '已完成'
  if (diversionComparisonStatus.value === 'RUNNING') return '运行中'
  if (diversionComparisonStatus.value === 'ERROR') return '失败'
  return '未运行'
})

const comparisonStatusState = computed(() => {
  if (hasCompletedComparison.value) return 'neutral'
  if (diversionComparisonStatus.value === 'ERROR') return 'danger'
  return 'neutral'
})

const comparisonStatusDetail = computed(() => {
  if (hasCompletedComparison.value) return strategyComparison.value?.conclusion || '已生成真实对比结果'
  if (diversionComparisonStatus.value === 'RUNNING') return '正在调用分流建议、compare 仿真与策略对比接口'
  if (diversionComparisonStatus.value === 'ERROR') return comparisonError.value || '分流对比失败'
  return diversionReason.value
})

const baselineSummary = computed(() => {
  if (!baselineRun.value) return '请先保存当前 run'
  const avgWait = baselineRun.value.metrics?.avgWaitMinutes ?? '--'
  const maxQueue = baselineRun.value.metrics?.maxQueueLength ?? '--'
  return `平均等待 ${avgWait} 分钟 / 最大排队 ${maxQueue} 人`
})

const hasAnyComparisonState = computed(
  () =>
    Boolean(
      baselineRun.value ||
        compareRun.value ||
        diversionResult.value ||
        strategyComparison.value ||
        comparisonError.value,
    ),
)

const restaurantLookup = computed(() => {
  const map = new Map()
  ;[currentRun.value, baselineRun.value, compareRun.value].forEach((run) => {
    ;(run?.timePoints || []).forEach((point) => {
      ;(point.restaurants || []).forEach((restaurant) => {
        map.set(restaurant.restaurantId, restaurant.name)
        ;(restaurant.windows || []).forEach((window) => {
          map.set(`window:${window.windowId}`, `${restaurant.name} / ${window.name}`)
        })
      })
    })
  })
  ;(windows.value || []).forEach((window) => {
    if (!map.has(`window:${window.windowId}`)) {
      const restaurantName = map.get(window.restaurantId) || `餐厅 ${window.restaurantId}`
      map.set(`window:${window.windowId}`, `${restaurantName} / ${window.name}`)
    }
  })
  return map
})

onMounted(async () => {
  try {
    await store.initializeDashboard()
    if (!currentRun.value && profiles.value.length && scenarios.value.length) {
      await store.runCurrentSimulation()
    }
    if (currentRun.value && !recommendation.value) {
      await store.refreshRecommendation({ minute: currentMinute.value })
    }
  } catch (error) {
    ElMessage.error(error.message || '推荐页初始化失败')
  }
})

function handleSaveBaseline() {
  store.saveBaselineRun()
}

async function handleRunComparison() {
  try {
    await store.runDiversionComparison()
  } catch (error) {
    ElMessage.error(error.message || '运行分流对比失败')
  }
}

async function handleMinuteChange(minute) {
  store.setSelectedCompareMinute(minute)
  try {
    await store.refreshRecommendation({ minute })
  } catch (error) {
    ElMessage.error(error.message || '刷新推荐失败')
  }
}

function formatMinute(value) {
  return `第 ${value} 分钟`
}

function crowdLabel(level) {
  return (
    {
      IDLE: '空闲',
      NORMAL: '正常',
      BUSY: '繁忙',
      EXTREME: '极拥挤',
    }[level] || level
  )
}

function relatedRestaurantName(restaurantId) {
  return restaurantLookup.value.get(restaurantId) || `餐厅 ${restaurantId}`
}

function relatedWindowName(windowId) {
  return restaurantLookup.value.get(`window:${windowId}`) || `窗口 ${windowId}`
}

function resolveWindowLabel(restaurantId, windowId) {
  return (
    restaurantLookup.value.get(`window:${windowId}`) ||
    `${relatedRestaurantName(restaurantId)} / 窗口 ${windowId}`
  )
}
</script>

<style scoped>
.recommendation-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.recommendation-panel {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid #dbe4ee;
  border-radius: 20px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-head h3 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
}

.panel-head span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.recommendation-list {
  display: grid;
  gap: 12px;
}

.recommendation-item {
  display: grid;
  gap: 8px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
}

.item-head,
.item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.item-head strong {
  color: #0f172a;
  font-size: 15px;
}

.item-rank,
.item-meta {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.recommendation-item p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

@media (max-width: 1200px) {
  .recommendation-grid {
    grid-template-columns: 1fr;
  }
}
</style>
