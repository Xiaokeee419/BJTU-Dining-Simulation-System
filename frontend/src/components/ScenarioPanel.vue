<template>
  <section class="panel scenario-panel">
    <div class="panel-header">
      <h2 class="panel-title">场景参数</h2>
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
        </el-form-item>
        <el-form-item label="仿真人数">
          <el-slider
            :model-value="modelValue.virtualUserCount"
            :min="100"
            :max="600"
            :step="20"
            show-input
            @update:model-value="updateField('virtualUserCount', $event)"
          />
        </el-form-item>
        <el-form-item label="影响系数">
          <div class="factor-row">
            <span>天气</span>
            <el-input-number
              :model-value="modelValue.weatherFactor"
              :min="0.6"
              :max="1.6"
              :step="0.05"
              controls-position="right"
              @update:model-value="updateField('weatherFactor', $event)"
            />
          </div>
          <div class="factor-row">
            <span>活动</span>
            <el-input-number
              :model-value="modelValue.eventFactor"
              :min="0.6"
              :max="1.6"
              :step="0.05"
              controls-position="right"
              @update:model-value="updateField('eventFactor', $event)"
            />
          </div>
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
              :label="window.name"
              :value="window.windowId"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>
  </section>
</template>

<script setup>
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
  { label: '极拥挤', value: 'EXTREME' },
]

function updateField(key, value) {
  emit('update:modelValue', {
    ...props.modelValue,
    [key]: value,
  })
}
</script>

<style scoped>
.preset-select {
  width: 190px;
}

.factor-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
  margin-bottom: 10px;
}

.factor-row:last-child {
  margin-bottom: 0;
}
</style>
