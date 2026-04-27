<template>
  <section class="panel recommendation-panel">
    <div class="panel-header">
      <h2 class="panel-title">推荐结果</h2>
      <span v-if="recommendation" class="muted">第 {{ recommendation.minute }} 分钟</span>
    </div>
    <div class="panel-body">
      <el-empty
        v-if="!recommendation"
        :image-size="80"
        description="暂无推荐结果"
      />
      <template v-else>
        <el-tabs model-value="restaurants" stretch>
          <el-tab-pane label="餐厅" name="restaurants">
            <RecommendationList :items="recommendation.restaurants" />
          </el-tab-pane>
          <el-tab-pane label="窗口" name="windows">
            <RecommendationList :items="recommendation.windows" />
          </el-tab-pane>
          <el-tab-pane label="菜品" name="dishes">
            <RecommendationList :items="recommendation.dishes" />
          </el-tab-pane>
        </el-tabs>
        <div class="suggestion">
          <span>分流建议</span>
          <p>{{ recommendation.diversionSuggestion }}</p>
        </div>
      </template>
    </div>
  </section>
</template>

<script setup>
import RecommendationList from './RecommendationList.vue'

defineProps({
  recommendation: {
    type: Object,
    default: null,
  },
})
</script>

<style scoped>
.suggestion {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
}

.suggestion span {
  display: block;
  margin-bottom: 6px;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 700;
}

.suggestion p {
  margin: 0;
  color: #1e3a8a;
  line-height: 1.6;
}
</style>
