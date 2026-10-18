<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageBanner from '@/components/PageBanner.vue'
import PostCard from '@/components/PostCard.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import EmptyState from '@/components/EmptyState.vue'
import { blogApi } from '@/api/blog'
import type { Category, PostSummary, Tag } from '@/api/types'
import { categoryEmoji } from '@/utils/constants'

const route = useRoute()
const router = useRouter()

const posts = ref<PostSummary[]>([])
const total = ref(0)
const pages = ref(0)
const loading = ref(true)
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const searchInput = ref('')
let searchTimer: number | undefined

const page = computed(() => Number(route.query.page) || 1)
const activeCategory = computed(() => String(route.query.category || ''))
const activeTag = computed(() => String(route.query.tag || ''))
const keyword = computed(() => String(route.query.keyword || ''))
const sort = computed(() => (route.query.sort === 'popular' ? 'popular' : 'latest'))
const hasFilters = computed(
  () => Boolean(activeCategory.value || activeTag.value || keyword.value) || sort.value === 'popular',
)

function cleanQuery(overrides: Record<string, string | number | undefined>) {
  const merged: Record<string, string | number | undefined> = {
    category: activeCategory.value || undefined,
    tag: activeTag.value || undefined,
    keyword: keyword.value || undefined,
    sort: sort.value === 'popular' ? 'popular' : undefined,
    page: page.value,
    ...overrides,
  }
  const result: Record<string, string> = {}
  for (const [key, value] of Object.entries(merged)) {
    if (value !== undefined && value !== '' && !(key === 'page' && Number(value) <= 1)) {
      result[key] = String(value)
    }
  }
  return result
}

async function updateQuery(overrides: Record<string, string | number | undefined>) {
  const query = cleanQuery(overrides)
  const current = JSON.stringify(route.query)
  if (JSON.stringify(query) !== current) {
    await router.push({ name: 'posts', query })
  }
}

function setCategory(slug: string) {
  updateQuery({ category: slug === activeCategory.value ? undefined : slug, page: 1 })
}

function setTag(slug: string) {
  updateQuery({ tag: slug === activeTag.value ? undefined : slug, page: 1 })
}

function setSort(value: 'latest' | 'popular') {
  updateQuery({ sort: value === 'popular' ? 'popular' : undefined, page: 1 })
}

function resetFilters() {
  searchInput.value = ''
  router.push({ name: 'posts' })
}

function onSearch() {
  updateQuery({ keyword: searchInput.value.trim() || undefined, page: 1 })
}

watch(searchInput, (value) => {
  window.clearTimeout(searchTimer)
  if (value === keyword.value) return
  searchTimer = window.setTimeout(() => {
    updateQuery({ keyword: value.trim() || undefined, page: 1 })
  }, 380)
})

async function loadPosts() {
  loading.value = true
  searchInput.value = keyword.value
  try {
    const result = await blogApi.listPosts({
      page: page.value,
      size: 6,
      category: activeCategory.value || undefined,
      tag: activeTag.value || undefined,
      keyword: keyword.value || undefined,
      sort: sort.value,
    })
    posts.value = result.records
    total.value = result.total
    pages.value = result.pages
    if (result.total > 0 && result.records.length === 0 && page.value > result.pages) {
      await updateQuery({ page: result.pages })
    }
  } finally {
    loading.value = false
  }
}

watch(
  () => route.query,
  () => loadPosts(),
  { immediate: true },
)

onMounted(async () => {
  const [categoryResult, tagResult] = await Promise.all([blogApi.categories(), blogApi.tags()])
  categories.value = categoryResult
  tags.value = tagResult
})
</script>

<template>
  <div class="posts-page page-shell">
    <div class="container">
      <PageBanner
        eyebrow="THE SUNNY ARCHIVE"
        title="所有文章"
        description="把生活、代码和远方，一页一页地收好。挑一个喜欢的分类，或者随便逛逛。"
      >
        <span class="banner-count">共 {{ total }} 篇文章</span>
        <span class="banner-tip">☀ 建议配一杯冰美式阅读</span>
      </PageBanner>

      <section class="filter-panel card">
        <div class="filter-top">
          <div class="search-input">
            <span class="search-icon">⌕</span>
            <input
              v-model="searchInput"
              class="input"
              type="search"
              placeholder="搜索标题、摘要或正文…"
              aria-label="搜索文章"
              @keyup.enter="onSearch"
            />
            <button
              v-if="searchInput"
              type="button"
              class="search-clear"
              aria-label="清空搜索"
              @click="searchInput = ''; onSearch()"
            >
              ×
            </button>
          </div>
          <div class="sort-switch" role="group" aria-label="排序">
            <button
              type="button"
              :class="{ active: sort === 'latest' }"
              @click="setSort('latest')"
            >
              最新
            </button>
            <button
              type="button"
              :class="{ active: sort === 'popular' }"
              @click="setSort('popular')"
            >
              最热
            </button>
          </div>
          <button v-if="hasFilters" type="button" class="btn btn-sm btn-ghost" @click="resetFilters">
            重置筛选
          </button>
        </div>

        <div class="filter-row">
          <span class="filter-label">分类</span>
          <div class="filter-options">
            <button
              type="button"
              class="filter-chip"
              :class="{ active: !activeCategory }"
              @click="setCategory('')"
            >
              全部
            </button>
            <button
              v-for="category in categories"
              :key="category.id"
              type="button"
              class="filter-chip"
              :class="{ active: activeCategory === category.slug }"
              :style="{ '--cat-color': category.color || '#8CC8E8' }"
              @click="setCategory(category.slug)"
            >
              {{ categoryEmoji(category.slug) }} {{ category.name }}
            </button>
          </div>
        </div>

        <div class="filter-row tags-row">
          <span class="filter-label">标签</span>
          <div class="filter-options tags-options">
            <button
              v-for="tag in tags"
              :key="tag.id"
              type="button"
              class="tag-filter"
              :class="{ active: activeTag === tag.slug }"
              :style="{ '--tag-color': tag.color || '#8CC8E8' }"
              @click="setTag(tag.slug)"
            >
              # {{ tag.name }}
            </button>
          </div>
        </div>
      </section>

      <div class="results-head">
        <p v-if="keyword" class="results-text">
          关于「<strong>{{ keyword }}</strong>」的搜索结果
        </p>
        <p v-else-if="activeTag" class="results-text">
          标签 <strong>#{{ tags.find((t) => t.slug === activeTag)?.name || activeTag }}</strong> 下的文章
        </p>
        <p v-else-if="activeCategory" class="results-text">
          分类 <strong>{{ categories.find((c) => c.slug === activeCategory)?.name || activeCategory }}</strong> 下的文章
        </p>
        <p v-else class="results-text">慢慢挑，总有一篇合你心意。</p>
        <span class="results-count">{{ total }} 篇</span>
      </div>

      <template v-if="loading">
        <div class="posts-grid">
          <div v-for="i in 6" :key="i" class="skeleton post-skeleton"></div>
        </div>
      </template>

      <template v-else-if="posts.length">
        <div class="posts-grid">
          <PostCard v-for="post in posts" :key="post.id" :post="post" />
        </div>
        <PaginationBar
          :page="page"
          :pages="pages"
          :total="total"
          @update:page="updateQuery({ page: $event })"
        />
      </template>

      <div v-else class="card">
        <EmptyState
          emoji="🌾"
          title="没有找到对应的文章"
          description="换一个关键词，或者去别的分类看看吧。"
        >
          <button type="button" class="btn btn-sm btn-ghost mt-8" @click="resetFilters">
            看看全部文章
          </button>
        </EmptyState>
      </div>
    </div>
  </div>
