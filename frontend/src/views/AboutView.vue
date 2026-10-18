<script setup lang="ts">
import { computed, onMounted } from 'vue'
import PageBanner from '@/components/PageBanner.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import { useSiteStore } from '@/stores/site'
import { formatNumber } from '@/utils/format'

const site = useSiteStore()
const profile = computed(() => site.profile)
const stats = computed(() => site.stats)

const techStack = [
  { name: 'Vue 3', color: '#A8D8C8' },
  { name: 'TypeScript', color: '#A9D8F5' },
  { name: 'Spring Boot', color: '#BFE3D0' },
  { name: 'MyBatis-Plus', color: '#F9C6D5' },
  { name: 'Redis', color: '#F7B7C8' },
  { name: 'MySQL / H2', color: '#FFE3A3' },
]

const aboutText = computed(
  () =>
    profile.value?.aboutContent ||
    `# 嗨，我是${profile.value?.nickname || '月白'} 👋

欢迎来到月白小筑。这里没有宏大的叙事，只有一些认真生活的碎片。

白天写代码，傍晚追晚霞，偶尔在厨房里研究一杯不太成功的拿铁。

希望这个小站，能在某个普通的下午，给你一点点明亮。`,
)

onMounted(() => site.load())
</script>

<template>
  <div class="about-page page-shell">
    <div class="container">
      <PageBanner
        eyebrow="NICE TO MEET YOU"
        title="关于我，也关于这个小站"
        :description="profile?.slogan || '把日子过成一首明亮的诗'"
      >
        <span class="about-badge">☀ 很高兴认识你</span>
      </PageBanner>

      <div class="about-grid">
        <aside class="about-side">
          <div class="card about-profile">
            <div class="profile-avatar-wrap">
              <div class="avatar-halo"></div>
              <img :src="profile?.avatar || '/images/avatar-yuebai.png'" alt="站长头像" />
              <span class="avatar-spark">✦</span>
            </div>
            <p class="profile-hello">HELLO, I AM</p>
            <h2>{{ profile?.nickname || '月白' }}</h2>
            <p class="profile-role">前端工程师 · 生活记录者</p>
            <p class="profile-bio">
              {{ profile?.bio || '白天写代码，傍晚追晚霞。在代码与云朵之间，收集每一个亮晶晶的日子。' }}
            </p>

            <div class="profile-info">
              <span v-if="profile?.location">⌖ {{ profile.location }}</span>
              <a v-if="profile?.email" :href="`mailto:${profile.email}`">✉ {{ profile.email }}</a>
              <a
                v-if="profile?.github"
                :href="profile.github"
                target="_blank"
                rel="noopener noreferrer"
              >
                ◇ GitHub
              </a>
            </div>

            <div class="profile-buttons">
              <a v-if="profile?.email" :href="`mailto:${profile.email}`" class="btn btn-sm btn-primary">
                写信给我
              </a>
              <RouterLink to="/guestbook" class="btn btn-sm btn-ghost">留言板</RouterLink>
            </div>
          </div>

          <div class="card mini-about-stats">
            <div>
              <strong>{{ formatNumber(stats?.posts ?? 0) }}</strong>
              <span>篇文章</span>
            </div>
            <div>
              <strong>{{ formatNumber(stats?.views ?? 0) }}</strong>
              <span>次阅读</span>
            </div>
            <div>
              <strong>{{ formatNumber(stats?.comments ?? 0) }}</strong>
              <span>条回声</span>
            </div>
          </div>

          <div class="quote-card-about">
            <span class="quote-icon">“</span>
            <p>愿我们都能在普通的日子里，找到属于自己的光。</p>
            <span class="quote-sign">— 月白</span>
          </div>
        </aside>

        <div class="about-main">
          <article class="card about-content">
            <div class="content-head">
              <span class="content-sun">☀</span>
              <div>
                <p class="section-eyebrow">A LITTLE MORE ABOUT ME</p>
                <h2>我的自白</h2>
              </div>
            </div>
            <MarkdownView :content="aboutText" />
          </article>

          <section class="card tech-card">
            <div class="tech-head">
              <div>
                <p class="section-eyebrow">UNDER THE HOOD</p>
                <h2>这个小站是怎么搭起来的</h2>
              </div>
              <span class="tech-emoji">🛠</span>
            </div>
            <p class="tech-desc">
              前台是 Vue 3 + TypeScript + Vite，后台是 Spring Boot + MyBatis-Plus 的经典 MVC 分层，
              Redis 负责缓存和阅读计数。数据库开发时用 H2，上线可切换 MySQL。
            </p>
            <div class="tech-tags">
              <span
                v-for="item in techStack"
                :key="item.name"
                class="tech-chip"
                :style="{ '--tech-color': item.color }"
              >
                {{ item.name }}
              </span>
            </div>
            <div class="tech-footer">
              <span>☕ 用一杯咖啡的时间维护</span>
              <span>♡ 持续更新中</span>
            </div>
          </section>

          <section class="contact-banner">
            <div>
              <p class="section-eyebrow">SAY HELLO</p>
              <h2>想聊聊天，或者只是路过？</h2>
              <p>留言板永远为你留着一个位置。</p>
            </div>
            <RouterLink to="/guestbook" class="btn btn-sun">去留一句话 →</RouterLink>
            <span class="contact-sun">☀</span>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.about-page {
  overflow-x: clip;
}

