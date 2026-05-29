<template>
  <SectionCard title="偏好标签分布" subtitle="根据当前画像和仿真快照汇总展示人群偏好标签占比。">
    <div class="tag-panel">
      <div class="chip-grid">
        <TagChip
          v-for="item in tags"
          :key="item.key"
          :label="item.key"
          :count="item.count"
          :tone="toneMap[item.key] || 'default'"
        />
      </div>

      <div class="tag-chart-grid">
        <div ref="chartRef" class="chart-surface"></div>

        <div class="ratio-list">
          <div v-for="item in tags" :key="item.key" class="ratio-row">
            <div class="ratio-meta">
              <span>{{ item.key }}</span>
              <strong>{{ toPercent(item.ratio) }}</strong>
            </div>
            <el-progress
              :percentage="Math.round(item.ratio * 100)"
              :show-text="false"
              :stroke-width="10"
              :color="item.color"
            />
          </div>
        </div>
      </div>
    </div>
  </SectionCard>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import SectionCard from './SectionCard.vue'
import TagChip from './TagChip.vue'

const props = defineProps({
  tags: {
    type: Array,
    default: () => [],
  },
})

const chartRef = ref(null)
let chart

const toneMap = {
  辣: 'red',
  清淡: 'green',
  米饭: 'blue',
  面食: 'purple',
  减脂: 'cyan',
  高蛋白: 'orange',
  素食: 'lime',
  快餐: 'blue',
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
})

watch(
  () => props.tags,
  () => nextTick(renderChart),
  { deep: true },
)

function renderChart() {
  if (!chartRef.value || !props.tags.length) {
    chart?.dispose()
    chart = null
    return
  }

  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  chart.setOption({
    tooltip: { trigger: 'item' },
    series: [
      {
        name: '标签占比',
        type: 'pie',
        radius: ['58%', '78%'],
        center: ['50%', '50%'],
        label: { show: false },
        data: props.tags.map((item) => ({
          value: item.count,
          name: item.key,
          itemStyle: { color: item.color },
        })),
      },
    ],
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '42%',
        style: {
          text: String(props.tags.reduce((sum, item) => sum + item.count, 0)),
          fill: '#0f172a',
          fontSize: 30,
          fontWeight: 800,
        },
      },
      {
        type: 'text',
        left: 'center',
        top: '55%',
        style: {
          text: '标签总数',
          fill: '#64748b',
          fontSize: 13,
          fontWeight: 700,
        },
      },
    ],
  })
}

function resizeChart() {
  chart?.resize()
}

function toPercent(value) {
  return `${Math.round(Number(value || 0) * 1000) / 10}%`
}
</script>

<style scoped>
.tag-panel {
  display: grid;
  gap: 22px;
}

.chip-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tag-chart-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  align-items: center;
}

.ratio-list {
  display: grid;
  gap: 12px;
}

.ratio-row {
  display: grid;
  gap: 6px;
}

.ratio-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ratio-meta span {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.ratio-meta strong {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

@media (max-width: 900px) {
  .tag-chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
