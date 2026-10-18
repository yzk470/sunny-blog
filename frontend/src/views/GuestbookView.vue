<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import PageBanner from '@/components/PageBanner.vue'
import EmptyState from '@/components/EmptyState.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { blogApi } from '@/api/blog'
import type { Guestbook } from '@/api/types'
import { useToastStore } from '@/stores/toast'
import { formatDate, initialOf } from '@/utils/format'

const toast = useToastStore()
const messages = ref<Guestbook[]>([])
const total = ref(0)
const page = ref(1)
const pages = ref(0)
const loading = ref(true)
const submitting = ref(false)
const sent = ref(false)
const form = reactive({ author: '', email: '', content: '' })

const noteColors = ['#FFF0F4', '#EAF6FE', '#FFF8E4', '#EFFAF4']
const noteTilts = ['-0.8deg', '0.7deg', '-0.4deg', '0.9deg']

const currentPage = computed(() => page.value)

function noteStyle(index: number) {
  return {
    background: noteColors[index % noteColors.length],
    '--tilt': noteTilts[index % noteTilts.length],
  }
}

async function loadMessages(target = 1) {
  loading.value = true
  try {
    const result = await blogApi.guestbook(target, 8)
    messages.value = result.records
    total.value = result.total
    page.value = result.page
    pages.value = result.pages
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.author.trim()) {
    toast.show('先留下你的昵称吧', 'error')
    return
  }
  if (!form.content.trim()) {
    toast.show('留言内容不能为空', 'error')
    return
  }
  submitting.value = true
  try {
    await blogApi.createGuestbook({
      author: form.author.trim(),
      email: form.email.trim() || undefined,
      content: form.content.trim(),
    })
    form.content = ''
    sent.value = true
    toast.show('留言已经贴上墙啦，审核通过后就会展示 ☀', 'success', 4200)
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '留言失败', 'error')
  } finally {
    submitting.value = false
  }
}

onMounted(() => loadMessages())
</script>

<template>
  <div class="guestbook-page page-shell">
    <div class="container">
      <PageBanner
        eyebrow="LEAVE A LITTLE CLOUD"
        title="留言板"
        description="把今天的好天气留在这里，或者只是说一句「我来过」。每一朵云，都会被认真收藏。"
      >
        <span class="message-count">☁ 已有 {{ total }} 朵云</span>
      </PageBanner>

      <div class="guestbook-layout">
        <aside class="guestbook-form-side">
          <div class="card guestbook-form-card">
            <div class="form-deco">
              <span class="form-sun">☀</span>
              <span class="form-stamp">POSTCARD</span>
            </div>
            <h2>写一张小卡片</h2>
            <p class="form-sub">留下你的昵称就可以啦，邮箱不会公开。</p>

            <form class="guestbook-form" @submit.prevent="submit">
              <label class="field">
                <span class="field-label">昵称 <em>*</em></span>
                <input v-model="form.author" class="input" maxlength="40" placeholder="怎么称呼你？" />
              </label>
              <label class="field">
                <span class="field-label">邮箱 <span class="hint">选填</span></span>
                <input
                  v-model="form.email"
                  class="input"
                  type="email"
                  maxlength="120"
                  placeholder="you@example.com"
                />
              </label>
              <label class="field">
                <span class="field-label">想说的话 <em>*</em></span>
                <textarea
                  v-model="form.content"
                  class="textarea"
                  maxlength="1000"
                  placeholder="今天有什么想分享的吗？"
                ></textarea>
              </label>
              <button class="btn btn-primary btn-block" type="submit" :disabled="submitting">
                <span v-if="submitting" class="spinner small-spinner"></span>
                {{ submitting ? '正在寄出…' : '寄出这张卡片 ☀' }}
              </button>
            </form>

            <div v-if="sent" class="sent-tip">
              <span>✓</span>
              收到啦，等站长审核后就会出现在墙上。
            </div>
            <p class="audit-note">留言会先经过审核，请温柔发言。</p>
          </div>

          <div class="form-side-note">
            <span class="note-icon">☁</span>
            <p>这里没有算法，只有认真读过的眼睛。</p>
          </div>
        </aside>

        <main class="message-wall">
          <div class="wall-head">
            <div>
              <p class="section-eyebrow">THE WALL OF CLOUDS</p>
              <h2 class="section-title"><span class="sun-dot"></span>云朵留言墙</h2>
            </div>
            <span class="wall-meta">第 {{ currentPage }} / {{ Math.max(pages, 1) }} 页</span>
          </div>

          <div v-if="loading" class="message-grid">
            <div v-for="i in 4" :key="i" class="skeleton note-skeleton"></div>
          </div>

          <div v-else-if="messages.length" class="message-grid">
            <article
              v-for="(message, index) in messages"
              :key="message.id"
              class="message-note"
              :style="noteStyle(index)"
            >
              <span class="note-tape"></span>
              <div class="note-head">
                <span class="note-avatar" :style="{ background: noteColors[(index + 1) % noteColors.length] }">
                  {{ initialOf(message.author) }}
                </span>
                <div class="note-author">
                  <strong>{{ message.author }}</strong>
                  <span>{{ formatDate(message.createdAt) }}</span>
                </div>
                <span class="note-stamp">{{ ['☀', '☁', '🌼', '✦'][index % 4] }}</span>
              </div>

              <p class="note-content">{{ message.content }}</p>

              <div v-if="message.reply" class="note-reply">
                <span class="reply-label">☀ 站长回复</span>
                <p>{{ message.reply }}</p>
              </div>
            </article>
          </div>

          <div v-else class="card">
            <EmptyState
              emoji="☁"
              title="留言墙还空着"
              description="第一朵云，留给你吧。"
            />
          </div>

          <PaginationBar
            v-if="pages > 1"
            :page="page"
            :pages="pages"
            :total="total"
            @update:page="loadMessages"
          />
        </main>
      </div>
    </div>
  </div>
