<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { agentApi, type AgentArtifact } from '@/api/agent'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const artifacts = ref<AgentArtifact[]>([])
const loading = ref(true)
const deleting = ref('')
const filter = ref('')
const typeFilter = ref('all')

const types = computed(() => {
  const set = new Set(artifacts.value.map((item) => item.type))
  return ['all', ...Array.from(set).sort()]
})

const filtered = computed(() => {
  const needle = filter.value.trim().toLowerCase()
  return artifacts.value.filter((item) => {
    if (typeFilter.value !== 'all' && item.type !== typeFilter.value) return false
    return !needle || item.name.toLowerCase().includes(needle) || item.path.toLowerCase().includes(needle)
  })
})

const totalSize = computed(() => artifacts.value.reduce((sum, item) => sum + item.size, 0))

async function load() {
  loading.value = true
  try {
    artifacts.value = await agentApi.adminArtifacts()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '加载产物失败', 'error')
  } finally {
    loading.value = false
  }
}

async function remove(artifact: AgentArtifact) {
  if (!window.confirm(`确定删除产物「${artifact.name}」吗？\n删除后无法恢复。`)) return
  deleting.value = artifact.path
  try {
    await agentApi.deleteArtifact(artifact.path)
    artifacts.value = artifacts.value.filter((item) => item.path !== artifact.path)
    toast.show('产物已删除')
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '删除失败', 'error')
  } finally {
    deleting.value = ''
  }
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function icon(type: string) {
  if (type === 'pptx') return '◫'
  if (type === 'docx') return '¶'
  if (type === 'xlsx' || type === 'csv') return '▦'
  if (type === 'html') return '</>'
  if (type === 'pdf') return 'PDF'
  if (type === 'image') return '▧'
  if (type === 'markdown' || type === 'text') return '≡'
  return '↓'
}

function isImage(type: string) {
  return type === 'image'
}

onMounted(load)
</script>

<template>
  <div class="admin-agent-artifacts">
    <div class="admin-page-head">
      <div>
        <p class="section-eyebrow">AGENT ARTIFACTS</p>
        <h1>产物文件</h1>
        <p>查看 Agent 生成的 PPT、Word、Excel、HTML、图片和其他文件，可预览、下载或删除。</p>
      </div>
      <button type="button" class="btn btn-ghost" :disabled="loading" @click="load">刷新</button>
    </div>

    <div class="artifact-stats">
      <div class="artifact-stat">
        <span>文件总数</span>
        <strong>{{ artifacts.length }}</strong>
      </div>
      <div class="artifact-stat">
        <span>占用空间</span>
        <strong>{{ formatSize(totalSize) }}</strong>
      </div>
      <div class="artifact-stat wide">
        <span>存储目录</span>
        <strong class="mono">data/agent/artifacts</strong>
      </div>
    </div>

    <section class="admin-panel">
      <div class="artifact-toolbar">
        <input v-model="filter" placeholder="搜索文件名或路径…" />
        <select v-model="typeFilter">
          <option v-for="type in types" :key="type" :value="type">
            {{ type === 'all' ? '全部类型' : type.toUpperCase() }}
          </option>
        </select>
        <span class="artifact-count">{{ filtered.length }} 个文件</span>
      </div>

      <p v-if="loading" class="artifact-empty">正在读取产物目录…</p>
      <p v-else-if="!artifacts.length" class="artifact-empty">
        还没有产物。让 Agent 生成 PPT、Word、HTML 或图片后，这里会出现文件。
      </p>
      <p v-else-if="!filtered.length" class="artifact-empty">没有匹配的文件。</p>

      <div v-else class="artifact-grid">
        <article v-for="artifact in filtered" :key="artifact.path" class="artifact-card">
          <div class="artifact-preview" :class="{ image: isImage(artifact.type) }">
            <img v-if="isImage(artifact.type)" :src="artifact.url" :alt="artifact.name" loading="lazy" />
            <span v-else class="artifact-type-icon">{{ icon(artifact.type) }}</span>
          </div>
          <div class="artifact-body">
            <strong :title="artifact.path">{{ artifact.name }}</strong>
            <small>{{ artifact.type.toUpperCase() }} · {{ formatSize(artifact.size) }}</small>
            <small class="artifact-path">{{ artifact.path }}</small>
            <small v-if="artifact.modifiedAt" class="artifact-time">{{ formatTime(artifact.modifiedAt) }}</small>
          </div>
          <div class="artifact-actions">
            <a
              class="artifact-action download"
              :href="`${artifact.url}&download=1`"
              :download="artifact.name"
              title="下载"
            >下载</a>
            <button
              type="button"
              class="artifact-action danger"
              :disabled="deleting === artifact.path"
              title="删除"
              @click="remove(artifact)"
            >{{ deleting === artifact.path ? '删除中' : '删除' }}</button>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.admin-agent-artifacts { padding-bottom: 40px; }
