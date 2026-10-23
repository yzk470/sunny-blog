import { httpDelete, httpGet, httpPost } from './http'

export interface AgentStatus {
  configured: boolean
  executionEnabled: boolean
  model: string
}

export interface AgentSkill {
  slug: string
  name: string
  description: string
  body?: string
}

export interface AgentMemory {
  id: string
  role: string
  content: string
  importance: 'HIGH' | 'MEDIUM' | 'LOW'
  kind: string
  createdAt: string
}

export interface AgentSession {
  id: string
  title: string
  memories: AgentMemory[]
}

export interface AgentRunStep {
  seq: number
  type: string
  title: string
  detail: string
  success: boolean
  createdAt: string
}

export interface AgentRun {
  id: string
  sessionId: string
  mode: 'react' | 'plan' | string
  question: string
  status: string
  answer: string | null
  validation: string | null
  createdAt: string
  updatedAt: string
  steps: AgentRunStep[]
}

export interface AgentArtifact {
  name: string
  path: string
  type: string
  size: number
  modifiedAt?: string
  url: string
}

export interface AgentTaskEvent {
  seq: number
  type: string
  message: string
  detail: string
  createdAt: string
}

export interface AgentTaskView {
  taskId: string
  sessionId: string
  clientTaskId: string
  status: string
  question: string
  mode: string
  answer: string | null
  validation: string | null
  lastEventSeq: number
  active: boolean
  attemptCount: number
  taskBudget: number
  taskSpent: number
  highValue: boolean
  events: AgentTaskEvent[]
}

export interface AgentMetrics {
  tasks: number
  successes: number
  failures: number
  successRate: number
  attempts: number
  failedAttempts: number
  retryRate: number
  steps: number
  averageSteps: number
  highValueTasks: number
  highValueSuccesses: number
  branchesExpanded: number
  capabilityTasks: number
  capabilitySuccesses: number
  capabilitySuccessRate: number
  securityTasks: number
  securityPasses: number
  securityPassRate: number
  scopeViolations: number
  productionTasks: number
}

export interface AgentTrendRow {
  day: string
  tasks: number
  successes: number
  attempts: number
  failedAttempts: number
  steps: number
  highValueTasks: number
  highValueSuccesses: number
  branches: number
}

export interface AgentFailureRow {
  failure_type: string
  count: number
}

export interface AgentMetricTask {
  runId: string
  sessionId: string
  mode: string
  highValue: boolean
  status: string
  taskBudget: number
  taskSpent: number
  attemptCount: number
  failedAttemptCount: number
  branchExpanded: number
  success: boolean
  durationMs: number
  createdAt: string
  testGroup: string
  benchmarkPass: boolean
  scopeViolation: boolean
}

export interface AgentBenchmarkStatus {
  running: boolean
  exitCode: number
  startedAt: string
  finishedAt: string
  log: string
  report: string
}

export interface AgentStreamHandlers {
  onEvent?: (type: string, message: string, detail: string, seq?: number) => void
  onDone?: () => void
  onError?: (message: string) => void
}

async function consume(response: Response, handlers: AgentStreamHandlers) {
  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let serverError = ''
  let completed = false
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    let separator = buffer.indexOf('\n\n')
    while (separator >= 0) {
      const block = buffer.slice(0, separator)
      buffer = buffer.slice(separator + 2)
      let event = 'message'
      let seq: number | undefined
      const dataLines: string[] = []
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('id:')) {
          const parsed = Number(line.slice(3).trim())
          if (Number.isFinite(parsed)) seq = parsed
        } else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
      }
      if (dataLines.length) {
        let data: any = null
        try {
          data = JSON.parse(dataLines.join('\n'))
        } catch {
          data = { message: dataLines.join('\n'), detail: '' }
        }
        const message = String(data?.message || '')
        const detail = String(data?.detail || '')
        if (event === 'error') {
          serverError = message || 'Agent 执行失败'
          handlers.onError?.(serverError)
        } else if (event === 'done') {
          completed = true
          handlers.onEvent?.(event, message, detail, seq)
          handlers.onDone?.()
        } else {
          handlers.onEvent?.(event, message, detail, seq)
        }
      }
      separator = buffer.indexOf('\n\n')
    }
  }
  if (serverError) throw new Error(serverError)
  if (!completed) throw new Error('连接中断，任务可能仍在后台运行')
}

