<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="not-found">
    <div class="not-found-card">
      <div class="lost-sun">
        <span class="lost-eye left"></span>
        <span class="lost-eye right"></span>
        <span class="lost-mouth"></span>
      </div>
      <p class="lost-code">404</p>
      <h1>这片云里没有页面</h1>
      <p class="lost-text">可能是风把它吹走了，也可能链接写错了一个字母。</p>
      <div class="lost-actions">
        <RouterLink to="/" class="btn btn-primary">回到首页</RouterLink>
        <RouterLink to="/posts" class="btn btn-ghost">去读文章</RouterLink>
      </div>
      <div class="lost-clouds" aria-hidden="true">
        <span>☁</span>
        <span>☁</span>
        <span>☁</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background:
    radial-gradient(circle at 20% 20%, rgba(169, 216, 245, 0.35), transparent 34%),
    radial-gradient(circle at 80% 30%, rgba(249, 198, 213, 0.3), transparent 32%),
    var(--bg);
}

.not-found-card {
  position: relative;
  max-width: 560px;
  width: 100%;
  padding: 54px 36px;
  border-radius: 34px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-lg);
  text-align: center;
  overflow: hidden;
}

.lost-sun {
  position: relative;
  width: 120px;
  height: 120px;
  margin: 0 auto 18px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #fff4c8, #ffe08a 60%, #ffd178);
  box-shadow: 0 0 60px rgba(255, 210, 120, 0.4);
  animation: float-y 7s ease-in-out infinite;
}

.lost-sun::before {
  content: "";
  position: absolute;
  inset: -18px;
  border-radius: 50%;
  border: 2px dashed rgba(246, 201, 107, 0.55);
}

.lost-eye {
  position: absolute;
  top: 48px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #8b734c;
}
.lost-eye.left {
  left: 40px;
}
.lost-eye.right {
  right: 40px;
}
.lost-mouth {
  position: absolute;
  left: 50%;
  top: 68px;
  width: 24px;
  height: 10px;
  transform: translateX(-50%);
  border-top: 3px solid #8b734c;
  border-radius: 50%;
}

.lost-code {
  color: rgba(238, 155, 182, 0.55);
  font-family: var(--font-display);
  font-size: 4.2rem;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 0.06em;
}

.not-found-card h1 {
  margin-top: 8px;
  font-size: 1.65rem;
}

.lost-text {
  margin: 10px 0 24px;
  color: var(--ink-soft);
  font-size: 0.88rem;
}

.lost-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
}

.lost-clouds {
  position: absolute;
  inset: 0;
  pointer-events: none;
  color: rgba(169, 216, 245, 0.35);
  font-size: 1.6rem;
}
.lost-clouds span:nth-child(1) {
  position: absolute;
  top: 60px;
  left: 30px;
}
.lost-clouds span:nth-child(2) {
  position: absolute;
  bottom: 50px;
  right: 40px;
  font-size: 2rem;
}
.lost-clouds span:nth-child(3) {
  position: absolute;
  bottom: 100px;
  left: 50px;
  font-size: 1rem;
}
</style>
