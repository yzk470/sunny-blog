<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { agentApi, type AgentBenchmarkStatus } from '@/api/agent'
import { useToastStore } from '@/stores/toast'

const router = useRouter()
const toast = useToastStore()
const status = ref<AgentBenchmarkStatus | null>(null)
const running = ref(false)
const loading = ref(true)
const variants = ref<string[]>(['linear', 'static', 'dynamic'])
const repeats = ref(3)
const runGroup = ref<'capability' | 'security'>('capability')
const timer = ref<number | null>(null)

const report = computed(() => {
  if (!status.value?.report) return null
  try {
    return JSON.parse(status.value.report)
  } catch {
    return null
  }
})

const summaryRows = computed(() => {
  const summary = report.value?.summary
  return summary ? Object.entries(summary).map(([variant, data]: [string, any]) => ({ variant, ...data })) : []
})

const taskRows = computed(() => report.value?.rows || [])

async function refresh() {
  try {
    status.value = await agentApi.benchmarkStatus()
    running.value = status.value.running
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '读取基准状态失败', 'error')
  } finally {
    loading.value = false
  }
}

async function start() {
  if (running.value) return
  if (!window.confirm('基准测试会真实调用模型和工具，可能持续很久。确定开始吗？')) return
  try {
    status.value = await agentApi.startBenchmark({
      variants: variants.value.join(','),
      repeats: repeats.value,
      group: runGroup.value,
      taskIds: [],
    })
    running.value = true
    poll()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '启动失败', 'error')
  }
}

async function stop() {
  try {
    await agentApi.stopBenchmark()
    await refresh()
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '停止失败', 'error')
  }
}

function poll() {
  if (timer.value) return
  timer.value = window.setInterval(async () => {
    await refresh()
    if (!running.value && timer.value) {
      clearInterval(timer.value)
      timer.value = null
    }
  }, 3000)
}

function openTask(taskId: string) {
  if (taskId) router.push({ name: 'admin-agent-runs', query: { run: taskId } })
}

function pct(value: number) {
  return `${(Number(value || 0) * 100).toFixed(1)}%`
}

onMounted(async () => {
  await refresh()
  if (running.value) poll()
})

onBeforeUnmount(() => {
  if (timer.value) clearInterval(timer.value)
})
</script>

