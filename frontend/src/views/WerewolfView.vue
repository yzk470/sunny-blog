<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import {
  werewolfApi,
  type WerewolfGame,
  type WerewolfLog,
  type WerewolfPlayer,
  type WerewolfStreamPayload,
} from '@/api/werewolf'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()

const setup = reactive({
  playerName: '月白',
  playerCount: 8,
  role: 'random',
})
const creating = ref(false)
const busy = ref(false)
const error = ref('')
const game = ref<WerewolfGame | null>(null)
const selectedTarget = ref('')
const speech = ref('')
const timelineRef = ref<HTMLElement | null>(null)
const aiConfigured = ref(false)

type StreamSpeech = {
  key: string
  speakerId: string
  speakerName: string
  seat: number
  text: string
  complete: boolean
}

type TimelineEntry =
  | { kind: 'divider'; key: string; label: string; sub: string }
  | { kind: 'log'; key: string; log: WerewolfLog }

const liveSpeeches = ref<StreamSpeech[]>([])
const activeSpeechKey = ref('')
const thinking = ref('')
const speakingTurn = ref(false)

const roleOptions = [
  { value: 'random', label: '随机身份', icon: '✦', hint: '把身份交给月白小镇的命运' },
  { value: 'WEREWOLF', label: '狼人', icon: '🐺', hint: '每晚与狼队共同袭击一人' },
  { value: 'SEER', label: '预言家', icon: '🔮', hint: '每晚查验一名玩家的阵营' },
  { value: 'WITCH', label: '女巫', icon: '🧪', hint: '解药与毒药，各只有一次' },
  { value: 'HUNTER', label: '猎人', icon: '🏹', hint: '出局时开枪带走一人，8 人起' },
  { value: 'VILLAGER', label: '村民', icon: '🌾', hint: '用发言和投票找出狼人' },
]

const availableRoles = computed(() =>
  roleOptions.filter((role) => role.value !== 'HUNTER' || setup.playerCount >= 8),
)

const myPlayer = computed<WerewolfPlayer | null>(() => {
  if (!game.value) return null
  return game.value.players.find((p) => p.id === game.value?.humanId) || null
})

const alivePlayers = computed(() => game.value?.players.filter((p) => p.alive) ?? [])
const targetIds = computed(() => new Set(game.value?.waiting?.options.map((o) => o.id) ?? []))
const phaseSteps = ['夜晚', '白天', '投票']
const phaseStepIndex = computed(() => {
  const phase = game.value?.phase
  if (!phase || phase === 'GAME_OVER') return -1
  if (phase.startsWith('NIGHT')) return 0
  if (phase === 'DAWN' || phase === 'DISCUSSION') return 1
  return 2
})

const winnerTone = computed(() => {
  if (!game.value?.winner) return ''
  return game.value.winner === 'WEREWOLF' ? 'wolf-win' : 'good-win'
})

const iWon = computed(() => {
  if (!game.value?.winner || !game.value.myTeam) return false
  return game.value.myTeam === (game.value.winner === 'WEREWOLF' ? '狼人阵营' : '好人阵营')
})

const actionLabel = computed(() => {
  const type = game.value?.waiting?.type
  if (type === 'WOLF_KILL') return '确认袭击'
  if (type === 'WITCH_SAVE') return selectedTarget.value === 'pass' ? '保留解药' : '使用解药'
  if (type === 'WITCH_POISON') return selectedTarget.value === 'pass' ? '不使用毒药' : '使用毒药'
  if (type === 'SEER_CHECK') return '查验身份'
  if (type === 'VOTE') return '投出这一票'
  if (type === 'HUNTER_SHOOT') return '开枪'
  return '确认'
})

const timeline = computed<TimelineEntry[]>(() => {
  if (!game.value) return []
  const entries: TimelineEntry[] = []
  let segment = ''
  for (const log of game.value.logs) {
    if (log.round > 0) {
      const night = log.phase.startsWith('NIGHT')
      const key = `${log.round}-${night ? 'night' : 'day'}`
      if (key !== segment) {
        entries.push({
          kind: 'divider',
          key: `divider-${key}`,
          label: `第 ${log.round} ${night ? '夜' : '日'}`,
          sub: night ? 'NIGHT FALLS' : 'DAY BREAKS',
        })
        segment = key
      }
    }
    entries.push({ kind: 'log', key: `log-${log.seq}`, log })
  }
  return entries
})

watch(
  () => setup.playerCount,
  () => {
    if (setup.role === 'HUNTER' && setup.playerCount < 8) setup.role = 'random'
  },
)

watch(
  () => [game.value?.logs.length, liveSpeeches.value.map((s) => s.text).join('|'), thinking.value],
  async () => {
    await nextTick()
    scrollBottom()
  },
)

onMounted(async () => {
  try {
    const status = await werewolfApi.status()
    aiConfigured.value = status.configured
  } catch {
    aiConfigured.value = false
  }
})

function scrollBottom() {
  if (timelineRef.value) timelineRef.value.scrollTop = timelineRef.value.scrollHeight
}

function roleHint(value: string) {
  return roleOptions.find((role) => role.value === value)?.hint || ''
}

function selectRole(role: string) {
  setup.role = role
}

function applyGame(next: WerewolfGame) {
  game.value = next
  selectedTarget.value = next.waiting?.options[0]?.id ?? ''
}

async function createGame(watchMode = false) {
  if (creating.value) return
  if (!setup.playerName.trim()) {
    toast.show('先给自己取一个名字吧', 'info', 2200)
    return
  }
  creating.value = true
  error.value = ''
  try {
    const created = await werewolfApi.create({
      playerName: setup.playerName.trim(),
      playerCount: setup.playerCount,
      role: setup.role,
    })
    applyGame(created)
    await runStream(watchMode ? { action: 'AUTO' } : { action: 'ADVANCE' })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '开局失败，请稍后再试'
  } finally {
    creating.value = false
  }
}

