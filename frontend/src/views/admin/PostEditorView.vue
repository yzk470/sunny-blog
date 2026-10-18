<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import MarkdownEditor from '@/components/admin/MarkdownEditor.vue'
import { adminApi } from '@/api/admin'
import type { Category, Tag } from '@/api/types'
import { useToastStore } from '@/stores/toast'
import { COVER_PRESETS, coverOf } from '@/utils/constants'
import { formatDate, formatNumber } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const toast = useToastStore()

const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const loading = ref(true)
const saving = ref<'draft' | 'publish' | null>(null)
const loadError = ref('')
const stats = ref<{ views: number; likes: number; comments: number; createdAt: string } | null>(null)

const form = reactive({
  title: '',
  slug: '',
  summary: '',
  content: '',
  coverImage: '/covers/morning.svg',
  categoryId: 0,
  tagIds: [] as number[],
  status: 0,
  isTop: false,
  viewCount: 0,
  likeCount: 0,
  commentCount: 0,
  readingTime: 1,
  createdAt: '',
})

const isEdit = computed(() => Boolean(Number(route.params.id)))
const postId = computed(() => Number(route.params.id) || 0)
const activeCategory = computed(() => categories.value.find((item) => item.id === form.categoryId))
const charCount = computed(() => form.content.replace(/\s/g, '').length)
const summaryCount = computed(() => form.summary.length)

function resetForm() {
  form.title = ''
  form.slug = ''
  form.summary = ''
  form.content = ''
  form.coverImage = '/covers/morning.svg'
  form.categoryId = categories.value[0]?.id ?? 0
  form.tagIds = []
  form.status = 0
  form.isTop = false
  stats.value = null
  loadError.value = ''
}

async function loadMeta() {
  const [categoryResult, tagResult] = await Promise.all([adminApi.categories(), adminApi.tags()])
  categories.value = categoryResult
  tags.value = tagResult
}

async function loadPost() {
  loading.value = true
  loadError.value = ''
  try {
    const post = await adminApi.post(postId.value)
    form.title = post.title
    form.slug = post.slug
    form.summary = post.summary || ''
    form.content = post.content
    form.coverImage = post.coverImage || ''
    form.categoryId = post.categoryId ?? categories.value[0]?.id ?? 0
    form.tagIds = [...post.tagIds]
    form.status = post.status
    form.isTop = post.isTop
    stats.value = {
      views: post.viewCount,
      likes: post.likeCount,
      comments: post.commentCount,
      createdAt: post.createdAt,
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '文章加载失败'
  } finally {
    loading.value = false
  }
}

async function init() {
  loading.value = true
  try {
    await loadMeta()
    if (isEdit.value) {
      await loadPost()
    } else {
      resetForm()
      loading.value = false
    }
  } finally {
    if (!isEdit.value) loading.value = false
  }
}

function toggleTag(id: number) {
  const index = form.tagIds.indexOf(id)
  if (index >= 0) form.tagIds.splice(index, 1)
  else form.tagIds.push(id)
}

async function save(publish?: boolean) {
  if (!form.title.trim()) {
    toast.show('先给文章起一个标题吧', 'error')
    return
  }
  if (!form.categoryId) {
    toast.show('请选择一个分类', 'error')
    return
  }
  if (!form.content.trim()) {
    toast.show('正文还是空的，写一点点也好', 'error')
    return
  }
  const nextStatus = publish === undefined ? form.status : publish ? 1 : 0
  saving.value = nextStatus === 1 ? 'publish' : 'draft'
  try {
    const payload = {
      title: form.title.trim(),
      slug: form.slug.trim() || undefined,
      summary: form.summary.trim() || undefined,
      content: form.content,
      coverImage: form.coverImage || undefined,
      categoryId: form.categoryId,
      tagIds: form.tagIds,
      status: nextStatus,
      isTop: form.isTop,
    }
    const result = isEdit.value
      ? await adminApi.updatePost(postId.value, payload)
      : await adminApi.createPost(payload)
    form.status = result.status
    form.slug = result.slug
    toast.show(
      result.status === 1
        ? isEdit.value
          ? '文章已经更新，继续在阳光下发光 ☀'
          : '新文章发布啦，去前台看看 ☀'
        : '草稿已保存，慢慢写也没关系',
    )
    await router.push({ name: 'admin-posts' })
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '保存失败', 'error')
  } finally {
    saving.value = null
  }
}

