<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import { adminApi } from '@/api/admin'
import type { Guestbook } from '@/api/types'
import { useToastStore } from '@/stores/toast'
import { formatDate, initialOf } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const toast = useToastStore()

const messages = ref<Guestbook[]>([])
const total = ref(0)
const pages = ref(0)
const loading = ref(true)
const busyId = ref<number | null>(null)
const deleteTarget = ref<Guestbook | null>(null)
const deleting = ref(false)
const replyTarget = ref<Guestbook | null>(null)
const replyText = ref('')
const replying = ref(false)

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
  await router.push({ name: 'admin-guestbook', query })
}

async function loadMessages() {
  loading.value = true
  try {
    const result = await adminApi.guestbook({
      page: page.value,
      size: 10,
      status: statusFilter.value,
    })
    messages.value = result.records
    total.value = result.total
    pages.value = result.pages
  } finally {
    loading.value = false
  }
}

async function moderate(message: Guestbook, status: number) {
  busyId.value = message.id
  try {
    await adminApi.moderateGuestbook(message.id, status)
    toast.show(status === 1 ? '留言已通过，出现在留言墙上了 ☁' : '留言已标记为拒绝')
    await loadMessages()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '操作失败', 'error')
  } finally {
    busyId.value = null
  }
}

function openReply(message: Guestbook) {
  replyTarget.value = message
  replyText.value = message.reply || ''
}

async function saveReply() {
  if (!replyTarget.value) return
  if (!replyText.value.trim()) {
    toast.show('回复内容不能为空', 'error')
    return
  }
  replying.value = true
  try {
    await adminApi.replyGuestbook(replyTarget.value.id, replyText.value.trim())
    toast.show('回复已送达，像寄出一张明信片 ☀')
    replyTarget.value = null
    await loadMessages()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '回复失败', 'error')
  } finally {
    replying.value = false
  }
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await adminApi.deleteGuestbook(deleteTarget.value.id)
    toast.show('留言已删除')
    deleteTarget.value = null
    await loadMessages()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '删除失败', 'error')
  } finally {
    deleting.value = false
  }
}

watch(
  () => route.query,
  () => loadMessages(),
  { immediate: true },
)
</script>

