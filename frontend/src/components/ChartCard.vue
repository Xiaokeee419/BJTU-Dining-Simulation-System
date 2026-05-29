<template>
  <SectionCard :title="title" :subtitle="subtitle">
    <template #extra>
      <slot name="extra" />
    </template>
    <EmptyState
      v-if="empty"
      :title="emptyTitle"
      :description="emptyDescription"
    />
    <slot v-else />
  </SectionCard>
</template>

<script setup>
import EmptyState from './EmptyState.vue'
import SectionCard from './SectionCard.vue'

defineProps({
  title: {
    type: String,
    required: true,
  },
  subtitle: {
    type: String,
    default: '',
  },
  empty: {
    type: Boolean,
    default: false,
  },
  emptyTitle: {
    type: String,
    default: '暂无数据',
  },
  emptyDescription: {
    type: String,
    default: '请先运行仿真后再查看此区域。',
  },
})
</script>
