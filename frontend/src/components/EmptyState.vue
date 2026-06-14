<template>
  <div class="empty-state">
    <el-empty :image-size="84" :description="title" />
    <p v-if="description">{{ description }}</p>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: '暂无数据',
  },
  description: {
    type: String,
    default: '',
  },
})
</script>

<style scoped>
.empty-state {
  display: grid;
  justify-items: center;
  gap: 10px;
  min-height: 240px;
  padding: 22px 0;
}

.empty-state p {
  max-width: 420px;
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
  text-align: center;
}
</style>
