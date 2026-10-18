<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { blogApi } from '@/api/blog'
import type { Comment } from '@/api/types'
import { initialOf, relativeTime } from '@/utils/format'
import { useToastStore } from '@/stores/toast'

const props = defineProps<{ slug: string }>()

const toast = useToastStore()
const comments = ref<Comment[]>([])
const total = ref(0)
const page = ref(1)
const pages = ref(0)
const loading = ref(true)
const submitting = ref(false)
const loaded = ref(false)
const replyTo = ref<Comment | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const formWrap = ref<HTMLElement | null>(null)

const form = reactive({
  author: '',
  email: '',
  website: '',
  content: '',
})

const formTitle = computed(() =>
  replyTo.value ? `回复 @${replyTo.value.author}` : '留下你的回声',
)

const avatarColors = ['#F7B7C8', '#A9D8F5', '#BFE3D0', '#FFE3A3', '#C9C3F0', '#F3C6A5']
function avatarColor(author: string): string {
  let hash = 0
  for (const ch of author) hash = (hash * 31 + ch.charCodeAt(0)) % 997
  return avatarColors[hash % avatarColors.length]
}

async function loadComments(nextPage = 1, append = false) {
  if (!append) loading.value = true
  try {
    const result = await blogApi.comments(props.slug, nextPage, 20)
    comments.value = append ? [...comments.value, ...result.records] : result.records
    total.value = result.total
    page.value = result.page
    pages.value = result.pages
  } catch {
    if (!append) comments.value = []
  } finally {
    loading.value = false
    loaded.value = true
  }
}

function startReply(comment: Comment) {
  replyTo.value = comment
  formWrap.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  window.setTimeout(() => textareaRef.value?.focus(), 260)
}

function cancelReply() {
  replyTo.value = null
  form.content = ''
}

async function submit() {
  if (!form.author.trim()) {
    toast.show('请先留下你的昵称', 'error')
    return
  }
  if (!form.content.trim()) {
    toast.show('评论内容不能为空', 'error')
    return
  }
  submitting.value = true
  try {
    await blogApi.createComment(props.slug, {
      author: form.author.trim(),
      email: form.email.trim() || undefined,
      website: form.website.trim() || undefined,
      content: form.content.trim(),
      parentId: replyTo.value?.id ?? null,
    })
    form.content = ''
    replyTo.value = null
    toast.show('评论已送达，审核通过后就会出现在这里 ☀', 'success', 4200)
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '评论发送失败', 'error')
  } finally {
    submitting.value = false
  }
}

onMounted(() => loadComments())
</script>

<template>
  <section class="comments-section">
    <div class="comments-head">
      <div>
        <p class="section-eyebrow">A LITTLE ECHO</p>
        <h2 class="comments-title">
          <span class="sun-dot small-dot"></span>
          讨论区
          <span class="comment-count">{{ total }}</span>
        </h2>
      </div>
      <p class="comments-note">温柔发言，都会被好好看见。</p>
    </div>

    <div v-if="loading" class="loading-row">
      <span class="spinner"></span>
      正在搬来小板凳…
    </div>

    <div v-else-if="comments.length" class="comment-list">
      <article v-for="comment in comments" :key="comment.id" class="comment-item">
        <div
          class="comment-avatar"
          :style="{ background: avatarColor(comment.author) }"
          aria-hidden="true"
        >
          {{ initialOf(comment.author) }}
        </div>
        <div class="comment-main">
          <div class="comment-meta">
            <strong>{{ comment.author }}</strong>
            <span v-if="comment.isAdmin" class="admin-badge">☀ 站长</span>
            <a
              v-if="comment.website"
              :href="comment.website"
              target="_blank"
              rel="noopener noreferrer"
              class="comment-site"
            >
              网站
            </a>
            <span class="comment-time">{{ relativeTime(comment.createdAt) }}</span>
          </div>
          <p v-if="comment.parentId" class="reply-hint">
            回复了楼上的小伙伴
          </p>
          <p class="comment-content">{{ comment.content }}</p>
          <button class="reply-btn" type="button" @click="startReply(comment)">
            ↳ 回复
          </button>
        </div>
      </article>

      <button
        v-if="page < pages"
        type="button"
        class="load-more"
        @click="loadComments(page + 1, true)"
      >
        加载更多回声 ↓
      </button>
    </div>

    <div v-else-if="loaded" class="comment-empty">
      <span class="empty-cloud">☁</span>
      <p>还没有人说话。第一朵云，留给你吧。</p>
    </div>

    <div ref="formWrap" class="comment-form-card">
      <div class="form-head">
        <div>
          <h3>{{ formTitle }}</h3>
          <p>留下昵称就可以啦，邮箱不会公开展示。</p>
        </div>
        <button v-if="replyTo" type="button" class="cancel-reply" @click="cancelReply">
          取消回复 ×
        </button>
      </div>

      <form class="comment-form" @submit.prevent="submit">
        <div class="form-row">
          <label class="field">
            <span class="field-label">昵称 <em>*</em></span>
            <input v-model="form.author" class="input" maxlength="40" placeholder="怎么称呼你？" />
          </label>
          <label class="field">
            <span class="field-label">邮箱 <span class="hint">选填，不公开</span></span>
            <input
              v-model="form.email"
              class="input"
              type="email"
              maxlength="120"
              placeholder="you@example.com"
            />
          </label>
        </div>
        <label class="field">
          <span class="field-label">网站 <span class="hint">选填</span></span>
          <input
            v-model="form.website"
            class="input"
            maxlength="200"
            placeholder="https://your-site.com"
          />
        </label>
        <label class="field">
          <span class="field-label">想说的话 <em>*</em></span>
          <textarea
            ref="textareaRef"
            v-model="form.content"
            class="textarea comment-textarea"
            maxlength="1000"
            placeholder="今天天气很好，或者，只是想说一句你好…"
          ></textarea>
        </label>
        <div class="form-footer">
          <span class="form-tip">评论会先经过审核，再出现在这里 ☀</span>
          <button class="btn btn-primary" type="submit" :disabled="submitting">
            <span v-if="submitting" class="spinner small-spinner"></span>
            {{ submitting ? '正在送达…' : '送出回声' }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<style scoped>
.comments-section {
  margin-top: 56px;
}

.comments-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}

