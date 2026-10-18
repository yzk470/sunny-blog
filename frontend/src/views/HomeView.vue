<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import PostCard from '@/components/PostCard.vue'
import { blogApi } from '@/api/blog'
import type { Category, PostSummary, Tag } from '@/api/types'
import { useSiteStore } from '@/stores/site'
import { categoryEmoji, coverOf } from '@/utils/constants'
import { formatNumber, greeting, todayLabel } from '@/utils/format'

const site = useSiteStore()

const latest = ref<PostSummary[]>([])
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const loading = ref(true)

const profile = computed(() => site.profile)
const stats = computed(() => site.stats)
const heroPost = computed(() => latest.value[0] || null)
const heroCover = computed(() => coverOf(heroPost.value?.coverImage))
const morePosts = computed(() => latest.value.slice(1, 5))
const tagCloud = computed(() => tags.value.slice(0, 10))

const statCards = computed(() => [
  { label: '文章', value: stats.value?.posts ?? 0, suffix: '篇' },
  { label: '阅读', value: stats.value?.views ?? 0, suffix: '次' },
  { label: '回声', value: stats.value?.comments ?? 0, suffix: '条' },
])

onMounted(async () => {
  site.load()
  try {
    const [latestResult, categoryResult, tagResult] = await Promise.all([
      blogApi.latest(6),
      blogApi.categories(),
      blogApi.tags(),
    ])
    latest.value = latestResult
    categories.value = categoryResult
    tags.value = tagResult
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="home page-shell">
    <div class="container">
      <!-- Hero -->
      <section class="hero">
        <div class="hero-copy fade-up">
          <p class="hero-eyebrow">
            <span class="eyebrow-dot"></span>
            HELLO, SUNNY DAY
            <span class="eyebrow-date">{{ todayLabel() }}</span>
          </p>
          <h1 class="hero-title">
            <span class="line">把日子过成</span>
            <span class="line"><span class="accent">一首明亮的诗</span></span>
          </h1>
          <p class="hero-desc">
            {{ profile?.bio || '在代码与云朵之间，收集每一个亮晶晶的日子。这里记录生活、代码、书与远方。' }}
          </p>

          <div class="hero-actions">
            <RouterLink to="/posts" class="btn btn-primary">
              读一读文章
              <span>→</span>
            </RouterLink>
            <RouterLink to="/about" class="btn btn-ghost">
              认识我
              <span class="btn-wave">☀</span>
            </RouterLink>
          </div>

          <div class="hero-stats">
            <div v-for="item in statCards" :key="item.label" class="stat-item">
              <strong>{{ formatNumber(item.value) }}<small>{{ item.suffix }}</small></strong>
              <span>{{ item.label }}</span>
            </div>
            <div class="stat-divider"></div>
            <div class="hero-greeting">
              <span class="greeting-sun">☀</span>
              <p>{{ greeting() }}</p>
              <small>{{ profile?.location || '在某个有阳光的城市' }}</small>
            </div>
          </div>
        </div>

        <div class="hero-visual fade-up" style="animation-delay: 0.12s">
          <div class="visual-blob"></div>
          <div class="visual-ring"></div>
          <div class="visual-sun"></div>

          <RouterLink v-if="heroPost" :to="`/posts/${heroPost.slug}`" class="hero-photo">
            <img :src="heroCover" :alt="heroPost.title" />
            <div class="photo-caption">
              <span class="caption-label">☀ 本周推荐</span>
              <strong>{{ heroPost.title }}</strong>
            </div>
            <span class="photo-tape"></span>
          </RouterLink>

          <RouterLink to="/about" class="profile-chip">
            <img :src="profile?.avatar || '/images/avatar-yuebai.png'" alt="站长头像" />
            <div class="profile-chip-text">
              <strong>{{ profile?.nickname || '月白' }}</strong>
              <span>正在晒太阳</span>
            </div>
            <span class="online-dot"></span>
          </RouterLink>

          <div class="mood-note">
            <span class="mood-emoji">🌼</span>
            <div>
              <span>今日份好心情</span>
              <strong>+1</strong>
            </div>
          </div>

          <div class="mini-cloud mini-cloud-a"></div>
          <div class="mini-cloud mini-cloud-b"></div>
          <span class="visual-spark">✦</span>
          <span class="visual-star">✧</span>
        </div>
      </section>

      <!-- Category strip -->
      <section class="category-strip fade-up" style="animation-delay: 0.2s">
        <RouterLink
          v-for="category in categories"
          :key="category.id"
          :to="{ name: 'posts', query: { category: category.slug } }"
          class="category-pill"
          :style="{ '--cat-color': category.color || '#8CC8E8' }"
        >
          <span class="category-icon">{{ categoryEmoji(category.slug) }}</span>
          <span class="category-info">
            <strong>{{ category.name }}</strong>
            <small>{{ category.description || '去逛逛这个分类' }}</small>
          </span>
          <span class="category-arrow">→</span>
        </RouterLink>
      </section>

      <!-- Latest -->
      <section class="section home-content">
        <div class="home-posts">
          <div class="section-head">
            <div>
              <p class="section-eyebrow">FRESH FROM THE SUN</p>
              <h2 class="section-title"><span class="sun-dot"></span>最近写下的</h2>
            </div>
            <RouterLink to="/posts" class="section-link">全部文章 →</RouterLink>
          </div>

          <template v-if="loading">
            <div class="skeleton featured-skeleton"></div>
            <div class="post-grid">
              <div v-for="i in 2" :key="i" class="skeleton card-skeleton"></div>
            </div>
          </template>

          <template v-else-if="latest.length">
            <PostCard :post="latest[0]" variant="featured" />
            <div class="post-grid">
              <PostCard v-for="post in morePosts" :key="post.id" :post="post" />
            </div>
          </template>

          <div v-else class="card empty-state">
            <div class="empty-emoji">🌱</div>
            <h3>第一篇文章正在发芽</h3>
            <p>等它长大一点，再请你来看。</p>
          </div>
        </div>

        <!-- Sidebar -->
        <aside class="home-sidebar">
          <div class="card profile-card">
            <div class="profile-card-top">
              <div class="avatar-wrap">
                <img :src="profile?.avatar || '/images/avatar-yuebai.png'" alt="站长头像" />
                <span class="avatar-sun">☀</span>
              </div>
              <div>
                <span class="profile-hi">HELLO, I AM</span>
                <h3>{{ profile?.nickname || '月白' }}</h3>
                <p class="profile-location">⌖ {{ profile?.location || '杭州' }}</p>
              </div>
            </div>
            <p class="profile-bio">
              {{ profile?.bio || '白天写代码，傍晚追晚霞。这里存放生活里的光。' }}
            </p>
            <div class="profile-actions">
              <RouterLink to="/about" class="btn btn-sm btn-primary">关于我</RouterLink>
              <RouterLink to="/guestbook" class="btn btn-sm btn-ghost">留个言</RouterLink>
            </div>
            <div class="profile-slogan">「{{ profile?.slogan || '把日子过成一首明亮的诗' }}」</div>
          </div>

          <div class="card side-card">
            <div class="side-card-head">
              <h3>热门标签</h3>
              <span class="side-deco">✦</span>
            </div>
            <div class="tag-cloud">
              <RouterLink
                v-for="tag in tagCloud"
                :key="tag.id"
                :to="{ name: 'posts', query: { tag: tag.slug } }"
                class="chip tag-color"
                :style="{ '--tag-color': tag.color || '#8CC8E8' }"
              >
                # {{ tag.name }}
              </RouterLink>
            </div>
          </div>

          <div class="card side-card stats-card">
            <div class="side-card-head">
              <h3>小站数据</h3>
              <span class="side-deco">☁</span>
            </div>
            <div class="mini-stat-grid">
              <div class="mini-stat">
                <strong>{{ formatNumber(stats?.posts ?? 0) }}</strong>
                <span>篇文章</span>
              </div>
              <div class="mini-stat">
                <strong>{{ formatNumber(stats?.views ?? 0) }}</strong>
                <span>次阅读</span>
              </div>
              <div class="mini-stat">
                <strong>{{ formatNumber(stats?.comments ?? 0) }}</strong>
                <span>条评论</span>
              </div>
              <div class="mini-stat">
                <strong>{{ formatNumber(stats?.tags ?? 0) }}</strong>
                <span>个标签</span>
              </div>
            </div>
          </div>

          <div class="quote-card">
            <span class="quote-mark">“</span>
            <p>慢慢来，比较快。<br />好天气会等你。</p>
            <span class="quote-sun">☀</span>
          </div>
        </aside>
      </section>

      <!-- Digital human CTA -->
      <section class="dh-home-cta">
        <div class="dh-home-copy">
          <p class="section-eyebrow">MEET THE DIGITAL HUMAN</p>
          <h2>去 3D 会客厅，<br />和爻光聊聊天</h2>
          <p>Spring AI 负责思考，WebGL 负责让她站在你面前。文字、语音、口型和表情，都在浏览器里发生。</p>
          <RouterLink to="/digital-human" class="btn btn-primary">推门进会客厅 →</RouterLink>
        </div>
        <div class="dh-home-visual" aria-hidden="true">
          <span class="dh-home-star star-a">✦</span>
          <span class="dh-home-star star-b">✧</span>
          <span class="dh-home-star star-c">✦</span>
          <div class="dh-home-orb">
            <span class="dh-home-orb-inner">爻</span>
          </div>
          <div class="dh-home-shadow"></div>
          <div class="dh-home-tag">SPRING AI × WEBGL</div>
        </div>
      </section>

      <!-- Guestbook CTA -->
      <section class="guestbook-cta">
        <div class="cta-copy">
          <p class="section-eyebrow">LEAVE A LITTLE CLOUD</p>
          <h2>来留言板，留一朵云吧</h2>
          <p>分享今天的好天气，或者只是说一句「我来过」。</p>
        </div>
        <RouterLink to="/guestbook" class="btn btn-sun">去留言板坐坐 →</RouterLink>
        <div class="cta-decor" aria-hidden="true">
          <div class="cta-sun"></div>
          <div class="cta-cloud"></div>
          <span class="cta-spark">✦</span>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.home {
  overflow-x: clip;
}

/* ---------------- Hero ---------------- */
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
  align-items: center;
  gap: 44px;
  padding: 36px 0 22px;
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 7px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-sm);
  color: #8b93a1;
  font-size: 0.67rem;
  font-weight: 900;
  letter-spacing: 0.16em;
}

.eyebrow-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--pink-strong);
  box-shadow: 0 0 0 4px rgba(249, 198, 213, 0.3);
}

