<template>
  <div v-if="suggestions.length" class="suggestion-list">
    <article
      v-for="item in suggestions"
      :key="`${item.fromWindowId}-${item.toWindowId}`"
      class="suggestion-item"
    >
      <div class="suggestion-head">
        <div class="route-block">
          <strong>{{ resolveLabel(item.fromRestaurantId, item.fromWindowId) }}</strong>
          <span class="route-arrow">→</span>
          <strong>{{ resolveLabel(item.toRestaurantId, item.toWindowId) }}</strong>
        </div>
        <span class="count-pill">建议 {{ item.suggestedUserCount }} 人</span>
      </div>

      <div class="metric-grid">
        <div>
          <span>接受率</span>
          <strong>{{ percent(item.acceptanceRate) }}</strong>
        </div>
        <div>
          <span>预计接受人数</span>
          <strong>{{ item.estimatedAcceptedCount }}</strong>
        </div>
        <div>
          <span>预计等待下降</span>
          <strong>{{ formatValue(item.estimatedWaitReduction) }} 分钟</strong>
        </div>
        <div>
          <span>标签相似度</span>
          <strong>{{ percent(item.tagSimilarity) }}</strong>
        </div>
      </div>

      <p class="reason-text">{{ item.reason || '未提供分流原因' }}</p>
    </article>
  </div>
  <EmptyState
    v-else
    title="当前没有可执行的分流建议"
    :description="reason || '该分钟没有需要分流的拥挤窗口，或没有满足承接条件的目标窗口。'"
  />
</template>

<script setup>
import EmptyState from './EmptyState.vue'

const props = defineProps({
  suggestions: {
    type: Array,
    default: () => [],
  },
  reason: {
    type: String,
    default: '',
  },
  resolveWindowLabel: {
    type: Function,
    default: null,
  },
})

function resolveLabel(restaurantId, windowId) {
  return props.resolveWindowLabel?.(restaurantId, windowId) || `窗口 ${windowId}`
}

function percent(value) {
  return `${Math.round(Number(value || 0) * 100)}%`
}

function formatValue(value) {
  return Math.round(Number(value || 0) * 10) / 10
}
</script>

<style scoped>
.suggestion-list {
  display: grid;
  gap: 14px;
}

.suggestion-item {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid #dbe4ee;
  border-radius: 20px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.suggestion-head,
.route-block {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.suggestion-head {
  justify-content: space-between;
}

.route-block strong {
  color: #0f172a;
  font-size: 15px;
}

.route-arrow {
  color: #2563eb;
  font-size: 18px;
  font-weight: 800;
}

.count-pill {
  padding: 6px 12px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-grid div {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #f8fafc;
}

.metric-grid span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.metric-grid strong {
  color: #0f172a;
  font-size: 18px;
}

.reason-text {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
