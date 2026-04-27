<template>
  <section class="panel queue-panel">
    <div class="panel-header">
      <h2 class="panel-title">排队状态</h2>
      <span class="muted">第 {{ timePoint?.minute ?? 0 }} 分钟</span>
    </div>
    <div class="panel-body queue-body">
      <div class="restaurant-list">
        <div
          v-for="restaurant in timePoint?.restaurants || []"
          :key="restaurant.restaurantId"
          class="restaurant-row"
        >
          <div>
            <strong>{{ restaurant.name }}</strong>
            <span class="muted">{{ restaurant.currentCount }} / {{ restaurant.capacity }} 人</span>
          </div>
          <div class="restaurant-status">
            <el-progress
              :percentage="capacityRate(restaurant)"
              :stroke-width="8"
              :show-text="false"
            />
            <el-tag
              class="density-tag"
              :type="tagType(restaurant.crowdLevel)"
              effect="light"
            >
              {{ crowdLabel(restaurant.crowdLevel) }}
            </el-tag>
          </div>
        </div>
      </div>

      <el-table :data="windowRows" size="small" height="300" class="window-table">
        <el-table-column prop="restaurantName" label="餐厅" min-width="90" />
        <el-table-column prop="name" label="窗口" min-width="120" />
        <el-table-column prop="queueLength" label="队列" width="78" />
        <el-table-column prop="waitMinutes" label="等待" width="78">
          <template #default="{ row }">{{ row.waitMinutes }} 分</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="86">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CLOSED' ? 'info' : 'success'" effect="plain">
              {{ row.status === 'CLOSED' ? '关闭' : '开放' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="crowdLevel" label="拥挤度" width="92">
          <template #default="{ row }">
            <el-tag :type="tagType(row.crowdLevel)" effect="light">
              {{ crowdLabel(row.crowdLevel) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  timePoint: {
    type: Object,
    default: null,
  },
})

const windowRows = computed(() =>
  (props.timePoint?.restaurants || []).flatMap((restaurant) =>
    restaurant.windows.map((window) => ({
      ...window,
      restaurantName: restaurant.name,
      restaurantId: restaurant.restaurantId,
    })),
  ),
)

function capacityRate(restaurant) {
  if (!restaurant?.capacity) return 0
  return Math.min(100, Math.round((restaurant.currentCount / restaurant.capacity) * 100))
}

function crowdLabel(level) {
  return (
    {
      IDLE: '空闲',
      NORMAL: '正常',
      BUSY: '繁忙',
      EXTREME: '极拥挤',
    }[level] || level
  )
}

function tagType(level) {
  return (
    {
      IDLE: 'success',
      NORMAL: 'primary',
      BUSY: 'warning',
      EXTREME: 'danger',
    }[level] || 'info'
  )
}
</script>

<style scoped>
.queue-body {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(420px, 1.3fr);
  gap: 16px;
}

.restaurant-list {
  display: grid;
  gap: 10px;
}

.restaurant-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e5eaf2;
  border-radius: 8px;
  background: #f8fafc;
}

.restaurant-row strong {
  display: block;
  margin-bottom: 4px;
  color: #0f172a;
}

.restaurant-status {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 10px;
}

.window-table {
  width: 100%;
}

@media (max-width: 900px) {
  .queue-body {
    grid-template-columns: 1fr;
  }
}
</style>
