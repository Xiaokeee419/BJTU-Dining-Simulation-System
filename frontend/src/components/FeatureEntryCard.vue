<template>
  <button type="button" class="feature-card" @click="$emit('open')">
    <div class="feature-icon" :class="`tone-${tone}`">
      <component :is="icon" />
    </div>
    <strong>{{ title }}</strong>
    <p>{{ description }}</p>
    <ArrowRight class="feature-arrow" />
  </button>
</template>

<script setup>
import { ArrowRight } from '@element-plus/icons-vue'

defineProps({
  icon: {
    type: [Object, Function],
    required: true,
  },
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    default: '',
  },
  tone: {
    type: String,
    default: 'primary',
  },
})

defineEmits(['open'])
</script>

<style scoped>
.feature-card {
  display: grid;
  grid-template-rows: auto auto 1fr auto;
  gap: 12px;
  min-height: 176px;
  padding: 18px;
  border: 1px solid rgb(226 232 240 / 90%);
  border-radius: 20px;
  text-align: left;
  cursor: pointer;
  background: linear-gradient(180deg, #ffffff 0%, #fafcff 100%);
  box-shadow: 0 18px 45px rgb(15 23 42 / 7%);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.feature-card:hover {
  border-color: rgb(37 99 235 / 24%);
  box-shadow: 0 22px 48px rgb(37 99 235 / 10%);
  transform: translateY(-3px);
}

.feature-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 18px;
  color: #2563eb;
  background: linear-gradient(135deg, rgb(37 99 235 / 12%), rgb(20 184 166 / 12%));
  font-size: 28px;
}

.feature-icon.tone-teal {
  color: #0f9488;
  background: linear-gradient(135deg, rgb(20 184 166 / 14%), rgb(37 99 235 / 10%));
}

.feature-icon.tone-cyan {
  color: #0891b2;
  background: linear-gradient(135deg, rgb(8 145 178 / 12%), rgb(37 99 235 / 10%));
}

.feature-card strong {
  color: #0f172a;
  font-size: 18px;
  line-height: 1.2;
}

.feature-card p {
  margin: 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.feature-arrow {
  justify-self: end;
  width: 20px;
  height: 20px;
  color: #2563eb;
}
</style>
