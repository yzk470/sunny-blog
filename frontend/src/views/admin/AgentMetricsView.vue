<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  agentApi,
  type AgentFailureRow,
  type AgentMetricTask,
  type AgentMetrics,
  type AgentTrendRow,
} from '@/api/agent'
import { useToastStore } from '@/stores/toast'

const router = useRouter()
const toast = useToastStore()
const days = ref(30)
const loading = ref(true)
const summary = ref<AgentMetrics | null>(null)
const trend = ref<AgentTrendRow[]>([])
const failures = ref<AgentFailureRow[]>([])
const tasks = ref<AgentMetricTask[]>([])
const failureFilter = ref('')

const maxFailure = computed(() => Math.max(1, ...failures.value.map((item) => item.count)))
const maxTasks = computed(() => Math.max(1, ...trend.value.map((item) => item.tasks)))
const filterLabel = computed(() => failureFilter.value ? failureLabel(failureFilter.value) : '')

async function load() {
  loading.value = true
  try {
    const [s, t, f] = await Promise.all([
      agentApi.metricsSummary(days.value),
      agentApi.metricsTrend(days.value),
      agentApi.metricsFailures(days.value),
    ])
    summary.value = s
    trend.value = t
    failures.value = f
    await loadTasks()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '加载执行统计失败', 'error')
  } finally {
    loading.value = false
  }
}

async function loadTasks() {
  try {
    tasks.value = await agentApi.metricsTasks(20, failureFilter.value)
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '加载任务失败', 'error')
  }
}

async function selectFailure(type: string) {
  failureFilter.value = failureFilter.value === type ? '' : type
  await loadTasks()
}

async function clearFailure() {
  failureFilter.value = ''
  await loadTasks()
}

function percent(value: number) {
  return `${(value * 100).toFixed(1)}%`
}