.eyebrow-date {
  padding-left: 9px;
  border-left: 1px solid var(--line);
  color: #aab1bb;
  letter-spacing: 0.04em;
  font-weight: 700;
}

.hero-title {
  margin-top: 24px;
  font-size: clamp(2.6rem, 5.2vw, 4.3rem);
  line-height: 1.16;
  letter-spacing: -0.045em;
}

.hero-title .line {
  display: block;
}

.hero-title .accent {
  position: relative;
  isolation: isolate;
  color: #4d84a3;
}

.hero-title .accent::after {
  content: "";
  position: absolute;
  z-index: -1;
  left: -1.5%;
  right: -1.5%;
  bottom: 2px;
  height: 9px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(255, 227, 163, 0.82), rgba(249, 198, 213, 0.74));
  transform: rotate(-1.1deg);
}

.hero-desc {
  max-width: 520px;
  margin-top: 22px;
  color: var(--ink-soft);
  font-size: 1rem;
  line-height: 1.95;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}

.btn-wave {
  display: inline-block;
  animation: spin-slow 14s linear infinite;
}

.hero-stats {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 22px;
  margin-top: 36px;
  padding-top: 24px;
  border-top: 1px dashed rgba(105, 135, 160, 0.17);
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-item strong {
  font-family: var(--font-display);
  color: var(--ink);
  font-size: 1.55rem;
  font-weight: 900;
  letter-spacing: -0.03em;
  line-height: 1.2;
}

.stat-item strong small {
  margin-left: 3px;
  color: var(--ink-faint);
  font-size: 0.62rem;
  font-weight: 700;
}

.stat-item span {
  color: var(--ink-faint);
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.stat-divider {
  width: 1px;
  height: 38px;
  background: rgba(105, 135, 160, 0.14);
}

.hero-greeting {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding-left: 4px;
}

.greeting-sun {
  position: absolute;
  top: -4px;
  right: -20px;
  color: var(--butter-strong);
  font-size: 0.9rem;
  animation: spin-slow 16s linear infinite;
}

.hero-greeting p {
  color: var(--ink-soft);
  font-size: 0.8rem;
  font-weight: 800;
}

.hero-greeting small {
  color: var(--ink-faint);
  font-size: 0.66rem;
}

/* Hero visual */
.hero-visual {
  position: relative;
  height: 490px;
}

.visual-blob {
  position: absolute;
  top: 40px;
  right: 10px;
  width: 400px;
  height: 400px;
  border-radius: 48% 52% 46% 54% / 52% 44% 56% 48%;
  background: linear-gradient(140deg, rgba(169, 216, 245, 0.65), rgba(249, 198, 213, 0.6) 56%, rgba(255, 227, 163, 0.55));
  filter: blur(2px);
  animation: blob-morph 14s ease-in-out infinite;
}

.visual-ring {
  position: absolute;
  top: 4px;
  right: 34px;
  width: 330px;
  height: 330px;
  border-radius: 50%;
  border: 1.5px dashed rgba(119, 188, 229, 0.45);
  animation: spin-slow 40s linear infinite;
}

.visual-ring::after {
  content: "";
  position: absolute;
  inset: 28px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.8);
}

.visual-sun {
  position: absolute;
  top: 18px;
  right: 14px;
  width: 92px;
  height: 92px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #fff4c8, #ffe08a 58%, #ffd178);
  box-shadow: 0 0 50px rgba(255, 210, 120, 0.45);
  animation: float-y 8s ease-in-out infinite;
}

.hero-photo {
  position: absolute;
  top: 92px;
  left: 12px;
  width: 340px;
  padding: 12px 12px 0;
  border-radius: 22px;
  background: #fffdf8;
  box-shadow: 0 26px 60px rgba(89, 118, 138, 0.22);
  transform: rotate(-3.2deg);
  transition: transform 0.45s cubic-bezier(0.2, 0.7, 0.2, 1), box-shadow 0.45s ease;
}

.hero-photo:hover {
  transform: rotate(-1deg) translateY(-6px);
  box-shadow: 0 34px 70px rgba(89, 118, 138, 0.28);
}

.hero-photo img {
  width: 100%;
  height: 240px;
  object-fit: cover;
  border-radius: 14px;
}

.photo-caption {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 13px 8px 16px;
}

.caption-label {
  color: #9c7a33;
  font-size: 0.63rem;
  font-weight: 900;
  letter-spacing: 0.12em;
}

.photo-caption strong {
  color: var(--ink);
  font-size: 0.95rem;
  font-weight: 800;
  line-height: 1.5;
}

.photo-tape {
  position: absolute;
  top: -13px;
  left: 50%;
  width: 86px;
  height: 25px;
  transform: translateX(-50%) rotate(2deg);
  background: rgba(255, 227, 163, 0.72);
  border-radius: 3px;
  box-shadow: 0 4px 10px rgba(160, 130, 70, 0.12);
}

.profile-chip {
  position: absolute;
  right: 0;
  bottom: 58px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px 10px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.96);
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(12px);
  transform: rotate(2deg);
  transition: transform 0.3s ease;
}

