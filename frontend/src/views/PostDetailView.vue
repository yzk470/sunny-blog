<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import MarkdownView from '@/components/MarkdownView.vue'
import CommentSection from '@/components/CommentSection.vue'
import { blogApi } from '@/api/blog'
import type { PostDetail } from '@/api/types'
import { useSiteStore } from '@/stores/site'
import { useToastStore } from '@/stores/toast'
import { categoryEmoji, coverOf } from '@/utils/constants'
import { formatDate, formatNumber, initialOf, readingLabel } from '@/utils/format'
import { extractHeadings, type HeadingItem } from '@/utils/markdown'

const route = useRoute()
const site = useSiteStore()
const toast = useToastStore()

const post = ref<PostDetail | null>(null)
const loading = ref(true)
const error = ref('')
const liked = ref(false)
const liking = ref(false)
const headings = ref<HeadingItem[]>([])
const activeHeading = ref('')
const coverBroken = ref(false)

let currentSlug = ''
let observer: IntersectionObserver | null = null

const profile = computed(() => site.profile)
const cover = computed(() => (post.value ? coverOf(post.value.coverImage) : ''))
const renderContent = computed(() => {
  const value = post.value?.content || ''
  const title = post.value?.title?.trim()
  const match = /^\s*#\s+(.+?)\s*(?:\r?\n|$)/.exec(value)
  if (title && match && match[1].trim() === title) {
    return value.slice(match[0].length)
  }
  return value
})

function setupToc() {
  observer?.disconnect()
  if (!headings.value.length) return
  const elements = headings.value
    .map((item) => document.getElementById(item.id))
    .filter((el): el is HTMLElement => Boolean(el))
  if (!elements.length) return
  observer = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
      if (visible[0]?.target.id) {
        activeHeading.value = visible[0].target.id
      }
    },
    { rootMargin: '-110px 0px -68% 0px', threshold: [0, 1] },
  )
  elements.forEach((el) => observer?.observe(el))
}

async function loadPost(slug: string) {
  if (!slug) return
  loading.value = true
  error.value = ''
  coverBroken.value = false
  try {
    const result = await blogApi.detail(slug)
    if (currentSlug !== slug) return
    post.value = result
    headings.value = extractHeadings(renderContent.value)
    liked.value = localStorage.getItem(`sunnyblog_liked:${slug}`) === '1'
    document.title = `${result.title} · 月白小筑`
    const description = result.summary || ''
    const meta = document.querySelector('meta[name="description"]')
    if (meta && description) meta.setAttribute('content', description)

    await nextTick()
    setupToc()

    if (!sessionStorage.getItem(`sunnyblog_viewed:${slug}`)) {
      try {
        const view = await blogApi.recordView(slug)
        if (post.value) post.value.viewCount = view.viewCount
        sessionStorage.setItem(`sunnyblog_viewed:${slug}`, '1')
      } catch {
        /* reading shouldn't be blocked by counter */
      }
    }
  } catch (err) {
    if (currentSlug !== slug) return
    error.value = err instanceof Error ? err.message : '文章加载失败'
    post.value = null
  } finally {
    loading.value = false
  }
}

async function likePost() {
  if (!post.value || liked.value || liking.value) return
  liking.value = true
  try {
    const result = await blogApi.like(post.value.slug)
    post.value.likeCount = result.likeCount
    liked.value = true
    localStorage.setItem(`sunnyblog_liked:${post.value.slug}`, '1')
    toast.show('收到你的喜欢啦，今天也闪闪发光 ☀')
  } catch (err) {
    toast.show(err instanceof Error ? err.message : '点赞失败', 'error')
  } finally {
    liking.value = false
  }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(window.location.href)
    toast.show('链接已复制，去分享好天气吧')
  } catch {
    toast.show('复制失败，可以手动复制地址栏', 'error')
  }
}

