<template>
  <article class="strategy-card">
    <header class="strategy-header">
      <span class="strategy-icon">策</span>
      <h2>智能分流建议</h2>
    </header>

    <div v-if="available" class="strategy-route">
      <span>当前拥堵源</span>
      <strong>{{ source }}</strong>
      <i>→</i>
      <span>推荐去向</span>
      <strong class="strategy-target">{{ target }}</strong>
    </div>

    <dl v-if="available" class="strategy-facts">
      <div>
        <dt>建议引导人数</dt>
        <dd>{{ suggestedCount }} 人</dd>
      </div>
      <div>
        <dt>预计等待下降</dt>
        <dd>{{ waitReduction == null ? '待验证' : `-${waitReduction} 分钟` }}</dd>
      </div>
      <div>
        <dt>最大队列下降</dt>
        <dd>{{ maxQueueReduction == null ? '待验证' : `-${maxQueueReduction} 人` }}</dd>
      </div>
    </dl>

    <div v-else class="strategy-empty">
      <strong>当前无需分流</strong>
      <span>系统只会在存在明确拥堵且扣除步行成本后仍有收益时生成建议。</span>
    </div>

    <p v-if="reason" class="strategy-reason">{{ reason }}</p>

    <button type="button" class="strategy-button" @click="$emit('open')">
      查看策略验证
    </button>
  </article>
</template>

<script setup>
defineEmits(['open'])

defineProps({
  available: {
    type: Boolean,
    default: true,
  },
  source: {
    type: String,
    default: '',
  },
  target: {
    type: String,
    required: true,
  },
  suggestedCount: {
    type: Number,
    default: 0,
  },
  waitReduction: {
    type: Number,
    default: null,
  },
  maxQueueReduction: {
    type: Number,
    default: null,
  },
  reason: {
    type: String,
    default: '',
  },
})
</script>

<style scoped>
.strategy-card {
  position: relative;
  display: grid;
  min-height: 100%;
  align-content: start;
  gap: 22px;
  padding: 28px;
  border: 1px solid #001d4f;
  border-radius: var(--radius-card);
  color: #fff;
  background:
    radial-gradient(circle at 100% 100%, rgb(113 177 254 / 20%), transparent 32%),
    linear-gradient(145deg, #002b6b 0%, #052f73 60%, #001f52 100%);
  box-shadow: 0 16px 34px rgb(0 43 107 / 18%);
  overflow: hidden;
}

.strategy-card::after {
  position: absolute;
  right: -36px;
  bottom: -48px;
  width: 150px;
  height: 150px;
  border: 20px solid rgb(176 198 255 / 10%);
  border-radius: 50%;
  content: "";
}

.strategy-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.strategy-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid rgb(176 198 255 / 38%);
  border-radius: 50%;
  color: var(--color-secondary-fixed);
  font-size: 13px;
  font-weight: 800;
}

.strategy-header h2 {
  margin: 0;
  font-size: 19px;
}

.strategy-route {
  display: grid;
  gap: 7px;
  padding: 17px;
  border: 1px solid rgb(176 198 255 / 34%);
  border-radius: 6px;
  background: rgb(255 255 255 / 6%);
}

.strategy-route span,
.strategy-facts dt {
  color: #b0c6ff;
  font-size: 11px;
}

.strategy-route strong {
  font-size: 16px;
}

.strategy-route i {
  color: #71b1fe;
  font-size: 18px;
  font-style: normal;
}

.strategy-target {
  color: #8dc1ff;
  font-size: 20px !important;
}

.strategy-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.strategy-facts div {
  min-width: 0;
  padding-right: 10px;
  border-right: 1px solid rgb(176 198 255 / 18%);
}

.strategy-facts div:last-child {
  border-right: 0;
}

.strategy-facts dd {
  margin: 8px 0 0;
  color: #8dc1ff;
  font-family: var(--font-data);
  font-size: 19px;
  font-weight: 650;
}

.strategy-reason {
  margin: 0;
  color: #d9e2ff;
  font-size: 12px;
  line-height: 1.65;
}

.strategy-empty {
  display: grid;
  gap: 10px;
  padding: 22px 18px;
  border: 1px solid rgb(176 198 255 / 30%);
  border-radius: 6px;
  background: rgb(255 255 255 / 6%);
}

.strategy-empty strong {
  color: #8dc1ff;
  font-size: 20px;
}

.strategy-empty span {
  color: #d9e2ff;
  font-size: 13px;
  line-height: 1.65;
}

.strategy-button {
  position: relative;
  z-index: 1;
  min-height: 50px;
  margin-top: auto;
  border: 1px solid #71b1fe;
  border-radius: 4px;
  color: #002b6b;
  background: #71b1fe;
  cursor: pointer;
  font-weight: 800;
  transition:
    background 0.18s ease,
    transform 0.18s ease;
}

.strategy-button:hover {
  background: #a2c9ff;
}

.strategy-button:active {
  transform: translateY(1px);
}

@media (max-width: 520px) {
  .strategy-facts {
    grid-template-columns: 1fr;
  }

  .strategy-facts div {
    padding: 0 0 10px;
    border-right: 0;
    border-bottom: 1px solid rgb(176 198 255 / 18%);
  }
}
</style>
