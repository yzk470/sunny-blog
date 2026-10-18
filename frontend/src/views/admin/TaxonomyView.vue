<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { adminApi } from '@/api/admin'
import type { Category, Tag } from '@/api/types'
import { useToastStore } from '@/stores/toast'
import { COLOR_PRESETS, categoryEmoji } from '@/utils/constants'

const toast = useToastStore()

const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const loading = ref(true)
const saving = ref(false)

const categoryModal = ref(false)
const editingCategory = ref<Category | null>(null)
const categoryForm = reactive({
  name: '',
  slug: '',
  description: '',
  color: '#8CC8E8',
  sortOrder: 99,
})

const tagModal = ref(false)
const editingTag = ref<Tag | null>(null)
const tagForm = reactive({ name: '', slug: '', color: '#F7B7C8' })

const deleteTarget = ref<{ kind: 'category' | 'tag'; id: number; name: string } | null>(null)
const deleting = ref(false)

async function loadAll() {
  loading.value = true
  try {
    const [categoryResult, tagResult] = await Promise.all([
      adminApi.categories(),
      adminApi.tags(),
    ])
    categories.value = categoryResult
    tags.value = tagResult
  } finally {
    loading.value = false
  }
}

function openCategory(category?: Category) {
  editingCategory.value = category || null
  if (category) {
    categoryForm.name = category.name
    categoryForm.slug = category.slug
    categoryForm.description = category.description || ''
    categoryForm.color = category.color || '#8CC8E8'
    categoryForm.sortOrder = 99
  } else {
    categoryForm.name = ''
    categoryForm.slug = ''
    categoryForm.description = ''
    categoryForm.color = '#8CC8E8'
    categoryForm.sortOrder = 99
  }
  categoryModal.value = true
}

