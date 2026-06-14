<template>
  <article class="metric-card" :class="`metric-card-${tone}`">
    <span class="metric-card-label">{{ label }}</span>
    <div class="metric-card-main">
      <strong>{{ value }}</strong>
      <span v-if="unit">{{ unit }}</span>
    </div>
    <span v-if="status" class="metric-card-status" :class="`status-${statusTone}`">
      {{ status }}
    </span>
  </article>
</template>

<script setup>
defineProps({
  label: {
    type: String,
    required: true,
  },
  value: {
    type: [String, Number],
    required: true,
  },
  unit: {
    type: String,
    default: '',
  },
  status: {
    type: String,
    default: '',
  },
  tone: {
    type: String,
    default: 'primary',
  },
  statusTone: {
    type: String,
    default: 'neutral',
  },
})
</script>

<style scoped>
.metric-card {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 108px;
  align-content: center;
  gap: 8px;
  padding: 18px 20px;
  border: 1px solid var(--color-outline);
  border-radius: var(--radius-card);
  background: #fff;
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.metric-card::after {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 34%;
  height: 3px;
  background: var(--color-secondary);
  content: "";
}

.metric-card-label {
  color: var(--color-on-surface-variant);
  font-size: 13px;
  font-weight: 700;
}

.metric-card-main {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.metric-card-main strong {
  color: var(--color-secondary);
  font-family: var(--font-data);
  font-size: clamp(28px, 2.15vw, 34px);
  font-weight: 650;
  line-height: 1;
  letter-spacing: -0.035em;
}

.metric-card-main span {
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 700;
}

.metric-card-status {
  position: absolute;
  right: 16px;
  bottom: 20px;
  font-size: 12px;
  font-weight: 750;
}

.status-success {
  color: var(--color-success);
}

.status-warning {
  color: var(--color-warning-strong);
}

.status-danger {
  color: var(--color-danger);
}

.status-neutral {
  color: var(--color-subtle);
}

.metric-card-danger::after {
  background: var(--color-danger);
}

.metric-card-warning::after {
  background: var(--color-warning);
}

.metric-card-success::after {
  background: var(--color-success);
}

@media (max-width: 1180px) {
  .metric-card-status {
    position: static;
  }
}
</style>