.profile-chip:hover {
  transform: rotate(0deg) translateY(-3px);
}

.profile-chip img {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 2px solid #fff;
}

.profile-chip-text {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.profile-chip-text strong {
  font-size: 0.82rem;
}

.profile-chip-text span {
  color: var(--ink-faint);
  font-size: 0.64rem;
}

.online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #8fd2b0;
  box-shadow: 0 0 0 4px rgba(143, 210, 176, 0.24);
}

.mood-note {
  position: absolute;
  top: 40px;
  left: -18px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  border-radius: 14px;
  background: rgba(255, 250, 238, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow-sm);
  transform: rotate(-4deg);
  animation: float-soft 6s ease-in-out infinite;
}

.mood-note div {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.mood-note span {
  color: var(--ink-faint);
  font-size: 0.6rem;
}

.mood-note strong {
  color: #c08b9c;
  font-size: 0.92rem;
}

.mood-emoji {
  font-size: 1.1rem;
}

.mini-cloud {
  position: absolute;
  height: 22px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 10px 22px rgba(112, 142, 166, 0.08);
}
.mini-cloud::before,
.mini-cloud::after {
  content: "";
  position: absolute;
  border-radius: 50%;
  background: inherit;
}
.mini-cloud::before {
  width: 36px;
  height: 36px;
  left: 12px;
  bottom: 3px;
}
.mini-cloud::after {
  width: 26px;
  height: 26px;
  right: 14px;
  bottom: 1px;
}
.mini-cloud-a {
  width: 92px;
  right: 32px;
  top: 90px;
  opacity: 0.9;
  animation: drift 13s ease-in-out infinite;
}
.mini-cloud-b {
  width: 72px;
  left: 22px;
  bottom: 20px;
  opacity: 0.6;
  animation: drift 17s ease-in-out infinite reverse;
}

.visual-spark {
  position: absolute;
  top: 200px;
  right: 4px;
  color: var(--pink-strong);
  font-size: 1.1rem;
  animation: float-soft 5s ease-in-out infinite;
}
.visual-star {
  position: absolute;
  top: 340px;
  left: 4px;
  color: var(--butter-strong);
  font-size: 0.9rem;
  animation: float-soft 7s ease-in-out infinite;
}

@keyframes blob-morph {
  0%,
  100% {
    border-radius: 48% 52% 46% 54% / 52% 44% 56% 48%;
    transform: rotate(0deg);
  }
  50% {
    border-radius: 54% 46% 52% 48% / 46% 54% 48% 52%;
    transform: rotate(4deg);
  }
}

/* ---------------- Categories ---------------- */
.category-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-top: 30px;
}

