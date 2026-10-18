<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { adminApi } from '@/api/admin'
import type { Dashboard } from '@/api/types'
import { formatNumber, greeting, initialOf, relativeTime, todayLabel } from '@/utils/format'

const dashboard = ref<Dashboard | null>(null)
const loading = ref(true)

const pendingTotal = computed(
  () => (dashboard.value?.pendingCommentCount ?? 0) + (dashboard.value?.draftCount ?? 0),
)

const statCards = computed(() => [
  {
    label: '全部文章',
    value: dashboard.value?.postCount ?? 0,
    foot: `已发布 ${dashboard.value?.publishedCount ?? 0} · 草稿 ${dashboard.value?.draftCount ?? 0}`,
    icon: '📝',
    color: '#EAF6FE',
  },
  {
    label: '累计阅读',
    value: dashboard.value?.totalViews ?? 0,
    foot: 'Redis 实时计数 + 定时回写',
    icon: '◉',
    color: '#FFF8E4',
  },
  {
    label: '评论总数',
    value: dashboard.value?.commentCount ?? 0,
    foot: `${dashboard.value?.pendingCommentCount ?? 0} 条等待审核`,
    icon: '💬',
    color: '#FFF0F4',
  },
  {
    label: '留言总数',
    value: dashboard.value?.guestbookCount ?? 0,
    foot: '来自云朵墙的问候',
    icon: '☁️',
    color: '#EFFAF4',
  },
])

const statusText = (status: number | null) => {
  if (status === 0) return { text: '待审核', cls: 'status-pending' }
  if (status === 1) return { text: '已通过', cls: 'status-approved' }
  return { text: '已拒绝', cls: 'status-rejected' }
}