async function saveCategory() {
  if (!categoryForm.name.trim()) {
    toast.show('分类名称不能为空', 'error')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: categoryForm.name.trim(),
      slug: categoryForm.slug.trim() || undefined,
      description: categoryForm.description.trim() || undefined,
      color: categoryForm.color,
      sortOrder: categoryForm.sortOrder,
    }
    if (editingCategory.value) {
      await adminApi.updateCategory(editingCategory.value.id, payload)
      toast.show('分类已经更新')
    } else {
      await adminApi.createCategory(payload)
      toast.show('新分类建好啦')
    }
    categoryModal.value = false
    await loadAll()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

function openTag(tag?: Tag) {
  editingTag.value = tag || null
  if (tag) {
    tagForm.name = tag.name
    tagForm.slug = tag.slug
    tagForm.color = tag.color || '#F7B7C8'
  } else {
    tagForm.name = ''
    tagForm.slug = ''
    tagForm.color = '#F7B7C8'
  }
  tagModal.value = true
}

async function saveTag() {
  if (!tagForm.name.trim()) {
    toast.show('标签名称不能为空', 'error')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: tagForm.name.trim(),
      slug: tagForm.slug.trim() || undefined,
      color: tagForm.color,
    }
    if (editingTag.value) {
      await adminApi.updateTag(editingTag.value.id, payload)
      toast.show('标签已经更新')
    } else {
      await adminApi.createTag(payload)
      toast.show('新标签加上啦')
    }
    tagModal.value = false
    await loadAll()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    if (deleteTarget.value.kind === 'category') {
      await adminApi.deleteCategory(deleteTarget.value.id)
    } else {
      await adminApi.deleteTag(deleteTarget.value.id)
    }
    toast.show('已经删除了')
    deleteTarget.value = null
    await loadAll()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '删除失败', 'error')
  } finally {
    deleting.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="admin-taxonomy">
    <div class="admin-page-head">
      <div>
        <h1>分类与标签</h1>
        <p>给每篇文章找到它的小抽屉</p>
      </div>
      <div class="admin-page-actions">
        <button type="button" class="btn btn-sm btn-ghost" @click="openTag()">＋ 新标签</button>
        <button type="button" class="btn btn-sm btn-primary" @click="openCategory()">＋ 新分类</button>
      </div>
    </div>

    <div class="taxonomy-grid">
      <section class="admin-card">
        <div class="admin-card-head">
          <div>
            <h2>分类</h2>
            <p>{{ categories.length }} 个分类 · 文章的主目录</p>
          </div>
          <button type="button" class="row-action" @click="openCategory()">＋ 添加</button>
        </div>
        <div v-if="loading" class="admin-loading"><span class="spinner"></span>加载中…</div>
        <div v-else-if="categories.length" class="category-list">
          <div v-for="category in categories" :key="category.id" class="category-row">
            <span
              class="category-icon"
              :style="{ background: `${category.color || '#8CC8E8'}33` }"
            >
              {{ categoryEmoji(category.slug) }}
            </span>
            <div class="category-info">
              <strong>{{ category.name }}</strong>
              <span>/{{ category.slug }}</span>
              <p v-if="category.description">{{ category.description }}</p>
            </div>
            <span
              class="category-color"
              :style="{ background: category.color || '#8CC8E8' }"
            ></span>
            <div class="row-actions">
              <button type="button" class="row-action" @click="openCategory(category)">编辑</button>
              <button
                type="button"
                class="row-action danger"
                @click="deleteTarget = { kind: 'category', id: category.id, name: category.name }"
              >
                删除
              </button>
            </div>
          </div>
        </div>
        <div v-else class="admin-empty"><span>🏷️</span>还没有分类。</div>
      </section>

      <section class="admin-card">
        <div class="admin-card-head">
          <div>
            <h2>标签</h2>
            <p>{{ tags.length }} 个标签 · 文章的彩色便签</p>
          </div>
          <button type="button" class="row-action" @click="openTag()">＋ 添加</button>
        </div>
        <div v-if="loading" class="admin-loading"><span class="spinner"></span>加载中…</div>
        <div v-else-if="tags.length" class="tag-manage-grid">
          <div
            v-for="tag in tags"
            :key="tag.id"
            class="tag-manage-item"
            :style="{ '--tag-color': tag.color || '#8CC8E8' }"
          >
            <span class="tag-dot"></span>
            <div class="tag-manage-info">
              <strong># {{ tag.name }}</strong>
              <span>/{{ tag.slug }}</span>
            </div>
            <div class="tag-actions">
              <button type="button" class="tag-action" title="编辑" @click="openTag(tag)">✎</button>
              <button
                type="button"
                class="tag-action danger"
                title="删除"
                @click="deleteTarget = { kind: 'tag', id: tag.id, name: tag.name }"
              >
                ×
              </button>
            </div>
          </div>
        </div>
        <div v-else class="admin-empty"><span>#</span>还没有标签。</div>
      </section>
    </div>

    <ModalDialog
      :model-value="categoryModal"
      :title="editingCategory ? '编辑分类' : '新建分类'"
      subtitle="分类是文章的主目录，建议保持精简。"
      width="500px"
      @update:model-value="categoryModal = false"
    >
      <div class="admin-form">
        <div class="admin-form-grid">
          <label class="field">
            <span class="field-label">名称 *</span>
            <input v-model="categoryForm.name" class="input" maxlength="40" placeholder="例如：生活随笔" />
          </label>
          <label class="field">
            <span class="field-label">slug <span class="hint">留空自动生成</span></span>
            <input v-model="categoryForm.slug" class="input" maxlength="80" placeholder="life" />
          </label>
        </div>
        <label class="field">
          <span class="field-label">描述</span>
          <input v-model="categoryForm.description" class="input" maxlength="200" placeholder="一句话介绍这个分类" />
        </label>
        <div class="field">
          <span class="field-label">颜色</span>
          <div class="color-row">
            <button
              v-for="color in COLOR_PRESETS"
              :key="color"
              type="button"
              class="color-swatch"
              :class="{ active: categoryForm.color === color }"
              :style="{ background: color }"
              :aria-label="color"
              @click="categoryForm.color = color"
            ></button>
            <input v-model="categoryForm.color" type="color" class="color-input" aria-label="自定义颜色" />
          </div>
        </div>
        <div class="category-preview">
          <span class="category-icon" :style="{ background: `${categoryForm.color}33` }">
            {{ categoryEmoji(categoryForm.slug || categoryForm.name) }}
          </span>
          <strong>{{ categoryForm.name || '分类名称' }}</strong>
          <span class="preview-color" :style="{ background: categoryForm.color }"></span>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="categoryModal = false">取消</button>
        <button class="btn btn-primary" type="button" :disabled="saving" @click="saveCategory">
          {{ saving ? '保存中…' : '保存分类' }}
        </button>
      </template>
    </ModalDialog>

    <ModalDialog
      :model-value="tagModal"
      :title="editingTag ? '编辑标签' : '新建标签'"
      subtitle="给文章贴上一枚彩色便签。"
      width="480px"
      @update:model-value="tagModal = false"
    >
      <div class="admin-form">
        <div class="admin-form-grid">
          <label class="field">
            <span class="field-label">名称 *</span>
            <input v-model="tagForm.name" class="input" maxlength="30" placeholder="例如：阳光" />
          </label>
          <label class="field">
            <span class="field-label">slug <span class="hint">留空自动生成</span></span>
            <input v-model="tagForm.slug" class="input" maxlength="80" placeholder="sunshine" />
          </label>
        </div>
        <div class="field">
          <span class="field-label">颜色</span>
          <div class="color-row">
            <button
              v-for="color in COLOR_PRESETS"
              :key="color"
              type="button"
              class="color-swatch"
              :class="{ active: tagForm.color === color }"
              :style="{ background: color }"
              :aria-label="color"
              @click="tagForm.color = color"
            ></button>
            <input v-model="tagForm.color" type="color" class="color-input" aria-label="自定义颜色" />
          </div>
        </div>
        <div class="tag-preview">
          <span class="chip tag-color" :style="{ '--tag-color': tagForm.color }">
            # {{ tagForm.name || '标签名称' }}
          </span>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="tagModal = false">取消</button>
        <button class="btn btn-primary" type="button" :disabled="saving" @click="saveTag">
          {{ saving ? '保存中…' : '保存标签' }}
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      :model-value="Boolean(deleteTarget)"
      :title="deleteTarget?.kind === 'category' ? '删除这个分类？' : '删除这个标签？'"
      :message="
        deleteTarget?.kind === 'category'
          ? `「${deleteTarget?.name}」下如果还有文章，需要先移走文章才能删除。`
          : `「${deleteTarget?.name}」会从所有文章上取下，文章本身不受影响。`
      "
      confirm-text="确认删除"
      :loading="deleting"
      @update:model-value="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </div>
</template>

<style scoped>
.taxonomy-grid {
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 16px;
  align-items: start;
}

.category-list {
  display: flex;
  flex-direction: column;
}

.category-row {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px 20px;
  border-bottom: 1px solid rgba(105, 135, 160, 0.07);
}

.category-row:last-child {
  border-bottom: 0;
}

.category-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border-radius: 11px;
  font-size: 1rem;
}

