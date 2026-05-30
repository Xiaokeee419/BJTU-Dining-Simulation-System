<template>
  <section class="panel scenario-panel">
    <div class="panel-header">
      <div>
        <h2 class="panel-title">场景参数</h2>
        <p class="panel-subtitle">这些参数决定 baseline 仿真的场景压力和采样规模。</p>
      </div>
      <el-select
        :model-value="selectedScenarioId"
        size="small"
        class="preset-select"
        @change="$emit('preset-change', $event)"
      >
        <el-option
          v-for="scenario in scenarios"
          :key="scenario.scenarioId"
          :label="scenario.name"
          :value="scenario.scenarioId"
        />
      </el-select>
    </div>

    <div class="panel-body">
      <el-form label-position="top">
        <el-form-item label="就餐时段">
          <el-segmented
            :model-value="modelValue.mealPeriod"
            :options="mealOptions"
            @update:model-value="updateField('mealPeriod', $event)"
          />
        </el-form-item>

        <el-form-item label="日期类型">
          <el-segmented
            :model-value="modelValue.dayType"
            :options="dayOptions"
            @update:model-value="updateField('dayType', $event)"
          />
        </el-form-item>

        <el-form-item label="拥挤等级">
          <el-segmented
            :model-value="modelValue.crowdLevel"
            :options="crowdOptions"
            @update:model-value="updateField('crowdLevel', $event)"
          />
          <p class="field-help">{{ demandHint }}</p>
        </el-form-item>

        <el-form-item label="虚拟就餐人数">
          <el-slider
            :model-value="modelValue.virtualUserCount"
            :min="300"
            :max="1800"
            :step="50"
            show-input
            @update:model-value="updateField('virtualUserCount', $event)"
          />
        </el-form-item>

        <el-form-item label="天气因子">
          <el-input-number
            :model-value="modelValue.weatherFactor"
            :min="0.6"
            :max="1.6"
            :step="0.05"
            controls-position="right"
            @update:model-value="updateField('weatherFactor', $event)"
          />
        </el-form-item>

        <el-form-item label="活动因子">
          <el-input-number
            :model-value="modelValue.eventFactor"
            :min="0.6"
            :max="1.6"
            :step="0.05"
            controls-position="right"
            @update:model-value="updateField('eventFactor', $event)"
          />
          <p class="field-help">{{ factorHint }}</p>
        </el-form-item>

        <el-form-item label="关闭窗口">
          <el-select
            :model-value="modelValue.closedWindowIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            @update:model-value="updateField('closedWindowIds', $event)"
          >
            <el-option
              v-for="window in windows"
              :key="window.windowId"
              :label="windowOptionLabel(window)"
              :value="window.windowId"
            />
          </el-select>
        </el-form-item>

        <div class="time-grid">
          <el-form-item label="仿真时长">
            <el-select
              :model-value="modelValue.durationMinutes"
              @update:model-value="updateDurationMinutes"
            >
              <el-option v-for="item in durationOptions" :key="item" :label="`${item} 分钟`" :value="item" />
            </el-select>
          </el-form-item>

          <el-form-item label="时间粒度">
            <el-select
              :model-value="modelValue.stepMinutes"
              @update:model-value="updateStepMinutes"
            >
              <el-option v-for="item in stepOptions" :key="item" :label="`${item} 分钟`" :value="item" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  scenarios: {
    type: Array,
    default: () => [],
  },
  windows: {
    type: Array,
    default: () => [],
  },
  selectedScenarioId: {
    type: String,
    default: '',
  },
  modelValue: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['update:modelValue', 'preset-change'])

const mealOptions = [
  { label: '早餐', value: 'BREAKFAST' },
  { label: '午餐', value: 'LUNCH' },
  { label: '晚餐', value: 'DINNER' },
]

const dayOptions = [
  { label: '工作日', value: 'WEEKDAY' },
  { label: '周末', value: 'WEEKEND' },
]

const crowdOptions = [
  { label: '空闲', value: 'IDLE' },
  { label: '正常', value: 'NORMAL' },
  { label: '繁忙', value: 'BUSY' },
  { label: '极端拥挤', value: 'EXTREME' },
]

const durationOptions = [60, 90, 120, 150]
const stepOptions = [5, 10, 15]

const demandHint = computed(() => {
  const crowdLevel = props.modelValue.crowdLevel
  const dinerCount = Number(props.modelValue.virtualUserCount || 0)
  if (crowdLevel === 'EXTREME' && dinerCount < 800) {
    return '当前设为极端拥挤，但虚拟就餐人数偏低，可能不容易形成明显排队压力。'
  }
  if (crowdLevel === 'IDLE' && dinerCount > 700) {
    return '当前虚拟就餐人数较高，建议将拥挤等级调到正常或繁忙以保持场景一致。'
  }
  return '拥挤等级会影响到达集中程度，虚拟就餐人数决定本轮仿真的样本规模。'
})

const factorHint = computed(() => {
  const pressure = Number(props.modelValue.weatherFactor || 1) * Number(props.modelValue.eventFactor || 1)
  return `1.0 表示常态客流；当前天气与活动因子的综合压力约为 ${pressure.toFixed(2)} 倍。`
})

function updateField(key, value) {
  emit('update:modelValue', {
    ...props.modelValue,
    [key]: value,
  })
}

function updateDurationMinutes(value) {
  const durationMinutes = normalizeDuration(Number(value || 60), Number(props.modelValue.stepMinutes || 5))
  emit('update:modelValue', {
    ...props.modelValue,
    durationMinutes,
  })
}

function updateStepMinutes(value) {
  const stepMinutes = Number(value || 5)
  emit('update:modelValue', {
    ...props.modelValue,
    stepMinutes,
    durationMinutes: normalizeDuration(Number(props.modelValue.durationMinutes || 60), stepMinutes),
  })
}

function normalizeDuration(durationMinutes, stepMinutes) {
  const safeStep = Math.max(1, stepMinutes)
  const rounded = Math.max(safeStep, Math.round(durationMinutes / safeStep) * safeStep)
  return rounded
}

function windowOptionLabel(window) {
  const restaurantText = window.restaurantName || `餐厅 ${window.restaurantId}`
  return `${restaurantText} / ${window.name}`
}
</script>

<style scoped>
.preset-select {
  width: 190px;
}

.time-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field-help {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 760px) {
  .time-grid {
    grid-template-columns: 1fr;
  }
}
</style>