async function runStream(payload: WerewolfStreamPayload) {
  if (!game.value || busy.value) return
  busy.value = true
  liveSpeeches.value = []
  activeSpeechKey.value = ''
  speakingTurn.value = false
  thinking.value = 'AI 正在整理这一轮的局势…'
  const gameId = game.value.id
  try {
    await werewolfApi.stream(gameId, payload, {
      onThinking(text) {
        thinking.value = text || 'AI 正在整理这一轮的局势…'
      },
      onSpeakingTurn() {
        thinking.value = ''
        activeSpeechKey.value = ''
        speakingTurn.value = true
      },
      onSpeechStart(data) {
        speakingTurn.value = false
        thinking.value = ''
        const key = `live-${Date.now()}-${data.speakerId}-${liveSpeeches.value.length}`
        liveSpeeches.value.push({
          key,
          speakerId: data.speakerId,
          speakerName: data.speakerName,
          seat: data.seat,
          text: '',
          complete: false,
        })
        activeSpeechKey.value = key
      },
      onToken(text) {
        const current = liveSpeeches.value.find((s) => s.key === activeSpeechKey.value)
        if (current) current.text += text
      },
      onSpeechEnd(text) {
        const current = liveSpeeches.value.find((s) => s.key === activeSpeechKey.value)
        if (current) {
          current.text = text
          current.complete = true
        }
        activeSpeechKey.value = ''
      },
      onState(next) {
        if (game.value?.id !== gameId) return
        applyGame(next)
        liveSpeeches.value = []
        activeSpeechKey.value = ''
        thinking.value = ''
      },
      onDone() {
        thinking.value = ''
      },
    })
  } catch (err) {
    toast.show(err instanceof Error ? err.message : '本回合处理失败', 'error', 2800)
  } finally {
    busy.value = false
    thinking.value = ''
  }
}

function advance() {
  if (game.value?.waiting) {
    toast.show('先完成当前行动，再继续推进', 'info', 2200)
    return
  }
  void runStream({ action: 'ADVANCE' })
}

function submitAction() {
  if (!game.value?.waiting || !selectedTarget.value) return
  const waiting = game.value.waiting
  void runStream({ action: 'ACTION', type: waiting.type, targetId: selectedTarget.value })
}

function makeSpeech() {
  const text = speech.value.trim()
  if (!text || !game.value) return
  speech.value = ''
  void runStream({ action: 'SPEAK', text })
}

function autoPlay() {
  void runStream({ action: 'AUTO' })
}

function selectTarget(id: string) {
  if (!targetIds.value.has(id)) return
  selectedTarget.value = id
}

function resetGame() {
  if (game.value) void werewolfApi.remove(game.value.id).catch(() => undefined)
  game.value = null
  selectedTarget.value = ''
  speech.value = ''
  error.value = ''
  liveSpeeches.value = []
  activeSpeechKey.value = ''
  thinking.value = ''
}

function isTargetable(player: WerewolfPlayer) {
  return targetIds.value.has(player.id)
}

function isSelected(player: WerewolfPlayer) {
  return selectedTarget.value === player.id
}

function playerSubtitle(player: WerewolfPlayer) {
  if (!player.alive) return player.roleLabel ? `已出局 · ${player.roleLabel}` : '已出局'
  if (player.human) return '你 · 玩家'
  if (player.claimedRole) return `自称 ${player.claimedRole}`
  return '身份未知'
}

function speechParts(log: WerewolfLog) {
  const index = log.text.indexOf('：')
  if (index <= 0) return { name: '', body: log.text }
  return { name: log.text.slice(0, index), body: log.text.slice(index + 1) }
}

function isSpeech(log: WerewolfLog) {
  return log.type === 'speech'
}

function isMySpeech(log: WerewolfLog) {
  return speechParts(log).name === myPlayer.value?.name
}

function logIcon(log: WerewolfLog) {
  if (log.privateLog) return '🔒'
  if (log.type === 'vote') return '▣'
  if (log.type === 'death') return '☾'
  if (log.type === 'claim') return '✦'
  if (log.type === 'hunter') return '🏹'
  if (log.type === 'result') return '★'
  if (log.type === 'dawn') return '☀'
  return '·'
}

function eventClass(log: WerewolfLog) {
  return [`type-${log.type}`, { private: log.privateLog }]
}
</script>