function removeCover() {
  form.coverImage = ''
}

onMounted(init)

watch(
  () => route.params.id,
  (value, old) => {
    if (value !== old) init()
  },
)
</script>

<template>
  <div class="post-editor-page">
    <div class="editor-page-head">
      <div>
        <RouterLink :to="{ name: 'admin-posts' }" class="back-link">← 返回文章管理</RouterLink>
        <h1>{{ isEdit ? '编辑文章' : '写新文章' }}</h1>
        <p v-if="isEdit && form.slug">
          前台地址：/posts/{{ form.slug }}
          <a :href="`/posts/${form.slug}`" target="_blank" rel="noopener noreferrer">预览 ↗</a>
        </p>
        <p v-else>把一个想法，慢慢写成一个故事。</p>
      </div>
      <div class="editor-page-actions">
        <button
          type="button"
          class="btn btn-ghost"
          :disabled="Boolean(saving)"
          @click="save(false)"
        >
          {{ saving === 'draft' ? '保存中…' : '保存草稿' }}
        </button>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="Boolean(saving)"
          @click="save(true)"
        >
          <span v-if="saving === 'publish'" class="spinner small-spinner"></span>
          {{ saving === 'publish' ? '发布中…' : form.status === 1 ? '更新并发布' : '发布文章' }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="editor-loading">
      <div class="skeleton title-input-skeleton"></div>
      <div class="skeleton editor-skeleton"></div>
    </div>

    <div v-else-if="loadError" class="admin-card admin-empty">
      <span>☁</span>
      <p>{{ loadError }}</p>
      <RouterLink :to="{ name: 'admin-posts' }" class="btn btn-sm btn-ghost">返回列表</RouterLink>
    </div>

    <div v-else class="editor-layout">
      <div class="editor-main">
        <div class="admin-card title-card">
          <input
            v-model="form.title"
            class="title-input"
            maxlength="120"
            placeholder="给这篇文章起一个喜欢的标题…"
          />
          <div class="slug-row">
            <span class="slug-label">/posts/</span>
            <input
              v-model="form.slug"
              class="slug-input"
              maxlength="160"
              placeholder="留空则自动生成"
            />
          </div>
        </div>

        <div class="admin-card summary-card">
          <div class="field">
            <span class="field-label">
              摘要
              <span class="hint">{{ summaryCount }} / 500 · 留空会自动截取正文</span>
            </span>
            <textarea
              v-model="form.summary"
              class="textarea summary-textarea"
              maxlength="500"
              placeholder="用一两句话介绍这篇文章…"
            ></textarea>
          </div>
        </div>

        <div class="admin-card editor-card">
          <div class="editor-card-head">
            <div>
              <h2>正文</h2>
              <p>支持 Markdown，写完后可以切到预览看看</p>
            </div>
            <span class="editor-char">{{ charCount }} 字</span>
          </div>
          <MarkdownEditor v-model="form.content" min-height="480px" />
        </div>
      </div>

      <aside class="editor-sidebar">
        <div class="admin-card setting-card">
          <div class="setting-head">
            <h2>发布设置</h2>
            <span class="status" :class="form.status === 1 ? 'status-published' : 'status-draft'">
              {{ form.status === 1 ? '已发布' : '草稿' }}
            </span>
          </div>

          <div class="setting-block">
            <span class="setting-label">分类</span>
            <div class="category-options">
              <button
                v-for="category in categories"
                :key="category.id"
                type="button"
                class="category-option"
                :class="{ active: form.categoryId === category.id }"
                :style="{ '--cat-color': category.color || '#8CC8E8' }"
                @click="form.categoryId = category.id"
              >
                {{ category.name }}
              </button>
            </div>
          </div>

          <div class="setting-block">
            <span class="setting-label">
              标签
              <small>可多选</small>
            </span>
            <div class="tag-options">
              <button
                v-for="tag in tags"
                :key="tag.id"
                type="button"
                class="tag-option"
                :class="{ active: form.tagIds.includes(tag.id) }"
                :style="{ '--tag-color': tag.color || '#8CC8E8' }"
                @click="toggleTag(tag.id)"
              >
                # {{ tag.name }}
              </button>
            </div>
          </div>

          <div class="switch-row">
            <div>
              <strong class="switch-title">置顶推荐</strong>
              <p class="switch-desc">会优先出现在首页推荐位</p>
            </div>
            <button
              type="button"
              class="switch"
              :class="{ on: form.isTop }"
              :aria-pressed="form.isTop"
              @click="form.isTop = !form.isTop"
            ></button>
          </div>
        </div>

        <div class="admin-card setting-card">
          <div class="setting-head">
            <h2>封面</h2>
            <button v-if="form.coverImage" type="button" class="text-btn" @click="removeCover">
              移除
            </button>
          </div>
          <div class="cover-preview">
            <img :src="coverOf(form.coverImage)" alt="封面预览" />
            <span class="cover-label">封面预览</span>
          </div>
          <div class="cover-presets">
            <button
              v-for="preset in COVER_PRESETS"
              :key="preset.value"
              type="button"
              class="cover-preset"
              :class="{ active: form.coverImage === preset.value }"
              :title="preset.label"
              @click="form.coverImage = preset.value"
            >
              <img :src="preset.value" :alt="preset.label" />
            </button>
          </div>
          <input
            v-model="form.coverImage"
            class="input cover-url"
            placeholder="也可以粘贴图片地址"
          />
        </div>

        <div v-if="isEdit && stats" class="admin-card meta-card">
          <h2>文章数据</h2>
          <div class="meta-grid">
            <div><strong>{{ formatNumber(stats.views) }}</strong><span>阅读</span></div>
            <div><strong>{{ formatNumber(stats.likes) }}</strong><span>喜欢</span></div>
            <div><strong>{{ formatNumber(stats.comments) }}</strong><span>评论</span></div>
          </div>
          <p class="meta-created">创建于 {{ formatDate(stats.createdAt) }}</p>
        </div>

        <div class="editor-tip">
          <span>☀</span>
          <p>不必一次写完。保存草稿，等灵感来敲门。</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.editor-page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}

