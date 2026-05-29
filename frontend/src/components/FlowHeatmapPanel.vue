<template>
  <SectionCard title="食堂区域人流热力图" subtitle="颜色越深，表示该区域当前人流越密集。">
    <div class="heatmap-shell">
      <div class="floorplan">
        <div
          v-for="zone in zones"
          :key="zone.key"
          class="zone"
          :class="zone.density"
          :style="zoneStyles[zone.key]"
        >
          <span>{{ zone.label }}</span>
          <strong>{{ zone.count }} 人</strong>
        </div>
      </div>

      <div class="legend">
        <span>人流密度：</span>
        <div class="legend-bar"></div>
        <strong>低</strong>
        <strong>中</strong>
        <strong>高</strong>
      </div>
    </div>
  </SectionCard>
</template>

<script setup>
import SectionCard from './SectionCard.vue'

defineProps({
  zones: {
    type: Array,
    default: () => [],
  },
})

const zoneStyles = {
  entrance: { left: '4%', top: '12%', width: '18%', height: '62%' },
  ordering: { left: '26%', top: '10%', width: '40%', height: '36%' },
  diningA: { left: '69%', top: '10%', width: '25%', height: '36%' },
  diningB: { left: '26%', top: '52%', width: '45%', height: '22%' },
  recycle: { left: '74%', top: '52%', width: '20%', height: '22%' },
}
</script>

<style scoped>
.heatmap-shell {
  display: grid;
  gap: 18px;
}

.floorplan {
  position: relative;
  min-height: 420px;
  border: 2px solid #cfd9e8;
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgb(239 246 255 / 55%) 0%, rgb(255 255 255 / 92%) 100%),
    repeating-linear-gradient(0deg, transparent, transparent 32px, rgb(219 234 254 / 20%) 32px, rgb(219 234 254 / 20%) 33px),
    repeating-linear-gradient(90deg, transparent, transparent 32px, rgb(219 234 254 / 20%) 32px, rgb(219 234 254 / 20%) 33px);
  overflow: hidden;
}

.zone {
  position: absolute;
  display: grid;
  place-items: center;
  gap: 10px;
  border: 1px solid rgb(148 163 184 / 36%);
  border-radius: 22px;
  color: #0f172a;
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 35%);
}

.zone span {
  font-size: 14px;
  font-weight: 700;
}

.zone strong {
  font-size: 18px;
}

.zone.low {
  background: rgb(191 219 254 / 45%);
}

.zone.medium {
  background: rgb(190 242 100 / 42%);
}

.zone.high {
  background: linear-gradient(135deg, rgb(254 215 170 / 75%) 0%, rgb(252 165 165 / 66%) 100%);
}

.legend {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.legend-bar {
  width: 240px;
  height: 14px;
  border-radius: 999px;
  background: linear-gradient(90deg, #93c5fd 0%, #fef3c7 52%, #fb7185 100%);
}

@media (max-width: 900px) {
  .floorplan {
    min-height: 360px;
  }

  .legend {
    flex-wrap: wrap;
  }
}
</style>
