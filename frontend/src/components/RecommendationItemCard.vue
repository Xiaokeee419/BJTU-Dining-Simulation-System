<template>
  <article class="recommendation-card">
    <div class="recommendation-rank">{{ rank }}</div>
    <div class="recommendation-main">
      <div class="recommendation-head">
        <strong>{{ title }}</strong>
        <span class="priority" :class="priorityClass">{{ priorityLabel }}</span>
      </div>
      <p>{{ description }}</p>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  rank: {
    type: [String, Number],
    default: 1,
  },
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    default: '',
  },
  priority: {
    type: String,
    default: 'medium',
  },
})

const priorityLabel = computed(() =>
  ({
    high: '高优先级',
    medium: '中优先级',
    low: '低优先级',
  }[props.priority] || '中优先级'),
)
const priorityClass = computed(() => `priority-${props.priority}`)
</script>

<style scoped>
.recommendation-card {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 16px;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.recommendation-rank {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 18px;
  color: #ffffff;
  background: linear-gradient(180deg, #2563eb 0%, #1d4ed8 100%);
  font-size: 24px;
  font-weight: 800;
}

.recommendation-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.recommendation-head strong {
  color: #0f172a;
  font-size: 16px;
}

.priority {
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.priority-high {
  color: #dc2626;
  background: #fef2f2;
}

.priority-medium {
  color: #ea580c;
  background: #fff7ed;
}

.priority-low {
  color: #0284c7;
  background: #eff6ff;
}

.recommendation-main p {
  margin: 10px 0 0;
  color: #475569;
  line-height: 1.7;
}
</style>