</template>

<style scoped>
.posts-page {
  overflow-x: clip;
}

.banner-count {
  display: inline-flex;
  align-items: center;
  padding: 7px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.9);
  color: var(--ink-soft);
  font-size: 0.76rem;
  font-weight: 800;
}

.banner-tip {
  color: #a48b5a;
  font-size: 0.74rem;
  font-weight: 700;
}

.filter-panel {
  margin-top: 24px;
  padding: 20px 22px;
}

.filter-top {
  display: flex;
  align-items: center;
  gap: 14px;
}

.search-input {
  flex: 1;
}

.search-clear {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 50%;
  background: rgba(169, 216, 245, 0.2);
  color: var(--ink-faint);
  font-size: 1rem;
  line-height: 1;
}

.sort-switch {
  display: inline-flex;
  padding: 4px;
  border-radius: 999px;
  background: rgba(255, 249, 240, 0.9);
  border: 1px solid var(--line);
  flex: 0 0 auto;
}

.sort-switch button {
  padding: 7px 15px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--ink-faint);
  font-size: 0.78rem;
  font-weight: 800;
  transition: all 0.2s ease;
}

.sort-switch button.active {
  background: #fff;
  color: var(--ink);
  box-shadow: 0 4px 12px rgba(112, 142, 166, 0.12);
}

.filter-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed rgba(105, 135, 160, 0.14);
}

.filter-label {
  flex: 0 0 40px;
  padding-top: 5px;
  color: var(--ink-faint);
  font-size: 0.7rem;
  font-weight: 900;
  letter-spacing: 0.1em;
}

.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-chip {
  padding: 6px 14px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.65);
  color: var(--ink-soft);
  font-size: 0.78rem;
  font-weight: 800;
  transition: all 0.2s ease;
}

.filter-chip:hover {
  background: #fff;
  color: var(--ink);
  border-color: var(--line);
}

.filter-chip.active {
  background: color-mix(in srgb, var(--cat-color, #a9d8f5) 24%, white);
  color: color-mix(in srgb, var(--cat-color, #77bce5) 65%, #39404d);
  border-color: color-mix(in srgb, var(--cat-color, #a9d8f5) 40%, white);
  box-shadow: 0 6px 14px rgba(112, 142, 166, 0.1);
}

.tags-row {
  margin-top: 12px;
  padding-top: 12px;
}

.tag-filter {
  padding: 4px 11px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--ink-faint);
  font-size: 0.74rem;
  font-weight: 700;
  transition: all 0.2s ease;
}

.tag-filter:hover {
  background: rgba(234, 246, 254, 0.8);
  color: #4e86a7;
}

.tag-filter.active {
  background: color-mix(in srgb, var(--tag-color) 22%, white);
  color: color-mix(in srgb, var(--tag-color) 68%, #39404d);
}

.results-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 30px 4px 16px;
}

.results-text {
  color: var(--ink-soft);
  font-size: 0.86rem;
}

.results-text strong {
  color: var(--ink);
}

.results-count {
  color: var(--ink-faint);
  font-size: 0.74rem;
  font-weight: 700;
}

.posts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 22px;
}

.post-skeleton {
  height: 430px;
}

@media (max-width: 860px) {
  .filter-top {
    flex-wrap: wrap;
  }
  .search-input {
    flex: 1 1 100%;
  }
  .posts-grid {
    grid-template-columns: 1fr;
  }
  .post-skeleton {
    height: 380px;
  }
}

@media (max-width: 560px) {
  .filter-panel {
    padding: 16px;
  }
  .filter-row {
    flex-direction: column;
    gap: 8px;
  }
  .filter-label {
    padding-top: 0;
  }
  .results-head {
    margin-top: 22px;
  }
}
</style>