.category-pill {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px 16px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9), color-mix(in srgb, var(--cat-color) 12%, white));
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-sm);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.category-pill:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-md);
}

.category-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  border-radius: 14px;
  background: color-mix(in srgb, var(--cat-color) 24%, white);
  font-size: 1.15rem;
}

.category-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.4;
}

.category-info strong {
  font-size: 0.88rem;
}

.category-info small {
  overflow: hidden;
  color: var(--ink-faint);
  font-size: 0.66rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-arrow {
  margin-left: auto;
  color: var(--ink-faint);
  font-size: 0.8rem;
  transition: transform 0.2s ease;
}

.category-pill:hover .category-arrow {
  transform: translateX(3px);
  color: var(--ink-soft);
}

/* ---------------- Content ---------------- */
.home-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 38px;
  align-items: start;
}

.post-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.featured-skeleton {
  height: 330px;
}
.card-skeleton {
  height: 290px;
}

.home-sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
  position: sticky;
  top: 104px;
}

.profile-card {
  padding: 24px 22px 20px;
  background:
    radial-gradient(circle at 100% 0%, rgba(249, 198, 213, 0.35), transparent 40%),
    linear-gradient(145deg, rgba(255, 255, 255, 0.94), rgba(255, 249, 240, 0.9));
}