.category-info {
  flex: 1;
  min-width: 0;
}

.category-info strong {
  font-size: 0.8rem;
}

.category-info > span {
  margin-left: 5px;
  color: var(--ink-faint);
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 0.6rem;
}

.category-info p {
  overflow: hidden;
  margin-top: 2px;
  color: var(--ink-faint);
  font-size: 0.64rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-color {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.tag-manage-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 6px;
  padding: 14px 18px 18px;
}

.tag-manage-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 11px;
  border-radius: 11px;
  background: color-mix(in srgb, var(--tag-color) 10%, white);
  border: 1px solid color-mix(in srgb, var(--tag-color) 20%, white);
}

.tag-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--tag-color);
  flex: 0 0 auto;
}

.tag-manage-info {
  display: flex;
  align-items: baseline;
  gap: 5px;
  flex: 1;
  min-width: 0;
}

.tag-manage-info strong {
  font-size: 0.74rem;
}

.tag-manage-info span {
  color: var(--ink-faint);
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 0.58rem;
}

.tag-actions {
  display: flex;
  gap: 3px;
}

.tag-action {
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.7);
  color: var(--ink-soft);
  font-size: 0.7rem;
}

.tag-action:hover {
  background: #fff;
  color: #4e86a7;
}

.tag-action.danger:hover {
  color: #c75f78;
  background: #fff0f2;
}

.admin-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 4px;
}

.color-row {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}

.color-swatch {
  width: 28px;
  height: 28px;
  border: 2px solid #fff;
  border-radius: 9px;
  box-shadow: 0 0 0 1px rgba(105, 135, 160, 0.15);
}

.color-swatch.active {
  box-shadow: 0 0 0 2px #fff, 0 0 0 4px var(--sky-strong);
}

.color-input {
  width: 34px;
  height: 30px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
}

.category-preview {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 11px 13px;
  border-radius: 11px;
  background: #fdfaf4;
  border: 1px dashed var(--line);
}

.category-preview strong {
  font-size: 0.78rem;
}

.preview-color {
  margin-left: auto;
  width: 20px;
  height: 20px;
  border-radius: 7px;
}

.tag-preview {
  padding: 4px 0 6px;
}

@media (max-width: 960px) {
  .taxonomy-grid {
    grid-template-columns: 1fr;
  }
}
</style>