.about-badge {
  display: inline-flex;
  padding: 7px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.9);
  color: #9c7a33;
  font-size: 0.76rem;
  font-weight: 800;
}

.about-grid {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 30px;
  align-items: start;
  margin-top: 28px;
}

.about-side {
  position: sticky;
  top: 104px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.about-profile {
  padding: 30px 24px 24px;
  text-align: center;
  background:
    radial-gradient(circle at 50% 0%, rgba(255, 227, 163, 0.32), transparent 36%),
    linear-gradient(150deg, rgba(255, 255, 255, 0.95), rgba(255, 247, 236, 0.86));
}

.profile-avatar-wrap {
  position: relative;
  width: 138px;
  height: 138px;
  margin: 0 auto;
}

.profile-avatar-wrap img {
  position: relative;
  z-index: 1;
  width: 138px;
  height: 138px;
  border-radius: 44px;
  border: 4px solid #fff;
  box-shadow: 0 18px 40px rgba(112, 142, 166, 0.2);
}

.avatar-halo {
  position: absolute;
  inset: -14px;
  border-radius: 50%;
  border: 1.5px dashed rgba(246, 201, 107, 0.55);
  animation: spin-slow 30s linear infinite;
}

.avatar-spark {
  position: absolute;
  z-index: 2;
  right: -4px;
  top: 2px;
  color: var(--pink-strong);
  font-size: 1.1rem;
  animation: float-soft 5s ease-in-out infinite;
}

.profile-hello {
  margin-top: 18px;
  color: var(--ink-faint);
  font-size: 0.58rem;
  font-weight: 900;
  letter-spacing: 0.2em;
}

.about-profile h2 {
  margin-top: 3px;
  font-size: 1.6rem;
}

.profile-role {
  margin-top: 4px;
  color: #b08d9a;
  font-size: 0.72rem;
  font-weight: 700;
}

.profile-bio {
  margin-top: 13px;
  color: var(--ink-soft);
  font-size: 0.82rem;
  line-height: 1.85;
}

.profile-info {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 18px;
  padding: 14px 0;
  border-top: 1px dashed rgba(105, 135, 160, 0.16);
  border-bottom: 1px dashed rgba(105, 135, 160, 0.16);
  color: var(--ink-soft);
  font-size: 0.74rem;
  text-align: left;
}

.profile-info a:hover {
  color: #4e86a7;
}

.profile-buttons {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}

.profile-buttons .btn {
  flex: 1;
}

.mini-about-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  padding: 16px 10px;
  text-align: center;
}

