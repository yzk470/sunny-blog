<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { PostSummary } from '@/api/types'
import { categoryEmoji, coverOf } from '@/utils/constants'
import { formatDate, formatNumber, readingLabel } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    post: PostSummary
    variant?: 'default' | 'featured' | 'compact'
  }>(),
  { variant: 'default' },
)

const imageBroken = ref(false)
const image = computed(() => coverOf(props.post.coverImage))
const visibleTags = computed(() => props.post.tags.slice(0, 3))
</script>

<template>
  <RouterLink
    :to="`/posts/${post.slug}`"
    class="post-card"
    :class="[`post-card-${variant}`]"
  >
    <div class="card-cover">
      <img
        v-if="!imageBroken"
        :src="image"
        :alt="post.title"
        loading="lazy"
        @error="imageBroken = true"
      />
      <div v-else class="cover-fallback">
        <span>{{ categoryEmoji(post.category?.slug) }}</span>
      </div>
      <div class="cover-shade"></div>
      <div class="cover-topline">
        <span v-if="post.isTop" class="pin-badge">☀ 置顶</span>
        <span v-else-if="post.category" class="category-badge">
          {{ categoryEmoji(post.category.slug) }} {{ post.category.name }}
        </span>
      </div>
      <span class="cover-date">{{ formatDate(post.publishedAt || post.createdAt) }}</span>
    </div>

    <div class="card-body">
      <div class="card-meta">
        <span>{{ formatDate(post.publishedAt || post.createdAt) }}</span>
        <span class="meta-dot"></span>
        <span>{{ readingLabel(post.readingTime) }}</span>
      </div>

      <h3 class="card-title">{{ post.title }}</h3>
      <p v-if="post.summary" class="card-summary">{{ post.summary }}</p>

      <div v-if="visibleTags.length" class="card-tags">
        <span
          v-for="tag in visibleTags"
          :key="tag.id"
          class="chip tag-color"
          :style="{ '--tag-color': tag.color || '#8CC8E8' }"
        >
          # {{ tag.name }}
        </span>
      </div>

      <div class="card-footer">
        <span class="read-more">
          阅读全文
          <span class="arrow">→</span>
        </span>
        <div class="card-stats">
          <span title="阅读量">◉ {{ formatNumber(post.viewCount) }}</span>
          <span title="评论数">✎ {{ post.commentCount }}</span>
        </div>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.post-card {
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-sm);
  transition: transform 0.3s cubic-bezier(0.2, 0.7, 0.2, 1), box-shadow 0.3s ease;
}

.post-card:hover {
  transform: translateY(-6px);
  box-shadow: var(--shadow-md);
}

.card-cover {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 9.6;
  background: linear-gradient(135deg, #eaf6fe, #fff0f4);
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.65s cubic-bezier(0.2, 0.7, 0.2, 1);
}

.post-card:hover .card-cover img {
  transform: scale(1.045);
}

.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2.6rem;
  background: linear-gradient(135deg, #eaf6fe, #fff0f4);
}

.cover-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, rgba(70, 96, 114, 0.16), transparent 42%, rgba(70, 96, 114, 0.12));
  opacity: 0.85;
  pointer-events: none;
}

.cover-topline {
  position: absolute;
  top: 14px;
  left: 14px;
  display: flex;
  gap: 8px;
}

.pin-badge,
.category-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 11px;
  border-radius: 999px;
  background: rgba(255, 253, 246, 0.92);
  backdrop-filter: blur(8px);
  color: #7c6231;
  font-size: 0.68rem;
  font-weight: 900;
  box-shadow: 0 6px 14px rgba(70, 96, 114, 0.12);
}

.category-badge {
  color: #4f7e99;
}

.cover-date {
  position: absolute;
  right: 14px;
  bottom: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.24);
  border: 1px solid rgba(255, 255, 255, 0.35);
  backdrop-filter: blur(8px);
  color: #fff;
  font-size: 0.64rem;
  font-weight: 800;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.18);
}

.card-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 20px 22px 18px;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-faint);
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.meta-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #cbd2da;
}

.card-title {
  margin-top: 9px;
  font-size: 1.18rem;
  line-height: 1.45;
  transition: color 0.2s ease;
}

.post-card:hover .card-title {
  color: #4e86a7;
}

.card-summary {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-top: 9px;
  color: var(--ink-soft);
  font-size: 0.84rem;
  line-height: 1.8;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 14px;
}

.card-tags .chip {
  padding: 3px 9px;
  font-size: 0.66rem;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  padding-top: 15px;
  border-top: 1px dashed rgba(105, 135, 160, 0.14);
}

.read-more {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #5a92b2;
  font-size: 0.78rem;
  font-weight: 900;
}

.read-more .arrow {
  transition: transform 0.2s ease;
}

.post-card:hover .read-more .arrow {
  transform: translateX(4px);
}

.card-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--ink-faint);
  font-size: 0.68rem;
  font-weight: 700;
}

/* featured */
.post-card-featured {
  display: grid;
  grid-template-columns: 1.08fr 1fr;
}

.post-card-featured .card-cover {
  height: 100%;
  min-height: 330px;
  aspect-ratio: auto;
}

.post-card-featured .card-body {
  justify-content: center;
  padding: 34px 38px;
}

.post-card-featured .card-title {
  font-size: clamp(1.35rem, 2.4vw, 1.85rem);
  line-height: 1.38;
}

.post-card-featured .card-summary {
  -webkit-line-clamp: 3;
  font-size: 0.9rem;
}

.post-card-featured .card-tags {
  margin-top: 18px;
}

.post-card-featured .card-footer {
  margin-top: 22px;
  padding-top: 18px;
}

/* compact */
.post-card-compact {
  flex-direction: row;
  align-items: stretch;
}

.post-card-compact .card-cover {
  width: 130px;
  flex: 0 0 130px;
  aspect-ratio: auto;
}

.post-card-compact .card-body {
  padding: 14px 16px;
}

.post-card-compact .card-title {
  font-size: 0.92rem;
  line-height: 1.5;
  margin-top: 4px;
}

.post-card-compact .card-summary,
.post-card-compact .card-tags,
.post-card-compact .cover-date,
.post-card-compact .card-stats {
  display: none;
}

.post-card-compact .card-footer {
  padding-top: 8px;
  border-top: 0;
}

.post-card-compact .read-more {
  font-size: 0.7rem;
}

@media (max-width: 760px) {
  .post-card-featured {
    grid-template-columns: 1fr;
  }
  .post-card-featured .card-cover {
    min-height: 0;
    aspect-ratio: 16 / 9.6;
  }
  .post-card-featured .card-body {
    padding: 22px;
  }
}
</style>
