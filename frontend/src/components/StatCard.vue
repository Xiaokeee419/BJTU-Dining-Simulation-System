<template>
  <article class="stat-card" :class="`tone-${tone}`">
    <div class="stat-icon">
      <component :is="icon" />
    </div>
    <div class="stat-main">
      <span class="stat-label">{{ label }}</span>
      <div class="stat-value">
        <strong>{{ formattedValue }}</strong>
        <span v-if="unit">{{ unit }}</span>
      </div>
      <div class="stat-foot">
        <span>{{ caption }}</span>
        <em v-if="delta" :class="deltaClass">{{ delta }}</em>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  icon: {
    type: [Object, Function],
    required: true,
  },
  label: {
    type: String,
    required: true,
  },
  value: {
    type: [String, Number],
    default: null,
  },
  unit: {
    type: String,
    default: '',
  },
  caption: {
    type: String,
    default: '当前',
  },
  delta: {
    type: String,
    default: '',
  },
  deltaState: {
    type: String,
    default: 'neutral',
  },
  tone: {
    type: String,
    default: 'primary',
  },
})

const formattedValue = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return '--'
  if (typeof props.value === 'number') {
    return Number.isInteger(props.value) ? props.value.toLocaleString('en-US') : props.value
  }
  return props.value
})
const deltaClass = computed(() => `delta-${props.deltaState}`)
</script>

<style scoped>
.stat-card {
  display: grid;
  grid-template-columns: 58px 1fr;
  align-items: center;
  gap: 14px;
  min-height: 110px;
  padding: 18px 20px;
  border: 1px solid rgb(226 232 240 / 90%);
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 18px 45px rgb(15 23 42 / 7%);
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  color: #2563eb;
  background: linear-gradient(135deg, rgb(37 99 235 / 12%), rgb(37 99 235 / 18%));
  font-size: 24px;
}

.tone-teal .stat-icon {
  color: #0f9488;
  background: linear-gradient(135deg, rgb(20 184 166 / 12%), rgb(20 184 166 / 18%));
}

.tone-orange .stat-icon {
  color: #f97316;
  background: linear-gradient(135deg, rgb(249 115 22 / 12%), rgb(251 146 60 / 18%));
}

.tone-green .stat-icon {
  color: #16a34a;
  background: linear-gradient(135deg, rgb(22 163 74 / 12%), rgb(74 222 128 / 18%));
}

.stat-label {
  display: block;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.stat-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 6px;
}

.stat-value strong {
  color: #2563eb;
  font-size: 30px;
  line-height: 1.1;
}

.stat-value span {
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.stat-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  color: #64748b;
  font-size: 12px;
}

.stat-foot em {
  font-style: normal;
  font-weight: 800;
}

.delta-positive {
  color: #f97316;
}

.delta-negative {
  color: #16a34a;
}

.delta-neutral {
  color: #64748b;
}

.delta-danger {
  color: #ef4444;
}
</style>