.profile-card-top {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar-wrap {
  position: relative;
  flex: 0 0 auto;
}

.avatar-wrap img {
  width: 70px;
  height: 70px;
  border-radius: 24px;
  border: 3px solid #fff;
  box-shadow: 0 10px 24px rgba(112, 142, 166, 0.16);
}

.avatar-sun {
  position: absolute;
  right: -7px;
  bottom: -7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 25px;
  height: 25px;
  border-radius: 50%;
  background: var(--butter);
  border: 2px solid #fff;
  color: #a37a2d;
  font-size: 0.7rem;
}

.profile-hi {
  color: var(--ink-faint);
  font-size: 0.58rem;
  font-weight: 900;
  letter-spacing: 0.16em;
}

.profile-card h3 {
  margin-top: 2px;
  font-size: 1.18rem;
}

.profile-location {
  margin-top: 2px;
  color: var(--ink-faint);
  font-size: 0.7rem;
}

.profile-bio {
  margin-top: 14px;
  color: var(--ink-soft);
  font-size: 0.82rem;
  line-height: 1.85;
}

.profile-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}

.profile-actions .btn {
  flex: 1;
}

.profile-slogan {
  margin-top: 16px;
  padding-top: 13px;
  border-top: 1px dashed rgba(105, 135, 160, 0.16);
  color: #b08d9a;
  font-size: 0.7rem;
  text-align: center;
}

.side-card {
  padding: 20px 20px 18px;
}

.side-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 13px;
}

.side-card-head h3 {
  font-size: 0.95rem;
}