.mini-about-stats div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  border-right: 1px dashed rgba(105, 135, 160, 0.14);
}

.mini-about-stats div:last-child {
  border-right: 0;
}

.mini-about-stats strong {
  font-family: var(--font-display);
  font-size: 1.2rem;
  font-weight: 900;
}

.mini-about-stats span {
  color: var(--ink-faint);
  font-size: 0.62rem;
}

.quote-card-about {
  position: relative;
  padding: 22px 20px;
  border-radius: var(--radius-lg);
  background: linear-gradient(140deg, #fff8e4, #fff0f4);
  border: 1px solid rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow-sm);
}

.quote-icon {
  position: absolute;
  top: -8px;
  left: 12px;
  color: rgba(238, 155, 182, 0.3);
  font-family: Georgia, serif;
  font-size: 3.2rem;
  line-height: 1;
}

.quote-card-about p {
  position: relative;
  color: #7d7180;
  font-size: 0.8rem;
  line-height: 1.9;
  text-align: center;
}

.quote-sign {
  display: block;
  margin-top: 7px;
  color: var(--ink-faint);
  font-size: 0.64rem;
  text-align: right;
}

.about-main {
  display: flex;
  flex-direction: column;
  gap: 22px;
  min-width: 0;
}

.about-content {
  padding: 36px 40px;
}

.content-head {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-bottom: 24px;
  padding-bottom: 18px;
  border-bottom: 1px dashed rgba(105, 135, 160, 0.16);
}

.content-sun {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 15px;
  background: var(--butter-soft);
  color: var(--butter-strong);
  font-size: 1.2rem;
}

.content-head h2 {
  margin-top: 2px;
  font-size: 1.35rem;
}

.tech-card {
  padding: 28px 32px;
  background:
    radial-gradient(circle at 100% 0%, rgba(169, 216, 245, 0.22), transparent 34%),
    rgba(255, 255, 255, 0.84);
}

.tech-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.tech-head h2 {
  margin-top: 3px;
  font-size: 1.2rem;
}

.tech-emoji {
  font-size: 1.5rem;
}

.tech-desc {
  margin-top: 12px;
  color: var(--ink-soft);
  font-size: 0.84rem;
  line-height: 1.85;
}

.tech-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.tech-chip {
  padding: 6px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--tech-color) 22%, white);
  color: color-mix(in srgb, var(--tech-color) 60%, #39404d);
  font-size: 0.72rem;
  font-weight: 800;
}

.tech-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px dashed rgba(105, 135, 160, 0.16);
  color: var(--ink-faint);
  font-size: 0.68rem;
}

.contact-banner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 30px 34px;
  border-radius: var(--radius-lg);
  background: linear-gradient(120deg, rgba(234, 246, 254, 0.92), rgba(255, 240, 244, 0.86));
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.contact-banner h2 {
  margin-top: 3px;
  font-size: 1.25rem;
}

.contact-banner p:last-child {
  margin-top: 4px;
  color: var(--ink-soft);
  font-size: 0.78rem;
}

.contact-banner .btn {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
}

.contact-sun {
  position: absolute;
  right: 130px;
  top: -22px;
  color: rgba(255, 224, 138, 0.55);
  font-size: 4.4rem;
}

@media (max-width: 960px) {
  .about-grid {
    grid-template-columns: 1fr;
  }
  .about-side {
    position: static;
    display: grid;
    grid-template-columns: 1.2fr 1fr;
    align-items: stretch;
  }
  .about-profile {
    grid-row: span 3;
  }
  .quote-card-about {
    grid-column: 2;
  }
}

@media (max-width: 700px) {
  .about-side {
    display: flex;
  }
  .about-content {
    padding: 26px 22px;
  }
  .tech-card {
    padding: 22px;
  }
  .contact-banner {
    flex-direction: column;
    align-items: flex-start;
    padding: 24px 22px;
  }
}
</style>
