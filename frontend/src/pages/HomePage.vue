<template>
  <main class="home-page">
    <section class="hero-card">
      <div class="hero-copy">
        <p class="hero-kicker">v0.4 Development Track</p>
        <h1>把项目主链收束为 数据真源、仿真拥挤、分流参数、优化过程。</h1>
        <p class="hero-text">
          当前版本把旧的个性化推荐页降到次要位置，重点展示人流曲线、分钟级拥挤指标、参数化分流策略和模拟退火优化过程。
        </p>
        <RouterLink to="/workspace" class="hero-link">进入工作台</RouterLink>
      </div>
      <div class="hero-grid">
        <article class="hero-metric">
          <span>主链</span>
          <strong>Data → Simulation → Diversion → Optimization</strong>
        </article>
        <article class="hero-metric">
          <span>优化方式</span>
          <strong>Random Search + Simulated Annealing</strong>
        </article>
        <article class="hero-metric">
          <span>可视化重点</span>
          <strong>Arrival Curve / Minute Metrics / Iteration History</strong>
        </article>
      </div>
    </section>
  </main>
</template>

<script setup>
import { RouterLink } from 'vue-router'
</script>