.back-link {
  display: inline-block;
  margin-bottom: 7px;
  color: var(--ink-faint);
  font-size: 0.7rem;
  font-weight: 700;
}

.back-link:hover {
  color: #4e86a7;
}

.editor-page-head h1 {
  font-size: 1.55rem;
  letter-spacing: -0.025em;
}

.editor-page-head p {
  margin-top: 4px;
  color: var(--ink-faint);
  font-size: 0.72rem;
}

.editor-page-head p a {
  margin-left: 6px;
  color: #6d9bb5;
  font-weight: 700;
}

.editor-page-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.small-spinner {
  width: 15px;
  height: 15px;
  border-width: 2px;
}

.editor-loading {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.title-input-skeleton {
  height: 110px;
}
.editor-skeleton {
  height: 520px;
}

.editor-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  gap: 18px;
  align-items: start;
}

.editor-main,
.editor-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.title-card {
  padding: 18px 22px 14px;
}

.title-input {
  width: 100%;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  outline: none;
}

.title-input::placeholder {
  color: #c2c8d0;
}

.slug-row {
  display: flex;
  align-items: center;
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px dashed rgba(105, 135, 160, 0.14);
  color: var(--ink-faint);
  font-size: 0.7rem;
}

.slug-label {
  font-family: "Cascadia Code", Consolas, monospace;
  color: #b0b6c0;
}

.slug-input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: var(--ink-soft);
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 0.7rem;
  outline: none;
}

.summary-card {
  padding: 16px 22px;
}

.summary-textarea {
  min-height: 74px;
  resize: vertical;
}

.editor-card {
  padding: 18px;
}

.editor-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.editor-card-head h2 {
  font-size: 0.92rem;
}

.editor-card-head p {
  margin-top: 2px;
  color: var(--ink-faint);
  font-size: 0.64rem;
}

.editor-char {
  color: var(--ink-faint);
  font-size: 0.64rem;
}

/* Sidebar */
.setting-card {
  padding: 18px;
}

.setting-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.setting-head h2 {
  font-size: 0.88rem;
}

