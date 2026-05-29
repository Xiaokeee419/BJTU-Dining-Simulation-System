<template>
  <main class="page-shell page-stack">
    <section class="panel recommendation-hero">
      <div class="hero-copy">
        <p class="eyebrow">推荐结果</p>
        <h1>仿真推荐结果</h1>
        <p class="page-description">
          基于仿真分析，本系统为当前场景生成优化建议，帮助提升就餐体验与运营效率。
        </p>
      </div>
      <div class="hero-scene"></div>
    </section>

    <PageHeader
      title="当前分钟推荐与方案评估"
      description="保留原有推荐接口与场景对比能力，并将结果组织为更适合展示和决策的页面结构。"
    >
      <template #actions>
        <el-button :disabled="!currentRun" @click="store.setBaseRun">保存 baseline</el-button>
        <el-button
          type="success"
          :disabled="!baseRun"
          :loading="comparing"
          @click="handleCompare"
        >
          运行对比
        </el-button>
      </template>
    </PageHeader>

    <EmptyState
      v-if="!currentRun || !insights"
      title="请先运行仿真"
      description="推荐结果页需要依赖仿真 Run 和推荐接口返回值。完成仿真后，这里会展示建议方案、效果预估和基准对比。"
    />

    <template v-else>
      <section class="card-grid-3">
        <StatCard
          :icon="Grid"
          label="推荐开放窗口数"
          :value="insights.topCards[0].value"
          :unit="insights.topCards[0].unit"
          caption="建议"
          :delta="insights.topCards[0].note"
          delta-state="neutral"
        />
        <StatCard
          :icon="Clock"
          label="预计高峰时段"
          :value="insights.topCards[1].value"
          :unit="insights.topCards[1].unit"
          caption="建议"
          :delta="insights.topCards[1].note"
          delta-state="neutral"
          tone="teal"
        />
        <StatCard
          :icon="TrendCharts"
          label="预期优化效果"
          :value="insights.topCards[2].value"
          unit=""
          caption="综合"
          :delta="insights.topCards[2].note"
          delta-state="positive"
          tone="green"
        />
      </section>

      <section class="panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">推荐时间点</h2>
            <p class="panel-subtitle">拖动时间轴可以刷新第 {{ currentMinute }} 分钟的推荐结果。</p>
          </div>
        </div>
        <div class="panel-body">
          <el-slider
            :model-value="currentMinute"
            :min="0"
            :max="maxMinute"
            :step="timeStep"
            :format-tooltip="formatMinute"
            @change="handleMinuteChange"
          />
        </div>
      </section>

      <section class="section-grid">
        <section class="plan-zone panel">
          <div class="panel-header">
            <div>
              <h2 class="panel-title">推荐方案</h2>
              <p class="panel-subtitle">系统基于当前结果给出的四项优先建议。</p>
            </div>
          </div>
          <div class="panel-body recommendation-list">
            <RecommendationItemCard
              v-for="item in insights.planItems"
              :key="item.rank"
              :rank="item.rank"
              :title="item.title"
              :description="item.description"
              :priority="item.priority"
            />
          </div>
        </section>

        <section class="effect-zone panel">
          <div class="panel-header">
            <div>
              <h2 class="panel-title">方案效果预估</h2>
              <p class="panel-subtitle">对比当前方案与推荐方案的关键指标变化。</p>
            </div>
          </div>
          <div class="panel-body effect-table">
            <div class="effect-head">
              <span>指标</span>
              <span>当前方案</span>
              <span>推荐方案</span>
              <span>变化</span>
            </div>
            <div v-for="row in insights.effectRows" :key="row.label" class="effect-row">
              <div class="metric-name">
                <strong>{{ row.label }}</strong>
                <span>{{ row.unit }}</span>
              </div>
              <div class="metric-bar">
                <div class="bar current" :style="{ width: barWidth(row.current, row.recommended) }"></div>
                <strong>{{ row.current }}</strong>
              </div>
              <div class="metric-bar">
                <div class="bar target" :style="{ width: barWidth(row.recommended, row.current) }"></div>
                <strong>{{ row.recommended }}</strong>
              </div>
              <div class="metric-delta" :class="deltaClass(row.current, row.recommended, row.label)">
                {{ metricDelta(row.current, row.recommended, row.label) }}
              </div>
            </div>
          </div>
        </section>

        <section class="priority-zone page-stack">
          <section class="panel">
            <div class="panel-header">
              <div>
                <h2 class="panel-title">执行优先级</h2>
                <p class="panel-subtitle">建议优先处理能快速缓解排队的策略。</p>
              </div>
            </div>
            <div class="panel-body priority-list">
              <div v-for="item in insights.priorities" :key="item.title" class="priority-row">
                <span class="priority-badge" :class="item.priority">{{ item.priority === 'high' ? '高优先级' : '中优先级' }}</span>
                <strong>{{ item.title }}</strong>
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="panel-header">
              <div>
                <h2 class="panel-title">操作建议</h2>
                <p class="panel-subtitle">可以保存当前方案，或直接把建议带回下一轮仿真。</p>
              </div>
            </div>
            <div class="panel-body action-box">
              <p v-for="item in insights.suggestions" :key="item">{{ item }}</p>
              <div class="action-row">
                <el-button @click="store.saveCurrentScheme">保存推荐方案</el-button>
                <el-button type="primary" @click="applyNextRound">应用到下一轮仿真</el-button>
              </div>
            </div>
          </section>
        </section>
      </section>

      <section class="section-grid">
        <div class="raw-recommendation">
          <RecommendationPanel :recommendation="recommendation" />
        </div>
        <div class="raw-compare">
          <ComparePanel
            :base-run="baseRun"
            :compare-run="compareRun"
            :comparison="comparison"
            @clear="store.clearComparison"
          />
        </div>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { Clock, Grid, TrendCharts } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ComparePanel from '../components/ComparePanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import RecommendationItemCard from '../components/RecommendationItemCard.vue'
