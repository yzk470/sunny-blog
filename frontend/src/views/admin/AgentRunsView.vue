<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { agentApi, type AgentMetrics, type AgentRun, type AgentRunStep } from '@/api/agent'
import { useToastStore } from '@/stores/toast'

const route = useRoute()
const toast = useToastStore()
const runs = ref<AgentRun[]>([])
const metrics = ref<AgentMetrics | null>(null)
const selected = ref<AgentRun | null>(null)
const loading = ref(true)
const loadingDetail = ref(false)
const filter = ref('')

const filtered = computed(() => {
  const needle = filter.value.trim().toLowerCase()
  if (!needle) return runs.value
  return runs.value.filter((run) =>
    `${run.question} ${run.mode} ${run.status}`.toLowerCase().includes(needle),
  )
})

async function load() {
  loading.value = true
  try {
    const [list, stats] = await Promise.all([agentApi.runs(), agentApi.metrics()])
    runs.value = list
    metrics.value = stats
    const requested = String(route.query.run || '')
    const target = runs.value.find((run) => run.id === requested)
    if (!selected.value && target) await open(target)
    else if (!selected.value && runs.value.length) await open(runs.value[0])
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '加载执行记录失败', 'error')
  } finally {
    loading.value = false
  }
}

async function open(run: AgentRun) {
  loadingDetail.value = true
  try {
    selected.value = await agentApi.run(run.id)
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '加载详情失败', 'error')
  } finally {
    loadingDetail.value = false
  }
}

function formatTime(value: string) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function stepTone(step: AgentRunStep) {
  if (step.success === false) return 'failed'
  if (step.type === 'TOOL') return 'tool'
  if (step.type === 'VALIDATION') return 'validation'
  if (step.type === 'PLAN' || step.type === 'PLAN_STEP') return 'plan'
  return 'thought'
}

function stepIcon(step: AgentRunStep) {
  const tone = stepTone(step)
  if (tone === 'failed') return '!'
  if (tone === 'tool') return '⚙'
  if (tone === 'validation') return '✓'
  if (tone === 'plan') return '◇'
  return '·'
}

onMounted(load)
</script>

<template>
  <div class="admin-agent-runs">
    <div class="admin-page-head">
      <div>
        <p class="section-eyebrow">AGENT TRACE</p>
        <h1>执行链路</h1>
        <p>查看每次任务的模式、计划、工具调用、错误和独立验收结果。</p>
      </div>
      <button type="button" class="btn btn-ghost" :disabled="loading" @click="load">刷新</button>
    </div>

    <div v-if="metrics" class="trace-metrics">
      <div><span>任务总数</span><strong>{{ metrics.tasks }}</strong></div>
      <div><span>成功率</span><strong>{{ (metrics.successRate * 100).toFixed(1) }}%</strong></div>
      <div><span>重试率</span><strong>{{ (metrics.retryRate * 100).toFixed(1) }}%</strong></div>
      <div><span>平均预算</span><strong>{{ metrics.averageSteps.toFixed(1) }}</strong></div>
      <div><span>尝试次数</span><strong>{{ metrics.attempts }}</strong></div>
      <div><span>高价值任务</span><strong>{{ metrics.highValueTasks }}</strong></div>
      <div><span>分支展开</span><strong>{{ metrics.branchesExpanded }}</strong></div>
      <div><span>成本单位</span><strong>{{ metrics.steps }}</strong></div>
    </div>

    <div class="trace-layout">
      <section class="admin-panel trace-list">
        <div class="trace-search">
          <input v-model="filter" placeholder="搜索任务、模式、状态…" />
        </div>
        <p v-if="loading" class="admin-empty">加载中…</p>
        <p v-else-if="!filtered.length" class="admin-empty">还没有执行记录。</p>
        <button
          v-for="run in filtered"
          :key="run.id"
          type="button"
          class="trace-item"
          :class="{ active: selected?.id === run.id }"
          @click="open(run)"
        >
          <span class="trace-mode" :class="run.mode">{{ run.mode === 'plan' ? 'PLAN' : 'REACT' }}</span>
          <span class="trace-question">{{ run.question }}</span>
          <span class="trace-meta">
            <em :class="run.status.toLowerCase()">{{ run.status }}</em>
            <small>{{ formatTime(run.createdAt) }}</small>
          </span>
        </button>
      </section>

      <section class="admin-panel trace-detail">
        <p v-if="loadingDetail" class="admin-empty">加载详情…</p>
        <p v-else-if="!selected" class="admin-empty">选择左侧一条记录查看执行链路。</p>
        <template v-else>
          <div class="trace-detail-head">
            <div>
              <span class="trace-mode" :class="selected.mode">{{ selected.mode }}</span>
              <h2>{{ selected.question }}</h2>
            </div>
            <span class="trace-status" :class="selected.status.toLowerCase()">{{ selected.status }}</span>
          </div>
          <div class="trace-summary">
            <span>会话 {{ selected.sessionId.slice(0, 8) }}</span>
            <span>{{ formatTime(selected.createdAt) }}</span>
            <span>{{ selected.steps.length }} 步</span>
          </div>
          <div v-if="selected.answer" class="trace-answer">
            <strong>最终回答</strong>
            <p>{{ selected.answer }}</p>
          </div>
          <p v-if="selected.validation" class="trace-validation">验收：{{ selected.validation }}</p>
          <div class="trace-steps">
            <div v-for="step in selected.steps" :key="step.seq" class="trace-step" :class="stepTone(step)">
              <span class="trace-step-icon">{{ stepIcon(step) }}</span>
              <div class="trace-step-body">
                <div class="trace-step-title">
                  <strong>{{ step.title }}</strong>
                  <small>{{ step.type }} · {{ formatTime(step.createdAt) }}</small>
                </div>
                <pre v-if="step.detail">{{ step.detail }}</pre>
              </div>
            </div>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin-agent-runs { padding-bottom: 40px; }
