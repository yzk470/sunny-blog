<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { agentApi, type AgentArtifact, type AgentMemory, type AgentSkill, type AgentStatus } from '@/api/agent'
import { ragApi } from '@/api/rag'

type ActivityKind = 'status' | 'thought' | 'tool' | 'plan' | 'validation'

interface Activity {
  kind: ActivityKind
  title: string
  detail?: string
  ok?: boolean
}

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  activities: Activity[]
  artifacts: AgentArtifact[]
  taskId?: string
  validation?: string
  failed?: boolean
  streaming?: boolean
}

const SESSION_KEY = 'sunny_agent_session_v1'
const MODE_KEY = 'sunny_agent_mode_v1'
const ACTIVE_TASK_KEY = 'sunny_agent_active_task_v1'

interface ActiveTask {
  sessionId: string
  clientTaskId: string
  taskId?: string
  question: string
  mode: string
  knowledgeBaseIds: string[]
  lastSeq: number
  assistantId: string
}

const status = ref<AgentStatus | null>(null)
const skills = ref<AgentSkill[]>([])
const knowledgeBases = ref<Array<{ id: string; name: string; description: string; files: number }>>([])
const selectedKbs = ref<string[]>(JSON.parse(localStorage.getItem('sunny_agent_kbs_v1') || '[]'))
const sessionId = ref(localStorage.getItem(SESSION_KEY) || '')
const mode = ref(localStorage.getItem(MODE_KEY) || 'auto')
const messages = ref<ChatMessage[]>([])
const input = ref('')
const busy = ref(false)
const error = ref('')
const activeTask = ref<ActiveTask | null>(readActiveTask())
const reconnecting = ref(false)
const needsResume = ref(false)
const timelineRef = ref<HTMLElement | null>(null)
let abortController: AbortController | null = null

const canSend = computed(() => !busy.value && input.value.trim().length > 0 && status.value?.configured !== false)
const suggestions = [
  '帮我设计一个月度学习计划，并生成一份可执行的清单',
  '写一个 Python 脚本统计 workspace 里的文件数量和大小',
  '记住：我偏好简洁、先给结论的回答',
]

onMounted(async () => {
  try {
    const [s, k] = await Promise.all([agentApi.status(), agentApi.skills()])
    status.value = s
    skills.value = k
  } catch {
    // Keep the chat usable; status errors are shown on send.
  }
  try {
    knowledgeBases.value = await ragApi.databases()
  } catch {
    knowledgeBases.value = []
  }
  if (sessionId.value) {
    try {
      const session = await agentApi.session(sessionId.value)
      messages.value = session.memories
        .filter((m) => (m.role === 'user' || m.role === 'assistant') && m.content.trim())
        .map(fromMemory)
        .slice(-40)
      if (!messages.value.length) sessionId.value = session.id
    } catch {
      sessionId.value = ''
    }
  }
  await restoreActiveTask()
  await scrollBottom()
})

onBeforeUnmount(() => {
  abortController?.abort()
})

