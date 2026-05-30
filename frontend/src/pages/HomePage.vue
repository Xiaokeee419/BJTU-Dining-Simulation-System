<template>
  <main class="page-shell page-stack">
    <PageHeader
      eyebrow="首页"
      title="食堂仿真与分流决策平台"
      description="围绕未分流 baseline 仿真、分流建议生成和 compare 对比验证，帮助查看高峰排队压力并评估分流策略效果。"
    />

    <section class="card-grid-4">
      <StatCard
        :icon="UserFilled"
        label="高峰负载人数"
        :value="overview.peakLoadCount"
        unit="人"
        :caption="overview.peakCaption"
        :delta="overview.loadNote"
        delta-state="neutral"
      />
      <StatCard
        :icon="Clock"
        label="平均排队时长"
        :value="overview.avgWaitMinutes"
        unit="分钟"
        caption="最近一次未分流仿真"
        :delta="overview.waitNote"
        delta-state="neutral"
        tone="teal"
      />
      <StatCard
        :icon="Tickets"
        label="最大排队人数"
        :value="overview.maxQueueLength"
        unit="人"
        caption="高峰窗口压力"
        :delta="overview.queueNote"
        delta-state="neutral"
        tone="orange"
      />
      <StatCard
        :icon="Grid"
        label="开放窗口数"
        :value="overview.openWindowCount"
        unit="个"
        caption="高峰快照"
        :delta="overview.windowNote"
        delta-state="neutral"
        tone="green"
      />
    </section>

    <section class="section-grid home-content">
      <section class="feature-panel">
        <div class="feature-grid">
          <FeatureEntryCard
            :icon="Setting"
            title="参数配置"
            description="设置主导人群画像和场景参数，运行 baseline 仿真。"
            tone="teal"
            @open="router.push('/config')"
          />
          <FeatureEntryCard
            :icon="UserFilled"
            title="人流快照"
            description="查看高峰分钟的餐厅排队、服务负载和窗口拥挤分布。"
            tone="primary"
            @open="router.push('/flow')"
          />
          <FeatureEntryCard
            :icon="Histogram"
            title="统计分析"
            description="查看 baseline 仿真的整体排队趋势、窗口负载和未服务人数。"
            tone="cyan"
            @open="router.push('/statistics')"
          />
          <FeatureEntryCard
            :icon="DataAnalysis"
            title="分流对比"
            description="生成分流建议，并验证未分流与分流后的真实效果差异。"
            tone="primary"
            @open="router.push('/recommendation')"
          />
        </div>
      </section>

      <section class="recent-panel panel">
        <div class="panel-body">
          <RecentSimulationCard
            :run-meta="lastRunMeta"
            @open="router.push(currentRun?.status === 'FINISHED' ? '/flow' : '/config')"
          />
        </div>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { Clock, DataAnalysis, Grid, Histogram, Setting, Tickets, UserFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import FeatureEntryCard from '../components/FeatureEntryCard.vue'
import PageHeader from '../components/PageHeader.vue'
import RecentSimulationCard from '../components/RecentSimulationCard.vue'
import StatCard from '../components/StatCard.vue'
import { useSimulationStore } from '../stores/simulationStore'
import { openWindowCount, resolvePeakTimePoint, totalCurrentCount } from '../utils/simulationStats'

const router = useRouter()
const store = useSimulationStore()
const { currentRun, lastRunMeta } = storeToRefs(store)

const overview = computed(() => {
  const peakPoint = resolvePeakTimePoint(currentRun.value)
  const peakMinute = peakPoint?.minute ?? lastRunMeta.value?.peakMinute ?? null

  return {
    peakLoadCount: peakPoint ? totalCurrentCount(peakPoint) : lastRunMeta.value?.peakLoadCount ?? '--',
    avgWaitMinutes: currentRun.value?.metrics?.avgWaitMinutes ?? lastRunMeta.value?.avgWaitMinutes ?? '--',
    maxQueueLength: currentRun.value?.metrics?.maxQueueLength ?? lastRunMeta.value?.maxQueueLength ?? '--',
    openWindowCount: peakPoint ? openWindowCount(peakPoint) : lastRunMeta.value?.openWindowCount ?? '--',
    peakCaption: peakMinute != null ? `高峰快照第 ${peakMinute} 分钟` : '请先运行未分流仿真',
    loadNote: currentRun.value?.metrics?.totalVirtualUsers
      ? `仿真样本 ${currentRun.value.metrics.totalVirtualUsers} 人`
      : '',
    waitNote: currentRun.value?.runId ? `Run #${currentRun.value.runId}` : '请先到参数配置页运行仿真',
    queueNote: peakMinute != null ? '优先关注高峰窗口排队压力' : '',
    windowNote: peakMinute != null ? '按高峰快照统计' : '',
  }
})

onMounted(async () => {
  try {
    await store.initializeDashboard()
  } catch (error) {
    ElMessage.error(error.message || '首页初始化失败')
  }
})
</script>

<style scoped>
.home-content {
  align-items: stretch;
}

.feature-panel {
  grid-column: span 8;
  min-width: 0;
}

.recent-panel {
  grid-column: span 4;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.recent-panel :deep(.panel-body) {
  height: 100%;
}

@media (max-width: 1100px) {
  .feature-panel,
  .recent-panel {
    grid-column: auto;
  }
}

@media (max-width: 760px) {
  .feature-grid {
    grid-template-columns: 1fr;
  }
}
</style>
