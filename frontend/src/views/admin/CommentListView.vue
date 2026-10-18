<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { adminApi } from '@/api/admin'
import type { Comment } from '@/api/types'
import { useToastStore } from '@/stores/toast'
import { formatDate, initialOf } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const toast = useToastStore()

const comments = ref<Comment[]>([])
const total = ref(0)
const pages = ref(0)
const loading = ref(true)
const busyId = ref<number | null>(null)
const deleteTarget = ref<Comment | null>(null)
const deleting = ref(false)

const page = computed(() => Number(route.query.page) || 1)
const statusFilter = computed<number | undefined>(() => {
  if (!route.query.status || route.query.status === 'all') return undefined
  const value = Number(route.query.status)
  return Number.isNaN(value) ? undefined : value
})

const statusMeta = (status: number | null) => {
  if (status === 0) return { text: '待审核', cls: 'status-pending' }
  if (status === 1) return { text: '已通过', cls: 'status-approved' }
  return { text: '已拒绝', cls: 'status-rejected' }
}

async function updateQuery(overrides: Record<string, string | number | undefined>) {
  const merged: Record<string, string | number | undefined> = {
    status: statusFilter.value !== undefined ? String(statusFilter.value) : undefined,
    page: page.value,
    ...overrides,
  }
  const query: Record<string, string> = {}
  for (const [key, value] of Object.entries(merged)) {
    if (value !== undefined && value !== '') query[key] = String(value)
  }
  await router.push({ name: 'admin-comments', query })
}

async function loadComments() {
  loading.value = true
  try {
    const result = await adminApi.comments({
      page: page.value,
      size: 10,
      status: statusFilter.value,
    })
    comments.value = result.records
    total.value = result.total
    pages.value = result.pages
  } finally {
    loading.value = false
  }
}

async function moderate(comment: Comment, status: number) {
  busyId.value = comment.id
  try {
    await adminApi.moderateComment(comment.id, status)
    toast.show(status === 1 ? '评论已通过审核，出现在文章下面啦 ☀' : '评论已标记为拒绝')
    await loadComments()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '操作失败', 'error')
  } finally {
    busyId.value = null
  }
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await adminApi.deleteComment(deleteTarget.value.id)
    toast.show('评论已删除')
    deleteTarget.value = null
    await loadComments()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '删除失败', 'error')
  } finally {
    deleting.value = false
  }
}

watch(
  () => route.query,
  () => loadComments(),
  { immediate: true },
)
</script>

