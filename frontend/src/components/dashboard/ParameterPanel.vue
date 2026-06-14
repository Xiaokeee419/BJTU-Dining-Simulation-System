<template>
  <section :class="['flow-panel', 'parameter-panel', tone ? `tone-${tone}` : '']">
    <h3>{{ title }}</h3>
    <dl v-if="parameters">
      <div v-for="field in parameterFields" :key="field.key">
        <dt>{{ field.label }}</dt>
        <dd>{{ formatNumber(parameters[field.key], field.digits) }}</dd>
      </div>
    </dl>
    <p v-else class="flow-empty-copy">{{ emptyText }}</p>
  </section>
</template>

<script setup>
import { formatNumber } from '../../utils/dashboardMetrics'

defineProps({
  title: { type: String, required: true },
  parameters: { type: Object, default: null },
  emptyText: { type: String, default: '暂无参数' },
  tone: { type: String, default: '' },
})

const parameterFields = [
  { key: 'sourcePressureScale', label: '源压力阈值倍率', digits: 3 },
  { key: 'targetPressureBufferScale', label: '目标压力缓冲倍率', digits: 3 },
  { key: 'transferScale', label: '转移规模倍率', digits: 3 },
  { key: 'maxTransferCount', label: '单条最大转移人数', digits: 0 },
  { key: 'acceptanceBias', label: '接受率偏置', digits: 3 },
  { key: 'waitReductionWeight', label: '等待改善权重', digits: 3 },
  { key: 'pressureWaitWeight', label: '压力等待权重', digits: 3 },
  { key: 'pressureQueueWeight', label: '压力队列权重', digits: 3 },
  { key: 'crossRestaurantPenalty', label: '跨餐厅惩罚', digits: 2 },
]
</script>