.side-deco {
  color: var(--butter-strong);
  font-size: 0.85rem;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.tag-cloud .chip {
  font-size: 0.7rem;
}

.mini-stat-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.mini-stat {
  display: flex;
  flex-direction: column;
  padding: 11px 12px;
  border-radius: 14px;
  background: rgba(255, 249, 240, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.9);
}

.mini-stat strong {
  font-family: var(--font-display);
  font-size: 1.2rem;
  font-weight: 900;
  color: var(--ink);
  line-height: 1.2;
}

.mini-stat span {
  color: var(--ink-faint);
  font-size: 0.64rem;
}

.quote-card {
  position: relative;
  padding: 24px 22px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, rgba(255, 248, 228, 0.96), rgba(255, 240, 244, 0.9));
  border: 1px solid rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.quote-mark {
  position: absolute;
  top: -12px;
  left: 12px;
  color: rgba(238, 155, 182, 0.28);
  font-family: Georgia, serif;
  font-size: 4.2rem;
  line-height: 1;
}

.quote-card p {
  position: relative;
  color: #7d7180;
  font-size: 0.9rem;
  font-weight: 700;
  line-height: 1.9;
  text-align: center;
}

.quote-sun {
  display: block;
  margin-top: 8px;
  color: var(--butter-strong);
  text-align: center;
  font-size: 0.9rem;
}

/* ---------------- Digital human CTA ---------------- */
.dh-home-cta {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) 0.9fr;
  gap: 24px;
  margin-top: 76px;
  padding: 40px 46px;
  border-radius: var(--radius-xl);
  overflow: hidden;
  background:
    radial-gradient(circle at 82% 18%, rgba(146, 198, 233, 0.3), transparent 32%),
    radial-gradient(circle at 12% 100%, rgba(249, 198, 213, 0.18), transparent 34%),
    linear-gradient(135deg, #111c30, #1a2038 55%, #281d35);
  box-shadow: 0 24px 60px rgba(58, 73, 97, 0.18);
}

.dh-home-copy {
  position: relative;
  z-index: 2;
}

.dh-home-copy .section-eyebrow {
  color: rgba(255, 227, 163, 0.72);
}

.dh-home-copy h2 {
  margin-top: 8px;
  color: #fffaf0;
  font-size: clamp(1.6rem, 3.4vw, 2.35rem);
  line-height: 1.25;
}

.dh-home-copy > p {
  max-width: 520px;
  margin-top: 12px;
  color: rgba(228, 238, 247, 0.64);
  font-size: 0.82rem;
  line-height: 1.85;
}

.dh-home-copy .btn {
  margin-top: 20px;
  background: linear-gradient(135deg, #ffe3a3, #f9c6d5);
  color: #5f4a2f;
  box-shadow: 0 12px 28px rgba(249, 198, 213, 0.22);
}

.dh-home-visual {
  position: relative;
  min-height: 220px;
}

.dh-home-orb {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 156px;
  height: 156px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background:
    radial-gradient(circle at 34% 26%, rgba(255, 255, 255, 0.92), transparent 20%),
    radial-gradient(circle at 65% 70%, rgba(249, 198, 213, 0.8), transparent 38%),
    linear-gradient(145deg, #a9d8f5, #d9c7f2 55%, #f9c6d5);
  box-shadow: 0 0 0 12px rgba(169, 216, 245, 0.08), 0 0 50px rgba(169, 216, 245, 0.3);
}

.dh-home-orb-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 108px;
  height: 108px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.65);
  color: rgba(255, 255, 255, 0.9);
  font-family: var(--font-serif);
  font-size: 2.6rem;
  text-shadow: 0 4px 18px rgba(102, 118, 158, 0.35);
}

.dh-home-shadow {
  position: absolute;
  left: 50%;
  bottom: 24px;
  width: 190px;
  height: 24px;
  transform: translateX(-50%);
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.26);
  filter: blur(12px);
}

.dh-home-star {
  position: absolute;
  color: rgba(255, 227, 163, 0.8);
  animation: float-soft 5s ease-in-out infinite;
}

.dh-home-star.star-a { top: 14%; left: 12%; font-size: 0.9rem; }
.dh-home-star.star-b { top: 24%; right: 10%; font-size: 1.2rem; color: rgba(249, 198, 213, 0.8); animation-delay: -1.6s; }
.dh-home-star.star-c { bottom: 20%; left: 22%; font-size: 0.65rem; animation-delay: -3.2s; }

.dh-home-tag {
  position: absolute;
  right: 2%;
  bottom: 4%;
  color: rgba(228, 238, 247, 0.28);
  font-size: 0.52rem;
  font-weight: 800;
  letter-spacing: 0.16em;
}