<template>
  <div class="werewolf-page page-shell">
    <div class="container">
      <header class="ww-hero">
        <div class="ww-hero-copy">
          <p class="section-eyebrow">WEREWOLF ARENA · DEEPSEEK AGENTS</p>
          <h1>月白小镇的<br /><span>狼人杀之夜</span></h1>
          <p class="ww-hero-desc">
            八位身份不明的镇民，一场关于发言、投票和信任的博弈。
            你可以亲自入局，也可以坐下来，看 DeepSeek 驱动的 AI 们把这一夜演完。
          </p>
          <div class="ww-hero-tags">
            <span>{{ aiConfigured ? 'DeepSeek 决策 + 发言' : '模型未配置 · 本地回退' }}</span>
            <span>人机对战</span>
            <span>流式对话</span>
          </div>
        </div>
        <div class="ww-hero-orbit" aria-hidden="true">
          <div class="ww-moon">☾</div>
          <span class="ww-orbit-star s1">✦</span>
          <span class="ww-orbit-star s2">✧</span>
          <span class="ww-orbit-star s3">·</span>
          <p>THE NIGHT<br />COUNCIL</p>
        </div>
      </header>

      <section v-if="!game" class="ww-setup">
        <div class="ww-setup-main">
          <div class="ww-setup-head">
            <p class="section-eyebrow">BEFORE THE NIGHT</p>
            <h2>坐上牌桌</h2>
            <p>选择你的名字、镇民人数和想要的身份。身份发放后，只有你自己知道。</p>
          </div>

          <div class="ww-form-row">
            <label class="ww-field">
              <span>你的名字</span>
              <input
                v-model="setup.playerName"
                maxlength="12"
                placeholder="例如：月白"
                @keyup.enter="createGame(false)"
              />
            </label>
            <label class="ww-field">
              <span>镇民人数</span>
              <select v-model="setup.playerCount">
                <option :value="6">6 人 · 轻量局</option>
                <option :value="8">8 人 · 标准局</option>
                <option :value="10">10 人 · 进阶局</option>
                <option :value="12">12 人 · 满桌局</option>
              </select>
            </label>
          </div>

          <div class="ww-role-label">
            <span>你希望拿到</span>
            <em>{{ roleHint(setup.role) }}</em>
          </div>
          <div class="ww-role-grid">
            <button
              v-for="role in availableRoles"
              :key="role.value"
              type="button"
              class="ww-role-option"
              :class="{ active: setup.role === role.value }"
              @click="selectRole(role.value)"
            >
              <span class="ww-role-icon">{{ role.icon }}</span>
              <strong>{{ role.label }}</strong>
              <small>{{ role.hint }}</small>
            </button>
          </div>

          <div class="ww-start-row">
            <button type="button" class="btn btn-primary ww-start-btn" :disabled="creating" @click="createGame(false)">
              <span v-if="creating" class="spinner small-spinner"></span>
              {{ creating ? '正在发牌…' : '开始这一局' }}
            </button>
            <button type="button" class="btn btn-ghost ww-watch-btn" :disabled="creating" @click="createGame(true)">
              ☾ 只看 AI 演一局
            </button>
          </div>
          <p v-if="error" class="ww-error">{{ error }}</p>
        </div>

        <aside class="ww-setup-aside">
          <div class="ww-rules-card">
            <p class="section-eyebrow">HOW TO PLAY</p>
            <h3>这一夜怎么玩</h3>
            <ol>
              <li><strong>夜晚</strong> 狼人袭击、女巫用药、预言家查验。</li>
              <li><strong>白天</strong> 听取 AI 发言，也可以自己发言。</li>
              <li><strong>投票</strong> 放逐最可疑的人，猎人出局可以开枪。</li>
              <li><strong>胜负</strong> 放逐所有狼人，或让狼人数量达到好人数。</li>
            </ol>
          </div>
          <div class="ww-mini-note">
            <span>☀</span>
            <p>没想好怎么玩？直接开一局观战，看 DeepSeek 和本地 Agent 一起把局演完。</p>
          </div>
        </aside>
      </section>

      <section v-else class="ww-game">
        <div class="ww-game-bar">
          <div class="ww-round">
            <span class="ww-round-moon">{{ game.phase.startsWith('NIGHT') ? '☾' : '☀' }}</span>
            <div>
              <p>第 {{ game.round }} {{ game.phase.startsWith('NIGHT') ? '夜' : '日' }}</p>
              <strong>{{ game.phaseLabel }}</strong>
            </div>
          </div>
          <div class="ww-phase-steps">
            <span
              v-for="(step, index) in phaseSteps"
              :key="step"
              :class="{ active: index === phaseStepIndex, done: index < phaseStepIndex }"
            >
              {{ step }}
            </span>
          </div>
          <div class="ww-game-actions">
            <span class="ww-ai-state" :class="{ on: aiConfigured }">
              {{ aiConfigured ? 'DeepSeek 决策' : '本地回退' }}
            </span>
            <button type="button" class="ww-icon-btn" title="换个角色" @click="resetGame">＋ 新局</button>
            <button type="button" class="ww-icon-btn" :disabled="busy || game.over" title="自动演算" @click="autoPlay">
              {{ busy ? '演算中…' : '自动演算' }}
            </button>
          </div>
        </div>

        <div class="ww-layout">
          <aside class="ww-roster">
            <div class="ww-panel-head">
              <div>
                <p class="section-eyebrow">THE TOWN</p>
                <h2>镇民席</h2>
              </div>
              <span class="ww-alive-count">{{ alivePlayers.length }}/{{ game.players.length }} 存活</span>
            </div>
            <div class="ww-player-list">
              <button
                v-for="player in game.players"
                :key="player.id"
                type="button"
                class="ww-player"
                :class="{
                  dead: !player.alive,
                  human: player.human,
                  targetable: isTargetable(player),
                  selected: isSelected(player),
                  'wolf-claim': player.claimedRole === '狼人',
                }"
                :disabled="!isTargetable(player)"
                @click="selectTarget(player.id)"
              >
                <span class="ww-seat">{{ String(player.seat).padStart(2, '0') }}</span>
                <span class="ww-avatar">{{ player.name.slice(0, 1) }}</span>
                <span class="ww-player-copy">
                  <strong>{{ player.name }}<em v-if="player.human">你</em></strong>
                  <small>{{ playerSubtitle(player) }}</small>
                </span>
                <span class="ww-player-meta">
                  <span v-if="player.roleIcon" class="ww-player-role">{{ player.roleIcon }}</span>
                  <span class="ww-life-dot" :class="{ on: player.alive }"></span>
                </span>
              </button>
            </div>
            <div class="ww-roster-legend">
              <span><i class="dot good"></i>存活</span>
              <span><i class="dot dead"></i>出局</span>
              <span><i class="dot target"></i>可选目标</span>
            </div>
          </aside>

          <main class="ww-table">
            <div v-if="game.over" class="ww-result" :class="winnerTone">
              <span class="ww-result-icon">{{ iWon ? '☀' : '☾' }}</span>
              <p class="section-eyebrow">THE NIGHT IS OVER</p>
              <h2>{{ game.winnerLabel }}获胜</h2>
              <p>{{ iWon ? '你站在了胜利的一边。' : '这一夜不属于你，但故事还没有结束。' }}</p>
              <div class="ww-result-actions">
                <button type="button" class="btn btn-primary" @click="resetGame">再来一局</button>
                <button type="button" class="btn btn-ghost" @click="createGame(true)" :disabled="creating">
                  {{ creating ? '发牌中…' : '再看 AI 演一局' }}
                </button>
              </div>
            </div>

            <div class="ww-dialogue">
              <div class="ww-dialogue-head">
                <div>
                  <p class="section-eyebrow">THE NIGHT COUNCIL</p>
                  <h2>这一夜的对话</h2>
                </div>
                <span>{{ game.summary }}</span>
              </div>

              <div ref="timelineRef" class="ww-timeline">
                <template v-for="entry in timeline" :key="entry.key">
                  <div v-if="entry.kind === 'divider'" class="ww-divider">
                    <span>{{ entry.label }}</span>
                    <em>{{ entry.sub }}</em>
                  </div>
                  <div
                    v-else-if="isSpeech(entry.log)"
                    class="ww-chat-row"
                    :class="{
                      mine: isMySpeech(entry.log),
                      private: entry.log.privateLog,
                      claim: entry.log.type === 'claim',
                    }"
                  >
                    <span class="ww-chat-avatar">{{ speechParts(entry.log).name.slice(0, 1) }}</span>
                    <div class="ww-chat-body">
                      <div class="ww-chat-meta">
                        <strong>{{ speechParts(entry.log).name }}</strong>
                        <em>{{ entry.log.type === 'claim' ? '公开身份' : entry.log.phaseLabel }}</em>
                      </div>
                      <p class="ww-bubble">{{ speechParts(entry.log).body }}</p>
                    </div>
                  </div>
                  <div v-else class="ww-event-row" :class="eventClass(entry.log)">
                    <span class="ww-event-icon">{{ logIcon(entry.log) }}</span>
                    <p><strong>{{ entry.log.phaseLabel }}</strong>{{ entry.log.text }}</p>
                  </div>
                </template>

                <div
                  v-for="speech in liveSpeeches"
                  :key="speech.key"
                  class="ww-chat-row"
                  :class="{ streaming: !speech.complete, mine: speech.speakerId === game.humanId }"
                >
                  <span class="ww-chat-avatar">{{ speech.speakerName.slice(0, 1) }}</span>
                  <div class="ww-chat-body">
                    <div class="ww-chat-meta">
                      <strong>{{ speech.speakerName }}</strong>
                      <em>{{ speech.complete ? '已发言' : aiConfigured ? 'DeepSeek 输出中' : '生成中' }}</em>
                    </div>
                    <p class="ww-bubble">{{ speech.text }}<span v-if="!speech.complete" class="ww-cursor"></span></p>
                  </div>
                </div>

                <div v-if="thinking && !activeSpeechKey" class="ww-thinking">
                  <span class="ww-thinking-dots"><i></i><i></i><i></i></span>
                  {{ thinking }}
                </div>
              </div>

              <div v-if="!game.over" class="ww-composer">
                <template v-if="speakingTurn">
                  <div class="ww-composer-head">
                    <span>YOUR TURN · SAY SOMETHING</span>
                    <strong>轮到你发言</strong>
                  </div>
                  <p class="ww-composer-prompt">
                    以 {{ myPlayer?.name || '你' }} 的身份说一句。AI 只知道自己身份和公开信息，下一句会接你的话。
                  </p>
                  <div class="ww-speech-form">
                    <input v-model="speech" maxlength="160" placeholder="例如：我觉得 3 号太安静了，先听听他的解释…" @keyup.enter="makeSpeech" />
                    <button type="button" class="btn btn-primary" :disabled="busy || !speech.trim()" @click="makeSpeech">发言</button>
                  </div>
                </template>
                <template v-else-if="game.waiting">
                  <div class="ww-composer-head">
                    <span>YOUR MOVE · {{ game.waiting.type.replace('_', ' ') }}</span>
                    <strong>{{ game.waiting.title }}</strong>
                  </div>
                  <p class="ww-composer-prompt">{{ game.waiting.prompt }}</p>
                  <div class="ww-options">
                    <button
                      v-for="option in game.waiting.options"
                      :key="option.id"
                      type="button"
                      class="ww-option"
                      :class="{ active: selectedTarget === option.id, pass: option.id === 'pass' }"
                      @click="selectedTarget = option.id"
                    >
                      <span class="ww-option-mark">{{ selectedTarget === option.id ? '●' : '○' }}</span>
                      {{ option.label }}
                    </button>
                  </div>
                  <button type="button" class="btn btn-primary ww-confirm" :disabled="busy || !selectedTarget" @click="submitAction">
                    <span v-if="busy" class="spinner small-spinner"></span>
                    {{ busy ? '记录中…' : actionLabel }}
                  </button>
                </template>
                <template v-else>
                  <p v-if="!game.humanAlive" class="ww-watch-note">你已经出局，可以继续看 AI 们把这一夜演完。</p>
                  <button type="button" class="btn btn-primary ww-continue-btn" :disabled="busy" @click="advance">
                    <span v-if="busy" class="spinner small-spinner"></span>
                    {{ busy ? 'AI 思考中…' : '继续 · 让 AI 行动' }}
                  </button>
                </template>
              </div>
            </div>
          </main>

          <aside class="ww-side">
            <section class="ww-identity" :class="`role-${game.myRole.toLowerCase()}`">
              <div class="ww-identity-head">
                <span class="ww-identity-icon">{{ game.myRoleIcon }}</span>
                <div>
                  <p class="section-eyebrow">YOUR IDENTITY</p>
                  <h2>{{ game.myRoleLabel }}</h2>
                  <span>{{ game.myTeam }}</span>
                </div>
              </div>
              <p class="ww-identity-desc">{{ game.myRoleDescription }}</p>
              <div class="ww-abilities">
                <span v-if="game.myRole === 'WITCH'" :class="{ used: !game.abilities.antidote }">
                  🧪 解药 {{ game.abilities.antidote ? '可用' : '已用' }}
                </span>
                <span v-if="game.myRole === 'WITCH'" :class="{ used: !game.abilities.poison }">
                  ☠ 毒药 {{ game.abilities.poison ? '可用' : '已用' }}
                </span>
                <span v-if="game.myRole === 'HUNTER'" :class="{ used: !game.abilities.canShoot }">
                  🏹 猎枪 {{ game.abilities.canShoot ? '可用' : '已用' }}
                </span>
                <span v-if="game.myRole === 'WEREWOLF'">🐺 与狼队共谋</span>
              </div>
              <div v-if="game.teammates.length" class="ww-teammates">
                <span>你的狼队友</span>
                <strong v-for="mate in game.teammates" :key="mate">{{ mate }}</strong>
              </div>
              <p v-if="!game.humanAlive" class="ww-dead-note">你已出局，正在观战。</p>
            </section>

            <section class="ww-stat-card">
              <p class="section-eyebrow">TABLE STATE</p>
              <div class="ww-stat-row"><span>存活</span><strong>{{ game.summary.split('·')[0].trim() }}</strong></div>
              <div class="ww-stat-row"><span>狼人</span><strong>{{ game.summary.split('·')[1]?.trim() || '—' }}</strong></div>
              <div class="ww-stat-row"><span>好人数</span><strong>{{ game.summary.split('·')[2]?.trim() || '—' }}</strong></div>
            </section>

            <section class="ww-side-controls">
              <button type="button" class="btn btn-ghost" @click="resetGame">☾ 重新开局</button>
              <button type="button" class="btn btn-ghost" :disabled="busy || game.over" @click="autoPlay">
                {{ busy ? '演算中…' : '✦ 自动演算完整局' }}
              </button>
              <p>{{ aiConfigured ? 'AI 的刀人、查验、用药、投票、开枪和发言全部由 DeepSeek 决策。' : '当前没有可用的模型 Key，已启用本地安全回退。' }}</p>
            </section>
          </aside>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.werewolf-page {
  padding-bottom: 30px;
}

