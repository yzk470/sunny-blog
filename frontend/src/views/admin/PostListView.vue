<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { adminApi } from '@/api/admin'
import type { PostSummary } from '@/api/types'
import { useToastStore } from '@/stores/toast'
import { formatDate, formatNumber } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const toast = useToastStore()

const posts = ref<PostSummary[]>([])
const total = ref(0)
const pages = ref(0)
const loading = ref(true)
const keywordInput = ref('')
const deleteTarget = ref<PostSummary | null>(null)
const deleting = ref(false)
const statusBusy = ref<number | null>(null)
let searchTimer: number | undefined

const page = computed(() => Number(route.query.page) || 1)
const keyword = computed(() => String(route.query.keyword || ''))
const statusFilter = computed<number | undefined>(() => {
  if (route.query.status === '0') return 0
  if (route.query.status === '1') return 1
  return undefined
})

function queryWith(overrides: Record<string, string | number | undefined>) {
  const merged: Record<string, string | number | undefined> = {
    status: statusFilter.value !== undefined ? String(statusFilter.value) : undefined,
    keyword: keyword.value || undefined,
    page: page.value,
    ...overrides,
  }
  const query: Record<string, string> = {}
  for (const [key, value] of Object.entries(merged)) {
    if (value !== undefined && value !== '') query[key] = String(value)
  }
  return query
}

async function updateQuery(overrides: Record<string, string | number | undefined>) {
  await router.push({ name: 'admin-posts', query: queryWith(overrides) })
}

function setStatus(status?: number) {
  updateQuery({ status: status === undefined ? undefined : String(status), page: 1 })
}

function onSearch() {
  updateQuery({ keyword: keywordInput.value.trim() || undefined, page: 1 })
}

watch(keywordInput, (value) => {
  window.clearTimeout(searchTimer)
  if (value === keyword.value) return
  searchTimer = window.setTimeout(() => {
    updateQuery({ keyword: value.trim() || undefined, page: 1 })
  }, 380)
})

async function loadPosts() {
  loading.value = true
  keywordInput.value = keyword.value
  try {
    const result = await adminApi.posts({
      page: page.value,
      size: 10,
      status: statusFilter.value,
      keyword: keyword.value || undefined,
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

async function toggleStatus(post: PostSummary) {
  statusBusy.value = post.id
  const next = post.status === 1 ? 0 : 1
  try {
    await adminApi.changePostStatus(post.id, next)
    toast.show(next === 1 ? '文章已经发布，去晒太阳吧 ☀' : '文章已收回草稿箱')
    await loadPosts()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '操作失败', 'error')
  } finally {
    statusBusy.value = null
  }
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await adminApi.deletePost(deleteTarget.value.id)
    toast.show('文章已删除')
    deleteTarget.value = null
    await loadPosts()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '删除失败', 'error')
  } finally {
    deleting.value = false
  }
}

watch(
  () => route.query,
  () => loadPosts(),
  { immediate: true },
)
</script>

<template>
  <div class="admin-posts">
    <div class="admin-page-head">
      <div>
        <h1>文章管理</h1>
        <p>共 {{ total }} 篇文章 · 好的故事值得被认真安放</p>
      </div>
      <div class="admin-page-actions">
        <RouterLink :to="{ name: 'admin-post-new' }" class="btn btn-primary">✎ 写新文章</RouterLink>
      </div>
    </div>

    <section class="admin-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <div class="filter-tabs">
            <button type="button" :class="{ active: statusFilter === undefined }" @click="setStatus()">
              全部
            </button>
            <button type="button" :class="{ active: statusFilter === 1 }" @click="setStatus(1)">
              已发布
            </button>
            <button type="button" :class="{ active: statusFilter === 0 }" @click="setStatus(0)">
              草稿箱
            </button>
          </div>
        </div>
        <div class="admin-toolbar-right">
          <div class="admin-search search-input">
            <span class="search-icon">⌕</span>
            <input
              v-model="keywordInput"
              class="input"
              type="search"
              placeholder="搜索文章标题…"
              @keyup.enter="onSearch"
            />
          </div>
          <button type="button" class="btn btn-sm btn-ghost" @click="loadPosts">刷新</button>
        </div>
      </div>

      <div v-if="loading" class="admin-loading">
        <span class="spinner"></span>
        正在整理文章…
      </div>

      <div v-else-if="posts.length" class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>文章</th>
              <th>分类</th>
              <th>状态</th>
              <th>阅读</th>
              <th>更新时间</th>
              <th style="text-align: right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="post in posts" :key="post.id">
              <td class="cell-title">
                <strong>{{ post.title }}</strong>
                <span>/posts/{{ post.slug }}</span>
              </td>
              <td>
                <span
                  v-if="post.category"
                  class="category-dot"
                  :style="{ background: post.category.color || '#8CC8E8' }"
                ></span>
                {{ post.category?.name || '未分类' }}
              </td>
              <td>
                <span class="status" :class="post.status === 1 ? 'status-published' : 'status-draft'">
                  {{ post.status === 1 ? '已发布' : '草稿' }}
                </span>
              </td>
              <td>{{ formatNumber(post.viewCount) }}</td>
              <td class="time-cell">{{ formatDate(post.updatedAt) }}</td>
              <td class="cell-actions">
                <div class="row-actions">
                  <a
                    v-if="post.status === 1"
                    :href="`/posts/${post.slug}`"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="row-action"
                  >
                    预览
                  </a>
                  <RouterLink
                    :to="{ name: 'admin-post-edit', params: { id: post.id } }"
                    class="row-action"
                  >
                    编辑
                  </RouterLink>
                  <button
                    type="button"
                    class="row-action"
                    :disabled="statusBusy === post.id"
                    @click="toggleStatus(post)"
                  >
                    {{ post.status === 1 ? '撤回' : '发布' }}
                  </button>
                  <button type="button" class="row-action danger" @click="deleteTarget = post">
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="admin-empty">
        <span>📝</span>
        <p>还没有文章。要写下第一篇吗？</p>
        <RouterLink :to="{ name: 'admin-post-new' }" class="btn btn-sm btn-primary mt-8">
          开始写作
        </RouterLink>
      </div>

      <div v-if="pages > 1 && !loading" class="admin-pagination">
        <span>第 {{ page }} / {{ pages }} 页 · 共 {{ total }} 篇</span>
        <div class="admin-pagination-actions">
          <button type="button" :disabled="page <= 1" @click="updateQuery({ page: page - 1 })">
            ← 上一页
          </button>
          <button type="button" :disabled="page >= pages" @click="updateQuery({ page: page + 1 })">
            下一页 →
          </button>
        </div>
      </div>
    </section>

    <ConfirmDialog
      :model-value="Boolean(deleteTarget)"
      title="删除这篇文章？"
      :message="`「${deleteTarget?.title || ''}」和它的评论都会一起消失，且无法恢复。`"
      confirm-text="确认删除"
      :loading="deleting"
      @update:model-value="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </div>
</template>

<style scoped>
.category-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 5px;
  border-radius: 50%;
  vertical-align: 1px;
}

.time-cell {
  color: var(--ink-faint);
  font-size: 0.7rem;
  white-space: nowrap;
}

@media (max-width: 620px) {
  .admin-toolbar-left,
  .admin-toolbar-right {
    width: 100%;
  }
  .time-cell {
    display: none;
  }
}
</style>