<template>
  <div class="admin-comments">
    <div class="admin-page-head">
      <div>
        <h1>评论管理</h1>
        <p>共 {{ total }} 条评论 · 温柔地对待每一句回声</p>
      </div>
      <div class="admin-page-actions">
        <button type="button" class="btn btn-sm btn-ghost" @click="loadComments">刷新</button>
      </div>
    </div>

    <section class="admin-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <div class="filter-tabs">
            <button type="button" :class="{ active: statusFilter === 0 }" @click="updateQuery({ status: '0', page: 1 })">
              待审核
            </button>
            <button type="button" :class="{ active: statusFilter === 1 }" @click="updateQuery({ status: '1', page: 1 })">
              已通过
            </button>
            <button type="button" :class="{ active: statusFilter === 2 }" @click="updateQuery({ status: '2', page: 1 })">
              已拒绝
            </button>
            <button type="button" :class="{ active: statusFilter === undefined }" @click="updateQuery({ status: undefined, page: 1 })">
              全部
            </button>
          </div>
        </div>
        <div class="admin-toolbar-right">
          <span class="comment-hint">审核通过后才会出现在前台文章页</span>
        </div>
      </div>

      <div v-if="loading" class="admin-loading">
        <span class="spinner"></span>
        正在收信箱…
      </div>

      <div v-else-if="comments.length" class="comment-manage-list">
        <article v-for="comment in comments" :key="comment.id" class="comment-manage-item">
          <div class="manage-avatar">{{ initialOf(comment.author) }}</div>
          <div class="manage-body">
            <div class="manage-head">
              <strong>{{ comment.author }}</strong>
              <span v-if="comment.email" class="manage-email">{{ comment.email }}</span>
              <span class="manage-time">{{ formatDate(comment.createdAt, true) }}</span>
              <span class="status" :class="statusMeta(comment.status).cls">
                {{ statusMeta(comment.status).text }}
              </span>
            </div>
            <p class="manage-content">{{ comment.content }}</p>
            <div class="manage-meta">
              <RouterLink
                :to="{ name: 'admin-post-edit', params: { id: comment.postId } }"
                class="manage-post"
              >
                《{{ comment.postTitle || '已删除的文章' }}》
              </RouterLink>
              <span v-if="comment.parentId" class="manage-reply">回复了 #{{ comment.parentId }}</span>
            </div>
          </div>
          <div class="manage-actions">
            <button
              v-if="comment.status !== 1"
              type="button"
              class="btn btn-sm btn-primary"
              :disabled="busyId === comment.id"
              @click="moderate(comment, 1)"
            >
              ✓ 通过
            </button>
            <button
              v-if="comment.status !== 2"
              type="button"
              class="btn btn-sm btn-ghost"
              :disabled="busyId === comment.id"
              @click="moderate(comment, 2)"
            >
              拒绝
            </button>
            <button
              type="button"
              class="row-action danger"
              @click="deleteTarget = comment"
            >
              删除
            </button>
          </div>
        </article>
      </div>

      <div v-else class="admin-empty">
        <span>💬</span>
        <p>{{ statusFilter === 0 ? '没有待审核的评论，今天很清爽。' : '这里还没有评论。' }}</p>
      </div>

      <div v-if="pages > 1 && !loading" class="admin-pagination">
        <span>第 {{ page }} / {{ pages }} 页 · 共 {{ total }} 条</span>
        <div class="admin-pagination-actions">
          <button type="button" :disabled="page <= 1" @click="updateQuery({ page: page - 1 })">← 上一页</button>
          <button type="button" :disabled="page >= pages" @click="updateQuery({ page: page + 1 })">下一页 →</button>
        </div>
      </div>
    </section>

    <ConfirmDialog
      :model-value="Boolean(deleteTarget)"
      title="删除这条评论？"
      :message="`来自「${deleteTarget?.author || ''}」的评论会被永久删除，无法恢复。`"
      confirm-text="确认删除"
      :loading="deleting"
      @update:model-value="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </div>
</template>

<style scoped>
.comment-hint {
  color: var(--ink-faint);
  font-size: 0.64rem;
}

.comment-manage-list {
  display: flex;
  flex-direction: column;
}

.comment-manage-item {
  display: flex;
  align-items: flex-start;
  gap: 13px;
  padding: 18px 22px;
  border-bottom: 1px solid rgba(105, 135, 160, 0.07);
}

.comment-manage-item:last-child {
  border-bottom: 0;
}

.manage-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border-radius: 12px;
  background: var(--sky-soft);
  color: #5b8dab;
  font-size: 0.9rem;
  font-weight: 900;
}

.manage-body {
  flex: 1;
  min-width: 0;
}

.manage-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
}

.manage-head strong {
  font-size: 0.8rem;
}

.manage-email {
  color: var(--ink-faint);
  font-size: 0.64rem;
}

.manage-time {
  margin-left: auto;
  color: var(--ink-faint);
  font-size: 0.62rem;
}

.manage-content {
  margin-top: 7px;
  color: var(--ink-soft);
  font-size: 0.8rem;
  line-height: 1.75;
  word-break: break-word;
}

.manage-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 7px;
}

.manage-post {
  color: #6d9bb5;
  font-size: 0.66rem;
  font-weight: 700;
}

.manage-post:hover {
  color: #4e86a7;
}

.manage-reply {
  color: var(--ink-faint);
  font-size: 0.62rem;
}

.manage-actions {
  display: flex;
  align-items: center;
  gap: 5px;
  flex: 0 0 auto;
}

.manage-actions .btn {
  min-height: 30px;
  padding: 5px 11px;
  font-size: 0.68rem;
}

@media (max-width: 760px) {
  .comment-manage-item {
    flex-wrap: wrap;
  }
  .manage-body {
    flex: 1 1 calc(100% - 55px);
  }
  .manage-actions {
    width: 100%;
    justify-content: flex-end;
    padding-left: 51px;
  }
  .manage-time {
    width: 100%;
    margin-left: 0;
  }
}
</style>