onMounted(async () => {
  try {
    dashboard.value = await adminApi.dashboard()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="dashboard">
    <div class="dashboard-hero">
      <div>
        <p class="dashboard-date">{{ todayLabel() }} · {{ greeting() }}</p>
        <h1>欢迎回到月白小筑</h1>
        <p class="dashboard-sub">
          今天也有人在等你的新故事。已经有
          <strong>{{ dashboard?.publishedCount ?? 0 }}</strong> 篇文章在阳光下啦。
        </p>
      </div>
      <div class="dashboard-hero-actions">
        <RouterLink :to="{ name: 'admin-post-new' }" class="btn btn-primary">✎ 写新文章</RouterLink>
        <RouterLink :to="{ name: 'admin-comments', query: { status: '0' } }" class="btn btn-ghost">
          💬 审核评论
          <span v-if="dashboard?.pendingCommentCount" class="pending-dot">
            {{ dashboard.pendingCommentCount }}
          </span>
        </RouterLink>
      </div>
      <div class="hero-decor" aria-hidden="true">
        <span class="hero-sun">☀</span>
        <span class="hero-cloud">☁</span>
        <span class="hero-spark">✦</span>
      </div>
    </div>

    <div v-if="loading" class="dashboard-loading">
      <div class="admin-stat-grid">
        <div v-for="i in 4" :key="i" class="skeleton stat-skeleton"></div>
      </div>
      <div class="skeleton panel-skeleton"></div>
    </div>

    <template v-else-if="dashboard">
      <div class="admin-stat-grid">
        <div
          v-for="card in statCards"
          :key="card.label"
          class="admin-stat"
          :style="{ '--stat-color': card.color }"
        >
          <div class="admin-stat-top">
            <span class="admin-stat-icon">{{ card.icon }}</span>
            <span class="admin-stat-label">{{ card.label }}</span>
          </div>
          <p class="admin-stat-value">{{ formatNumber(card.value) }}</p>
          <p class="admin-stat-foot">{{ card.foot }}</p>
        </div>
      </div>

      <div class="dashboard-grid">
        <div class="dashboard-left">
          <section class="admin-card">
            <div class="admin-card-head">
              <div>
                <h2>最近评论</h2>
                <p>来自读者的小回声</p>
              </div>
              <RouterLink :to="{ name: 'admin-comments' }" class="row-action">查看全部 →</RouterLink>
            </div>
            <div v-if="dashboard.recentComments.length" class="recent-list">
              <div
                v-for="comment in dashboard.recentComments"
                :key="comment.id"
                class="recent-item"
              >
                <span class="recent-avatar">{{ initialOf(comment.author) }}</span>
                <div class="recent-body">
                  <p>{{ comment.content }}</p>
                  <div class="recent-meta">
                    <strong>{{ comment.author }}</strong>
                    <span>·</span>
                    <span>{{ relativeTime(comment.createdAt) }}</span>
                    <span>·</span>
                    <span>{{ comment.postTitle }}</span>
                  </div>
                </div>
                <span
                  class="status"
                  :class="statusText(comment.status).cls"
                >
                  {{ statusText(comment.status).text }}
                </span>
              </div>
            </div>
            <div v-else class="admin-empty"><span>☁</span>还没有评论，去写一篇文章吧。</div>
          </section>

          <section class="admin-card">
            <div class="admin-card-head">
              <div>
                <h2>热门文章</h2>
                <p>被认真读过很多次的故事</p>
              </div>
              <RouterLink :to="{ name: 'admin-posts' }" class="row-action">文章管理 →</RouterLink>
            </div>
            <div v-if="dashboard.popularPosts.length" class="popular-list">
              <RouterLink
                v-for="(post, index) in dashboard.popularPosts"
                :key="post.id"
                :to="{ name: 'admin-post-edit', params: { id: post.id } }"
                class="popular-item"
              >
                <span class="popular-rank" :class="`rank-${index + 1}`">{{ index + 1 }}</span>
                <div class="popular-info">
                  <strong>{{ post.title }}</strong>
                  <span>{{ post.category?.name || '未分类' }} · {{ relativeTime(post.publishedAt) }}</span>
                </div>
                <span class="popular-views">◉ {{ formatNumber(post.viewCount) }}</span>
                <span class="popular-arrow">→</span>
              </RouterLink>
            </div>
            <div v-else class="admin-empty"><span>🌱</span>还没有已发布的文章。</div>
          </section>
        </div>

        <aside class="dashboard-right">
          <section class="admin-card quick-card">
            <div class="admin-card-head">
              <div>
                <h2>快捷入口</h2>
                <p>今天想做点什么？</p>
              </div>
            </div>
            <div class="quick-grid">
              <RouterLink :to="{ name: 'admin-post-new' }" class="quick-item">
                <span class="quick-icon" style="background: #eaf6fe">📝</span>
                <span>写文章</span>
              </RouterLink>
              <RouterLink :to="{ name: 'admin-comments' }" class="quick-item">
                <span class="quick-icon" style="background: #fff0f4">💬</span>
                <span>审评论</span>
              </RouterLink>
              <RouterLink :to="{ name: 'admin-guestbook' }" class="quick-item">
                <span class="quick-icon" style="background: #effaf4">☁️</span>
                <span>看留言</span>
              </RouterLink>
              <RouterLink :to="{ name: 'admin-profile' }" class="quick-item">
                <span class="quick-icon" style="background: #fff8e4">⚙️</span>
                <span>改资料</span>
              </RouterLink>
            </div>
          </section>

          <section class="admin-card todo-card">
            <div class="todo-head">
              <span class="todo-icon">☀</span>
              <div>
                <h2>今日小提醒</h2>
                <p>{{ pendingTotal ? `有 ${pendingTotal} 件小事等你处理` : '今天也很清爽，没有待办' }}</p>
              </div>
            </div>
            <div class="todo-list">
              <RouterLink
                :to="{ name: 'admin-comments', query: { status: '0' } }"
                class="todo-item"
              >
                <span class="todo-dot pink"></span>
                <span>待审核评论</span>
                <strong>{{ dashboard.pendingCommentCount }}</strong>
              </RouterLink>
              <RouterLink :to="{ name: 'admin-posts', query: { status: '0' } }" class="todo-item">
                <span class="todo-dot butter"></span>
                <span>草稿箱文章</span>
                <strong>{{ dashboard.draftCount }}</strong>
              </RouterLink>
              <RouterLink :to="{ name: 'admin-guestbook' }" class="todo-item">
                <span class="todo-dot sky"></span>
                <span>留言板留言</span>
                <strong>{{ dashboard.guestbookCount }}</strong>
              </RouterLink>
              <RouterLink :to="{ name: 'admin-taxonomy' }" class="todo-item">
                <span class="todo-dot mint"></span>
                <span>分类 / 标签</span>
                <strong>{{ dashboard.categoryCount }} / {{ dashboard.tagCount }}</strong>
              </RouterLink>
            </div>
          </section>

          <div class="dashboard-quote">
            <span class="quote-sun">☀</span>
            <p>“写作是把日子过两遍。”</p>
            <span>今天也写一点点吧。</span>
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.dashboard-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 30px 32px;
  margin-bottom: 22px;
  border-radius: 22px;
  background:
    radial-gradient(circle at 88% 10%, rgba(255, 227, 163, 0.55), transparent 30%),
    linear-gradient(120deg, rgba(234, 246, 254, 0.95), rgba(255, 240, 244, 0.88));
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 8px 26px rgba(112, 142, 166, 0.06);
  overflow: hidden;
}

.dashboard-date {
  color: #9c8a6a;
  font-size: 0.7rem;
  font-weight: 800;
}

.dashboard-hero h1 {
  margin-top: 5px;
  font-size: 1.7rem;
  letter-spacing: -0.03em;
}

.dashboard-sub {
  margin-top: 6px;
  color: var(--ink-soft);
  font-size: 0.8rem;
}

.dashboard-sub strong {
  color: #4e86a7;
}

.dashboard-hero-actions {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}

.pending-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--pink-strong);
  color: #fff;
  font-size: 0.6rem;
}

