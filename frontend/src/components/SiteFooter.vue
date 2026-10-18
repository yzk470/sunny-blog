<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useSiteStore } from '@/stores/site'

const site = useSiteStore()
const profile = computed(() => site.profile)
const year = new Date().getFullYear()
</script>

<template>
  <footer class="site-footer">
    <div class="footer-wave" aria-hidden="true"></div>
    <div class="container footer-inner">
      <div class="footer-brand">
        <div class="footer-logo">
          <span class="footer-sun">☀</span>
          <span>月白小筑</span>
        </div>
        <p class="footer-slogan">
          {{ profile?.footerText || '愿你的每一天，都有阳光落在肩上。' }}
        </p>
        <div class="footer-stamp">
          <span>EST. 2025</span>
          <span class="stamp-dot"></span>
          <span>MADE WITH SUNSHINE</span>
        </div>
      </div>

      <div class="footer-column">
        <h4>逛一逛</h4>
        <RouterLink to="/posts">所有文章</RouterLink>
        <RouterLink to="/archives">时光归档</RouterLink>
        <RouterLink to="/digital-human">3D 数字人</RouterLink>
        <RouterLink to="/guestbook">留言板</RouterLink>
        <RouterLink to="/about">关于站长</RouterLink>
      </div>

      <div class="footer-column">
        <h4>找到我</h4>
        <a v-if="profile?.email" :href="`mailto:${profile.email}`">✉ {{ profile.email }}</a>
        <a v-if="profile?.github" :href="profile.github" target="_blank" rel="noopener noreferrer">
          ◇ GitHub
        </a>
        <span v-if="profile?.location">⌖ {{ profile.location }}</span>
        <RouterLink to="/admin">✎ 后台入口</RouterLink>
      </div>

      <div class="footer-note">
        <div class="note-card">
          <span class="note-sun">☀</span>
          <p>今天也要记得，<br />给自己留一点晒太阳的时间。</p>
        </div>
      </div>
    </div>

    <div class="container footer-bottom">
      <span>© {{ year }} 月白小筑 · 保留所有温柔</span>
      <span class="footer-tech">Vue 3 + TypeScript · Spring Boot + MyBatis-Plus · Redis</span>
    </div>
  </footer>
</template>

<style scoped>
.site-footer {
  position: relative;
  margin-top: 90px;
  padding-top: 26px;
  background: linear-gradient(180deg, rgba(255, 250, 240, 0.2), rgba(255, 247, 236, 0.9));
  border-top: 1px solid rgba(105, 135, 160, 0.08);
  overflow: hidden;
}

.footer-wave {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 120px;
  pointer-events: none;
  background:
    radial-gradient(ellipse at 15% 0%, rgba(169, 216, 245, 0.18), transparent 55%),
    radial-gradient(ellipse at 86% 8%, rgba(249, 198, 213, 0.2), transparent 52%);
}

.footer-inner {
  position: relative;
  display: grid;
  grid-template-columns: 1.6fr 0.8fr 0.9fr 1.1fr;
  gap: 32px;
  padding-top: 34px;
  padding-bottom: 38px;
}

.footer-brand {
  max-width: 300px;
}

.footer-logo {
  display: flex;
  align-items: center;
  gap: 9px;
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 900;
  letter-spacing: 0.05em;
}

.footer-sun {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffe9a8, #ffcd73);
  color: #a37a2d;
  font-size: 1rem;
  box-shadow: 0 8px 18px rgba(246, 201, 107, 0.3);
}

.footer-slogan {
  margin-top: 12px;
  color: var(--ink-soft);
  font-size: 0.86rem;
  line-height: 1.8;
}

.footer-stamp {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  color: #b1b7c1;
  font-size: 0.62rem;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.stamp-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #cbd1d9;
}

.footer-column {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding-top: 5px;
}

.footer-column h4 {
  margin-bottom: 5px;
  font-size: 0.8rem;
  font-weight: 900;
  letter-spacing: 0.1em;
  color: var(--ink);
}

.footer-column a,
.footer-column span {
  color: var(--ink-soft);
  font-size: 0.82rem;
  transition: color 0.2s ease, transform 0.2s ease;
  width: fit-content;
}

.footer-column a:hover {
  color: #4e86a7;
  transform: translateX(2px);
}

.footer-note {
  display: flex;
  justify-content: flex-end;
}

.note-card {
  position: relative;
  max-width: 230px;
  padding: 20px 20px 18px;
  border-radius: 22px;
  background: linear-gradient(145deg, #fffdf7, #fff1f5);
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-sm);
}

.note-card p {
  color: var(--ink-soft);
  font-size: 0.82rem;
  line-height: 1.9;
  font-weight: 600;
}

.note-sun {
  position: absolute;
  top: -13px;
  right: 16px;
  font-size: 1.4rem;
  filter: drop-shadow(0 6px 8px rgba(246, 201, 107, 0.35));
}

.footer-bottom {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding-top: 17px;
  padding-bottom: 22px;
  border-top: 1px dashed rgba(105, 135, 160, 0.16);
  color: #a7aeb9;
  font-size: 0.7rem;
}

.footer-tech {
  letter-spacing: 0.04em;
}

@media (max-width: 980px) {
  .footer-inner {
    grid-template-columns: 1.4fr 1fr 1fr;
  }
  .footer-note {
    display: none;
  }
}

@media (max-width: 620px) {
  .site-footer {
    margin-top: 60px;
  }
  .footer-inner {
    grid-template-columns: 1fr 1fr;
    gap: 26px;
  }
  .footer-brand {
    grid-column: 1 / -1;
    max-width: none;
  }
  .footer-bottom {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }
}
</style>