export const agentApi = {
  status() {
    return httpGet<AgentStatus>('/agent/status')
  },
  skills() {
    return httpGet<AgentSkill[]>('/agent/skills')
  },
  session(sessionId: string) {
    return httpGet<AgentSession>(`/agent/session/${encodeURIComponent(sessionId)}`)
  },
  async stream(
    payload: { sessionId: string; clientTaskId: string; message: string; mode: string; knowledgeBaseIds?: string[] },
    handlers: AgentStreamHandlers,
    signal?: AbortSignal,
  ) {
    const response = await fetch('/api/agent/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(payload),
      signal,
    })
    if (!response.ok || !response.body) {
      const error = await response.json().catch(() => null)
      throw new Error(error?.message || `Agent 请求失败（HTTP ${response.status}）`)
    }
    await consume(response, handlers)
  },
  async attach(taskId: string, after: number, handlers: AgentStreamHandlers, signal?: AbortSignal) {
    const response = await fetch(`/api/agent/tasks/${encodeURIComponent(taskId)}/stream?after=${after}`, {
      headers: { Accept: 'text/event-stream' },
      signal,
    })
    if (!response.ok || !response.body) throw new Error(`恢复连接失败（HTTP ${response.status}）`)
    await consume(response, handlers)
  },
  async resume(taskId: string, handlers: AgentStreamHandlers, signal?: AbortSignal) {
    const response = await fetch(`/api/agent/tasks/${encodeURIComponent(taskId)}/resume`, {
      method: 'POST',
      headers: { Accept: 'text/event-stream' },
      signal,
    })
    if (!response.ok || !response.body) throw new Error(`恢复任务失败（HTTP ${response.status}）`)
    await consume(response, handlers)
  },
  task(taskId: string) {
    return httpGet<AgentTaskView>(`/agent/tasks/${encodeURIComponent(taskId)}`)
  },
  taskByClient(clientTaskId: string) {
    return httpGet<AgentTaskView>(`/agent/tasks/by-client/${encodeURIComponent(clientTaskId)}`)
  },
  taskEvents(taskId: string, after = 0) {
    return httpGet<AgentTaskEvent[]>(`/agent/tasks/${encodeURIComponent(taskId)}/events?after=${after}`)
  },
  cancel(taskId: string) {
    return httpPost<boolean>(`/agent/tasks/${encodeURIComponent(taskId)}/cancel`, {})
  },

  adminSkills() {
    return httpGet<AgentSkill[]>('/admin/agent/skills')
  },
  saveSkill(payload: { slug?: string; name: string; description: string; content: string }) {
    return httpPost<AgentSkill>('/admin/agent/skills', payload)
  },
  deleteSkill(slug: string) {
    return httpDelete<void>(`/admin/agent/skills/${encodeURIComponent(slug)}`)
  },
  runs() {
    return httpGet<AgentRun[]>('/admin/agent/runs')
  },
  metrics() {
    return httpGet<AgentMetrics>('/admin/agent/metrics')
  },
  metricsSummary(days = 30) {
    return httpGet<AgentMetrics>(`/admin/agent/metrics/summary?days=${days}`)
  },
  metricsTrend(days = 30) {
    return httpGet<AgentTrendRow[]>(`/admin/agent/metrics/trend?days=${days}`)
  },
  metricsFailures(days = 30) {
    return httpGet<AgentFailureRow[]>(`/admin/agent/metrics/failures?days=${days}`)
  },
  metricsTasks(limit = 20, failureType = '', group = '') {
    const query = new URLSearchParams({ limit: String(limit) })
    if (failureType) query.set('failureType', failureType)
    if (group) query.set('group', group)
    return httpGet<AgentMetricTask[]>(`/admin/agent/metrics/tasks?${query.toString()}`)
  },
  startBenchmark(payload: { variants: string; repeats: number; group?: string; taskIds?: string[] }) {
    return httpPost<AgentBenchmarkStatus>('/admin/agent/benchmark/start', payload)
  },
  benchmarkStatus() {
    return httpGet<AgentBenchmarkStatus>('/admin/agent/benchmark/status')
  },
  stopBenchmark() {
    return httpPost<boolean>('/admin/agent/benchmark/stop', {})
  },
  run(id: string) {
    return httpGet<AgentRun>(`/admin/agent/runs/${encodeURIComponent(id)}`)
  },
  adminArtifacts() {
    return httpGet<AgentArtifact[]>('/admin/agent/artifacts')
  },
  deleteArtifact(path: string) {
    return httpDelete<void>(`/admin/agent/artifacts?path=${encodeURIComponent(path)}`)
  },
}
