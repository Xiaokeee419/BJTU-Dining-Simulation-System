<template>
  <section class="panel queue-panel">
    <div class="panel-header">
      <h2 class="panel-title">排队状态</h2>
      <span class="muted">第 {{ timePoint?.minute ?? 0 }} 分钟</span>
    </div>
    <div class="panel-body queue-body">
      <div class="restaurant-pane">
        <div class="subhead">
          <span>餐厅列表</span>
          <span class="muted">先选择餐厅，再查看窗口</span>
        </div>
        <div class="restaurant-list">
          <button
            v-for="restaurant in restaurants"
            :key="restaurant.restaurantId"
            type="button"
            class="restaurant-row"
            :class="{ active: restaurant.restaurantId === selectedRestaurant?.restaurantId }"
            @click="selectedRestaurantId = restaurant.restaurantId"
          >
            <div class="restaurant-main">
              <div>
                <strong>{{ restaurant.name }}</strong>
                <span class="muted">{{ restaurant.currentCount }} / {{ restaurant.capacity }} 人</span>
              </div>
              <el-tag
                class="density-tag"
                :type="tagType(restaurant.crowdLevel)"
                effect="light"
              >
                {{ crowdLabel(restaurant.crowdLevel) }}
              </el-tag>
            </div>
            <el-progress
              :percentage="capacityRate(restaurant)"
              :stroke-width="8"
              :show-text="false"
            />
            <div class="restaurant-foot">
              <span>{{ restaurant.windows.length }} 个窗口</span>
              <span>{{ openWindowCount(restaurant) }} 个开放</span>
            </div>
          </button>
        </div>
      </div>

      <div class="window-pane">
        <template v-if="selectedRestaurant">
          <div class="subhead">
            <div>
              <strong class="detail-title">{{ selectedRestaurant.name }}</strong>
              <span class="muted">
                当前人数 {{ selectedRestaurant.currentCount }} / {{ selectedRestaurant.capacity }}
              </span>
            </div>
            <el-tag
              class="density-tag"
              :type="tagType(selectedRestaurant.crowdLevel)"
              effect="light"
            >
              {{ crowdLabel(selectedRestaurant.crowdLevel) }}
            </el-tag>
          </div>

          <div class="summary-strip">
            <div class="summary-item">
              <span>开放窗口</span>
              <strong>{{ openWindowCount(selectedRestaurant) }}</strong>
            </div>
            <div class="summary-item">
              <span>平均等待</span>
              <strong>{{ averageWaitMinutes(selectedRestaurant) }} 分</strong>
            </div>
            <div class="summary-item">
              <span>最大排队</span>
              <strong>{{ maxQueueLength(selectedRestaurant) }} 人</strong>
            </div>
            <div class="summary-item">
              <span>容量占比</span>
              <strong>{{ capacityRate(selectedRestaurant) }}%</strong>
            </div>
          </div>

          <el-table
            :data="selectedRestaurant.windows"
            size="small"
            height="320"
            class="window-table"
          >
            <el-table-column prop="name" label="窗口" min-width="120" />
            <el-table-column prop="queueLength" label="队列" width="78" />
            <el-table-column prop="servingCount" label="服务中" width="84" />
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
        </template>
        <el-empty v-else :image-size="72" description="暂无餐厅数据" />
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  timePoint: {
    type: Object,
    default: null,
  },
})

const selectedRestaurantId = ref(null)

const restaurants = computed(() => props.timePoint?.restaurants || [])
const selectedRestaurant = computed(
  () =>
    restaurants.value.find((restaurant) => restaurant.restaurantId === selectedRestaurantId.value) ||
    restaurants.value[0] ||
    null,
)

watch(
  restaurants,
  (list) => {
    if (!list.length) {
      selectedRestaurantId.value = null
      return
    }
    const exists = list.some((restaurant) => restaurant.restaurantId === selectedRestaurantId.value)
    if (!exists) {
      selectedRestaurantId.value = list[0].restaurantId
    }
  },
  { immediate: true, deep: true },
)

function capacityRate(restaurant) {
  if (!restaurant?.capacity) return 0
  return Math.min(100, Math.round((restaurant.currentCount / restaurant.capacity) * 100))
}

function openWindowCount(restaurant) {
  return restaurant?.windows?.filter((window) => window.status !== 'CLOSED').length || 0
}

function averageWaitMinutes(restaurant) {
  const windows = restaurant?.windows?.filter((window) => window.status !== 'CLOSED') || []
  if (!windows.length) return 0
  const total = windows.reduce((sum, window) => sum + window.waitMinutes, 0)
  return Math.round((total / windows.length) * 10) / 10
}

function maxQueueLength(restaurant) {
  const queues = restaurant?.windows?.map((window) => window.queueLength) || []
  return queues.length ? Math.max(...queues) : 0
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
  grid-template-columns: minmax(280px, 0.9fr) minmax(480px, 1.35fr);
  gap: 16px;
}

.restaurant-pane,
.window-pane {
  min-width: 0;
}

.subhead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.restaurant-list {
  display: grid;
  gap: 10px;
  max-height: 420px;
  overflow: auto;
  padding-right: 4px;
}

.restaurant-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  width: 100%;
  padding: 13px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
  background: #fff;
}

.restaurant-row:hover {
  border-color: #cbd5e1;
  box-shadow: 0 10px 20px rgb(23 32 51 / 6%);
}

.restaurant-row.active {
  border-color: #9f1239;
  background:
    linear-gradient(180deg, #fff8fb 0%, #ffffff 100%);
  box-shadow: 0 0 0 1px rgb(159 18 57 / 12%);
}

.restaurant-row strong {
  display: block;
  margin-bottom: 4px;
  color: #172033;
}

.restaurant-main {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 10px;
}

.restaurant-foot {
  display: flex;
  justify-content: space-between;
  color: #657084;
  font-size: 12px;
  font-weight: 700;
}

.detail-title {
  display: block;
  margin-bottom: 4px;
  font-size: 16px;
  color: #172033;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.summary-item {
  padding: 12px 10px;
  border: 1px solid #e8edf4;
  border-radius: 8px;
  background: #f8fafc;
}

.summary-item span {
  display: block;
  margin-bottom: 6px;
  color: #657084;
  font-size: 12px;
  font-weight: 700;
}

.summary-item strong {
  color: #172033;
  font-size: 18px;
}

.window-table {
  width: 100%;
  border: 1px solid #e8edf4;
  border-radius: 8px;
  overflow: hidden;
}

@media (max-width: 900px) {
  .queue-body {
    grid-template-columns: 1fr;
  }

  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .subhead {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-strip {
    grid-template-columns: 1fr;
  }
}
</style>