/* ---------------- CTA ---------------- */
.guestbook-cta {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  margin-top: 76px;
  padding: 38px 44px;
  border-radius: var(--radius-xl);
  background:
    radial-gradient(circle at 88% 20%, rgba(255, 227, 163, 0.7), transparent 30%),
    linear-gradient(120deg, rgba(234, 246, 254, 0.95), rgba(255, 240, 244, 0.92));
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.cta-copy {
  position: relative;
  z-index: 1;
}

.cta-copy h2 {
  margin-top: 6px;
  font-size: clamp(1.4rem, 2.8vw, 2rem);
}

.cta-copy p:last-child {
  margin-top: 7px;
  color: var(--ink-soft);
  font-size: 0.86rem;
}

.guestbook-cta .btn {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
}

.cta-decor {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.cta-sun {
  position: absolute;
  top: -40px;
  right: 110px;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  background: rgba(255, 224, 138, 0.55);
  filter: blur(2px);
}

.cta-cloud {
  position: absolute;
  right: 30px;
  bottom: -26px;
  width: 150px;
  height: 44px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
}

.cta-cloud::before {
  content: "";
  position: absolute;
  left: 28px;
  bottom: 6px;
  width: 70px;
  height: 70px;
  border-radius: 50%;
  background: inherit;
}

.cta-spark {
  position: absolute;
  top: 30px;
  left: 44%;
  color: rgba(238, 155, 182, 0.75);
  font-size: 1rem;
}

@media (max-width: 1060px) {
  .hero {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  .hero-visual {
    height: 420px;
    max-width: 620px;
    margin: 0 auto;
    width: 100%;
  }
  .hero-photo {
    left: 8%;
  }
  .profile-chip {
    right: 6%;
  }
  .home-content {
    grid-template-columns: minmax(0, 1fr) 300px;
    gap: 26px;
  }
  .category-strip {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 820px) {
  .home-content {
    grid-template-columns: 1fr;
  }
  .home-sidebar {
    position: static;
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
  .profile-card {
    grid-column: 1 / -1;
  }
  .quote-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .hero {
    padding-top: 20px;
  }
  .hero-title {
    font-size: clamp(2.25rem, 10vw, 3rem);
    margin-top: 18px;
  }
  .hero-desc {
    font-size: 0.9rem;
  }
  .hero-stats {
    gap: 16px;
  }
  .stat-divider,
  .hero-greeting {
    display: none;
  }
  .hero-visual {
    height: 350px;
  }
  .visual-blob {
    width: 300px;
    height: 300px;
    right: 50%;
    transform: translateX(50%);
    top: 30px;
  }
  .visual-ring {
    right: 50%;
    transform: translateX(50%);
    width: 260px;
    height: 260px;
    top: 0;
  }
  .visual-sun {
    right: 8%;
    width: 70px;
    height: 70px;
  }
  .hero-photo {
    width: 250px;
    left: 50%;
    top: 60px;
    transform: translateX(-55%) rotate(-3deg);
  }
  .hero-photo:hover {
    transform: translateX(-55%) rotate(-1deg) translateY(-4px);
  }
  .hero-photo img {
    height: 170px;
  }
  .mood-note {
    top: 10px;
    left: 0;
  }
  .profile-chip {
    right: 2%;
    bottom: 10px;
    transform: scale(0.9);
    transform-origin: right bottom;
  }
  .profile-chip:hover {
    transform: scale(0.9) translateY(-2px);
  }
  .mini-cloud-a {
    display: none;
  }
  .post-grid {
    grid-template-columns: 1fr;
  }
  .home-sidebar {
    grid-template-columns: 1fr;
  }
  .category-strip {
    grid-template-columns: 1fr;
  }
  .guestbook-cta {
    flex-direction: column;
    align-items: flex-start;
    padding: 30px 24px;
    margin-top: 54px;
  }
  .dh-home-cta {
    grid-template-columns: 1fr;
    padding: 30px 24px;
    margin-top: 54px;
  }
  .dh-home-visual {
    min-height: 190px;
  }
  .dh-home-orb {
    width: 130px;
    height: 130px;
  }
  .dh-home-orb-inner {
    width: 90px;
    height: 90px;
    font-size: 2.1rem;
  }
  .cta-sun {
    right: -30px;
  }
}
</style>
