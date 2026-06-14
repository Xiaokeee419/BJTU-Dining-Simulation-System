<template>
  <article :class="['flow-metric-card', tone ? `tone-${tone}` : '']">
    <span>{{ label }}</span>
    <strong>{{ displayValue }}</strong>
    <small v-if="detail">{{ detail }}</small>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: null },
  unit: { type: String, default: '' },
  detail: { type: String, default: '' },
  tone: { type: String, default: '' },
})

const displayValue = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return '--'
  return `${props.value}${props.unit ? ` ${props.unit}` : ''}`
})
</script>