</template>

<style scoped>
.guestbook-page {
  overflow-x: clip;
}

.message-count {
  display: inline-flex;
  padding: 7px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.9);
  color: #6f8ca0;
  font-size: 0.76rem;
  font-weight: 800;
}

.guestbook-layout {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 30px;
  align-items: start;
  margin-top: 28px;
}

.guestbook-form-side {
  position: sticky;
  top: 104px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.guestbook-form-card {
  position: relative;
  padding: 30px 26px 24px;
  background:
    radial-gradient(circle at 100% 0%, rgba(255, 227, 163, 0.3), transparent 34%),
    linear-gradient(150deg, rgba(255, 255, 255, 0.96), rgba(255, 249, 240, 0.9));
  overflow: hidden;
}

.form-deco {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.form-sun {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: var(--butter-soft);
  color: var(--butter-strong);
  font-size: 1.2rem;
}

.form-stamp {
  padding: 3px 8px;
  border: 1px dashed rgba(176, 141, 154, 0.45);
  border-radius: 4px;
  color: #b08d9a;
  font-size: 0.56rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  transform: rotate(3deg);
}

.guestbook-form-card h2 {
  font-size: 1.35rem;
}

.form-sub {
  margin-top: 5px;
  color: var(--ink-faint);
  font-size: 0.76rem;
  line-height: 1.7;
}

.guestbook-form {
  display: flex;
  flex-direction: column;
  gap: 13px;
  margin-top: 20px;
}

.guestbook-form .textarea {
  min-height: 130px;
}

.field-label em {
  color: var(--pink-strong);
  font-style: normal;
}

.small-spinner {
  width: 15px;
  height: 15px;
  border-width: 2px;
}

.sent-tip {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 12px;
  padding: 9px 12px;
  border-radius: 10px;
  background: var(--mint-soft);
  color: #4d8c6e;
  font-size: 0.7rem;
  font-weight: 700;
}

.audit-note {
  margin-top: 13px;
  color: var(--ink-faint);
  font-size: 0.64rem;
  text-align: center;
}

.form-side-note {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 248, 228, 0.7);
  border: 1px dashed rgba(246, 201, 107, 0.35);
}

.note-icon {
  font-size: 1.1rem;
}

.form-side-note p {
  color: #a48b5a;
  font-size: 0.7rem;
  line-height: 1.6;
}

.message-wall {
  min-width: 0;
}

.wall-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.wall-head .section-title {
  margin-top: 4px;
  font-size: clamp(1.35rem, 2.4vw, 1.7rem);
}

.wall-meta {
  color: var(--ink-faint);
  font-size: 0.7rem;
}

.message-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.note-skeleton {
  height: 190px;
}

.message-note {
  position: relative;
  padding: 22px 20px 18px;
  border-radius: 4px 18px 18px 18px;
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-sm);
  transform: rotate(var(--tilt, 0deg));
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.message-note:hover {
  transform: rotate(0deg) translateY(-4px);
  box-shadow: var(--shadow-md);
}

.note-tape {
  position: absolute;
  top: -9px;
  left: 28px;
  width: 54px;
  height: 18px;
  background: rgba(255, 255, 255, 0.65);
  border-left: 1px dashed rgba(160, 160, 160, 0.18);
  border-right: 1px dashed rgba(160, 160, 160, 0.18);
  transform: rotate(-2deg);
}

.note-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.note-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border-radius: 13px;
  border: 2px solid #fff;
  color: #5b5560;
  font-size: 0.9rem;
  font-weight: 900;
}

.note-author {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.35;
}

.note-author strong {
  font-size: 0.8rem;
}

.note-author span {
  color: var(--ink-faint);
  font-size: 0.62rem;
}

.note-stamp {
  margin-left: auto;
  font-size: 1.1rem;
  opacity: 0.65;
}

.note-content {
  margin-top: 12px;
  color: #5a5f6b;
  font-size: 0.84rem;
  line-height: 1.85;
  white-space: pre-line;
  word-break: break-word;
}

.note-reply {
  margin-top: 13px;
  padding: 11px 13px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.65);
  border-left: 3px solid var(--pink);
}

.reply-label {
  color: #b76580;
  font-size: 0.62rem;
  font-weight: 900;
  letter-spacing: 0.04em;
}

.note-reply p {
  margin-top: 3px;
  color: var(--ink-soft);
  font-size: 0.76rem;
  line-height: 1.7;
}

@media (max-width: 960px) {
  .guestbook-layout {
    grid-template-columns: 1fr;
  }
  .guestbook-form-side {
    position: static;
  }
  .guestbook-form-card {
    max-width: 560px;
  }
}

@media (max-width: 620px) {
  .message-grid {
    grid-template-columns: 1fr;
  }
  .wall-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }
}
</style>