function onScrollSpy() {
  if (!headings.value.length) return
  let current = headings.value[0].id
  for (const heading of headings.value) {
    const element = document.getElementById(heading.id)
    if (element && element.getBoundingClientRect().top <= 140) {
      current = heading.id
    }
  }
  activeHeading.value = current
}

watch(
  () => route.params.slug,
  (slug) => {
    currentSlug = String(slug || '')
    if (currentSlug) loadPost(currentSlug)
  },
  { immediate: true },
)

onMounted(() => {
  window.addEventListener('scroll', onScrollSpy, { passive: true })
})

onUnmounted(() => {
  observer?.disconnect()
  window.removeEventListener('scroll', onScrollSpy)
})
</script>

<template>
  <div class="post-detail-page page-shell">
    <div class="container">
      <div v-if="loading" class="detail-loading">
        <div class="skeleton title-skeleton"></div>
        <div class="skeleton meta-skeleton"></div>
        <div class="skeleton cover-skeleton"></div>
        <div class="skeleton body-skeleton"></div>
      </div>

      <div v-else-if="error" class="detail-error card">
        <span class="error-emoji">☁️</span>
        <h1>这篇文章躲进云里了</h1>
        <p>{{ error }}</p>
        <RouterLink to="/posts" class="btn btn-primary">回去看看别的文章</RouterLink>
      </div>

      <template v-else-if="post">
        <nav class="breadcrumb" aria-label="面包屑">
          <RouterLink to="/">首页</RouterLink>
          <span>/</span>
          <RouterLink to="/posts">文章</RouterLink>
          <span>/</span>
          <span class="current">{{ post.category?.name || '未分类' }}</span>
        </nav>

        <header class="article-header">
          <div class="article-badges">
            <RouterLink
              v-if="post.category"
              :to="{ name: 'posts', query: { category: post.category.slug } }"
              class="chip tag-color category-chip"
              :style="{ '--tag-color': post.category.color || '#8CC8E8' }"
            >
              {{ categoryEmoji(post.category.slug) }} {{ post.category.name }}
            </RouterLink>
            <span v-if="post.isTop" class="chip chip-butter">☀ 置顶推荐</span>
          </div>

          <h1 class="article-title">{{ post.title }}</h1>
          <p v-if="post.summary" class="article-summary">{{ post.summary }}</p>

          <div class="article-meta">
            <div class="author-mini">
              <img :src="profile?.avatar || '/images/avatar-yuebai.png'" alt="作者头像" />
              <div>
                <strong>{{ profile?.nickname || '月白' }}</strong>
                <span>写于 {{ formatDate(post.publishedAt || post.createdAt) }}</span>
              </div>
            </div>
            <span class="meta-divider"></span>
            <span class="meta-item">☕ {{ readingLabel(post.readingTime) }}</span>
            <span class="meta-item">◉ {{ formatNumber(post.viewCount) }} 次阅读</span>
            <span class="meta-item">✎ {{ post.commentCount }} 条评论</span>
          </div>
        </header>

        <figure v-if="!coverBroken" class="article-cover">
          <img :src="cover" :alt="post.title" @error="coverBroken = true" />
          <figcaption>
            <span>☀ {{ post.category?.name || '月白小筑' }}</span>
            <span>{{ formatDate(post.publishedAt || post.createdAt) }}</span>
          </figcaption>
        </figure>

        <div class="article-layout">
          <article class="article-card card">
            <MarkdownView :content="renderContent" />

            <div class="article-tags">
              <span class="article-tags-label">标签</span>
              <RouterLink
                v-for="tag in post.tags"
                :key="tag.id"
                :to="{ name: 'posts', query: { tag: tag.slug } }"
                class="chip tag-color"
                :style="{ '--tag-color': tag.color || '#8CC8E8' }"
              >
                # {{ tag.name }}
              </RouterLink>
            </div>

            <div class="article-end">
              <div class="end-line">
                <span></span>
                <span class="end-sun">☀</span>
                <span></span>
              </div>
              <p>谢谢你读到这里，愿你今天也有好天气。</p>
            </div>

            <div class="article-actions">
              <button
                class="like-button"
                :class="{ liked }"
                type="button"
                :disabled="liking || liked"
                @click="likePost"
              >
                <span class="heart">{{ liked ? '♥' : '♡' }}</span>
                {{ liked ? '已喜欢' : '喜欢这篇' }}
                <span class="like-count">{{ post.likeCount }}</span>
              </button>
              <button class="share-button" type="button" @click="copyLink">
                <span>⌁</span> 复制链接
              </button>
            </div>

            <div class="author-box">
              <img :src="profile?.avatar || '/images/avatar-yuebai.png'" alt="作者头像" />
              <div class="author-box-text">
                <span class="author-box-label">ABOUT THE AUTHOR</span>
                <strong>{{ profile?.nickname || '月白' }}</strong>
                <p>{{ profile?.bio || '白天写代码，傍晚追晚霞。' }}</p>
              </div>
              <RouterLink to="/about" class="btn btn-sm btn-ghost">认识我 →</RouterLink>
            </div>
          </article>

          <aside class="article-sidebar">
            <div v-if="headings.length > 1" class="card toc-card">
              <div class="toc-head">
                <span class="toc-icon">☰</span>
                <h3>本文目录</h3>
              </div>
              <nav class="toc-list" aria-label="文章目录">
                <a
                  v-for="heading in headings"
                  :key="heading.id"
                  :href="`#${heading.id}`"
                  class="toc-link"
                  :class="{ active: activeHeading === heading.id, level3: heading.level === 3 }"
                >
                  <span class="toc-dot"></span>
                  {{ heading.text }}
                </a>
              </nav>
            </div>

            <div class="card side-note">
              <div class="side-note-sun">☀</div>
              <p class="side-note-title">今日小纸条</p>
              <p class="side-note-text">“把普通的日子过得浪漫一点，生活就会偷偷奖励你。”</p>
              <span class="side-note-from">— 月白</span>
            </div>

            <div class="card side-profile">
              <img :src="profile?.avatar || '/images/avatar-yuebai.png'" alt="站长头像" />
              <strong>{{ profile?.nickname || '月白' }}</strong>
              <p>{{ profile?.slogan || '把日子过成一首明亮的诗' }}</p>
              <RouterLink to="/guestbook" class="btn btn-sm btn-sun">来留言板玩 →</RouterLink>
              <RouterLink
                :to="{ path: '/digital-human', query: { article: post.slug } }"
                class="btn btn-sm btn-ghost"
              >
                和数字人聊聊这篇 →
              </RouterLink>
            </div>
          </aside>
        </div>

        <CommentSection :slug="post.slug" />

        <div class="back-row">
          <RouterLink to="/posts" class="back-link">← 回到文章列表</RouterLink>
          <RouterLink to="/archives" class="back-link">看看时光归档 →</RouterLink>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.post-detail-page {
  overflow-x: clip;
}