.ww-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 28px;
  align-items: center;
  padding: 34px 0 30px;
}

.ww-hero h1 {
  margin: 8px 0 14px;
  font-size: clamp(2.2rem, 4.8vw, 4rem);
  line-height: 1.04;
  letter-spacing: -0.045em;
  color: var(--text-strong, #1d2b38);
}

.ww-hero h1 span {
  color: #9b6a2f;
}

.ww-hero-desc {
  max-width: 610px;
  margin: 0;
  color: var(--text-muted, #64748b);
  line-height: 1.85;
}

.ww-hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.ww-hero-tags span {
  padding: 5px 10px;
  border: 1px solid rgba(155, 106, 47, 0.2);
  border-radius: 999px;
  color: #8b5e24;
  background: rgba(255, 246, 226, 0.64);
  font-size: 0.75rem;
}

.ww-hero-orbit {
  position: relative;
  min-height: 210px;
  border: 1px solid rgba(151, 176, 201, 0.22);
  border-radius: 28px;
  background:
    radial-gradient(circle at 70% 20%, rgba(255, 227, 163, 0.18), transparent 28%),
    linear-gradient(145deg, rgba(17, 33, 52, 0.95), rgba(11, 22, 38, 0.98));
  box-shadow: 0 18px 50px rgba(8, 18, 31, 0.18);
  overflow: hidden;
}

.ww-hero-orbit::before,
.ww-hero-orbit::after {
  content: '';
  position: absolute;
  border: 1px solid rgba(196, 217, 237, 0.15);
  border-radius: 50%;
}

.ww-hero-orbit::before {
  inset: 28px 34px;
}

.ww-hero-orbit::after {
  inset: 62px 68px;
}

.ww-moon {
  position: absolute;
  top: 30px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 2.8rem;
  color: #ffe3a3;
  text-shadow: 0 0 26px rgba(255, 227, 163, 0.45);
}

.ww-hero-orbit p {
  position: absolute;
  bottom: 24px;
  left: 0;
  right: 0;
  margin: 0;
  text-align: center;
  color: rgba(226, 238, 250, 0.54);
  font-size: 0.62rem;
  letter-spacing: 0.24em;
  line-height: 1.7;
}

.ww-orbit-star {
  position: absolute;
  color: #f0c97b;
  font-size: 0.8rem;
}

.ww-orbit-star.s1 { top: 48px; left: 28px; }
.ww-orbit-star.s2 { top: 96px; right: 30px; }
.ww-orbit-star.s3 { bottom: 64px; left: 38px; }

.ww-setup {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 24px;
  align-items: start;
}

.ww-setup-main,
.ww-rules-card,
.ww-mini-note,
.ww-roster,
.ww-table > .ww-action-card,
.ww-log-card,
.ww-identity,
.ww-stat-card,
.ww-side-controls {
  border: 1px solid rgba(133, 158, 183, 0.16);
  border-radius: 22px;
  background: rgba(255, 252, 246, 0.72);
  box-shadow: 0 14px 38px rgba(74, 95, 116, 0.08);
}

.ww-setup-main {
  padding: 30px;
}

.ww-setup-head h2 {
  margin: 8px 0 8px;
  font-size: 1.8rem;
  color: var(--text-strong, #1d2b38);
}

.ww-setup-head p:last-child {
  margin: 0;
  color: var(--text-muted, #64748b);
  line-height: 1.75;
}

.ww-form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 26px;
}

.ww-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.ww-field > span {
  color: #7b8ea2;
  font-size: 0.74rem;
  letter-spacing: 0.08em;
}

.ww-field input,
.ww-field select,
.ww-speech-form input {
  width: 100%;
  height: 44px;
  padding: 0 13px;
  border: 1px solid rgba(132, 158, 183, 0.22);
  border-radius: 12px;
  outline: none;
  background: rgba(255, 255, 255, 0.82);
  color: var(--text-strong, #1d2b38);
  font: inherit;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.ww-field input:focus,
.ww-field select:focus,
.ww-speech-form input:focus {
  border-color: rgba(155, 106, 47, 0.52);
  box-shadow: 0 0 0 3px rgba(240, 201, 123, 0.16);
}

.ww-role-label {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 24px 0 10px;
  color: #43566a;
  font-size: 0.88rem;
}

.ww-role-label em {
  color: #9b6a2f;
  font-style: normal;
  font-size: 0.78rem;
}

.ww-role-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 9px;
}

.ww-role-option {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid rgba(132, 158, 183, 0.18);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.58);
  text-align: left;
  cursor: pointer;
  transition: 0.2s ease;
}

.ww-role-option:hover {
  transform: translateY(-1px);
  border-color: rgba(155, 106, 47, 0.32);
}

.ww-role-option.active {
  border-color: rgba(155, 106, 47, 0.62);
  background: linear-gradient(135deg, rgba(255, 241, 212, 0.88), rgba(255, 251, 241, 0.78));
  box-shadow: 0 7px 18px rgba(155, 106, 47, 0.1);
}

.ww-role-icon { font-size: 1.15rem; }
.ww-role-option strong { color: #304458; font-size: 0.9rem; }
.ww-role-option small { color: #7b8ea2; font-size: 0.68rem; line-height: 1.55; }

.ww-start-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 25px;
}

.ww-start-btn { min-width: 160px; }
.ww-watch-btn { min-width: 150px; }
.ww-error { margin: 12px 0 0; color: #c04a4a; font-size: 0.82rem; }

.ww-setup-aside { display: flex; flex-direction: column; gap: 14px; }
.ww-rules-card { padding: 22px; }
.ww-rules-card h3 { margin: 8px 0 14px; color: #2d4256; font-size: 1.1rem; }
.ww-rules-card ol { margin: 0; padding-left: 18px; color: #67798b; font-size: 0.82rem; line-height: 1.85; }
.ww-rules-card strong { color: #8b5e24; }
.ww-mini-note {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 16px;
  color: #67798b;
  font-size: 0.78rem;
  line-height: 1.65;
}
.ww-mini-note span { color: #d9a94f; font-size: 1.2rem; }
.ww-mini-note p { margin: 0; }

.ww-game-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 12px 16px;
  border: 1px solid rgba(133, 158, 183, 0.16);
  border-radius: 18px;
  background: rgba(255, 252, 246, 0.72);
}

.ww-round { display: flex; align-items: center; gap: 10px; }
.ww-round-moon {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 50%;
  background: linear-gradient(145deg, #172b44, #0e1b2d);
  color: #ffe3a3;
  font-size: 1.2rem;
  box-shadow: 0 5px 16px rgba(10, 22, 38, 0.2);
}
.ww-round p { margin: 0; color: #8b9aab; font-size: 0.72rem; }
.ww-round strong { color: #2d4256; font-size: 0.9rem; }

.ww-phase-steps { display: flex; gap: 5px; }
.ww-phase-steps span {
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(133, 158, 183, 0.08);
  color: #94a3b4;
  font-size: 0.7rem;
}
.ww-phase-steps span.active { background: #1d3550; color: #ffe3a3; }
.ww-phase-steps span.done { color: #8b5e24; background: rgba(240, 201, 123, 0.15); }

.ww-game-actions { display: flex; gap: 8px; }
.ww-icon-btn {
  padding: 7px 11px;
  border: 1px solid rgba(132, 158, 183, 0.2);
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.6);
  color: #536b80;
  font-size: 0.73rem;
  cursor: pointer;
}
.ww-icon-btn:hover:not(:disabled) { border-color: rgba(155, 106, 47, 0.4); color: #8b5e24; }
.ww-icon-btn:disabled { opacity: 0.45; cursor: wait; }

.ww-layout {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr) 282px;
  gap: 16px;
  align-items: start;
}

.ww-roster { padding: 18px; position: sticky; top: 88px; }
.ww-panel-head { display: flex; justify-content: space-between; gap: 8px; align-items: flex-start; }
.ww-panel-head h2 { margin: 6px 0 0; color: #2d4256; font-size: 1.2rem; }
.ww-alive-count { color: #8b9aab; font-size: 0.7rem; white-space: nowrap; padding-top: 5px; }

.ww-player-list { display: flex; flex-direction: column; gap: 6px; margin-top: 16px; }
.ww-player {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 8px;
  border: 1px solid transparent;
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.44);
  text-align: left;
  cursor: default;
  transition: 0.18s ease;
}
.ww-player.targetable { cursor: pointer; border-color: rgba(240, 201, 123, 0.32); }
.ww-player.targetable:hover { background: rgba(255, 244, 218, 0.75); }
.ww-player.selected { border-color: #c58b39; background: rgba(255, 241, 210, 0.9); box-shadow: 0 0 0 2px rgba(197, 139, 57, 0.12); }
.ww-player.dead { opacity: 0.46; }
.ww-player.human { background: rgba(237, 246, 255, 0.72); }
.ww-player.wolf-claim { border-color: rgba(190, 75, 75, 0.32); }
.ww-seat { width: 19px; color: #a0adba; font-size: 0.66rem; font-variant-numeric: tabular-nums; }
.ww-avatar {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  border-radius: 9px;
  background: linear-gradient(145deg, #203a58, #132438);
  color: #f7e8c5;
  font-size: 0.78rem;
}
.ww-player-copy { min-width: 0; flex: 1; display: flex; flex-direction: column; gap: 1px; }
.ww-player-copy strong { overflow: hidden; color: #33495d; font-size: 0.8rem; text-overflow: ellipsis; white-space: nowrap; }
.ww-player-copy strong em { margin-left: 4px; padding: 1px 4px; border-radius: 4px; background: #d9eafa; color: #3c739b; font-size: 0.58rem; font-style: normal; }
.ww-player-copy small { overflow: hidden; color: #8898a8; font-size: 0.65rem; text-overflow: ellipsis; white-space: nowrap; }
.ww-player-meta { display: flex; align-items: center; gap: 5px; }
.ww-player-role { font-size: 0.75rem; }
.ww-life-dot { width: 6px; height: 6px; border-radius: 50%; background: #c5ccd4; }
.ww-life-dot.on { background: #67b58b; box-shadow: 0 0 0 3px rgba(103, 181, 139, 0.12); }
.ww-roster-legend { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; color: #9aa8b6; font-size: 0.63rem; }
.ww-roster-legend span { display: inline-flex; align-items: center; gap: 4px; }
.dot { display: inline-block; width: 5px; height: 5px; border-radius: 50%; }
.dot.good { background: #67b58b; }
.dot.dead { background: #b8c1ca; }
.dot.target { background: #d9a94f; }

.ww-table { display: flex; flex-direction: column; gap: 14px; min-width: 0; }
.ww-action-card { padding: 24px; }
.ww-action-top { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.ww-action-kicker { color: #b07b3e; font-size: 0.65rem; letter-spacing: 0.12em; }
.ww-action-round { color: #a3b1bf; font-size: 0.7rem; }
.ww-action-card h2 { margin: 10px 0 6px; color: #243b50; font-size: 1.55rem; }
.ww-action-card > p { margin: 0; color: #687b8e; line-height: 1.75; }

.ww-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; margin: 18px 0 16px; }
.ww-option {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 10px;
  border: 1px solid rgba(132, 158, 183, 0.2);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.56);
  color: #4e6579;
  font-size: 0.78rem;
  text-align: left;
  cursor: pointer;
  transition: 0.18s ease;
}
.ww-option:hover { border-color: rgba(197, 139, 57, 0.38); }
.ww-option.active { border-color: #c58b39; background: rgba(255, 242, 213, 0.88); color: #7d551f; }
.ww-option.pass { color: #8a9aaa; }
.ww-option-mark { color: #d2a45d; font-size: 0.7rem; }
.ww-confirm { min-width: 150px; }
.ww-action-hint { margin-top: 12px !important; color: #a0adba !important; font-size: 0.68rem !important; }

.ww-waiting-ai { text-align: center; padding: 36px 24px; }
.ww-thinking-orbit {
  display: inline-grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border-radius: 50%;
  background: #14263c;
  color: #ffe3a3;
  font-size: 1.4rem;
  box-shadow: 0 0 0 7px rgba(20, 38, 60, 0.06);
}
.ww-waiting-ai h2 { margin-top: 14px; }
.ww-waiting-ai .btn { margin-top: 16px; }
.ww-dead-note { margin-top: 8px !important; color: #b07b3e !important; font-size: 0.8rem; }

.ww-speech-card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 17px 20px;
  border: 1px dashed rgba(197, 139, 57, 0.34);
  border-radius: 16px;
  background: rgba(255, 246, 226, 0.46);
}
.ww-speech-card h3 { margin: 5px 0 3px; color: #33495d; font-size: 1rem; }
.ww-speech-card p { margin: 0; color: #8898a8; font-size: 0.7rem; }
.ww-speech-form { display: flex; gap: 7px; flex: 1; min-width: 220px; max-width: 430px; }
.ww-speech-form input { height: 36px; font-size: 0.78rem; }

.ww-log-card { overflow: hidden; }
.ww-log-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; padding: 18px 20px 0; }
.ww-log-head h2 { margin: 5px 0 0; color: #2d4256; font-size: 1.15rem; }
.ww-log-count { padding-top: 5px; color: #a0adba; font-size: 0.68rem; white-space: nowrap; }
.ww-log {
  display: flex;
  flex-direction: column;
  gap: 1px;
  max-height: 370px;
  margin-top: 12px;
  padding: 0 14px 14px;
  overflow-y: auto;
  scrollbar-width: thin;
}
.ww-log-item { display: flex; gap: 8px; padding: 8px 6px; border-bottom: 1px solid rgba(132, 158, 183, 0.08); }
.ww-log-item:last-child { border-bottom: 0; }
.ww-log-icon { width: 16px; flex: 0 0 16px; color: #b6c2ce; text-align: center; font-size: 0.7rem; }
.ww-log-meta { display: flex; flex-wrap: wrap; gap: 7px; align-items: center; color: #a4b0bc; font-size: 0.62rem; }
.ww-log-meta em { padding: 1px 5px; border-radius: 4px; background: rgba(132, 158, 183, 0.1); font-style: normal; }
.ww-log-item p { margin: 3px 0 0; color: #53697d; font-size: 0.78rem; line-height: 1.65; }
.ww-log-item.type-speech p { color: #3d5a71; }
.ww-log-item.type-vote p { color: #81602e; }
.ww-log-item.type-death p { color: #a45656; }
.ww-log-item.type-result p { color: #8b5e24; font-weight: 600; }
.ww-log-item.type-claim p { color: #7a5928; }
.ww-log-item.private { background: rgba(255, 243, 211, 0.46); border-radius: 8px; }
.ww-log-item.private p { color: #7b5a2b; }

.ww-result { padding: 36px 24px; text-align: center; border-radius: 22px; background: linear-gradient(145deg, #12243a, #0c1727); color: #e7eef7; box-shadow: 0 18px 40px rgba(10, 23, 39, 0.2); }
.ww-result-icon { display: block; margin-bottom: 6px; color: #ffe3a3; font-size: 2.3rem; }
.ww-result .section-eyebrow { color: rgba(255, 227, 163, 0.66); }
.ww-result h2 { margin: 8px 0; font-size: 2rem; color: #fff6df; }
.ww-result p { color: rgba(222, 234, 248, 0.72); }
.ww-result-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 9px; margin-top: 18px; }
.ww-result-actions .btn-ghost { border-color: rgba(255, 255, 255, 0.22); color: #e7eef7; background: rgba(255, 255, 255, 0.06); }

.ww-side { display: flex; flex-direction: column; gap: 12px; position: sticky; top: 88px; }
.ww-identity { padding: 20px; background: linear-gradient(145deg, rgba(22, 41, 62, 0.97), rgba(12, 24, 39, 0.98)); color: #e7eef7; border-color: rgba(196, 217, 237, 0.12); }
.ww-identity-head { display: flex; align-items: center; gap: 12px; }
.ww-identity-icon { display: grid; width: 46px; height: 46px; place-items: center; border-radius: 14px; background: rgba(255, 227, 163, 0.1); font-size: 1.5rem; }
.ww-identity .section-eyebrow { color: rgba(255, 227, 163, 0.58); }
.ww-identity h2 { margin: 4px 0 2px; color: #fff6df; font-size: 1.45rem; }
.ww-identity-head span { color: rgba(220, 234, 248, 0.62); font-size: 0.7rem; }
.ww-identity-desc { margin: 16px 0; color: rgba(220, 234, 248, 0.72); font-size: 0.78rem; line-height: 1.7; }
.ww-abilities { display: flex; flex-wrap: wrap; gap: 5px; }
.ww-abilities span { padding: 4px 7px; border: 1px solid rgba(255, 227, 163, 0.18); border-radius: 6px; color: #f1ddba; font-size: 0.66rem; }
.ww-abilities span.used { opacity: 0.4; text-decoration: line-through; }
.ww-teammates { display: flex; flex-wrap: wrap; gap: 5px; align-items: center; margin-top: 13px; color: rgba(220, 234, 248, 0.56); font-size: 0.68rem; }
.ww-teammates strong { padding: 3px 7px; border-radius: 5px; background: rgba(190, 75, 75, 0.18); color: #f2c3c3; font-weight: 500; }
.ww-identity .ww-dead-note { color: #e3b66e !important; }

.ww-stat-card { padding: 16px 18px; }
.ww-stat-row { display: flex; justify-content: space-between; gap: 10px; padding: 7px 0; border-bottom: 1px dashed rgba(132, 158, 183, 0.12); color: #7b8ea2; font-size: 0.75rem; }
.ww-stat-row:last-child { border-bottom: 0; }
.ww-stat-row strong { color: #4a6074; font-weight: 600; }
.ww-side-controls { display: flex; flex-direction: column; gap: 7px; padding: 14px; }
.ww-side-controls p { margin: 3px 2px 0; color: #98a6b5; font-size: 0.66rem; line-height: 1.6; }

.ww-ai-state {
  padding: 5px 8px;
  border: 1px solid rgba(132, 158, 183, 0.18);
  border-radius: 999px;
  color: #8b9aaa;
  font-size: 0.65rem;
  white-space: nowrap;
}
.ww-ai-state.on { border-color: rgba(103, 181, 139, 0.34); color: #4d8a68; background: rgba(103, 181, 139, 0.08); }

.ww-dialogue {
  display: flex;
  flex-direction: column;
  min-height: 540px;
  max-height: calc(100vh - 180px);
  border: 1px solid rgba(133, 158, 183, 0.16);
  border-radius: 22px;
  background:
    radial-gradient(circle at 70% 0%, rgba(255, 227, 163, 0.08), transparent 34%),
    rgba(255, 252, 246, 0.76);
  box-shadow: 0 14px 38px rgba(74, 95, 116, 0.08);
  overflow: hidden;
}
.ww-dialogue-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px 14px;
  border-bottom: 1px solid rgba(132, 158, 183, 0.1);
}
.ww-dialogue-head h2 { margin: 5px 0 0; color: #2d4256; font-size: 1.15rem; }
.ww-dialogue-head > span { padding-top: 8px; color: #9aa8b6; font-size: 0.66rem; text-align: right; }

.ww-timeline {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-height: 280px;
  padding: 4px 18px 20px;
  overflow-y: auto;
  scrollbar-width: thin;
  scroll-behavior: smooth;
}
.ww-divider {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 20px 0 9px;
  color: #9b6a2f;
  font-size: 0.72rem;
  letter-spacing: 0.08em;
}
.ww-divider::before,
.ww-divider::after {
  content: '';
  height: 1px;
  flex: 1;
  background: linear-gradient(90deg, transparent, rgba(155, 106, 47, 0.24));
}
.ww-divider::after { background: linear-gradient(90deg, rgba(155, 106, 47, 0.24), transparent); }
.ww-divider span { white-space: nowrap; font-weight: 700; }
.ww-divider em { color: #b2bdc8; font-size: 0.56rem; font-style: normal; letter-spacing: 0.16em; }

.ww-chat-row { display: flex; gap: 8px; align-items: flex-start; margin: 5px 0; max-width: 92%; }
.ww-chat-row.mine { align-self: flex-end; flex-direction: row-reverse; }
.ww-chat-avatar {
  display: grid;
  width: 27px;
  height: 27px;
  flex: 0 0 27px;
  place-items: center;
  border-radius: 9px;
  background: linear-gradient(145deg, #203a58, #132438);
  color: #f7e8c5;
  font-size: 0.7rem;
}
.ww-chat-row.mine .ww-chat-avatar { background: linear-gradient(145deg, #c58b39, #9b6a2f); }
.ww-chat-body { min-width: 0; }
.ww-chat-meta { display: flex; align-items: baseline; gap: 6px; margin: 0 2px 3px; }
.ww-chat-meta strong { color: #4a6074; font-size: 0.68rem; }
.ww-chat-meta em { color: #a4b0bc; font-size: 0.58rem; font-style: normal; }
.ww-chat-row.mine .ww-chat-meta { justify-content: flex-end; }
.ww-bubble {
  margin: 0;
  padding: 9px 12px;
  border-radius: 4px 13px 13px 13px;
  background: rgba(255, 255, 255, 0.9);
  color: #40566a;
  font-size: 0.82rem;
  line-height: 1.7;
  box-shadow: 0 3px 12px rgba(61, 82, 101, 0.05);
  white-space: pre-wrap;
  word-break: break-word;
}
.ww-chat-row.mine .ww-bubble {
  border-radius: 13px 4px 13px 13px;
  background: #1b3552;
  color: #f3f7fb;
}
.ww-chat-row.private .ww-bubble { background: rgba(255, 243, 211, 0.92); color: #74572b; }
.ww-chat-row.claim .ww-bubble { border: 1px solid rgba(197, 139, 57, 0.3); background: rgba(255, 246, 226, 0.94); color: #6f5128; }
.ww-chat-row.streaming .ww-bubble { border: 1px dashed rgba(155, 106, 47, 0.35); background: rgba(255, 250, 238, 0.88); }
.ww-cursor {
  display: inline-block;
  width: 5px;
  height: 12px;
  margin-left: 2px;
  vertical-align: -2px;
  background: #9b6a2f;
  animation: ww-blink 0.8s steps(2) infinite;
}
@keyframes ww-blink { 50% { opacity: 0; } }

.ww-event-row {
  display: flex;
  gap: 7px;
  align-items: flex-start;
  margin: 4px 0;
  padding: 6px 9px;
  border-radius: 8px;
  color: #7b8ea2;
  font-size: 0.72rem;
  line-height: 1.6;
}
.ww-event-row p { margin: 0; }
.ww-event-row p strong { margin-right: 6px; color: #97a5b3; font-size: 0.62rem; font-weight: 500; }
.ww-event-icon { width: 14px; flex: 0 0 14px; color: #b5c0ca; text-align: center; }
.ww-event-row.type-night,
.ww-event-row.type-dawn { background: rgba(27, 53, 85, 0.045); }
.ww-event-row.type-death p { color: #a45656; }
.ww-event-row.type-vote p { color: #81602e; }
.ww-event-row.type-result p { color: #8b5e24; font-weight: 600; }
.ww-event-row.type-claim p { color: #7a5928; }
.ww-event-row.type-hunter p { color: #8e5d3d; }
.ww-event-row.private { background: rgba(255, 243, 211, 0.42); }

.ww-thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0;
  padding: 8px 10px;
  border-radius: 9px;
  color: #9b6a2f;
  background: rgba(255, 246, 226, 0.55);
  font-size: 0.7rem;
}
.ww-thinking-dots { display: inline-flex; gap: 3px; }
.ww-thinking-dots i { width: 4px; height: 4px; border-radius: 50%; background: #c58b39; animation: ww-pulse 1s ease-in-out infinite; }
.ww-thinking-dots i:nth-child(2) { animation-delay: 0.15s; }
.ww-thinking-dots i:nth-child(3) { animation-delay: 0.3s; }
@keyframes ww-pulse { 50% { opacity: 0.3; transform: translateY(-2px); } }

.ww-composer {
  padding: 14px 18px 17px;
  border-top: 1px solid rgba(132, 158, 183, 0.12);
  background: rgba(255, 252, 246, 0.92);
}
.ww-composer-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.ww-composer-head span { color: #b07b3e; font-size: 0.6rem; letter-spacing: 0.1em; }
.ww-composer-head strong { color: #2d4256; font-size: 0.9rem; }
.ww-composer-prompt { margin: 6px 0 10px; color: #7b8ea2; font-size: 0.74rem; line-height: 1.6; }
.ww-composer .ww-options { margin: 0 0 10px; }
.ww-composer .ww-confirm { width: 100%; }
.ww-continue-btn { width: 100%; margin-top: 8px; }
.ww-speech-form { display: flex; gap: 7px; }
.ww-speech-form input {
  flex: 1;
  height: 38px;
  padding: 0 12px;
  border: 1px solid rgba(132, 158, 183, 0.22);
  border-radius: 10px;
  outline: none;
  background: rgba(255, 255, 255, 0.85);
  color: #2d4256;
  font: inherit;
  font-size: 0.78rem;
}
.ww-speech-form input:focus { border-color: rgba(155, 106, 47, 0.5); box-shadow: 0 0 0 3px rgba(240, 201, 123, 0.14); }
.ww-watch-note { margin: 0 0 9px; color: #9b6a2f; font-size: 0.72rem; }

@media (max-width: 1100px) {
  .ww-layout { grid-template-columns: 220px minmax(0, 1fr); }
  .ww-side { grid-column: 1 / -1; position: static; display: grid; grid-template-columns: 1fr 1fr 1fr; align-items: start; }
}

@media (max-width: 820px) {
  .ww-hero { grid-template-columns: 1fr; padding-top: 24px; }
  .ww-hero-orbit { display: none; }
  .ww-setup { grid-template-columns: 1fr; }
  .ww-setup-aside { display: grid; grid-template-columns: 1fr 1fr; }
  .ww-layout { grid-template-columns: 1fr; }
  .ww-roster { position: static; order: 2; }
  .ww-table { order: 1; }
  .ww-side { order: 3; grid-template-columns: 1fr 1fr; }
  .ww-dialogue { max-height: none; min-height: 460px; }
  .ww-timeline { min-height: 260px; }
  .ww-identity { grid-column: 1 / -1; }
  .ww-player-list { display: grid; grid-template-columns: 1fr 1fr; }
}

@media (max-width: 560px) {
  .ww-setup-main { padding: 20px; }
  .ww-form-row, .ww-role-grid, .ww-setup-aside, .ww-side { grid-template-columns: 1fr; }
  .ww-role-grid { grid-template-columns: 1fr 1fr; }
  .ww-player-list { grid-template-columns: 1fr; }
  .ww-phase-steps { order: 3; width: 100%; }
  .ww-game-bar { gap: 10px; }
  .ww-options { grid-template-columns: 1fr; }
  .ww-timeline { padding: 4px 12px 16px; }
  .ww-chat-row { max-width: 100%; }
  .ww-dialogue-head { padding: 15px 14px 12px; }
  .ww-composer { padding: 12px 14px 14px; }
  .ww-log-card { border-radius: 16px; }
  .ww-action-card { padding: 19px; }
  .ww-action-card h2 { font-size: 1.28rem; }
  .ww-result h2 { font-size: 1.55rem; }
}
</style>
