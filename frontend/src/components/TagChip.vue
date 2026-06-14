<template>
  <span class="tag-chip" :class="`tone-${tone}`">
    <span>{{ label }}</span>
    <strong>{{ count }}</strong>
  </span>
</template>

<script setup>
defineProps({
  label: {
    type: String,
    required: true,
  },
  count: {
    type: [String, Number],
    default: '',
  },
  tone: {
    type: String,
    default: 'default',
  },
})
</script>

<style scoped>
.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  color: #334155;
  background: #ffffff;
  font-size: 14px;
  font-weight: 700;
}

.tag-chip strong {
  color: inherit;
}

.tone-red {
  border-color: #fecaca;
  color: #dc2626;
  background: #fef2f2;
}

.tone-green {
  border-color: #bbf7d0;
  color: #16a34a;
  background: #f0fdf4;
}

.tone-blue {
  border-color: #bfdbfe;
  color: #2563eb;
  background: #eff6ff;
}

.tone-purple {
  border-color: #ddd6fe;
  color: #7c3aed;
  background: #f5f3ff;
}

.tone-cyan {
  border-color: #bae6fd;
  color: #0891b2;
  background: #ecfeff;
}

.tone-orange {
  border-color: #fed7aa;
  color: #ea580c;
  background: #fff7ed;
}

.tone-lime {
  border-color: #d9f99d;
  color: #65a30d;
  background: #f7fee7;
}
</style>