/* Loading */
.detail-loading {
  max-width: 920px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.title-skeleton {
  height: 64px;
  width: 72%;
  margin: 30px auto 0;
}
.meta-skeleton {
  height: 24px;
  width: 46%;
  margin: 0 auto;
}
.cover-skeleton {
  height: 400px;
  margin-top: 18px;
}
.body-skeleton {
  height: 340px;
}

/* Error */
.detail-error {
  max-width: 560px;
  margin: 60px auto;
  padding: 48px 32px;
  text-align: center;
}
.error-emoji {
  font-size: 3rem;
}
.detail-error h1 {
  margin-top: 12px;
  font-size: 1.5rem;
}
.detail-error p {
  margin: 10px 0 22px;
  color: var(--ink-soft);
  font-size: 0.88rem;
}

/* Breadcrumb */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0 22px;
  color: var(--ink-faint);
  font-size: 0.72rem;
  font-weight: 700;
}
.breadcrumb a:hover {
  color: #4e86a7;
}
.breadcrumb .current {
  color: var(--ink-soft);
}

/* Header */
.article-header {
  max-width: 920px;
  margin: 0 auto;
  text-align: center;
}

.article-badges {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.category-chip {
  font-size: 0.72rem;
}

.article-title {
  margin-top: 16px;
  font-size: clamp(2rem, 4.6vw, 3.3rem);
  line-height: 1.28;
  letter-spacing: -0.04em;
}

.article-summary {
  max-width: 760px;
  margin: 18px auto 0;
  color: var(--ink-soft);
  font-size: 0.98rem;
  line-height: 1.9;
}

.article-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 26px;
}

