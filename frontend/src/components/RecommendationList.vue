<template>
  <div class="recommendation-list">
    <article
      v-for="item in items"
      :key="`${item.targetType}-${item.targetId}`"
      class="recommendation-item"
    >
      <div class="rank">#{{ item.rank }}</div>
      <div class="recommendation-main">
        <div class="item-title">
          <strong>{{ item.name }}</strong>
          <el-tag :type="tagType(item.crowdLevel)" size="small">
            {{ crowdLabel(item.crowdLevel) }}
          </el-tag>
        </div>
        <p>{{ item.reason }}</p>
        <div class="score-row">
          <span>匹配得分</span>
          <el-progress
            :percentage="normalizedScore(item.score)"
            :stroke-width="8"
            :show-text="false"
          />
          <strong>{{ normalizedScore(item.score) }}</strong>
        </div>
        <div class="item-meta">
          <span>{{ targetTypeLabel(item.targetType) }}</span>
          <span class="wait-chip" :class="waitLevel(item.estimatedWaitMinutes)">
            等待 {{ item.estimatedWaitMinutes }} 分钟
          </span>
        </div>
      </div>
    </article>
  </div>
</template>

<script setup>
defineProps({
  items: {
    type: Array,
    default: () => [],
  },
})

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

function tagType(level) {
  return (
    {
      IDLE: 'success',
      NORMAL: 'primary',
      BUSY: 'warning',
      EXTREME: 'danger',
    }[level] || 'info'
  )
}

function normalizedScore(score) {
  return Math.max(0, Math.min(100, Math.round(Number(score || 0))))
}

function waitLevel(minutes) {
  const value = Number(minutes || 0)
  if (value < 5) return 'good'
  if (value < 10) return 'normal'
  if (value < 20) return 'warn'
  return 'danger'
}

function targetTypeLabel(type) {
  return (
    {
      RESTAURANT: '餐厅推荐',
      WINDOW: '窗口推荐',
      DISH: '菜品推荐',
    }[type] || '推荐项'
  )
}
</script>

<style scoped>
.recommendation-list {
  display: grid;
  gap: 10px;
}

.recommendation-item {
  display: grid;
  grid-template-columns: 42px 1fr;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  box-shadow: 0 8px 18px rgb(23 32 51 / 5%);
}

.rank {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  color: #ffffff;
  background: #4a1020;
  font-weight: 800;
}

.item-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}

.item-title strong {
  color: #172033;
}

.recommendation-main p {
  margin: 0 0 8px;
  color: #4b5565;
  line-height: 1.55;
}

.score-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.score-row span,
.score-row strong {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #64748b;
  font-size: 13px;
}

.wait-chip {
  padding: 2px 8px;
  border-radius: 999px;
  background: #eef2f7;
  color: #475569;
  font-weight: 700;
}

.wait-chip.good {
  background: #dcfce7;
  color: #166534;
}

.wait-chip.warn {
  background: #fef3c7;
  color: #92400e;
}

.wait-chip.danger {
  background: #fee2e2;
  color: #991b1b;
}
</style>
