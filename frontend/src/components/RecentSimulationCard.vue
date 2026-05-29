<template>
  <section class="recent-card">
    <div class="recent-head">
      <Clock class="head-icon" />
      <h3>最近一次仿真</h3>
    </div>

    <div v-if="runMeta" class="recent-body">
      <span class="status-pill">仿真完成</span>
      <dl>
        <div>
          <dt>仿真方案</dt>
          <dd>{{ runMeta.scenarioName || `Run ${runMeta.runId}` }}</dd>
        </div>
        <div>
          <dt>仿真时间</dt>
          <dd>{{ formatDate(runMeta.createdAt) }}</dd>
        </div>
        <div>
          <dt>仿真时长</dt>
          <dd>{{ runMeta.durationMinutes || '--' }} 分钟</dd>
        </div>
        <div>
          <dt>平均等待</dt>
          <dd>{{ runMeta.avgWaitMinutes ?? '--' }} 分钟</dd>
        </div>
      </dl>
      <el-button type="primary" @click="$emit('open')">
        继续查看
      </el-button>
    </div>

    <EmptyState
      v-else
      title="尚无仿真记录"
      description="运行一次仿真后，最近结果会显示在这里。"
    />
  </section>
</template>

<script setup>
import { Clock } from '@element-plus/icons-vue'
import EmptyState from './EmptyState.vue'

defineProps({
  runMeta: {
    type: Object,
    default: null,
  },
})

defineEmits(['open'])

function formatDate(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.recent-card {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 12px;
  height: 100%;
}

.recent-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.recent-head h3 {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
}

.head-icon {
  width: 22px;
  height: 22px;
  color: #2563eb;
}

.recent-body {
  display: grid;
  align-content: start;
  gap: 14px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  padding: 8px 12px;
  border: 1px solid #dcfce7;
  border-radius: 12px;
  color: #15803d;
  background: #f0fdf4;
  font-size: 13px;
  font-weight: 700;
}

.status-pill::before {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: currentColor;
  content: "";
}

dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

dl div {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid rgb(226 232 240 / 90%);
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

dt {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

dd {
  margin: 0;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

:deep(.el-button) {
  width: 100%;
  min-height: 42px;
  margin-top: 2px;
}

:deep(.empty-state) {
  min-height: 140px;
  padding: 8px 0 0;
}

@media (max-width: 760px) {
  dl {
    grid-template-columns: 1fr;
  }
}
</style>
