<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ragApi, type RagDatabase, type RagFile, type RetrievedChunk } from '@/api/rag'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const databases = ref<RagDatabase[]>([])
const selectedId = ref('')
const files = ref<RagFile[]>([])
const loading = ref(true)
const busy = ref(false)
const newName = ref('')
const newDescription = ref('')
const strategy = ref('FIXED')
const fileInput = ref<HTMLInputElement | null>(null)
const testQuery = ref('')
const testResults = ref<RetrievedChunk[]>([])
const testing = ref(false)

const selected = computed(() => databases.value.find((db) => db.id === selectedId.value) || null)
const totalChunks = computed(() => files.value.reduce((sum, item) => sum + (item.chunks || 0), 0))

async function load() {
  loading.value = true
  try {
    databases.value = await ragApi.adminDatabases()
    if (!selectedId.value && databases.value.length) selectedId.value = databases.value[0].id
    if (selectedId.value) await loadFiles()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '加载知识库失败', 'error')
  } finally {
    loading.value = false
  }
}

async function loadFiles() {
  if (!selectedId.value) {
    files.value = []
    return
  }
  try {
    files.value = await ragApi.files(selectedId.value)
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '加载文件失败', 'error')
  }
}

async function selectDatabase(id: string) {
  selectedId.value = id
  testResults.value = []
  await loadFiles()
}

async function createDatabase() {
  if (!newName.value.trim()) {
    toast.show('请输入知识库名称', 'error')
    return
  }
  busy.value = true
  try {
    const db = await ragApi.createDatabase({ name: newName.value.trim(), description: newDescription.value.trim() })
    newName.value = ''
    newDescription.value = ''
    toast.show('知识库已创建')
    await load()
    await selectDatabase(db.id)
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '创建失败', 'error')
  } finally {
    busy.value = false
  }
}

async function removeDatabase() {
  if (!selected.value) return
  if (!window.confirm(`确定删除知识库「${selected.value.name}」吗？\n文件目录和所有向量都会删除。`)) return
  busy.value = true
  try {
    await ragApi.deleteDatabase(selected.value.id)
    toast.show('知识库已删除')
    selectedId.value = ''
    await load()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '删除失败', 'error')
  } finally {
    busy.value = false
  }
}

async function upload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || !selected.value) return
  busy.value = true
  try {
    await ragApi.upload(selected.value.id, file, strategy.value)
    toast.show(`已索引：${file.name}`)
    await loadFiles()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '上传失败', 'error')
  } finally {
    busy.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

async function removeFile(file: RagFile) {
  if (!selected.value) return
  if (!window.confirm(`确定删除「${file.name}」吗？\n文件夹和向量都会删除。`)) return
  busy.value = true
  try {
    await ragApi.deleteFile(selected.value.id, file.id)
    toast.show('文件已删除')
    await loadFiles()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '删除失败', 'error')
  } finally {
    busy.value = false
  }
}

async function testSearch() {
  if (!selected.value || !testQuery.value.trim()) return
  testing.value = true
  try {
    testResults.value = await ragApi.search(selected.value.id, testQuery.value.trim(), 5)
    if (!testResults.value.length) toast.show('没有检索到相关片段', 'info')
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '检索失败', 'error')
  } finally {
    testing.value = false
  }
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(load)
</script>

<template>
  <div class="rag-admin">
    <div class="admin-page-head">
      <div>
        <p class="section-eyebrow">AGENTIC RAG</p>
        <h1>知识库</h1>
        <p>导入文件、选择切分策略，使用 Qwen-VL-Embedding + Redis 向量集合构建可检索知识库。</p>
      </div>
      <button type="button" class="btn btn-ghost" :disabled="loading" @click="load">刷新</button>
    </div>

    <div class="rag-layout">
      <aside class="rag-panel rag-sidebar">
        <div class="rag-panel-head">
          <h2>向量数据库</h2>
          <span>{{ databases.length }}</span>
        </div>
        <button
          v-for="db in databases"
          :key="db.id"
          type="button"
          class="rag-db-item"
          :class="{ active: db.id === selectedId }"
          @click="selectDatabase(db.id)"
        >
          <strong>{{ db.name }}</strong>
          <small>{{ db.files?.length || 0 }} 文件</small>
        </button>
        <p v-if="!databases.length && !loading" class="rag-empty">还没有知识库。</p>

        <div class="rag-create">
          <p class="rag-form-title">新建知识库</p>
          <input v-model="newName" placeholder="名称，例如：产品手册" />
          <input v-model="newDescription" placeholder="描述（可选）" />
          <button type="button" class="btn btn-primary full" :disabled="busy" @click="createDatabase">创建</button>
        </div>
      </aside>

      <section class="rag-main">
        <div v-if="!selected" class="rag-placeholder">选择或创建一个知识库。</div>
        <template v-else>
          <div class="rag-panel rag-current">
            <div class="rag-current-head">
              <div>
                <p class="section-eyebrow">KNOWLEDGE BASE</p>
                <h2>{{ selected.name }}</h2>
                <p>{{ selected.description || '无描述' }} · {{ files.length }} 个文件 · {{ totalChunks }} 个片段</p>
              </div>
              <button type="button" class="btn btn-ghost danger-text" :disabled="busy" @click="removeDatabase">删除知识库</button>
            </div>

            <div class="rag-upload">
              <select v-model="strategy">
                <option value="FIXED">固定长度切分</option>
                <option value="PARAGRAPH">按段落/标题切分</option>
                <option value="AGENT">Agent 语义切分</option>
              </select>
              <button type="button" class="btn btn-primary" :disabled="busy" @click="fileInput?.click()">
                {{ busy ? '索引中…' : '导入文件' }}
              </button>
              <input ref="fileInput" type="file" hidden @change="upload" />
              <span class="rag-accept">PDF / Word / PPT / Excel / Markdown / HTML / 图片</span>
            </div>

            <div class="rag-file-list">
              <div v-for="file in files" :key="file.id" class="rag-file">
                <span class="rag-file-icon">{{ file.type?.toUpperCase() || 'FILE' }}</span>
                <div class="rag-file-copy">
                  <strong>{{ file.name }}</strong>
                  <small>{{ formatSize(file.size) }} · {{ file.strategy }} · {{ file.chunks }} 片段 · {{ formatTime(file.createdAt) }}</small>
                </div>
                <button type="button" class="mini-btn danger" :disabled="busy" @click="removeFile(file)">删除</button>
              </div>
              <p v-if="!files.length" class="rag-empty">还没有文件。选择切分策略后导入。</p>
            </div>
          </div>

          <div class="rag-panel rag-test">
            <div class="rag-panel-head">
              <h2>检索预览</h2>
              <span>粗检索 + 独立 rerank Agent</span>
            </div>
            <div class="rag-test-row">
              <input v-model="testQuery" placeholder="输入问题，测试知识库检索…" @keyup.enter="testSearch" />
              <button type="button" class="btn btn-primary" :disabled="testing" @click="testSearch">
                {{ testing ? '检索中…' : '检索' }}
              </button>
            </div>
            <div v-if="testResults.length" class="rag-results">
              <article v-for="(item, index) in testResults" :key="`${item.source}-${index}`" class="rag-result">
                <div class="rag-result-head">
                  <strong>{{ item.source }}</strong>
                  <span>{{ item.databaseName }} · score {{ item.score.toFixed(3) }}</span>
                </div>
                <p>{{ item.content }}</p>
              </article>
            </div>
            <p v-else class="rag-empty">检索结果会显示已由 rerank Agent 过滤后的片段。</p>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.rag-admin { padding-bottom: 40px; }
