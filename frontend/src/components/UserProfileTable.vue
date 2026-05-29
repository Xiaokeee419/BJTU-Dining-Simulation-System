<template>
  <SectionCard title="当前人群画像" subtitle="如果后端没有人级数据，这里展示当前人群的代表性样本。">
    <div class="profile-table">
      <div class="table-head">
        <span>用户</span>
        <span>标签</span>
        <span>所在区域</span>
        <span>停留时长</span>
      </div>

      <div v-for="item in items" :key="item.id" class="table-row">
        <div class="person">
          <div class="avatar">{{ item.name.slice(-1) }}</div>
          <div>
            <strong>{{ item.name }}</strong>
            <p>{{ item.id }}</p>
          </div>
        </div>
        <div class="tags">
          <span v-for="tag in item.tags" :key="tag">{{ tag }}</span>
        </div>
        <div class="cell">{{ item.area }}</div>
        <div class="cell">{{ item.stayTime }} 分钟</div>
      </div>
    </div>
  </SectionCard>
</template>

<script setup>
import SectionCard from './SectionCard.vue'

defineProps({
  items: {
    type: Array,
    default: () => [],
  },
})
</script>

<style scoped>
.profile-table {
  display: grid;
  gap: 12px;
}

.table-head,
.table-row {
  display: grid;
  grid-template-columns: 1.3fr 1fr 1fr 0.8fr;
  gap: 14px;
  align-items: center;
}

.table-head {
  padding: 0 0 8px;
  border-bottom: 1px solid #e5e7eb;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.table-row {
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
}

.person {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  color: #2563eb;
  background: #e0ecff;
  font-weight: 800;
}

.person strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
}

.person p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tags span {
  padding: 4px 8px;
  border-radius: 999px;
  color: #2563eb;
  background: #eff6ff;
  font-size: 12px;
  font-weight: 700;
}

.cell {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

@media (max-width: 900px) {
  .table-head {
    display: none;
  }

  .table-row {
    grid-template-columns: 1fr;
  }
}
</style>
