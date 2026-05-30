<template>
  <section class="panel profile-panel">
    <div class="panel-header">
      <div>
        <h2 class="panel-title">主导人群画像</h2>
        <p class="panel-subtitle">这些参数会直接作为 baseline 仿真的主导人群画像输入。</p>
      </div>
      <el-select
        :model-value="selectedProfileId"
        size="small"
        class="preset-select"
        @change="$emit('preset-change', $event)"
      >
        <el-option
          v-for="profile in profiles"
          :key="profile.profileId"
          :label="profile.name"
          :value="profile.profileId"
        />
      </el-select>
    </div>

    <div class="panel-body">
      <el-form label-position="top">
        <el-form-item label="用户类型">
          <el-segmented
            :model-value="modelValue.userType"
            :options="userTypeOptions"
            @update:model-value="updateField('userType', $event)"
          />
        </el-form-item>

        <el-form-item label="口味偏好">
          <el-select
            :model-value="modelValue.tasteTags"
            multiple
            collapse-tags
            collapse-tags-tooltip
            @update:model-value="updateField('tasteTags', $event)"
          >
            <el-option v-for="tag in tasteOptions" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>

        <el-form-item label="预算范围">
          <el-slider
            :model-value="[modelValue.budgetMin, modelValue.budgetMax]"
            range
            :min="6"
            :max="35"
            :step="1"
            @update:model-value="updateBudget"
          />
          <div class="field-meta">{{ modelValue.budgetMin }} - {{ modelValue.budgetMax }} 元</div>
        </el-form-item>

        <el-form-item label="等待容忍时间">
          <div class="input-row">
            <el-input-number
              :model-value="modelValue.waitingToleranceMinutes"
              :min="3"
              :max="30"
              controls-position="right"
              @update:model-value="updateField('waitingToleranceMinutes', $event)"
            />
            <span class="unit">分钟</span>
          </div>
        </el-form-item>
      </el-form>
    </div>
  </section>
</template>

<script setup>
const props = defineProps({
  profiles: {
    type: Array,
    default: () => [],
  },
  selectedProfileId: {
    type: String,
    default: '',
  },
  modelValue: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['update:modelValue', 'preset-change'])

const userTypeOptions = [
  { label: '学生', value: 'STUDENT' },
  { label: '赶时间', value: 'HURRY' },
  { label: '预算敏感', value: 'BUDGET_SENSITIVE' },
]

const tasteOptions = ['偏辣', '清淡', '米饭', '面食', '套餐', '轻食', '清真']

function updateField(key, value) {
  emit('update:modelValue', {
    ...props.modelValue,
    [key]: value,
  })
}

function updateBudget(value) {
  emit('update:modelValue', {
    ...props.modelValue,
    budgetMin: value[0],
    budgetMax: value[1],
  })
}
</script>

<style scoped>
.preset-select {
  width: 180px;
}

.input-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-meta {
  width: 100%;
  margin-top: 4px;
  color: #657084;
  font-size: 13px;
  font-weight: 650;
}

.unit {
  color: #657084;
  font-size: 13px;
  font-weight: 650;
}
</style>