.hero-decor {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.hero-sun {
  position: absolute;
  right: 32%;
  top: -22px;
  color: rgba(255, 224, 138, 0.6);
  font-size: 3.5rem;
}
.hero-cloud {
  position: absolute;
  right: 12%;
  bottom: -8px;
  color: rgba(255, 255, 255, 0.75);
  font-size: 2.2rem;
}
.hero-spark {
  position: absolute;
  left: 44%;
  top: 22px;
  color: rgba(238, 155, 182, 0.7);
  font-size: 0.8rem;
}

.dashboard-loading {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.stat-skeleton {
  height: 130px;
}
.panel-skeleton {
  height: 380px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(300px, 0.85fr);
  gap: 16px;
  margin-top: 16px;
}

.dashboard-left,
.dashboard-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.popular-list {
  display: flex;
  flex-direction: column;
}

.popular-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px 22px;
  border-bottom: 1px solid rgba(105, 135, 160, 0.07);
  transition: background 0.18s ease;
}

.popular-item:last-child {
  border-bottom: 0;
}

.popular-item:hover {
  background: #fffcf6;
}

.popular-rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex: 0 0 auto;
  border-radius: 8px;
  background: #f5f6f8;
  color: var(--ink-faint);
  font-size: 0.7rem;
  font-weight: 900;
}

.rank-1 {
  background: #fff3c9;
  color: #b08935;
}
.rank-2 {
  background: #eaf6fe;
  color: #5b8dab;
}
.rank-3 {
  background: #fff0f4;
  color: #b76580;
}

.popular-info {
  flex: 1;
  min-width: 0;
}

.popular-info strong {
  display: block;
  overflow: hidden;
  font-size: 0.78rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.popular-info span {
  color: var(--ink-faint);
  font-size: 0.62rem;
}

.popular-views {
  color: var(--ink-faint);
  font-size: 0.66rem;
  font-weight: 700;
  white-space: nowrap;
}

.popular-arrow {
  color: var(--ink-faint);
  font-size: 0.75rem;
  opacity: 0;
  transition: all 0.2s ease;
}

.popular-item:hover .popular-arrow {
  opacity: 1;
  transform: translateX(2px);
}

.quick-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 14px 18px 18px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border-radius: 12px;
  border: 1px solid rgba(105, 135, 160, 0.08);
  color: var(--ink-soft);
  font-size: 0.72rem;
  font-weight: 700;
  transition: all 0.2s ease;
}

.quick-item:hover {
  border-color: rgba(169, 216, 245, 0.6);
  background: #fffdf8;
  transform: translateY(-1px);
}

.quick-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  font-size: 0.9rem;
}

.todo-card {
  padding-bottom: 6px;
}

.todo-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 18px 20px 12px;
}

.todo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  background: var(--butter-soft);
  color: var(--butter-strong);
}

.todo-head h2 {
  font-size: 0.9rem;
}

.todo-head p {
  margin-top: 2px;
  color: var(--ink-faint);
  font-size: 0.64rem;
}

.todo-list {
  padding: 0 12px 12px;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 9px;
  color: var(--ink-soft);
  font-size: 0.72rem;
}

.todo-item:hover {
  background: #fffcf6;
}

.todo-item strong {
  margin-left: auto;
  color: var(--ink);
  font-size: 0.72rem;
}

.todo-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.todo-dot.pink {
  background: var(--pink-strong);
}
.todo-dot.butter {
  background: var(--butter-strong);
}
.todo-dot.sky {
  background: var(--sky-strong);
}
.todo-dot.mint {
  background: #8fd2b0;
}

.dashboard-quote {
  padding: 16px 18px;
  border-radius: 16px;
  background: linear-gradient(135deg, #fff9ec, #fff3f5);
  border: 1px dashed rgba(246, 201, 107, 0.35);
  text-align: center;
}

.quote-sun {
  color: var(--butter-strong);
  font-size: 0.9rem;
}

.dashboard-quote p {
  margin-top: 3px;
  color: #8a7d72;
  font-size: 0.78rem;
  font-weight: 700;
}

.dashboard-quote span:last-child {
  color: var(--ink-faint);
  font-size: 0.62rem;
}

@media (max-width: 1050px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
  .dashboard-right {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
  .dashboard-quote {
    grid-column: 1 / -1;
  }
}

@media (max-width: 760px) {
  .dashboard-hero {
    flex-direction: column;
    align-items: flex-start;
    padding: 24px;
  }
  .dashboard-hero h1 {
    font-size: 1.4rem;
  }
  .dashboard-right {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .admin-stat-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
