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
        <div class="item-meta">
          <span>得分 {{ item.score }}</span>
          <span>等待 {{ item.estimatedWaitMinutes }} 分钟</span>
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

.item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #64748b;
  font-size: 13px;
}
</style>