.comments-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
  font-size: clamp(1.35rem, 2.4vw, 1.75rem);
}

.small-dot {
  width: 26px;
  height: 26px;
}

.small-dot::before {
  inset: -6px;
}

.comment-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 26px;
  height: 26px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--pink-soft);
  color: #b76580;
  font-size: 0.72rem;
  font-weight: 900;
}

.comments-note {
  color: var(--ink-faint);
  font-size: 0.76rem;
}

.comment-list {
  border-top: 1px dashed rgba(105, 135, 160, 0.18);
}

.comment-item {
  display: flex;
  gap: 14px;
  padding: 22px 4px;
  border-bottom: 1px dashed rgba(105, 135, 160, 0.16);
}

.comment-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  border-radius: 16px;
  color: #5b5560;
  font-family: var(--font-display);
  font-size: 1rem;
  font-weight: 900;
  box-shadow: 0 6px 14px rgba(112, 142, 166, 0.1);
}

.comment-main {
  flex: 1;
  min-width: 0;
}

.comment-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.comment-meta strong {
  font-size: 0.86rem;
}

.admin-badge {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--butter-soft);
  color: #9c7a33;
  font-size: 0.62rem;
  font-weight: 900;
}

.comment-site {
  color: #6d9bb5;
  font-size: 0.68rem;
  border-bottom: 1px solid rgba(119, 188, 229, 0.4);
}

.comment-time {
  color: var(--ink-faint);
  font-size: 0.68rem;
}

.reply-hint {
  margin-top: 5px;
  color: var(--ink-faint);
  font-size: 0.68rem;
}

.comment-content {
  margin-top: 7px;
  color: #565c69;
  font-size: 0.88rem;
  line-height: 1.85;
  white-space: pre-line;
  word-break: break-word;
}

.reply-btn {
  margin-top: 7px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #7fa7bd;
  font-size: 0.7rem;
  font-weight: 800;
}

.reply-btn:hover {
  color: #4e86a7;
}

.load-more {
  display: block;
  margin: 18px auto 0;
  padding: 9px 20px;
  border: 1px dashed var(--line-strong);
  border-radius: 999px;
  background: transparent;
  color: var(--ink-soft);
  font-size: 0.76rem;
  font-weight: 800;
}

.load-more:hover {
  background: #fff;
  border-style: solid;
}

.comment-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 20px;
  border-top: 1px dashed rgba(105, 135, 160, 0.18);
  color: var(--ink-faint);
  font-size: 0.84rem;
  text-align: center;
}

.empty-cloud {
  font-size: 2rem;
  filter: drop-shadow(0 8px 12px rgba(112, 142, 166, 0.14));
}

.comment-form-card {
  margin-top: 28px;
  padding: 24px 26px;
  border-radius: var(--radius-lg);
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.9), rgba(255, 249, 240, 0.8));
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-sm);
}

.form-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.form-head h3 {
  font-size: 1rem;
}

.form-head p {
  margin-top: 3px;
  color: var(--ink-faint);
  font-size: 0.72rem;
}

.cancel-reply {
  padding: 5px 11px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: #fff;
  color: var(--ink-soft);
  font-size: 0.68rem;
  font-weight: 800;
}

.comment-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.field-label em {
  color: var(--pink-strong);
  font-style: normal;
}

.comment-textarea {
  min-height: 120px;
}

.form-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.form-tip {
  color: var(--ink-faint);
  font-size: 0.68rem;
}

.small-spinner {
  width: 15px;
  height: 15px;
  border-width: 2px;
}

@media (max-width: 640px) {
  .comments-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }
  .form-row {
    grid-template-columns: 1fr;
  }
  .comment-form-card {
    padding: 18px;
  }
  .form-footer {
    flex-direction: column;
    align-items: stretch;
  }
  .form-footer .btn {
    width: 100%;
  }
}
</style>
