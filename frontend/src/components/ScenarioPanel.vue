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
          <p class="field-help" :class="demandHint.level">
            {{ demandHint.text }}
          </p>
        </el-form-item>
        <el-form-item>
          <template #label>
            <div class="field-label">
              <span>参与仿真的就餐人数</span>
              <el-tooltip
                content="表示当前场景时间窗口内进入仿真的就餐样本量，不是全校总人数。"
                placement="top"
              >
                <span class="label-tip">?</span>
              </el-tooltip>
            </div>
          </template>
          <el-slider
            :model-value="modelValue.virtualUserCount"
            :min="100"
            :max="2000"
            :step="50"
            show-input
            @update:model-value="updateField('virtualUserCount', $event)"
          />
          <p class="field-help">
            建议按单个就餐时段估算需求规模：早餐 200-400，晚餐 500-700，午餐高峰 800-1200。
          </p>
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
          <p class="field-help">
            {{ factorHint }}
          </p>
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
          <p class="field-help">
            关闭窗口会直接影响服务能力，适合模拟窗口停业、设备故障或临时调度不足的情况。
          </p>
        </el-form-item>
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
  { label: '极拥挤', value: 'EXTREME' },
]

const demandHint = computed(() => {
  const count = Number(props.modelValue.virtualUserCount || 0)
  const crowdLevel = props.modelValue.crowdLevel
  const mealPeriod = props.modelValue.mealPeriod

  if (crowdLevel === 'EXTREME' && count < 1000) {
    return {
      level: 'warning',
      text: '当前选择了极拥挤，但仿真人数偏低，结果可能体现不出明显排队压力。',
    }
  }
  if (crowdLevel === 'IDLE' && count > 900) {
    return {
      level: 'warning',
      text: '当前人数较高但拥挤等级为空闲，建议调低人数或改为正常/繁忙以保持场景一致。',
    }
  }
  if (mealPeriod === 'LUNCH' && count < 600) {
    return {
      level: 'notice',
      text: '午餐高峰通常建议使用更高样本量，便于观察排队和推荐差异。',
    }
  }
  return {
    level: 'notice',
    text: '拥挤等级会影响用户到达集中程度，仿真人数决定本次场景的样本规模。',
  }
})

const factorHint = computed(() => {
  const weather = Number(props.modelValue.weatherFactor || 1)
  const event = Number(props.modelValue.eventFactor || 1)
  const pressure = Math.round(weather * event * 100) / 100
  return `1.0 表示正常客流；大于 1 表示客流放大，小于 1 表示客流减弱。当前综合压力约为 ${pressure} 倍。`
})

function updateField(key, value) {
  emit('update:modelValue', {
    ...props.modelValue,
    [key]: value,
  })
}

function windowOptionLabel(window) {
  const restaurantText = window.restaurantName || `餐厅 ${window.restaurantId}`
  return `${restaurantText} · ${window.name}`
}
</script>

<style scoped>
.preset-select {
  width: 190px;
}

.field-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.label-tip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #eef2f7;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  cursor: help;
}

.field-help {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.field-help.warning {
  color: #b45309;
}

.field-help.notice {
  color: #64748b;
}

.factor-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
  margin-bottom: 10px;
  padding: 8px 10px;
  border: 1px solid #e8edf4;
  border-radius: 8px;
  background: #fbfcfe;
}

.factor-row span {
  color: #4b5565;
  font-size: 13px;
  font-weight: 700;
}

.factor-row:last-child {
  margin-bottom: 0;
}
</style>