<template>
  <div class="admin-guestbook">
    <div class="admin-page-head">
      <div>
        <h1>留言管理</h1>
        <p>共 {{ total }} 条留言 · 云朵墙上的每一句话</p>
      </div>
      <div class="admin-page-actions">
        <button type="button" class="btn btn-sm btn-ghost" @click="loadMessages">刷新</button>
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
          <span class="guestbook-hint">通过后会显示在留言墙</span>
        </div>
      </div>

      <div v-if="loading" class="admin-loading">
        <span class="spinner"></span>
        正在看看云朵…
      </div>

      <div v-else-if="messages.length" class="guestbook-manage-list">
        <article v-for="message in messages" :key="message.id" class="guestbook-manage-item">
          <div class="manage-avatar">{{ initialOf(message.author) }}</div>
          <div class="manage-body">
            <div class="manage-head">
              <strong>{{ message.author }}</strong>
              <span v-if="message.email" class="manage-email">{{ message.email }}</span>
              <span class="manage-time">{{ formatDate(message.createdAt, true) }}</span>
              <span class="status" :class="statusMeta(message.status).cls">
                {{ statusMeta(message.status).text }}
              </span>
            </div>
            <p class="manage-content">{{ message.content }}</p>
            <div v-if="message.reply" class="manage-reply">
              <span>☀ 站长回复</span>
              <p>{{ message.reply }}</p>
            </div>
          </div>
          <div class="manage-actions">
            <button
              v-if="message.status !== 1"
              type="button"
              class="btn btn-sm btn-primary"
              :disabled="busyId === message.id"
              @click="moderate(message, 1)"
            >
              ✓ 通过
            </button>
            <button
              v-if="message.status !== 2"
              type="button"
              class="btn btn-sm btn-ghost"
              :disabled="busyId === message.id"
              @click="moderate(message, 2)"
            >
              拒绝
            </button>
            <button type="button" class="row-action" @click="openReply(message)">
              {{ message.reply ? '改回复' : '回复' }}
            </button>
            <button type="button" class="row-action danger" @click="deleteTarget = message">
              删除
            </button>
          </div>
        </article>
      </div>

      <div v-else class="admin-empty">
        <span>☁</span>
        <p>{{ statusFilter === 0 ? '没有待审核的留言。' : '留言墙还空着。' }}</p>
      </div>

      <div v-if="pages > 1 && !loading" class="admin-pagination">
        <span>第 {{ page }} / {{ pages }} 页 · 共 {{ total }} 条</span>
        <div class="admin-pagination-actions">
          <button type="button" :disabled="page <= 1" @click="updateQuery({ page: page - 1 })">← 上一页</button>
          <button type="button" :disabled="page >= pages" @click="updateQuery({ page: page + 1 })">下一页 →</button>
        </div>
      </div>
    </section>

    <ModalDialog
      :model-value="Boolean(replyTarget)"
      title="回复这条留言"
      :subtitle="replyTarget ? `写给 ${replyTarget.author}` : ''"
      width="480px"
      @update:model-value="replyTarget = null"
    >
      <p v-if="replyTarget" class="reply-quote">{{ replyTarget.content }}</p>
      <label class="field">
        <span class="field-label">回复内容</span>
        <textarea
          v-model="replyText"
          class="textarea"
          maxlength="1000"
          placeholder="写一句温柔的回复…"
        ></textarea>
      </label>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="replyTarget = null">取消</button>
        <button class="btn btn-primary" type="button" :disabled="replying" @click="saveReply">
          <span v-if="replying" class="spinner small-spinner"></span>
          送出回复
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      :model-value="Boolean(deleteTarget)"
      title="删除这条留言？"
      :message="`来自「${deleteTarget?.author || ''}」的留言会被永久删除，无法恢复。`"
      confirm-text="确认删除"
      :loading="deleting"
      @update:model-value="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </div>
</template>

<style scoped>
.guestbook-hint {
  color: var(--ink-faint);
  font-size: 0.64rem;
}

.guestbook-manage-list {
  display: flex;
  flex-direction: column;
}

.guestbook-manage-item {
  display: flex;
  align-items: flex-start;
  gap: 13px;
  padding: 18px 22px;
  border-bottom: 1px solid rgba(105, 135, 160, 0.07);
}

.guestbook-manage-item:last-child {
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
  background: var(--pink-soft);
  color: #b76580;
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

.manage-reply {
  margin-top: 8px;
  padding: 9px 12px;
  border-radius: 9px;
  background: #fdfaf4;
  border-left: 3px solid var(--pink);
}

.manage-reply span {
  color: #b76580;
  font-size: 0.6rem;
  font-weight: 800;
}

.manage-reply p {
  margin-top: 2px;
  color: var(--ink-soft);
  font-size: 0.72rem;
  line-height: 1.6;
}

.manage-actions {
  display: flex;
  align-items: center;
  gap: 5px;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.manage-actions .btn {
  min-height: 30px;
  padding: 5px 11px;
  font-size: 0.68rem;
}

.reply-quote {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 9px;
  background: #fdfaf4;
  color: var(--ink-faint);
  font-size: 0.74rem;
  line-height: 1.7;
}

.small-spinner {
  width: 15px;
  height: 15px;
  border-width: 2px;
}

@media (max-width: 760px) {
  .guestbook-manage-item {
    flex-wrap: wrap;
  }
  .manage-body {
    flex: 1 1 calc(100% - 55px);
  }
  .manage-actions {
    width: 100%;
    padding-left: 51px;
  }
  .manage-time {
    width: 100%;
  }
}
</style>