.author-mini {
  display: flex;
  align-items: center;
  gap: 9px;
  text-align: left;
}

.author-mini img {
  width: 38px;
  height: 38px;
  border-radius: 13px;
  border: 2px solid #fff;
  box-shadow: var(--shadow-sm);
}

.author-mini div {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.author-mini strong {
  font-size: 0.78rem;
}

.author-mini span {
  color: var(--ink-faint);
  font-size: 0.66rem;
}

.meta-divider {
  width: 1px;
  height: 26px;
  background: var(--line);
}

.meta-item {
  color: var(--ink-faint);
  font-size: 0.72rem;
  font-weight: 700;
}

/* Cover */
.article-cover {
  position: relative;
  max-width: 1040px;
  margin: 34px auto 0;
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: var(--shadow-md);
  background: linear-gradient(135deg, #eaf6fe, #fff0f4);
}

.article-cover img {
  width: 100%;
  height: clamp(260px, 46vw, 520px);
  object-fit: cover;
}

.article-cover figcaption {
  position: absolute;
  left: 20px;
  right: 20px;
  bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: #fff;
  font-size: 0.68rem;
  font-weight: 800;
  text-shadow: 0 1px 6px rgba(40, 60, 74, 0.28);
}

/* Layout */
.article-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 270px;
  gap: 30px;
  align-items: start;
  max-width: 1160px;
  margin: 30px auto 0;
}

.article-card {
  padding: 42px 46px 36px;
}

.article-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 36px;
  padding-top: 24px;
  border-top: 1px dashed rgba(105, 135, 160, 0.18);
}

.article-tags-label {
  margin-right: 4px;
  color: var(--ink-faint);
  font-size: 0.7rem;
  font-weight: 900;
  letter-spacing: 0.1em;
}

.article-end {
  margin-top: 34px;
  text-align: center;
}

.end-line {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-bottom: 12px;
}

.end-line span:not(.end-sun) {
  width: 50px;
  height: 1px;
  background: linear-gradient(to right, transparent, rgba(105, 135, 160, 0.24));
}

.end-line span:last-child {
  background: linear-gradient(to left, transparent, rgba(105, 135, 160, 0.24));
}

.end-sun {
  color: var(--butter-strong);
  font-size: 1rem;
}

.article-end p {
  color: var(--ink-faint);
  font-size: 0.78rem;
}

.article-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 28px;
}

.like-button,
.share-button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 10px 18px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: #fff;
  color: var(--ink-soft);
  font-size: 0.78rem;
  font-weight: 800;
  transition: all 0.2s ease;
}

.like-button:hover:not(:disabled),
.share-button:hover {
  transform: translateY(-2px);
  border-color: rgba(238, 155, 182, 0.45);
  color: #b76580;
  box-shadow: var(--shadow-sm);
}

.like-button.liked {
  background: var(--pink-soft);
  border-color: rgba(238, 155, 182, 0.35);
  color: #b76580;
}

.heart {
  color: var(--pink-strong);
  font-size: 0.95rem;
}

.like-count {
  padding: 1px 7px;
  border-radius: 999px;
  background: rgba(249, 198, 213, 0.28);
  font-size: 0.66rem;
}

