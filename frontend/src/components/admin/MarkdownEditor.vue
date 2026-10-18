<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import MarkdownView from '@/components/MarkdownView.vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    minHeight?: string
  }>(),
  {
    placeholder: '开始写下今天的故事吧…',
    minHeight: '420px',
  },
)

const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const mode = ref<'edit' | 'preview' | 'split'>('edit')

const charCount = computed(() => (props.modelValue || '').replace(/\s/g, '').length)
const readingMinutes = computed(() => Math.max(1, Math.ceil(charCount.value / 400)))

const tools = [
  { label: 'H2', title: '二级标题', action: () => prefix('## ') },
  { label: 'B', title: '加粗', action: () => wrap('**', '**', '加粗文字'), bold: true },
  { label: 'I', title: '斜体', action: () => wrap('*', '*', '斜体文字'), italic: true },
  { label: '“', title: '引用', action: () => prefix('> ') },
  { label: '•', title: '列表', action: () => prefix('- ') },
  { label: '</>', title: '代码块', action: () => wrap('```\n', '\n```', '代码') },
  { label: '🔗', title: '链接', action: () => wrap('[', '](https://)', '链接文字') },
  { label: '🖼', title: '图片', action: () => wrap('![', '](https://)', '图片描述') },
]

function update(value: string) {
  emit('update:modelValue', value)
}

function wrap(before: string, after: string, placeholder: string) {
  const textarea = textareaRef.value
  if (!textarea) return
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selected = props.modelValue.slice(start, end) || placeholder
  const next = props.modelValue.slice(0, start) + before + selected + after + props.modelValue.slice(end)
  update(next)
  nextTick(() => {
    textarea.focus()
    textarea.setSelectionRange(start + before.length, start + before.length + selected.length)
  })
}

function prefix(marker: string) {
  const textarea = textareaRef.value
  if (!textarea) return
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const lineStart = props.modelValue.lastIndexOf('\n', start - 1) + 1
  const next =
    props.modelValue.slice(0, lineStart) + marker + props.modelValue.slice(lineStart)
  update(next)
  nextTick(() => {
    textarea.focus()
    textarea.setSelectionRange(start + marker.length, end + marker.length)
  })
}
</script>

<template>
  <div class="markdown-editor">
    <div class="editor-toolbar">
      <div class="editor-tabs">
        <button type="button" :class="{ active: mode === 'edit' }" @click="mode = 'edit'">
          编辑
        </button>
        <button type="button" :class="{ active: mode === 'split' }" @click="mode = 'split'">
          分栏
        </button>
        <button type="button" :class="{ active: mode === 'preview' }" @click="mode = 'preview'">
          预览
        </button>
      </div>
      <div v-if="mode !== 'preview'" class="editor-tools">
        <button
          v-for="tool in tools"
          :key="tool.title"
          type="button"
          class="tool-btn"
          :class="{ bold: tool.bold, italic: tool.italic }"
          :title="tool.title"
          @click="tool.action()"
        >
          {{ tool.label }}
        </button>
      </div>
      <span class="editor-count">{{ charCount }} 字 · 约 {{ readingMinutes }} 分钟</span>
    </div>

    <div class="editor-body" :class="`mode-${mode}`" :style="{ '--editor-min-height': minHeight }">
      <textarea
        v-if="mode !== 'preview'"
        ref="textareaRef"
        class="editor-textarea"
        :value="modelValue"
        :placeholder="placeholder"
        spellcheck="false"
        @input="update(($event.target as HTMLTextAreaElement).value)"
      ></textarea>
      <div v-if="mode !== 'edit'" class="editor-preview" :class="{ empty: !modelValue }">
        <MarkdownView v-if="modelValue" :content="modelValue" />
        <p v-else class="preview-placeholder">预览会出现在这里 ☁</p>
      </div>
    </div>

    <div class="editor-footer">
      <span>☀ 支持 Markdown：标题、列表、引用、代码块、链接</span>
      <span v-if="mode === 'split'" class="footer-tip">左边写作，右边看到成稿</span>
    </div>
  </div>
</template>

<style scoped>
.markdown-editor {
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  overflow: hidden;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 12px;
  background: #fdfaf4;
  border-bottom: 1px solid rgba(105, 135, 160, 0.1);
  flex-wrap: wrap;
}

.editor-tabs {
  display: inline-flex;
  padding: 2px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(105, 135, 160, 0.1);
}

.editor-tabs button {
  padding: 5px 11px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ink-faint);
  font-size: 0.68rem;
  font-weight: 800;
}

.editor-tabs button.active {
  background: #fff;
  color: var(--ink);
  box-shadow: 0 2px 8px rgba(112, 142, 166, 0.1);
}

.editor-tools {
  display: flex;
  align-items: center;
  gap: 2px;
}

.tool-btn {
  min-width: 27px;
  height: 27px;
  padding: 0 6px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--ink-soft);
  font-size: 0.72rem;
  font-weight: 700;
  line-height: 1;
}

.tool-btn:hover {
  background: #fff;
  border-color: var(--line);
  color: var(--ink);
}

.tool-btn.bold {
  font-weight: 900;
}
.tool-btn.italic {
  font-style: italic;
}

.editor-count {
  margin-left: auto;
  color: var(--ink-faint);
  font-size: 0.62rem;
  white-space: nowrap;
}

.editor-body {
  display: grid;
  grid-template-columns: 1fr;
  min-height: var(--editor-min-height);
}

.editor-body.mode-split {
  grid-template-columns: 1fr 1fr;
}

.editor-textarea {
  width: 100%;
  min-height: var(--editor-min-height);
  padding: 18px 20px;
  border: 0;
  background: #fff;
  color: var(--ink);
  font-family: "Cascadia Code", "JetBrains Mono", Consolas, "Microsoft YaHei", monospace;
  font-size: 0.82rem;
  line-height: 1.9;
  resize: vertical;
  outline: none;
}

.editor-textarea::placeholder {
  color: #b8bec7;
}

.editor-preview {
  min-height: var(--editor-min-height);
  max-height: 760px;
  overflow-y: auto;
  padding: 20px 24px;
  background: #fffdf9;
  border-left: 1px solid rgba(105, 135, 160, 0.1);
}

.editor-preview.empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-placeholder {
  color: var(--ink-faint);
  font-size: 0.8rem;
}

.editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 14px;
  border-top: 1px solid rgba(105, 135, 160, 0.08);
  background: #fdfaf4;
  color: var(--ink-faint);
  font-size: 0.6rem;
}

.footer-tip {
  color: #a48b5a;
}

@media (max-width: 760px) {
  .editor-body.mode-split {
    grid-template-columns: 1fr;
  }
  .editor-body.mode-split .editor-preview {
    display: none;
  }
  .editor-count {
    display: none;
  }
}
</style>