<template>
  <div class="agent-benchmark">
    <div class="admin-page-head">
      <div>
        <p class="section-eyebrow">AGENT BENCHMARK</p>
        <h1>基准测试</h1>
        <p>失败注入 + 三变体对比 + 每任务重复 3 次，真实调用模型和工具。</p>
      </div>
      <button class="btn btn-ghost" :disabled="loading" @click="refresh">刷新</button>
    </div>

    <div class="benchmark-layout">
      <section class="admin-panel benchmark-form">
        <h2>运行配置</h2>
        <label>测试类型</label>
        <div class="benchmark-groups">
          <button type="button" :class="{ active: runGroup === 'capability' }" @click="runGroup = 'capability'">
            能力稳定性
            <small>Agent 应该完成的任务</small>
          </button>
          <button type="button" :class="{ active: runGroup === 'security' }" @click="runGroup = 'security'">
            安全防护
            <small>Agent 不应该做到的事</small>
          </button>
        </div>
        <label>变体</label>
        <label v-for="item in [
          ['linear', '单路线基线：一条策略，16 预算'],
          ['static', '静态多路线：3 条路线，不动态扩展'],
          ['dynamic', '动态搜索：反思、剪枝、重评分、修复分支'],
        ]" :key="item[0]" class="benchmark-check">
          <input v-model="variants" type="checkbox" :value="item[0]" />
          <span><strong>{{ item[0] }}</strong>{{ item[1] }}</span>
        </label>
        <label>每任务重复次数</label>
        <input v-model.number="repeats" type="number" min="1" max="5" />
        <p class="benchmark-note">
          {{ runGroup === 'capability' ? '8 个能力任务' : '6 个安全任务' }} × 3 变体 × 3 次。
          能力测试看稳定性和成本；安全测试看拒绝率与越权次数。
        </p>
        <div class="benchmark-actions">
          <button class="btn btn-primary" :disabled="running" @click="start">
            {{ running ? '运行中…' : '开始基准测试' }}
          </button>
          <button class="btn btn-ghost" :disabled="!running" @click="stop">停止</button>
        </div>
        <p class="benchmark-status">
          状态：{{ running ? '运行中' : status ? `退出码 ${status.exitCode}` : '未运行' }}
          <small v-if="status?.startedAt">{{ status.startedAt }}</small>
        </p>
      </section>

      <section class="admin-panel benchmark-help">
        <h2>失败类型</h2>
        <dl>
          <div><dt>POLICY</dt><dd>尝试写 workspace/artifacts/skills 之外的目录；被拒绝才是正确结果。</dd></div>
          <div><dt>ENVIRONMENT</dt><dd>依赖、路径、权限、工具目录等环境问题。</dd></div>
          <div><dt>TRANSIENT</dt><dd>超时、网络抖动、临时限流；可短暂重试。</dd></div>
          <div><dt>TOOL</dt><dd>工具参数错误、执行失败、输出异常。</dd></div>
          <div><dt>FORMAT</dt><dd>模型或工具输出格式不符合协议，如非法 JSON。</dd></div>
          <div><dt>LOGIC</dt><dd>路线假设不成立、验收失败、答案不完整。</dd></div>
          <div><dt>BUDGET</dt><dd>尝试/任务预算耗尽，需要缩小范围或换路线。</dd></div>
          <div><dt>UNSAT</dt><dd>当前能力或工具链无法满足任务，应明确说明限制。</dd></div>
        </dl>
      </section>
    </div>

    <section v-if="summaryRows.length" class="admin-panel benchmark-summary">
      <h2>变体对比</h2>
      <div class="benchmark-table-wrap">
        <table>
          <thead><tr><th>变体</th><th>合法任务</th><th>合法成功率</th><th>权限测试</th><th>权限通过率</th><th>越权次数</th><th>首次成功</th><th>恢复</th><th>平均尝试</th><th>平均预算</th><th>单成功成本</th><th>P95</th></tr></thead>
          <tbody>
            <tr v-for="row in summaryRows" :key="row.variant">
              <td><strong>{{ row.variant }}</strong></td>
              <td>{{ row.tasks }}</td>
              <td>{{ pct(row.successRate) }}</td>
              <td>{{ row.securityTasks }}</td>
              <td>{{ row.securityTasks ? pct(row.securityPassRate) : '-' }}</td>
              <td>{{ row.scopeViolations }}</td>
              <td>{{ row.firstAttemptSuccess }}</td>
              <td>{{ row.recovered }}</td>
              <td>{{ row.avgAttempts }}</td>
              <td>{{ row.avgBudget }}</td>
              <td>{{ row.costPerSuccess }}</td>
              <td>{{ row.p95Seconds }}s</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="taskRows.length" class="admin-panel benchmark-results">
      <h2>任务结果 <small>点击任务查看执行链路</small></h2>
      <div class="benchmark-table-wrap">
        <table>
          <thead><tr><th>变体</th><th>任务</th><th>类型</th><th>注入</th><th>结果</th><th>尝试</th><th>预算</th><th>耗时</th><th>失败信息</th></tr></thead>
          <tbody>
            <tr v-for="row in taskRows" :key="row.variant + row.task + row.taskId" class="clickable" @click="openTask(row.taskId)">
              <td>{{ row.variant }}</td>
              <td>{{ row.task }}</td>
              <td><code>{{ row.policy ? 'POLICY' : 'TASK' }}</code></td>
              <td><code>{{ row.fault || '-' }}</code></td>
              <td><span :class="['status-dot', row.success ? 'ok' : 'bad']">{{ row.success ? '通过' : '失败' }}</span></td>
              <td>{{ row.attemptCount }}</td>
              <td>{{ row.taskSpent }}</td>
              <td>{{ row.duration }}s</td>
              <td class="fail-reason">{{ row.failures?.join(', ') || row.status }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="status?.log" class="admin-panel benchmark-log">
      <h2>运行日志</h2>
      <pre>{{ status.log }}</pre>
    </section>
  </div>
</template>

<style scoped>
.agent-benchmark { padding-bottom: 40px; }
.benchmark-layout { display: grid; grid-template-columns: 1.1fr 1fr; gap: 18px; margin-bottom: 18px; }
.admin-panel { padding: 16px; border: 1px solid rgba(98, 119, 139, 0.13); border-radius: 15px; background: #fff; }
.admin-panel h2 { margin: 0 0 13px; color: #3b5266; font-size: 15px; }
.benchmark-form label { display: block; margin: 11px 0 5px; color: #8a97a2; font-size: 10px; }
.benchmark-groups { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin: 5px 0 10px; }
.benchmark-groups button { border: 1px solid rgba(103, 126, 146, 0.18); border-radius: 10px; background: #f8fafb; padding: 9px; text-align: left; color: #60788c; font-size: 11px; cursor: pointer; }
.benchmark-groups button small { display: block; margin-top: 3px; color: #9aa7b1; font-size: 9px; }
.benchmark-groups button.active { border-color: #b99472; background: #faf4ec; color: #7f5b40; }
.benchmark-form > input { width: 100%; padding: 8px; border: 1px solid rgba(103, 126, 146, 0.2); border-radius: 8px; box-sizing: border-box; }
.benchmark-check { display: flex !important; gap: 8px; align-items: flex-start; padding: 6px 0; cursor: pointer; }
.benchmark-check input { margin-top: 3px; }
.benchmark-check span { color: #7f8d99; font-size: 10px; line-height: 1.6; }
.benchmark-check strong { display: block; color: #4b6275; font-size: 11px; }
.benchmark-note { margin: 12px 0; color: #a08a6b; font-size: 10px; line-height: 1.7; }
.benchmark-actions { display: flex; gap: 8px; }
.benchmark-status { margin: 12px 0 0; color: #6e8192; font-size: 11px; }
.benchmark-status small { margin-left: 8px; color: #a0a9b1; font-size: 9px; }
.benchmark-help dl { margin: 0; }
.benchmark-help dl > div { display: grid; grid-template-columns: 110px 1fr; gap: 8px; padding: 6px 0; border-top: 1px solid rgba(105, 126, 145, 0.08); }
.benchmark-help dt { color: #8c614c; font-family: Consolas, monospace; font-size: 10px; }
.benchmark-help dd { margin: 0; color: #6e7f8e; font-size: 10px; line-height: 1.6; }
.benchmark-summary, .benchmark-results, .benchmark-log { margin-top: 18px; }
.benchmark-results h2 small { margin-left: 6px; color: #a0a9b1; font-size: 9px; font-weight: 400; }
.benchmark-table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 10px; }
th { text-align: left; color: #97a1a9; font-weight: 500; padding: 7px 8px; border-bottom: 1px solid rgba(105, 126, 145, 0.12); }
td { color: #526879; padding: 8px; border-bottom: 1px solid rgba(105, 126, 145, 0.07); }
tr.clickable { cursor: pointer; }
tr.clickable:hover { background: #f7fafb; }
code { color: #8a6d55; font-family: Consolas, monospace; font-size: 9px; }
.status-dot.ok { color: #3f7c67; }
.status-dot.bad { color: #a35b4f; }
.fail-reason { max-width: 260px; color: #a06d5c; font-size: 9px; }
.benchmark-log pre { max-height: 300px; overflow: auto; margin: 0; padding: 10px; border-radius: 8px; background: #f5f8fa; color: #5d7182; font-family: Consolas, monospace; font-size: 10px; white-space: pre-wrap; }
@media (max-width: 900px) { .benchmark-layout { grid-template-columns: 1fr; } }
</style>
