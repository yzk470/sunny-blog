<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { agentApi, type AgentSkill } from '@/api/agent'
import { TOKEN_KEY } from '@/api/http'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const skills = ref<AgentSkill[]>([])
const loading = ref(true)
const saving = ref(false)
const editingSlug = ref('')
const name = ref('')
const description = ref('')
const content = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const zipInput = ref<HTMLInputElement | null>(null)

async function load() {
  loading.value = true
  try {
    skills.value = await agentApi.adminSkills()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '加载技能失败', 'error')
  } finally {
    loading.value = false
  }
}

function edit(skill: AgentSkill) {
  editingSlug.value = skill.slug
  name.value = skill.name
  description.value = skill.description
  content.value = skill.body || ''
}

function resetForm() {
  editingSlug.value = ''
  name.value = ''
  description.value = ''
  content.value = ''
}

async function save() {
  if (!name.value.trim()) {
    toast.show('请填写技能名称', 'error')
    return
  }
  saving.value = true
  try {
    await agentApi.saveSkill({
      slug: editingSlug.value || undefined,
      name: name.value.trim(),
      description: description.value.trim(),
      content: content.value,
    })
    toast.show(editingSlug.value ? '技能已更新' : '技能已导入')
    resetForm()
    await load()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function remove(skill: AgentSkill) {
  if (!window.confirm(`确定删除技能「${skill.name}」吗？`)) return
  try {
    await agentApi.deleteSkill(skill.slug)
    toast.show('技能已删除')
    if (editingSlug.value === skill.slug) resetForm()
    await load()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '删除失败', 'error')
  }
}

async function importZip(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const form = new FormData()
  form.append('file', file)
  try {
    const token = localStorage.getItem(TOKEN_KEY)
    const response = await fetch('/api/admin/agent/skills/zip', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form,
    })
    const body = await response.json()
    if (!response.ok || body.code !== 0) throw new Error(body.message || '导入失败')
    toast.show('技能目录已导入')
    resetForm()
    await load()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '导入失败', 'error')
  } finally {
    if (zipInput.value) zipInput.value.value = ''
  }
}

async function importFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const form = new FormData()
  form.append('file', file)
  form.append('name', name.value.trim() || file.name.replace(/\.md$/i, ''))
  form.append('description', description.value.trim())
  try {
    const token = localStorage.getItem(TOKEN_KEY)
    const response = await fetch('/api/admin/agent/skills/upload', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form,
    })
    const body = await response.json()
    if (!response.ok || body.code !== 0) throw new Error(body.message || '导入失败')
    toast.show('SKILL.md 已导入')
    resetForm()
    await load()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '导入失败', 'error')
  } finally {
    if (fileInput.value) fileInput.value.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-agent-skills">
    <div class="admin-page-head">
      <div>
        <p class="section-eyebrow">AGENT SKILLS</p>
        <h1>Agent 技能库</h1>
        <p>每个技能是一个 SKILL.md，Agent 会先看到目录，需要时再读取完整内容。</p>
      </div>
      <button type="button" class="btn btn-ghost" @click="resetForm">新建技能</button>
    </div>

    <div class="agent-admin-grid">
      <section class="admin-panel">
        <div class="admin-panel-head">
          <h2>已加载技能</h2>
          <span>{{ skills.length }}</span>
        </div>
        <p v-if="loading" class="admin-empty">加载中…</p>
        <div v-else-if="!skills.length" class="admin-empty">还没有技能。右侧粘贴或导入一个 SKILL.md。</div>
        <div v-for="skill in skills" :key="skill.slug" class="skill-card">
          <div>
            <strong>{{ skill.name }}</strong>
            <span class="skill-slug">{{ skill.slug }}</span>
            <p>{{ skill.description || '无描述' }}</p>
          </div>
          <div class="skill-actions">
            <button type="button" class="mini-btn" @click="edit(skill)">编辑</button>
            <button type="button" class="mini-btn danger" @click="remove(skill)">删除</button>
          </div>
        </div>
      </section>

      <section class="admin-panel">
        <div class="admin-panel-head">
          <h2>{{ editingSlug ? '编辑技能' : '导入技能' }}</h2>
          <span>SKILL.md</span>
        </div>
        <label class="admin-field">
          <span>名称</span>
          <input v-model="name" maxlength="80" placeholder="例如：数据分析" />
        </label>
        <label class="admin-field">
          <span>描述</span>
          <input v-model="description" maxlength="200" placeholder="一句话说明什么时候使用" />
        </label>
        <label class="admin-field">
          <span>技能正文</span>
          <textarea
            v-model="content"
            rows="12"
            placeholder="写清楚步骤、约束、示例。Agent 读取后会按这里的方法执行。"
          ></textarea>
        </label>
        <div class="skill-form-actions">
          <button type="button" class="btn btn-primary" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : editingSlug ? '保存修改' : '创建技能' }}
          </button>
          <button type="button" class="btn btn-ghost" @click="fileInput?.click()">导入 SKILL.md</button>
          <button type="button" class="btn btn-ghost" @click="zipInput?.click()">导入技能目录 .zip</button>
          <input ref="fileInput" type="file" accept=".md,text/markdown,text/plain" hidden @change="importFile" />
          <input ref="zipInput" type="file" accept=".zip,application/zip" hidden @change="importZip" />
        </div>
        <p class="skill-note">技能目录可包含 SKILL.md、参考 markdown、scripts 和资源文件；打包为 zip 导入即可。只有单个 SKILL.md 的技能也兼容。</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin-agent-skills { padding-bottom: 40px; }
.agent-admin-grid { display: grid; grid-template-columns: 1fr 1.15fr; gap: 20px; align-items: start; }
.admin-panel { padding: 18px; border: 1px solid rgba(98, 119, 139, 0.13); border-radius: 16px; background: #fff; }
.admin-panel-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 13px; }
.admin-panel-head h2 { margin: 0; font-size: 16px; color: #2f4255; }
.admin-panel-head span { color: #9aa4ad; font-size: 11px; }
.admin-empty { color: #8e9aa5; font-size: 13px; padding: 18px 0; }
.skill-card { display: flex; justify-content: space-between; gap: 12px; padding: 12px 0; border-top: 1px solid rgba(107, 128, 147, 0.1); }
.skill-card strong { font-size: 13px; color: #34495c; }
.skill-slug { margin-left: 7px; color: #a1abb4; font-size: 10px; }
.skill-card p { margin: 5px 0 0; color: #7b8995; font-size: 12px; line-height: 1.6; }
.skill-actions { display: flex; gap: 5px; flex: 0 0 auto; }
.mini-btn { border: 1px solid rgba(103, 126, 146, 0.2); border-radius: 7px; padding: 4px 8px; background: #fff; color: #687989; font-size: 11px; cursor: pointer; }
.mini-btn.danger { color: #a25b50; }
.admin-field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px; }
.admin-field span { color: #6f7e8c; font-size: 11px; }
.admin-field input, .admin-field textarea { width: 100%; border: 1px solid rgba(103, 126, 146, 0.22); border-radius: 9px; padding: 9px 10px; color: #34495c; font: inherit; font-size: 13px; outline: none; }
.admin-field textarea { resize: vertical; line-height: 1.7; }
.admin-field input:focus, .admin-field textarea:focus { border-color: rgba(54, 90, 120, 0.5); }
.skill-form-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.skill-note { margin: 12px 0 0; color: #98a2ab; font-size: 11px; line-height: 1.6; }
@media (max-width: 900px) { .agent-admin-grid { grid-template-columns: 1fr; } }
</style>