import RecommendationPanel from '../components/RecommendationPanel.vue'
import StatCard from '../components/StatCard.vue'
import { useSimulationStore } from '../stores/simulationStore'
import { buildRecommendationInsights } from '../utils/recommendationInsights'

const store = useSimulationStore()
const {
  currentRun,
  baseRun,
  compareRun,
  comparison,
  recommendation,
  currentMinute,
  maxMinute,
  scenarioForm,
  strategyForm,
  windows,
  profiles,
  scenarios,
  comparing,
} = storeToRefs(store)

const insights = computed(() =>
  buildRecommendationInsights({
    currentRun: currentRun.value,
    baseRun: baseRun.value,
    compareRun: compareRun.value,
    comparison: comparison.value,
    recommendation: recommendation.value,
    windows: windows.value,
  }),
)
const timeStep = computed(() => Number(currentRun.value?.scenario?.stepMinutes || scenarioForm.value.stepMinutes || 5))

onMounted(async () => {
  try {
    await store.initializeDashboard()
    if (!currentRun.value && profiles.value.length && scenarios.value.length) {
      await store.runCurrentSimulation()
    }
    if (currentRun.value && !recommendation.value) {
      await store.refreshRecommendation()
    }
  } catch (error) {
    ElMessage.error(error.message || '推荐页初始化失败')
  }
})

async function handleCompare() {
  try {
    await store.runCompareSimulation()
  } catch (error) {
    ElMessage.error(error.message || '运行对比失败')
  }
}

async function handleMinuteChange(minute) {
  store.setCurrentMinute(minute)
  try {
    await store.refreshRecommendation()
  } catch (error) {
    ElMessage.error(error.message || '刷新推荐失败')
  }
}

function applyNextRound() {
  strategyForm.value.peakDiversion = 'ENABLED'
  scenarioForm.value.closedWindowIds = []
  ElMessage.success('已将建议带回参数配置，可继续运行下一轮仿真')
}