function readActiveTask(): ActiveTask | null {
  try {
    const raw = localStorage.getItem(ACTIVE_TASK_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as ActiveTask
    return parsed && parsed.clientTaskId && parsed.sessionId ? parsed : null
  } catch {
    return null
  }
}

function saveActiveTask(task: ActiveTask | null) {
  activeTask.value = task
  if (!task) localStorage.removeItem(ACTIVE_TASK_KEY)
  else localStorage.setItem(ACTIVE_TASK_KEY, JSON.stringify(task))
}

async function restoreActiveTask() {
  const task = activeTask.value
  if (!task) return
  if (!task.taskId) {
    try {
      const found = await agentApi.taskByClient(task.clientTaskId)
      task.taskId = found.taskId
      saveActiveTask(task)
    } catch {
      saveActiveTask(null)
      return
    }
  }
  try {
    const view = await agentApi.task(task.taskId)
    if (view.status === 'DONE') {
      saveActiveTask(null)
      return
    }
    if (!sessionId.value) {
      sessionId.value = view.sessionId
      localStorage.setItem(SESSION_KEY, view.sessionId)
    }
    const assistant: ChatMessage = newAssistant()
    assistant.taskId = task.taskId
    const already = messages.value.some((m) => m.taskId === task.taskId)
    if (!already) {
      messages.value.push({
        id: `user-${Date.now()}`,
        role: 'user',
        content: view.question,
        activities: [],
        artifacts: [],
      })
      messages.value.push(assistant)
    } else {
      const current = messages.value.find((m) => m.taskId === task.taskId)
      if (current) Object.assign(current, assistant)
    }
    task.assistantId = messages.value[messages.value.length - 1]?.id || assistant.id
    task.lastSeq = 0
    saveActiveTask(task)
    needsResume.value = view.status !== 'RUNNING' && view.status !== 'PAUSED'
    busy.value = true
    await attachActive(assistant, task)
  } catch {
    saveActiveTask(null)
    busy.value = false
  }
}

async function attachActive(assistant: ChatMessage, task: ActiveTask) {
  if (!task.taskId) return
  reconnecting.value = true
  try {
    await agentApi.attach(task.taskId, task.lastSeq, streamHandlers(assistant, task))
  } catch (ex) {
    if (!assistant.content) assistant.content = ex instanceof Error ? ex.message : '连接失败'
    assistant.failed = true
    needsResume.value = true
    reconnecting.value = false
  } finally {
    busy.value = false
    assistant.streaming = false
    reconnecting.value = false
    await scrollBottom()
  }
}

async function retryAttach(assistant: ChatMessage, task: ActiveTask) {
  for (let attempt = 0; attempt < 5; attempt++) {
    await new Promise((resolve) => setTimeout(resolve, 800 * Math.pow(2, attempt)))
    try {
      await agentApi.attach(task.taskId!, task.lastSeq, streamHandlers(assistant, task))
      return
    } catch {
      reconnecting.value = true
    }
  }
  assistant.failed = true
  assistant.streaming = false
  assistant.content ||= '连接已断开，任务仍在后台运行。刷新页面可以继续恢复。'
  error.value = assistant.content
  needsResume.value = true
  reconnecting.value = false
}

function streamHandlers(assistant: ChatMessage, task: ActiveTask) {
  return {
    onEvent(type: string, message: string, detail: string, seq?: number) {
      if (seq && seq > task.lastSeq) {
        task.lastSeq = seq
        saveActiveTask(task)
      }
      if (type === 'session') {
        if (message) {
          task.sessionId = message
          if (!sessionId.value) {
            sessionId.value = message
            localStorage.setItem(SESSION_KEY, message)
          }
        }
        if (detail) {
          task.taskId = detail
          assistant.taskId = detail
          saveActiveTask(task)
        }
        return
      }
      if (type === 'done' && message && message !== '完成') {
        needsResume.value = true
        saveActiveTask(task)
      }
      applyEvent(assistant, type, message, detail)
    },
    onDone() {
      assistant.streaming = false
      if (needsResume.value) return
      saveActiveTask(null)
      busy.value = false
    },
    onError(message: string) {
      assistant.streaming = false
      assistant.failed = true
      if (!assistant.content) assistant.content = message
      error.value = message
      needsResume.value = true
      saveActiveTask(task)
    },
  }
}

function applyEvent(assistant: ChatMessage, type: string, message: string, detail: string) {
  if (type === 'status') activity('status', message || '处理中…')
  else if (type === 'thought') activity('thought', message)
  else if (type === 'plan') activity('plan', message, detail)
  else if (type === 'plan_step') activity('plan', message, detail)
  else if (type === 'tool_start') activity('tool', message, detail)
  else if (type === 'tool_end') activity('tool', message, detail)
  else if (type === 'validation') {
    assistant.validation = detail || message
    activity('validation', message, detail)
  } else if (type === 'artifacts') {
    try {
      assistant.artifacts = JSON.parse(detail || '[]') as AgentArtifact[]
    } catch {
      assistant.artifacts = []
    }
  } else if (type === 'answer') {
    assistant.content = message || assistant.content
    assistant.streaming = false
  } else if (type === 'done') {
    assistant.streaming = false
  } else if (type === 'error') {
    assistant.streaming = false
    assistant.failed = true
    assistant.content = message
    error.value = message
  }
  void scrollBottom()
}

function fromMemory(memory: AgentMemory): ChatMessage {
  return {
    id: memory.id,
    role: memory.role === 'user' ? 'user' : 'assistant',
    content: memory.content,
    activities: [],
    artifacts: [],
    failed: memory.kind === 'error',
  }
}

function newAssistant(): ChatMessage {
  return {
    id: `assistant-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    role: 'assistant',
    content: '',
    activities: [],
    artifacts: [],
    streaming: true,
  }
}

function activity(kind: ActivityKind, title: string, detail = '', ok = true) {
  const last = messages.value[messages.value.length - 1]
  if (!last || last.role !== 'assistant') return
  const previous = last.activities[last.activities.length - 1]
  if (previous && previous.kind === kind && previous.title === title && previous.detail === detail) return
  last.activities.push({ kind, title, detail, ok })
  if (last.activities.length > 24) last.activities.shift()
}

async function send(text?: string) {
  if (busy.value) return
  const question = (text ?? input.value).trim()
  if (!question) return
  if (status.value?.configured === false) {
    error.value = 'Agent 模型未配置，请先在服务端设置 DEEPSEEK_API_KEY。'
    return
  }
  error.value = ''
  input.value = ''
  messages.value.push({
    id: `user-${Date.now()}`,
    role: 'user',
    content: question,
    activities: [],
    artifacts: [],
  })
  const assistant = newAssistant()
  messages.value.push(assistant)
  const task: ActiveTask = {
    sessionId: sessionId.value,
    clientTaskId: crypto.randomUUID ? crypto.randomUUID() : `task-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    question,
    mode: mode.value,
    knowledgeBaseIds: [...selectedKbs.value],
    lastSeq: 0,
    assistantId: assistant.id,
  }
  saveActiveTask(task)
  needsResume.value = false
  busy.value = true
  localStorage.setItem(MODE_KEY, mode.value)
  localStorage.setItem('sunny_agent_kbs_v1', JSON.stringify(selectedKbs.value))
  await scrollBottom()
  abortController = new AbortController()

  try {
    await agentApi.stream(
      {
        sessionId: sessionId.value,
        clientTaskId: task.clientTaskId,
        message: question,
        mode: mode.value,
        knowledgeBaseIds: selectedKbs.value,
      },
      streamHandlers(assistant, task),
      abortController.signal,
    )
  } catch (ex) {
    if (task.taskId && assistant.streaming) {
      reconnecting.value = true
      await retryAttach(assistant, task)
    } else {
      assistant.streaming = false
      assistant.failed = true
      if (!assistant.content) assistant.content = ex instanceof Error ? ex.message : '请求失败'
      error.value = assistant.content
      if (task.taskId) {
        needsResume.value = true
      } else {
        try {
          const found = await agentApi.taskByClient(task.clientTaskId)
          task.taskId = found.taskId
          assistant.taskId = found.taskId
          saveActiveTask(task)
          needsResume.value = found.status !== 'RUNNING'
        } catch {
          saveActiveTask(null)
        }
      }
    }
  } finally {
    busy.value = false
    assistant.streaming = false
    reconnecting.value = false
    await scrollBottom()
  }
}

async function resumeTask() {
  const task = activeTask.value
  if (!task?.taskId || busy.value) return
  const assistant = messages.value.find((m) => m.id === task.assistantId)
    || messages.value[messages.value.length - 1]
  if (!assistant || assistant.role !== 'assistant') return
  busy.value = true
  needsResume.value = false
  error.value = ''
  assistant.failed = false
  assistant.streaming = true
  try {
    await agentApi.resume(task.taskId, streamHandlers(assistant, task))
  } catch (ex) {
    assistant.streaming = false
    assistant.failed = true
    assistant.content ||= ex instanceof Error ? ex.message : '恢复失败'
    error.value = assistant.content
  } finally {
    busy.value = false
  }
}

async function cancelTask() {
  const task = activeTask.value
  if (!task?.taskId) return
  try {
    await agentApi.cancel(task.taskId)
    needsResume.value = true
    error.value = '已请求停止，等待当前步骤结束后可从断点继续。'
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '停止失败'
  }
}

async function scrollBottom() {
  await nextTick()
  const el = timelineRef.value
  if (el) el.scrollTop = el.scrollHeight
}

function resetSession() {
  if (busy.value) return
  sessionId.value = ''
  saveActiveTask(null)
  needsResume.value = false
  localStorage.removeItem(SESSION_KEY)
  messages.value = []
  error.value = ''
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void send()
  }
}