.text-btn {
  border: 0;
  background: transparent;
  color: var(--pink-strong);
  font-size: 0.64rem;
  font-weight: 800;
}

.setting-block + .setting-block {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed rgba(105, 135, 160, 0.14);
}

.setting-label {
  display: block;
  margin-bottom: 8px;
  color: var(--ink-soft);
  font-size: 0.7rem;
  font-weight: 800;
}

.setting-label small {
  margin-left: 4px;
  color: var(--ink-faint);
  font-size: 0.6rem;
  font-weight: 500;
}

.category-options,
.tag-options {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.category-option {
  padding: 5px 11px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: #fdfaf4;
  color: var(--ink-soft);
  font-size: 0.68rem;
  font-weight: 700;
}

.category-option:hover {
  border-color: var(--line);
}

.category-option.active {
  background: color-mix(in srgb, var(--cat-color) 22%, white);
  color: color-mix(in srgb, var(--cat-color) 65%, #39404d);
  border-color: color-mix(in srgb, var(--cat-color) 38%, white);
}

.tag-option {
  padding: 3px 8px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--ink-faint);
  font-size: 0.64rem;
  font-weight: 700;
}

.tag-option:hover {
  background: #fdfaf4;
}

.tag-option.active {
  background: color-mix(in srgb, var(--tag-color) 20%, white);
  color: color-mix(in srgb, var(--tag-color) 66%, #39404d);
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed rgba(105, 135, 160, 0.14);
}

.switch-title {
  display: block;
  font-size: 0.7rem;
}

.switch-desc {
  margin-top: 2px;
  color: var(--ink-faint);
  font-size: 0.6rem;
}

.cover-preview {
  position: relative;
  height: 122px;
  border-radius: 12px;
  overflow: hidden;
  background: linear-gradient(135deg, #eaf6fe, #fff0f4);
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-label {
  position: absolute;
  left: 8px;
  bottom: 7px;
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--ink-soft);
  font-size: 0.56rem;
  font-weight: 700;
}

.cover-presets {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 5px;
  margin-top: 8px;
}

.cover-preset {
  height: 42px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 8px;
  overflow: hidden;
  background: #f5f6f8;
}

.cover-preset img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-preset:hover {
  border-color: rgba(169, 216, 245, 0.6);
}

.cover-preset.active {
  border-color: var(--sky-strong);
  box-shadow: 0 0 0 2px rgba(169, 216, 245, 0.3);
}

.cover-url {
  margin-top: 8px;
  font-size: 0.68rem;
  padding: 8px 10px;
}

.meta-card {
  padding: 16px 18px;
}

.meta-card h2 {
  font-size: 0.82rem;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 10px;
  text-align: center;
}

.meta-grid div {
  padding: 8px 4px;
  border-radius: 9px;
  background: #fdfaf4;
}

.meta-grid strong {
  display: block;
  font-size: 0.92rem;
  font-weight: 900;
}

.meta-grid span {
  color: var(--ink-faint);
  font-size: 0.58rem;
}

.meta-created {
  margin-top: 9px;
  color: var(--ink-faint);
  font-size: 0.6rem;
  text-align: center;
}

.editor-tip {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 13px 15px;
  border-radius: 13px;
  background: linear-gradient(135deg, #fff9ec, #fff3f5);
  border: 1px dashed rgba(246, 201, 107, 0.4);
}

.editor-tip span {
  color: var(--butter-strong);
}

.editor-tip p {
  color: #a48b5a;
  font-size: 0.66rem;
  line-height: 1.6;
}

@media (max-width: 980px) {
  .editor-layout {
    grid-template-columns: 1fr;
  }
  .editor-sidebar {
    display: grid;
    grid-template-columns: 1fr 1fr;
    align-items: start;
  }
  .editor-tip {
    grid-column: 1 / -1;
  }
}

@media (max-width: 700px) {
  .editor-page-head {
    flex-direction: column;
    align-items: flex-start;
  }
  .editor-page-actions {
    width: 100%;
  }
  .editor-page-actions .btn {
    flex: 1;
  }
  .title-input {
    font-size: 1.2rem;
  }
  .editor-sidebar {
    grid-template-columns: 1fr;
  }
}
</style>