function formatTime(value: string) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function formatDuration(ms: number) {
  if (!ms) return '0s'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function failureLabel(value: string) {
  return {
    ENVIRONMENT: '环境/依赖',
    TRANSIENT: '瞬时故障',
    TOOL: '工具失败',
    FORMAT: '格式错误',
    LOGIC: '逻辑/验收',
    BUDGET: '预算耗尽',
    UNSAT: '能力不足',
  }[value] || value
}

function barWidth(value: number, max: number) {
  return `${Math.max(2, Math.round((value / max) * 100))}%`
}

function openTask(taskId: string) {
  router.push({ name: 'admin-agent-runs', query: { run: taskId } })
}

onMounted(load)
</script>

<template>
  <div class="agent-metrics">
    <div class="admin-page-head">
      <div>
        <p class="section-eyebrow">AGENT ANALYTICS</p>
        <h1>执行统计</h1>
        <p>成功率、重试率、预算成本、失败类型和高价值搜索分支。</p>
      </div>
      <div class="metrics-actions">
        <select v-model.number="days" @change="load">
          <option :value="7">近 7 天</option>
          <option :value="30">近 30 天</option>
          <option :value="90">近 90 天</option>
        </select>
        <button type="button" class="btn btn-ghost" :disabled="loading" @click="load">刷新</button>
      </div>
    </div>

    <div v-if="summary" class="metric-cards">
      <article><span>任务总数</span><strong>{{ summary.tasks }}</strong></article>
      <article><span>成功率</span><strong>{{ percent(summary.successRate) }}</strong></article>
      <article><span>重试率</span><strong>{{ percent(summary.retryRate) }}</strong></article>
      <article><span>平均预算</span><strong>{{ summary.averageSteps.toFixed(1) }}</strong></article>
      <article><span>尝试次数</span><strong>{{ summary.attempts }}</strong></article>
      <article><span>失败尝试</span><strong>{{ summary.failedAttempts }}</strong></article>
      <article><span>高价值任务</span><strong>{{ summary.highValueTasks }}</strong></article>
      <article><span>分支展开</span><strong>{{ summary.branchesExpanded }}</strong></article>
    </div>

    <div class="metrics-columns">
      <section class="admin-panel">
        <div class="panel-head"><h2>每日趋势</h2><span>{{ trend.length }} 天</span></div>
        <p v-if="!trend.length" class="metrics-empty">还没有落库数据。</p>
        <div v-for="row in trend" :key="row.day" class="trend-row">
          <div class="trend-label">
            <span>{{ row.day.slice(5) }}</span>
            <small>{{ row.tasks }} 任务 · {{ row.successes }} 成功</small>
          </div>
          <div class="trend-bars">
            <div class="trend-bar success" :style="{ width: barWidth(row.successes, maxTasks) }" />
            <div class="trend-bar total" :style="{ width: barWidth(row.tasks - row.successes, maxTasks) }" />
          </div>
          <span class="trend-cost">{{ row.steps }}</span>
        </div>
      </section>

      <section class="admin-panel">
        <div class="panel-head"><h2>失败类型</h2><span>{{ failures.length }} 类</span></div>
        <p v-if="!failures.length" class="metrics-empty">暂无失败记录。</p>
        <div v-for="item in failures" :key="item.failure_type"
             class="failure-row clickable" :class="{ active: failureFilter === item.failure_type }"
             @click="selectFailure(item.failure_type)">
          <div class="failure-label">
            <strong>{{ failureLabel(item.failure_type) }}</strong>
            <small>{{ item.failure_type }}</small>
          </div>
          <div class="failure-track"><i :style="{ width: barWidth(item.count, maxFailure) }" /></div>
          <span>{{ item.count }}</span>
        </div>
      </section>
    </div>

    <section class="admin-panel metrics-tasks">
      <div class="panel-head">
        <h2>最近任务 <small v-if="failureFilter">· {{ filterLabel }}</small></h2>
        <button v-if="failureFilter" type="button" class="clear-filter" @click="clearFailure">清除筛选</button>
        <span v-else>按时间倒序 · 点击任务查看链路</span>
      </div>
      <p v-if="!tasks.length" class="metrics-empty">还没有任务指标。</p>
      <div v-else class="metrics-table-wrap">
        <table>
          <thead>
            <tr><th>任务</th><th>模式</th><th>状态</th><th>尝试</th><th>成本</th><th>耗时</th><th>时间</th></tr>
          </thead>
          <tbody>
            <tr v-for="task in tasks" :key="task.runId" class="clickable" @click="openTask(task.runId)">
              <td><code>{{ task.runId.slice(0, 8) }}</code></td>
              <td>{{ task.mode }}<em v-if="task.highValue">高价值</em></td>
              <td><span :class="['status-dot', task.success ? 'ok' : 'bad']">{{ task.status }}</span></td>
              <td>{{ task.attemptCount }} / {{ task.failedAttemptCount }} 失败</td>
              <td>{{ task.taskSpent }} / {{ task.taskBudget }}</td>
              <td>{{ formatDuration(task.durationMs) }}</td>
              <td>{{ formatTime(task.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.agent-metrics { padding-bottom: 40px; }
.metrics-actions { display: flex; gap: 8px; align-items: center; }
.metrics-actions select { padding: 7px 9px; border: 1px solid rgba(103, 126, 146, 0.2); border-radius: 8px; background: #fff; color: #4a6072; font: inherit; font-size: 11px; }
.metric-cards { display: grid; grid-template-columns: repeat(8, minmax(0, 1fr)); gap: 9px; margin-bottom: 18px; }
.metric-cards article { padding: 12px; border: 1px solid rgba(98, 119, 139, 0.12); border-radius: 12px; background: #fff; }
.metric-cards span { display: block; color: #95a0a9; font-size: 9px; }
.metric-cards strong { display: block; margin-top: 4px; color: #3a5368; font-size: 18px; }
.metrics-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; margin-bottom: 18px; }
.admin-panel { padding: 15px; border: 1px solid rgba(98, 119, 139, 0.13); border-radius: 15px; background: #fff; }
.panel-head { display: flex; justify-content: space-between; align-items: baseline; gap: 10px; margin-bottom: 12px; }
.panel-head h2 { margin: 0; color: #3b5266; font-size: 14px; }
.panel-head span { color: #9ba5ad; font-size: 9px; }
.metrics-empty { margin: 10px 0; color: #99a4ad; font-size: 11px; }
.trend-row { display: grid; grid-template-columns: 76px 1fr 32px; gap: 8px; align-items: center; margin: 7px 0; }
.trend-label span { display: block; color: #607586; font-size: 10px; }
.trend-label small { color: #a0a9b1; font-size: 8px; }
.trend-bars { display: flex; height: 7px; border-radius: 999px; overflow: hidden; background: #f0f3f5; }
.trend-bar.success { background: #6ca58d; }
.trend-bar.total { background: #e2b8a9; }
.trend-cost { color: #9aa4ac; font-size: 9px; text-align: right; }
.failure-row { display: grid; grid-template-columns: 100px 1fr 24px; gap: 8px; align-items: center; margin: 8px 0; padding: 3px 5px; border-radius: 7px; }
.failure-row.clickable { cursor: pointer; }
.failure-row.clickable:hover { background: #f7fafb; }
.failure-row.active { background: #f5eee4; }
.clear-filter { margin-left: auto; border: 0; background: transparent; color: #9a6a52; font-size: 10px; cursor: pointer; }
.metrics-tasks .panel-head h2 small { color: #9a6a52; font-size: 10px; font-weight: 400; }
.failure-label strong { display: block; color: #52697c; font-size: 10px; }
.failure-label small { color: #a1aab2; font-size: 8px; }
.failure-track { height: 7px; border-radius: 999px; background: #f0f3f5; overflow: hidden; }
.failure-track i { display: block; height: 100%; border-radius: 999px; background: #c08a78; }
.failure-row > span { color: #8f9aa3; font-size: 9px; text-align: right; }
.metrics-tasks { padding: 15px; }
.metrics-table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 10px; }
th { text-align: left; color: #97a1a9; font-weight: 500; padding: 7px 8px; border-bottom: 1px solid rgba(105, 126, 145, 0.12); }
td { color: #526879; padding: 8px; border-bottom: 1px solid rgba(105, 126, 145, 0.07); }
tr.clickable { cursor: pointer; }
tr.clickable:hover { background: #f7fafb; }
code { color: #6f8596; font-family: Consolas, monospace; font-size: 9px; }
td em { margin-left: 5px; padding: 1px 4px; border-radius: 4px; background: #f5eddf; color: #977540; font-style: normal; font-size: 8px; }
.status-dot { font-size: 9px; }
.status-dot.ok { color: #3f7c67; }
.status-dot.bad { color: #a35b4f; }
@media (max-width: 1100px) { .metric-cards { grid-template-columns: repeat(4, 1fr); } .metrics-columns { grid-template-columns: 1fr; } }
</style>