.trace-metrics { display: grid; grid-template-columns: repeat(8, minmax(0, 1fr)); gap: 8px; margin-bottom: 16px; }
.trace-metrics div { display: flex; flex-direction: column; gap: 4px; padding: 10px 11px; border: 1px solid rgba(98, 119, 139, 0.12); border-radius: 11px; background: #fff; }
.trace-metrics span { color: #94a0aa; font-size: 9px; }
.trace-metrics strong { color: #3d566b; font-size: 16px; }
@media (max-width: 1100px) { .trace-metrics { grid-template-columns: repeat(4, minmax(0, 1fr)); } }
.trace-layout { display: grid; grid-template-columns: 340px 1fr; gap: 20px; align-items: start; }
.admin-panel { border: 1px solid rgba(98, 119, 139, 0.13); border-radius: 16px; background: #fff; padding: 16px; }
.admin-empty { color: #8e9aa5; font-size: 13px; padding: 16px 4px; }
.trace-list { padding: 12px; }
.trace-search input { width: 100%; padding: 9px 10px; border: 1px solid rgba(103, 126, 146, 0.2); border-radius: 9px; color: #3b5063; font: inherit; font-size: 12px; outline: none; margin-bottom: 8px; }
.trace-item { width: 100%; display: grid; grid-template-columns: auto 1fr; gap: 7px 8px; border: 0; border-radius: 10px; padding: 10px; background: transparent; text-align: left; cursor: pointer; }
.trace-item:hover { background: #f6f8f9; }
.trace-item.active { background: #eef3f6; }
.trace-mode { align-self: start; border-radius: 5px; padding: 2px 5px; font-size: 9px; letter-spacing: 0.04em; background: #e9eff3; color: #5c7285; }
.trace-mode.plan { background: #f5eddf; color: #977540; }
.trace-question { color: #3d5265; font-size: 12px; line-height: 1.5; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.trace-meta { grid-column: 2; display: flex; justify-content: space-between; gap: 6px; }
.trace-meta em { font-style: normal; font-size: 10px; color: #7e8d9a; }
.trace-meta em.done { color: #3f7c67; }
.trace-meta em.failed { color: #a35b4f; }
.trace-meta small { color: #a1abb3; font-size: 10px; }
.trace-detail-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.trace-detail-head h2 { margin: 8px 0 0; color: #33495c; font-size: 16px; line-height: 1.5; }
.trace-status { flex: 0 0 auto; border-radius: 999px; padding: 4px 9px; background: #eef2f5; color: #67798a; font-size: 10px; }
.trace-status.done { background: #eaf4ef; color: #3e7a64; }
.trace-status.failed { background: #fbeeeb; color: #a05a4c; }
.trace-summary { display: flex; gap: 14px; flex-wrap: wrap; margin: 10px 0 14px; color: #96a0a9; font-size: 10px; }
.trace-answer { padding: 12px; border-radius: 10px; background: #f7f9fa; }
.trace-answer strong { color: #5a6e80; font-size: 11px; }
.trace-answer p { margin: 6px 0 0; color: #415669; font-size: 12px; line-height: 1.8; white-space: pre-wrap; }
.trace-validation { color: #7e8b96; font-size: 11px; }
.trace-steps { margin-top: 14px; border-left: 1px solid rgba(105, 127, 147, 0.18); padding-left: 13px; }
.trace-step { display: flex; gap: 9px; margin: 0 0 13px; }
.trace-step-icon { flex: 0 0 auto; display: grid; place-items: center; width: 19px; height: 19px; margin-left: -23px; border-radius: 50%; background: #edf1f4; color: #657887; font-size: 10px; }
.trace-step.tool .trace-step-icon { background: #e7eff5; color: #426b8c; }
.trace-step.plan .trace-step-icon { background: #f6efe3; color: #957442; }
.trace-step.validation .trace-step-icon { background: #e8f2ed; color: #417a63; }
.trace-step.failed .trace-step-icon { background: #faeae7; color: #a25345; }
.trace-step-body { min-width: 0; flex: 1; }
.trace-step-title { display: flex; justify-content: space-between; gap: 8px; align-items: baseline; }
.trace-step-title strong { color: #4b6173; font-size: 11px; }
.trace-step-title small { color: #a1abb3; font-size: 9px; }
.trace-step pre { margin: 5px 0 0; padding: 8px 9px; border-radius: 7px; background: #f8fafb; color: #627484; font-family: Consolas, monospace; font-size: 10.5px; line-height: 1.65; white-space: pre-wrap; word-break: break-word; max-height: 260px; overflow: auto; }
@media (max-width: 980px) { .trace-layout { grid-template-columns: 1fr; } }
</style>