.artifact-stats { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
.artifact-stat { min-width: 130px; flex: 0 0 auto; padding: 12px 14px; border: 1px solid rgba(98, 119, 139, 0.13); border-radius: 12px; background: #fff; display: flex; flex-direction: column; gap: 4px; }
.artifact-stat.wide { flex: 1; min-width: 220px; }
.artifact-stat span { color: #8b98a4; font-size: 10px; }
.artifact-stat strong { color: #344b5f; font-size: 18px; }
.artifact-stat strong.mono { font-family: Consolas, monospace; font-size: 12px; font-weight: 500; color: #647789; }
.admin-panel { border: 1px solid rgba(98, 119, 139, 0.13); border-radius: 16px; background: #fff; padding: 16px; }
.artifact-toolbar { display: flex; gap: 9px; align-items: center; margin-bottom: 14px; flex-wrap: wrap; }
.artifact-toolbar input, .artifact-toolbar select { border: 1px solid rgba(103, 126, 146, 0.22); border-radius: 9px; padding: 8px 10px; color: #3f5365; background: #fff; font: inherit; font-size: 12px; outline: none; }
.artifact-toolbar input { flex: 1; min-width: 200px; }
.artifact-toolbar input:focus, .artifact-toolbar select:focus { border-color: rgba(56, 92, 122, 0.5); }
.artifact-count { margin-left: auto; color: #94a0aa; font-size: 11px; }
.artifact-empty { padding: 34px 6px; color: #8f9ba5; font-size: 13px; text-align: center; line-height: 1.8; }
.artifact-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
.artifact-card { display: flex; flex-direction: column; border: 1px solid rgba(105, 126, 145, 0.13); border-radius: 12px; overflow: hidden; background: #fbfcfd; transition: 0.16s ease; }
.artifact-card:hover { border-color: rgba(76, 108, 135, 0.35); box-shadow: 0 6px 18px rgba(55, 75, 93, 0.08); transform: translateY(-1px); }
.artifact-preview { display: grid; place-items: center; height: 118px; background: linear-gradient(135deg, #eef3f6, #f8fafb); color: #60798d; overflow: hidden; }
.artifact-preview.image { background: #eef2f5; }
.artifact-preview img { width: 100%; height: 100%; object-fit: cover; }
.artifact-type-icon { display: grid; place-items: center; width: 46px; height: 46px; border-radius: 13px; background: rgba(255, 255, 255, 0.75); color: #4e6d85; font-size: 17px; font-weight: 700; box-shadow: 0 3px 12px rgba(57, 79, 98, 0.08); }
.artifact-body { display: flex; flex-direction: column; gap: 3px; padding: 10px 12px 7px; min-width: 0; }
.artifact-body strong { color: #3c5165; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.artifact-body small { color: #98a4ae; font-size: 10px; }
.artifact-path { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.artifact-time { color: #aeb7bf !important; }
.artifact-actions { display: flex; gap: 6px; padding: 0 12px 11px; margin-top: auto; }
.artifact-action { flex: 1; text-align: center; border: 0; border-radius: 7px; padding: 6px 8px; font-size: 10.5px; text-decoration: none; cursor: pointer; }
.artifact-action.download { background: #edf3f7; color: #456a86; }
.artifact-action.danger { background: #fbeeeb; color: #a25a4d; }
.artifact-action:disabled { opacity: 0.55; cursor: not-allowed; }
@media (max-width: 640px) { .artifact-grid { grid-template-columns: 1fr; } .artifact-count { margin-left: 0; } }
</style>
