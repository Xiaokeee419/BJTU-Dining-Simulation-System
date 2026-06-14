<template>
  <section class="playback-bar" aria-label="仿真播放控制">
    <div class="playback-actions">
      <button
        type="button"
        class="playback-primary"
        :disabled="running"
        @click="$emit('primary')"
      >
        {{ primaryLabel }}
      </button>
      <button
        v-if="hasRun"
        type="button"
        class="playback-secondary"
        :disabled="running"
        @click="$emit('restart')"
      >
        从头播放
      </button>
      <button
        v-if="hasRun"
        type="button"
        class="playback-secondary"
        :disabled="running"
        @click="$emit('rerun')"
      >
        重新计算仿真
      </button>
    </div>

    <div class="playback-live">
      <span>
        当前总人流
        <strong>{{ currentFlow }}</strong>
        人
      </span>
      <span>
        当前排队
        <strong>{{ currentQueue }}</strong>
        人
      </span>
    </div>

    <div class="playback-timeline">
      <div class="playback-time">
        <strong>第 {{ minute }} 分钟</strong>
        <span>/ {{ endMinute }} 分钟</span>
      </div>
      <input
        type="range"
        :min="startMinute"
        :max="endMinute"
        step="1"
        :value="minute"
        :disabled="!hasRun || running"
        aria-label="仿真时间轴"
        @input="$emit('seek', Number($event.target.value))"
      />
      <span class="playback-rate">现实 1 秒 = 仿真 {{ rate }} 分钟</span>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  minute: {
    type: Number,
    default: 0,
  },
  startMinute: {
    type: Number,
    default: 0,
  },
  endMinute: {
    type: Number,
    default: 20,
  },
  rate: {
    type: Number,
    default: 2,
  },
  hasRun: {
    type: Boolean,
    default: false,
  },
  isPlaying: {
    type: Boolean,
    default: false,
  },
  running: {
    type: Boolean,
    default: false,
  },
  currentFlow: {
    type: Number,
    default: 0,
  },
  currentQueue: {
    type: Number,
    default: 0,
  },
})

defineEmits(['primary', 'restart', 'rerun', 'seek'])

const primaryLabel = computed(() => {
  if (props.running) return '正在计算仿真'
  if (!props.hasRun) return '开始仿真'
  if (props.isPlaying) return '暂停播放'
  if (props.minute >= props.endMinute) return '重新播放'
  return '继续播放'
})
</script>

<style scoped>
.playback-bar {
  display: grid;
  grid-template-columns: auto auto minmax(320px, 1fr);
  align-items: center;
  gap: 24px;
  padding: 14px 18px;
  border: 1px solid var(--color-outline);
  border-radius: var(--radius-card);
  background: rgb(255 255 255 / 94%);
  box-shadow: var(--shadow-card);
}

.playback-live {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.playback-live span {
  padding: 7px 10px;
  border: 1px solid #d7e4f5;
  border-radius: 4px;
  color: var(--color-on-surface-variant);
  background: #f5f9ff;
  font-size: 11px;
  white-space: nowrap;
}

.playback-live strong {
  margin: 0 3px 0 5px;
  color: var(--color-secondary);
  font-family: var(--font-data);
  font-size: 15px;
}

.playback-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.playback-actions button {
  min-height: 38px;
  padding: 0 16px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 750;
  transition:
    background 0.18s ease,
    border-color 0.18s ease,
    transform 0.18s ease;
}

.playback-actions button:disabled {
  cursor: wait;
  opacity: 0.62;
}

.playback-actions button:active:not(:disabled) {
  transform: translateY(1px);
}

.playback-primary {
  border: 1px solid var(--color-secondary);
  color: #fff;
  background: var(--color-secondary);
}

.playback-primary:hover:not(:disabled) {
  background: #004d89;
}

.playback-secondary {
  border: 1px solid var(--color-outline);
  color: var(--color-primary);
  background: #fff;
}

.playback-secondary:hover:not(:disabled) {
  border-color: var(--color-secondary);
  color: var(--color-secondary);
}

.playback-timeline {
  display: grid;
  grid-template-columns: auto minmax(140px, 1fr) auto;
  align-items: center;
  gap: 14px;
}

.playback-time {
  display: flex;
  align-items: baseline;
  gap: 5px;
  white-space: nowrap;
}

.playback-time strong {
  color: var(--color-primary);
  font-family: var(--font-data);
  font-size: 14px;
}

.playback-time span,
.playback-rate {
  color: var(--color-subtle);
  font-size: 11px;
  white-space: nowrap;
}

.playback-timeline input {
  width: 100%;
  height: 5px;
  margin: 0;
  border-radius: 999px;
  accent-color: var(--color-secondary);
  cursor: pointer;
}

.playback-timeline input:disabled {
  cursor: not-allowed;
}

@media (max-width: 1080px) {
  .playback-bar {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .playback-timeline {
    grid-template-columns: 1fr;
  }

  .playback-rate {
    justify-self: start;
  }
}
</style>