function modeLabel(value: string) {
  if (value === 'react') return 'ReAct 短任务'
  if (value === 'plan') return 'Plan 长任务'
  return '自动判断'
}

function initial(name: string) {
  return name.slice(0, 1)
}

function artifactIcon(type: string) {
  if (type === 'pptx') return '◫'
  if (type === 'docx') return '¶'
  if (type === 'xlsx' || type === 'csv') return '▦'
  if (type === 'html') return '</>'
  if (type === 'pdf') return 'PDF'
  if (type === 'image') return '▧'
  if (type === 'markdown' || type === 'text') return '≡'
  return '↓'
}

function formatSize(size: number) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <div class="agent-page">
    <div class="agent-shell">
      <header class="agent-head">
        <div class="agent-brand">
          <span class="agent-mark">月</span>
          <div>
            <p class="section-eyebrow">YUEBAI AGENT · TOOLS + MEMORY</p>
            <h1>月白智能体</h1>
            <p class="agent-sub">有记忆、会使用技能、能写脚本并自我修复的长对话 Agent。</p>
          </div>
        </div>
        <div class="agent-head-meta">
          <span class="agent-pill" :class="{ off: status && !status.configured }">
            {{ status?.configured ? 'DeepSeek 在线' : '模型未配置' }}
          </span>
          <span class="agent-pill muted">{{ modeLabel(mode) }}</span>
          <button class="agent-reset" type="button" @click="resetSession">新对话</button>
        </div>
      </header>

      <div v-if="status && !status.configured" class="agent-banner">
        服务端还没有配置模型 API Key，暂时无法执行任务。
      </div>
      <div v-else-if="status && !status.executionEnabled" class="agent-banner soft">
        脚本执行当前关闭（AGENT_EXECUTION_ENABLED=false），Agent 仍可分析和写作，但不能运行脚本。
      </div>
      <div v-if="reconnecting" class="agent-banner soft">
        连接断开，任务仍在后台运行，正在从断点恢复…
      </div>
      <div v-else-if="activeTask && activeTask.taskId" class="agent-banner task-banner">
        <span>
          后台任务 {{ activeTask.taskId.slice(0, 8) }}
          <template v-if="needsResume">已中断，可继续</template>
          <template v-else>执行中</template>
        </span>
        <button v-if="needsResume" type="button" class="mini-btn" @click="resumeTask">继续任务</button>
        <button v-else-if="busy" type="button" class="mini-btn" @click="cancelTask">停止</button>
      </div>

      <main ref="timelineRef" class="agent-timeline">
        <div v-if="!messages.length" class="agent-empty">
          <span class="agent-orbit">☾</span>
          <h2>把一个任务交给月白</h2>
          <p>短任务会走 ReAct；长任务会自动拆成计划并逐步执行。它可以读取技能、写脚本、运行并检查输出。</p>
          <div class="agent-suggestions">
            <button v-for="item in suggestions" :key="item" type="button" @click="send(item)">
              {{ item }}
            </button>
          </div>
          <div v-if="skills.length" class="agent-skills">
            <span class="agent-skills-label">已加载技能</span>
            <span v-for="skill in skills.slice(0, 8)" :key="skill.slug" class="agent-skill-chip" :title="skill.description">
              {{ skill.name }}
            </span>
          </div>
        </div>

        <div v-for="message in messages" :key="message.id" class="agent-row" :class="message.role">
          <span class="agent-avatar">{{ message.role === 'user' ? '你' : '月' }}</span>
          <div class="agent-message">
            <div class="agent-message-meta">
              <strong>{{ message.role === 'user' ? '你' : '月白' }}</strong>
              <em v-if="message.streaming">思考与执行中…</em>
              <em v-else-if="message.failed" class="bad">未完成</em>
              <em v-else-if="message.validation" class="ok">已验收</em>
              <em v-if="message.taskId" class="task-id">task {{ message.taskId.slice(0, 8) }}</em>
            </div>

            <div v-if="message.activities.length" class="agent-activities">
              <details
                v-for="(item, index) in message.activities"
                :key="`${message.id}-activity-${index}`"
                class="agent-activity"
                :class="[item.kind, { failed: item.ok === false }]"
                :open="item.kind === 'tool' || item.kind === 'validation' || item.kind === 'plan'"
              >
                <summary>
                  <span class="agent-activity-dot"></span>
                  <span>{{ item.title }}</span>
                  <small v-if="item.kind === 'tool'">工具</small>
                  <small v-else-if="item.kind === 'validation'">验收</small>
                  <small v-else-if="item.kind === 'plan'">计划</small>
                </summary>
                <pre v-if="item.detail">{{ item.detail }}</pre>
              </details>
            </div>

            <div v-if="message.artifacts.length" class="agent-artifacts">
              <a
                v-for="artifact in message.artifacts"
                :key="artifact.path"
                class="agent-artifact"
                :href="`${artifact.url}&download=1`"
                :download="artifact.name"
              >
                <span v-if="artifact.type === 'image'" class="agent-artifact-preview">
                  <img :src="artifact.url" :alt="artifact.name" loading="lazy" />
                </span>
                <span v-else class="agent-artifact-icon">{{ artifactIcon(artifact.type) }}</span>
                <span class="agent-artifact-copy">
                  <strong>{{ artifact.name }}</strong>
                  <small>{{ artifact.type.toUpperCase() }} · {{ formatSize(artifact.size) }}</small>
                </span>
                <span class="agent-artifact-download">↓</span>
              </a>
            </div>

            <div v-if="message.content" class="agent-bubble" :class="{ failed: message.failed }">
              {{ message.content }}<span v-if="message.streaming" class="agent-caret"></span>
            </div>
            <div v-else-if="message.streaming" class="agent-bubble thinking">
              <span class="agent-typing"><i></i><i></i><i></i></span>
              正在读取记忆、选择工具…
            </div>
            <p v-if="message.validation" class="agent-validation">独立验收：{{ message.validation }}</p>
          </div>
        </div>
      </main>

      <div class="agent-composer">
        <div class="agent-toolbar">
          <div class="agent-toolbar-left">
            <div class="agent-modes" role="group" aria-label="执行模式">
              <button v-for="item in ['auto', 'react', 'plan']" :key="item" type="button" :class="{ active: mode === item }" @click="mode = item">
                {{ modeLabel(item) }}
              </button>
            </div>
            <details v-if="knowledgeBases.length" class="agent-kb-picker">
              <summary>
                知识库
                <span v-if="selectedKbs.length">{{ selectedKbs.length }}</span>
              </summary>
              <div class="agent-kb-menu">
                <label v-for="kb in knowledgeBases" :key="kb.id">
                  <input v-model="selectedKbs" type="checkbox" :value="kb.id" />
                  <span>{{ kb.name }}<small>{{ kb.files }} 文件</small></span>
                </label>
                <p v-if="!knowledgeBases.length">还没有知识库</p>
              </div>
            </details>
          </div>
          <span class="agent-hint">Enter 发送 · Shift + Enter 换行</span>
        </div>
        <div class="agent-input-row">
          <textarea
            v-model="input"
            rows="2"
            maxlength="4000"
            placeholder="描述你想完成的任务。比如：帮我做一个学习计划，并写成文件保存…"
            @keydown="onKeydown"
          ></textarea>
          <button type="button" class="agent-send" :disabled="!canSend" @click="send()">
            <span v-if="busy" class="agent-spinner"></span>
            {{ busy ? '执行中' : '发送' }}
          </button>
        </div>
        <p v-if="error" class="agent-error">{{ error }}</p>
        <p class="agent-footnote">
          记忆会按重要程度保留在本次会话中；Agent 只能访问专用 workspace 与 skills 目录。
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agent-page {
  min-height: 100vh;
  padding: 38px 24px 70px;
  background:
    radial-gradient(circle at 14% 8%, rgba(230, 201, 155, 0.26), transparent 34%),
    radial-gradient(circle at 88% 0%, rgba(125, 158, 190, 0.18), transparent 30%),
    #f7f3ea;
}
.agent-shell {
  width: min(1080px, 100%);
  min-height: calc(100vh - 160px);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(93, 113, 133, 0.16);
  border-radius: 26px;
  background: rgba(255, 253, 248, 0.86);
  box-shadow: 0 24px 80px rgba(60, 72, 86, 0.12);
  overflow: hidden;
}
.agent-head { display: flex; justify-content: space-between; gap: 20px; padding: 22px 28px 18px; border-bottom: 1px solid rgba(121, 139, 158, 0.14); }
.agent-brand { display: flex; gap: 14px; align-items: flex-start; }
.agent-mark { display: grid; place-items: center; width: 46px; height: 46px; border-radius: 15px; color: #fff; background: linear-gradient(135deg, #243b55, #4e6e8e); font-size: 20px; box-shadow: 0 8px 18px rgba(36, 59, 85, 0.22); }
.agent-brand h1 { margin: 4px 0 3px; font-size: 24px; color: #233548; letter-spacing: 0.02em; }
.agent-sub { margin: 0; color: #77818c; font-size: 12px; }
.agent-head-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.agent-pill { border-radius: 999px; padding: 5px 10px; background: #e9f3ef; color: #2f6a59; font-size: 11px; }
.agent-pill.off { background: #f7e8e6; color: #9b4c45; }
.agent-pill.muted { background: #eceff3; color: #647180; }
.agent-reset { border: 0; background: transparent; color: #7a8795; font-size: 12px; cursor: pointer; }
.agent-banner { margin: 14px 28px 0; padding: 10px 12px; border-radius: 10px; color: #8c5a42; background: #fbf0e5; font-size: 12px; }
.agent-banner.soft { color: #6a7785; background: #eef2f5; }
.agent-banner.task-banner { display: flex; align-items: center; justify-content: space-between; gap: 10px; color: #526b80; background: #eef4f8; }
.agent-banner .mini-btn { border: 1px solid rgba(88, 119, 145, 0.22); border-radius: 7px; padding: 4px 9px; background: #fff; color: #48657c; font-size: 11px; cursor: pointer; }
.agent-timeline { flex: 1 1 auto; min-height: 420px; max-height: calc(100vh - 330px); overflow-y: auto; padding: 24px 28px; scrollbar-width: thin; scroll-behavior: smooth; }
.agent-empty { max-width: 650px; margin: 52px auto; text-align: center; color: #5f6d7b; }
.agent-orbit { display: grid; place-items: center; width: 58px; height: 58px; margin: 0 auto 16px; border-radius: 50%; background: #eef2f6; color: #45627e; font-size: 25px; }
.agent-empty h2 { margin: 0 0 10px; font-size: 22px; color: #2d4054; }
.agent-empty p { margin: 0 auto; max-width: 530px; font-size: 13px; line-height: 1.8; }
.agent-suggestions { display: flex; flex-direction: column; gap: 8px; max-width: 560px; margin: 22px auto; }
.agent-suggestions button { padding: 11px 14px; border: 1px solid rgba(106, 127, 147, 0.18); border-radius: 11px; background: rgba(255, 255, 255, 0.65); color: #526477; font-size: 12px; text-align: left; cursor: pointer; transition: 0.18s ease; }
.agent-suggestions button:hover { border-color: rgba(56, 92, 122, 0.45); background: #fff; transform: translateY(-1px); }
.agent-skills { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 6px; margin-top: 18px; }
.agent-skills-label { color: #8a949e; font-size: 11px; margin-right: 3px; }
.agent-skill-chip { padding: 4px 8px; border-radius: 999px; background: #eef1f4; color: #647383; font-size: 11px; }
.agent-row { display: flex; align-items: flex-start; gap: 10px; margin: 16px 0; }
.agent-row.user { flex-direction: row-reverse; }
.agent-avatar { flex: 0 0 auto; display: grid; place-items: center; width: 30px; height: 30px; border-radius: 10px; color: #fff; background: #2f4d69; font-size: 12px; }
.agent-row.user .agent-avatar { background: #b08a5b; }
.agent-message { max-width: 82%; min-width: 0; }
.agent-row.user .agent-message { display: flex; flex-direction: column; align-items: flex-end; }
.agent-message-meta { display: flex; gap: 8px; align-items: baseline; margin-bottom: 5px; color: #7d8995; font-size: 11px; }
.agent-message-meta strong { color: #405467; font-size: 12px; }
.agent-message-meta em { font-style: normal; color: #9aa3ac; }
.agent-message-meta em.ok { color: #3d7a65; }
.agent-message-meta em.bad { color: #a15a4f; }
.agent-message-meta em.task-id { color: #aeb7bf; font-family: Consolas, monospace; font-size: 9px; }
.agent-activities { display: flex; flex-direction: column; gap: 4px; margin-bottom: 7px; width: 100%; }
.agent-activity { border: 1px solid rgba(115, 135, 154, 0.16); border-radius: 9px; background: rgba(247, 249, 250, 0.86); color: #667789; font-size: 11px; }
.agent-activity.failed { border-color: rgba(168, 93, 78, 0.25); }
.agent-activity summary { display: flex; align-items: center; gap: 7px; padding: 7px 9px; cursor: pointer; list-style: none; }
.agent-activity summary::-webkit-details-marker { display: none; }
.agent-activity summary small { margin-left: auto; color: #9ba6b0; font-size: 10px; }
.agent-activity-dot { width: 6px; height: 6px; border-radius: 50%; background: #a9b6c1; }
.agent-activity.tool .agent-activity-dot { background: #4d789d; }
.agent-activity.plan .agent-activity-dot { background: #b18b57; }
.agent-activity.validation .agent-activity-dot { background: #4e8872; }
.agent-activity.failed .agent-activity-dot { background: #b56a5b; }
.agent-activity pre { margin: 0; padding: 8px 10px 9px; border-top: 1px solid rgba(115, 135, 154, 0.12); color: #5b6b7a; font-family: 'SFMono-Regular', Consolas, monospace; font-size: 10.5px; white-space: pre-wrap; word-break: break-word; max-height: 220px; overflow: auto; }
.agent-bubble { width: fit-content; max-width: 100%; padding: 10px 13px; border-radius: 4px 14px 14px 14px; background: #edf2f6; color: #34495c; font-size: 13px; line-height: 1.8; white-space: pre-wrap; word-break: break-word; }
.agent-row.user .agent-bubble { border-radius: 14px 4px 14px 14px; background: #2f4d69; color: #f8fbfd; }
.agent-bubble.failed { background: #fbefed; color: #8e5148; }
.agent-bubble.thinking { color: #798895; }
.agent-caret { display: inline-block; width: 2px; height: 14px; margin-left: 2px; vertical-align: -2px; background: #6f8ba3; animation: blink 1s steps(1) infinite; }
.agent-typing { display: inline-flex; gap: 3px; margin-right: 7px; vertical-align: middle; }
.agent-typing i { width: 4px; height: 4px; border-radius: 50%; background: #8fa0ae; animation: typing 1.2s infinite; }
.agent-typing i:nth-child(2) { animation-delay: 0.16s; }
.agent-typing i:nth-child(3) { animation-delay: 0.32s; }
@keyframes typing { 0%, 70%, 100% { opacity: 0.3; transform: translateY(0); } 35% { opacity: 1; transform: translateY(-2px); } }
@keyframes blink { 50% { opacity: 0; } }
.agent-validation { margin: 5px 0 0; color: #7e8c98; font-size: 10.5px; }
.agent-artifacts { display: flex; flex-wrap: wrap; gap: 7px; margin: 7px 0; }
.agent-artifact { display: flex; align-items: center; gap: 8px; min-width: 150px; max-width: 260px; padding: 7px 9px; border: 1px solid rgba(98, 122, 143, 0.18); border-radius: 10px; background: #fff; color: #4b6174; text-decoration: none; transition: 0.16s ease; }
.agent-artifact:hover { border-color: rgba(54, 91, 121, 0.42); box-shadow: 0 4px 12px rgba(60, 80, 98, 0.1); transform: translateY(-1px); }
.agent-artifact-icon { display: grid; place-items: center; flex: 0 0 auto; width: 29px; height: 29px; border-radius: 7px; background: #edf2f6; color: #57738b; font-size: 10px; font-weight: 700; }
.agent-artifact-preview { flex: 0 0 auto; width: 40px; height: 40px; border-radius: 7px; overflow: hidden; background: #eef2f5; }
.agent-artifact-preview img { width: 100%; height: 100%; object-fit: cover; }
.agent-artifact-copy { min-width: 0; flex: 1; display: flex; flex-direction: column; gap: 2px; }
.agent-artifact-copy strong { color: #42596d; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.agent-artifact-copy small { color: #98a3ad; font-size: 9.5px; }
.agent-artifact-download { flex: 0 0 auto; color: #6d879b; font-size: 14px; }
.agent-composer { flex: 0 0 auto; padding: 13px 28px 17px; border-top: 1px solid rgba(121, 139, 158, 0.14); background: rgba(255, 253, 248, 0.92); }
.agent-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 9px; }
.agent-toolbar-left { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.agent-kb-picker { position: relative; }
.agent-kb-picker summary { list-style: none; cursor: pointer; border-radius: 7px; padding: 5px 9px; background: #eef3f6; color: #5d7487; font-size: 10.5px; }
.agent-kb-picker summary::-webkit-details-marker { display: none; }
.agent-kb-picker summary span { display: inline-grid; place-items: center; min-width: 15px; height: 15px; margin-left: 4px; border-radius: 999px; background: #dce8f0; font-size: 9px; }
.agent-kb-menu { position: absolute; bottom: calc(100% + 7px); left: 0; z-index: 10; min-width: 220px; max-height: 240px; overflow-y: auto; padding: 7px; border: 1px solid rgba(96, 119, 139, 0.16); border-radius: 10px; background: #fff; box-shadow: 0 10px 30px rgba(45, 64, 82, 0.14); }
.agent-kb-menu label { display: flex; gap: 7px; align-items: flex-start; padding: 6px; border-radius: 6px; cursor: pointer; color: #526778; font-size: 11px; }
.agent-kb-menu label:hover { background: #f5f8fa; }
.agent-kb-menu label span { display: flex; flex-direction: column; gap: 2px; }
.agent-kb-menu label small { color: #9ca7b0; font-size: 9px; }
.agent-modes { display: flex; gap: 4px; padding: 3px; border-radius: 9px; background: #eef1f4; }
.agent-modes button { border: 0; border-radius: 7px; padding: 5px 9px; background: transparent; color: #778591; font-size: 10.5px; cursor: pointer; }
.agent-modes button.active { background: #fff; color: #35526d; box-shadow: 0 1px 4px rgba(55, 74, 92, 0.12); }
.agent-hint { color: #a0a9b1; font-size: 10px; }
.agent-input-row { display: flex; gap: 10px; align-items: stretch; }
.agent-input-row textarea { flex: 1; resize: none; min-height: 54px; padding: 11px 12px; border: 1px solid rgba(104, 127, 148, 0.22); border-radius: 12px; background: #fff; color: #354b5f; font: inherit; font-size: 13px; line-height: 1.6; outline: none; transition: border-color 0.16s ease; }
.agent-input-row textarea:focus { border-color: rgba(61, 99, 129, 0.55); }
.agent-send { align-self: stretch; min-width: 88px; border: 0; border-radius: 12px; color: #fff; background: #2f4d69; font-size: 12px; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; gap: 7px; }
.agent-send:disabled { opacity: 0.45; cursor: not-allowed; }
.agent-spinner { width: 12px; height: 12px; border: 2px solid rgba(255, 255, 255, 0.35); border-top-color: #fff; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.agent-error { margin: 8px 0 0; color: #9b554a; font-size: 11px; }
.agent-footnote { margin: 8px 0 0; color: #a3abb2; font-size: 10px; }
@media (max-width: 760px) {
  .agent-page { padding: 14px 10px 30px; }
  .agent-shell { min-height: calc(100vh - 40px); border-radius: 18px; }
  .agent-head { padding: 16px 16px 12px; flex-direction: column; }
  .agent-head-meta { justify-content: flex-start; }
  .agent-timeline { max-height: calc(100vh - 330px); padding: 16px; }
  .agent-composer { padding: 10px 14px 14px; }
  .agent-message { max-width: 88%; }
  .agent-hint { display: none; }
}

</style>