.rag-layout { display: grid; grid-template-columns: 240px 1fr; gap: 18px; align-items: start; }
.rag-panel { border: 1px solid rgba(98, 119, 139, 0.13); border-radius: 16px; background: #fff; padding: 16px; }
.rag-sidebar { position: sticky; top: 20px; }
.rag-panel-head { display: flex; justify-content: space-between; align-items: baseline; gap: 10px; margin-bottom: 12px; }
.rag-panel-head h2, .rag-current h2 { margin: 0; color: #33495c; font-size: 16px; }
.rag-panel-head span { color: #99a4ad; font-size: 10px; }
.rag-db-item { width: 100%; display: flex; flex-direction: column; gap: 3px; border: 0; border-radius: 9px; padding: 9px 10px; background: transparent; text-align: left; cursor: pointer; }
.rag-db-item:hover { background: #f5f8fa; }
.rag-db-item.active { background: #eaf2f7; }
.rag-db-item strong { color: #41586c; font-size: 12px; }
.rag-db-item small { color: #99a4ae; font-size: 10px; }
.rag-create { margin-top: 18px; padding-top: 14px; border-top: 1px solid rgba(105, 126, 145, 0.12); display: flex; flex-direction: column; gap: 7px; }
.rag-form-title { margin: 0 0 2px; color: #8a97a2; font-size: 10px; letter-spacing: 0.06em; }
.rag-create input, .rag-upload select, .rag-test-row input { border: 1px solid rgba(103, 126, 146, 0.22); border-radius: 8px; padding: 8px 9px; color: #405569; font: inherit; font-size: 11px; background: #fff; outline: none; }
.rag-create input:focus, .rag-upload select:focus, .rag-test-row input:focus { border-color: rgba(56, 92, 122, 0.5); }
.rag-main { display: flex; flex-direction: column; gap: 18px; min-width: 0; }
.rag-placeholder { padding: 50px; border: 1px dashed rgba(105, 126, 145, 0.22); border-radius: 16px; color: #98a3ad; text-align: center; }
.rag-current-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; }
.rag-current-head h2 { margin: 4px 0 4px; }
.rag-current-head p { margin: 0; color: #8b98a3; font-size: 11px; }
.danger-text { color: #a35b4f; }
.rag-upload { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin: 16px 0; padding: 11px; border-radius: 10px; background: #f7f9fa; }
.rag-accept { color: #9ca7b0; font-size: 10px; }
.rag-file { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-top: 1px solid rgba(105, 126, 145, 0.1); }
.rag-file-icon { flex: 0 0 auto; display: grid; place-items: center; min-width: 38px; height: 28px; padding: 0 5px; border-radius: 6px; background: #edf2f6; color: #55728a; font-size: 8px; font-weight: 700; }
.rag-file-copy { display: flex; flex-direction: column; gap: 3px; min-width: 0; flex: 1; }
.rag-file-copy strong { color: #40576b; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rag-file-copy small { color: #99a4ad; font-size: 10px; }
.rag-test-row { display: flex; gap: 8px; }
.rag-test-row input { flex: 1; }
.rag-results { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; }
.rag-result { padding: 10px 12px; border-radius: 9px; background: #f8fafb; }
.rag-result-head { display: flex; justify-content: space-between; gap: 8px; }
.rag-result-head strong { color: #456179; font-size: 11px; }
.rag-result-head span { color: #9aa5ae; font-size: 9px; }
.rag-result p { margin: 6px 0 0; color: #5a6d7e; font-size: 11px; line-height: 1.7; white-space: pre-wrap; }
.rag-empty { color: #99a4ad; font-size: 11px; padding: 12px 0; line-height: 1.7; }
.full { width: 100%; }
@media (max-width: 900px) { .rag-layout { grid-template-columns: 1fr; } .rag-sidebar { position: static; } }
</style>