function formatMinute(value) {
  return `第 ${value} 分钟`
}

function barWidth(value, fallback) {
  const max = Math.max(Number(value || 0), Number(fallback || 0), 1)
  return `${Math.round((Number(value || 0) / max) * 100)}%`
}

function metricDelta(current, recommended, label) {
  const delta = Number(recommended || 0) - Number(current || 0)
  const inverseMetric = label.includes('排队') || label.includes('拥堵')
  const normalized = inverseMetric ? -delta : delta
  if (normalized > 0) return `+${Math.round(normalized * 10) / 10}`
  return `${Math.round(normalized * 10) / 10}`
}

function deltaClass(current, recommended, label) {
  const delta = Number(recommended || 0) - Number(current || 0)
  const inverseMetric = label.includes('排队') || label.includes('拥堵')
  const normalized = inverseMetric ? -delta : delta
  if (normalized > 0) return 'good'
  if (normalized < 0) return 'bad'
  return ''
}
</script>

<style scoped>
.recommendation-hero {
  display: grid;
  grid-template-columns: 1fr 0.9fr;
  min-height: 220px;
  overflow: hidden;
}

.hero-copy {
  padding: 32px 36px;
}

.hero-copy h1 {
  margin: 0;
  color: #14316f;
  font-size: 32px;
}

.hero-scene {
  min-height: 220px;
  background:
    linear-gradient(90deg, rgb(255 255 255 / 8%) 0%, rgb(255 255 255 / 0%) 20%, rgb(255 255 255 / 0%) 100%),
    url("../assets/hero.png") center / cover no-repeat,
    linear-gradient(180deg, #e8f0ff 0%, #f8fbff 100%);
}

.plan-zone {
  grid-column: span 4;
}

.effect-zone {
  grid-column: span 5;
}

.priority-zone {
  grid-column: span 3;
}

.recommendation-list {
  display: grid;
  gap: 14px;
}

.effect-table {
  display: grid;
  gap: 14px;
}

.effect-head,
.effect-row {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr 0.9fr 0.6fr;
  gap: 14px;
  align-items: center;
}

.effect-head {
  padding-bottom: 10px;
  border-bottom: 1px solid #e5e7eb;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.effect-row {
  padding-bottom: 10px;
  border-bottom: 1px solid #f1f5f9;
}

.metric-name strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
}

.metric-name span {
  color: #64748b;
  font-size: 12px;
}

.metric-bar {
  display: grid;
  gap: 8px;
}

.bar {
  height: 14px;
  border-radius: 999px;
}

.bar.current {
  background: linear-gradient(90deg, #cbd5e1 0%, #94a3b8 100%);
}

.bar.target {
  background: linear-gradient(90deg, #60a5fa 0%, #2563eb 100%);
}

.metric-bar strong {
  color: #334155;
  font-size: 13px;
}

.metric-delta {
  font-size: 14px;
  font-weight: 800;
}

.metric-delta.good {
  color: #16a34a;
}

.metric-delta.bad {
  color: #ef4444;
}

.priority-list {
  display: grid;
  gap: 10px;
}

.priority-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
}

.priority-badge {
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.priority-badge.high {
  color: #dc2626;
  background: #fef2f2;
}

.priority-badge.medium {
  color: #ea580c;
  background: #fff7ed;
}

.priority-row strong {
  color: #0f172a;
  font-size: 14px;
}

.action-box {
  display: grid;
  gap: 12px;
}

.action-box p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
}

.raw-recommendation {
  grid-column: span 7;
}

.raw-compare {
  grid-column: span 5;
}

@media (max-width: 1100px) {
  .recommendation-hero {
    grid-template-columns: 1fr;
  }

  .plan-zone,
  .effect-zone,
  .priority-zone,
  .raw-recommendation,
  .raw-compare {
    grid-column: auto;
  }

  .effect-head,
  .effect-row {
    grid-template-columns: 1fr;
  }
}
</style>