.author-box {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-top: 34px;
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(234, 246, 254, 0.8), rgba(255, 240, 244, 0.65));
  border: 1px solid rgba(255, 255, 255, 0.95);
}

.author-box img {
  width: 58px;
  height: 58px;
  border-radius: 18px;
  border: 2px solid #fff;
  flex: 0 0 auto;
}

.author-box-text {
  flex: 1;
  min-width: 0;
}

.author-box-label {
  color: var(--ink-faint);
  font-size: 0.55rem;
  font-weight: 900;
  letter-spacing: 0.16em;
}

.author-box-text strong {
  display: block;
  margin-top: 2px;
  font-size: 0.9rem;
}

.author-box-text p {
  margin-top: 3px;
  color: var(--ink-soft);
  font-size: 0.72rem;
  line-height: 1.6;
}

/* Sidebar */
.article-sidebar {
  position: sticky;
  top: 104px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toc-card {
  padding: 18px;
}

.toc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.toc-head h3 {
  font-size: 0.84rem;
}

.toc-icon {
  color: var(--sky-strong);
  font-size: 0.8rem;
}

.toc-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 42vh;
  overflow-y: auto;
}

.toc-link {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 6px 7px 6px 8px;
  border-radius: 8px;
  color: var(--ink-faint);
  font-size: 0.72rem;
  line-height: 1.55;
  transition: all 0.2s ease;
}

.toc-link:hover {
  color: var(--ink);
  background: rgba(234, 246, 254, 0.6);
}

.toc-link.active {
  color: #4e86a7;
  background: var(--sky-soft);
  font-weight: 700;
}

.toc-link.level3 {
  padding-left: 20px;
  font-size: 0.68rem;
}

.toc-dot {
  width: 4px;
  height: 4px;
  flex: 0 0 auto;
  margin-top: 7px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.55;
}

.side-note {
  position: relative;
  padding: 24px 20px 18px;
  background: linear-gradient(145deg, #fff9e9, #fff2f5);
  overflow: hidden;
}

.side-note-sun {
  position: absolute;
  right: 14px;
  top: 10px;
  color: rgba(246, 201, 107, 0.55);
  font-size: 1.6rem;
}

.side-note-title {
  color: #9c7a33;
  font-size: 0.64rem;
  font-weight: 900;
  letter-spacing: 0.14em;
}

.side-note-text {
  margin-top: 9px;
  color: #7d7180;
  font-size: 0.78rem;
  line-height: 1.85;
}

.side-note-from {
  display: block;
  margin-top: 9px;
  color: var(--ink-faint);
  font-size: 0.66rem;
  text-align: right;
}

.side-profile {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 22px 18px;
  text-align: center;
}

.side-profile img {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  border: 3px solid #fff;
  box-shadow: var(--shadow-sm);
}

.side-profile strong {
  margin-top: 10px;
  font-size: 0.9rem;
}

.side-profile p {
  margin: 4px 0 14px;
  color: var(--ink-faint);
  font-size: 0.68rem;
  line-height: 1.6;
}

/* Back */
.back-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  max-width: 1160px;
  margin: 44px auto 0;
}

.back-link {
  color: var(--ink-soft);
  font-size: 0.8rem;
  font-weight: 800;
  transition: color 0.2s ease, transform 0.2s ease;
}

.back-link:hover {
  color: #4e86a7;
  transform: translateX(-2px);
}

@media (max-width: 980px) {
  .article-layout {
    grid-template-columns: 1fr;
  }
  .article-sidebar {
    display: none;
  }
}

@media (max-width: 700px) {
  .article-card {
    padding: 26px 22px 28px;
  }
  .article-meta {
    gap: 12px;
  }
  .meta-divider {
    display: none;
  }
  .article-cover {
    border-radius: 22px;
    margin-top: 24px;
  }
  .author-box {
    flex-wrap: wrap;
  }
  .author-box .btn {
    width: 100%;
  }
  .back-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
}
</style>
