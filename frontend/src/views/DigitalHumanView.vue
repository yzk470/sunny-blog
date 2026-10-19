<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import MarkdownView from '@/components/MarkdownView.vue'
import { digitalHumanApi, type DigitalHumanCharacter, type DigitalHumanMessage, type DigitalHumanSession, type HunyuanMotionJob } from '@/api/digitalHuman'
import { SceneManager } from '@/features/digital-human/SceneManager.js'
import { useToastStore } from '@/stores/toast'

const FbxPreview = defineAsyncComponent(() => import('@/components/FbxPreview.vue'))

const route = useRoute()
const toast = useToastStore()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const messagesRef = ref<HTMLElement | null>(null)
const shellRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)

const characters = ref<DigitalHumanCharacter[]>([])
const activeCode = ref('')
const session = ref<DigitalHumanSession | null>(null)
const messages = ref<DigitalHumanMessage[]>([])
const input = ref('')
const loading = ref(true)
const loadingText = ref('正在连接会客厅…')
const progress = ref(0)
const error = ref('')
const sending = ref(false)
const muted = ref(false)
const speaking = ref(false)
const fullscreen = ref(false)
const sceneReady = ref(false)
const motionPanelOpen = ref(false)
const activeMotion = ref('')
const motionLoading = ref(false)
const vmdPlaying = ref(false)
const hunyuanConfigured = ref(false)
const hunyuanPrompt = ref('')
const hunyuanLoading = ref(false)
const hunyuanJob = ref<HunyuanMotionJob | null>(null)
const retargetLoading = ref(false)
const previewOpen = ref(false)
const previewUrl = ref('')
const streamingReply = ref(false)
const activePackMotion = ref('')
const autoHunyuanLoading = ref(false)

let sceneManager: any = null
let audio: HTMLAudioElement | null = null
let audioConnected = false
let initToken = 0
let motionTimer: number | undefined
let packMotionTimer: number | undefined
let gestureRequest = 0
let gestureBusy = false
let gestureEpoch = 0
type GestureOptions = { resumeIdle?: boolean; onComplete?: () => void }
type QueuedGesture = { key: string; options: GestureOptions }
let gestureQueue: QueuedGesture[] = []
let hunyuanPollTimer: number | undefined
let greetingTimer: number | undefined
let speechEpoch = 0
let speechFetchChain: Promise<void> = Promise.resolve()
let speechAudioQueue: string[] = []
let speechAudioActive = false
let streamAbort: AbortController | null = null
let activeStreamTurn = 0
let silentPrimeUrl = ''
let primingAudio = false
let pcmContext: AudioContext | null = null
let pcmNextTime = 0
const pcmSources = new Set<AudioBufferSourceNode>()
let speechTargetMessage: DigitalHumanMessage | null = null

const gestureActions = [
  { key: 'wave', label: '挥手', icon: '✦' },
  { key: 'salute', label: '敬礼', icon: '✧' },
  { key: 'nod', label: '点头', icon: '✓' },
  { key: 'bow', label: '鞠躬', icon: '☾' },
  { key: 'think', label: '思考', icon: '◌' },
  { key: 'happy', label: '开心', icon: '☀' },
  { key: 'shy', label: '害羞', icon: '❀' },
  { key: 'stretch', label: '伸懒腰', icon: '⌁' },
  { key: 'shrug', label: '摊手', icon: '╮' },
]

// Downloaded エモート風モーション pack, kept local and out of git.
const gestureVmdMap: Record<string, string> = {
  wave: '/motions/local/gestures/wave.vmd',
  salute: '/motions/local/gestures/salute.vmd',
  bow: '/motions/local/gestures/bow.vmd',
  think: '/motions/local/gestures/think.vmd',
  shy: '/motions/local/gestures/rubhands.vmd',
  rubhands: '/motions/local/gestures/rubhands.vmd',
  stretch: '/motions/local/gestures/stretch.vmd',
  cheer: '/motions/local/gestures/cheer.vmd',
  letsgo: '/motions/local/gestures/letsgo.vmd',
  handclap: '/motions/local/gestures/handclap.vmd',
  spin: '/motions/local/gestures/spin.vmd',
  warn: '/motions/local/gestures/warn.vmd',
  calm: '/motions/local/gestures/calm.vmd',
  lonely: '/motions/local/gestures/lonely.vmd',
  beckon: '/motions/local/gestures/beckon.vmd',
}

const packMotions = [
  { key: 'bow', label: '鞠躬', file: '/motions/local/gestures/bow.vmd', emotion: 'shy' },
  { key: 'salute', label: '敬礼', file: '/motions/local/gestures/salute.vmd', emotion: 'happy' },
  { key: 'wave', label: '挥手', file: '/motions/local/gestures/wave.vmd', emotion: 'happy' },
  { key: 'cheer', label: '声援', file: '/motions/local/gestures/cheer.vmd', emotion: 'excited' },
  { key: 'letsgo', label: '出发', file: '/motions/local/gestures/letsgo.vmd', emotion: 'excited' },
  { key: 'handclap', label: '打拍子', file: '/motions/local/gestures/handclap.vmd', emotion: 'happy' },
  { key: 'spin', label: '转手', file: '/motions/local/gestures/spin.vmd', emotion: 'thinking' },
  { key: 'warn', label: '提醒', file: '/motions/local/gestures/warn.vmd', emotion: 'surprised' },
  { key: 'calm', label: '安抚', file: '/motions/local/gestures/calm.vmd', emotion: 'neutral' },
  { key: 'lonely', label: '寂寞', file: '/motions/local/gestures/lonely.vmd', emotion: 'sad' },
  { key: 'beckon', label: '招手', file: '/motions/local/gestures/beckon.vmd', emotion: 'happy' },
  { key: 'rubhands', label: '搓手', file: '/motions/local/gestures/rubhands.vmd', emotion: 'shy' },
]

const articleSlug = computed(() => (typeof route.query.article === 'string' ? route.query.article : ''))
const activeCharacter = computed(() => characters.value.find((item) => item.code === activeCode.value) || null)
const canSend = computed(() => Boolean(input.value.trim()) && !sending.value && !loading.value && Boolean(session.value))
const activeMotionLabel = computed(() => {
  if (activeMotion.value === 'dance') return '手舞'
  if (activeMotion.value === 'fbx') return '混元动作'
  return gestureActions.find((item) => item.key === activeMotion.value)?.label
    || packMotions.find((item) => item.key === activeMotion.value)?.label
    || ''
})

const quickPrompts = [
  '介绍一下你自己',
  '最近博客写了什么？',
  '推荐一篇文章给我',
  '用一句话安慰我',
]

const emotionLabel: Record<string, string> = {
  neutral: '平静',
  happy: '愉快',
  sad: '低落',
  surprised: '惊讶',
  shy: '害羞',
  thinking: '思考',
  excited: '兴奋',
}

const storageKey = (code: string) => `sunnyblog_dh_session_${code}`

function characterOf(code: string) {
  return characters.value.find((item) => item.code === code)
}

function chatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function stopPcmPlayback() {
  for (const source of pcmSources) {
    try {
      source.stop()
    } catch {
      // Already stopped.
    }
  }
  pcmSources.clear()
  pcmNextTime = 0
  speaking.value = false
  sceneManager?.motionEngine?.setSpeaking?.(false)
}

function stopAudio() {
  if (audio) {
    audio.pause()
    audio.currentTime = 0
  }
  stopPcmPlayback()
  speechEpoch += 1
  speechAudioQueue = []
  speechAudioActive = false
  primingAudio = false
  if (audio) audio.volume = 1
  speaking.value = false
  sceneManager?.motionEngine?.setSpeaking?.(false)
}

function resetAudioElement() {
  stopAudio()
  try {
    sceneManager?.lipSync?.disconnect?.()
  } catch {
    // LipSync may not be initialized yet.
  }
  pcmContext = null
  pcmNextTime = 0
  audio = new Audio()
  audio.preload = 'auto'
  audio.onplaying = () => {
    if (primingAudio) return
    speaking.value = true
    sceneManager?.motionEngine?.setSpeaking?.(true)
  }
  audio.onended = () => {
    if (primingAudio) {
      primingAudio = false
      return
    }
    speaking.value = false
    sceneManager?.motionEngine?.setSpeaking?.(false)
    playNextSpeechAudio()
  }
  audioConnected = false
}

function connectAudioGraph() {
  if (!audio) return
  if (!audioConnected && sceneManager) {
    sceneManager.connectAudioElement(audio)
    audioConnected = true
  }
  void sceneManager?.lipSync?.resume?.()
}

function ensurePcmContext(): AudioContext | null {
  try {
    let context = pcmContext
    if (!context || context.state === 'closed') {
      const lipContext = sceneManager?.lipSync?.audioContext
      context = lipContext || new (window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)()
      pcmContext = context
    }
    if (!context) return null
    if (context.state === 'suspended') {
      void context.resume()
    }
    return context
  } catch {
    return null
  }
}

function playPcmChunk(base64: string, sampleRate = 24000) {
  const context = ensurePcmContext()
  if (!context) return
  try {
    const binary = atob(base64)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i)
    if (bytes.length < 2) return
    const samples = new Int16Array(bytes.buffer, bytes.byteOffset, Math.floor(bytes.length / 2))
    const buffer = context.createBuffer(1, samples.length, sampleRate)
    const channel = buffer.getChannelData(0)
    for (let i = 0; i < samples.length; i += 1) channel[i] = samples[i] / 32768
    const source = context.createBufferSource()
    source.buffer = buffer
    const analyser = sceneManager?.lipSync?.analyser
    if (analyser && analyser.context === context) {
      source.connect(analyser)
    } else {
      source.connect(context.destination)
    }
    const startAt = Math.max(context.currentTime + 0.025, pcmNextTime)
    source.start(startAt)
    pcmNextTime = startAt + buffer.duration
    pcmSources.add(source)
    speaking.value = true
    sceneManager?.motionEngine?.setSpeaking?.(true)
    source.onended = () => {
      pcmSources.delete(source)
      if (pcmSources.size === 0 && !speechAudioActive) {
        speaking.value = false
        sceneManager?.motionEngine?.setSpeaking?.(false)
      }
    }
  } catch {
    // A dropped PCM chunk should not break the rest of the stream.
  }
}

function playNextSpeechAudio() {
  if (!audio) return
  if (muted.value) {
    speechAudioQueue = []
    speechAudioActive = false
    speaking.value = false
    sceneManager?.motionEngine?.setSpeaking?.(false)
    return
  }
  const url = speechAudioQueue.shift()
  if (!url) {
    speechAudioActive = false
    speaking.value = false
    sceneManager?.motionEngine?.setSpeaking?.(false)
    return
  }
  speechAudioActive = true
  try {
    connectAudioGraph()
    audio.src = url
    audio.currentTime = 0
    void audio.play().catch(() => {
      speechAudioActive = false
      speaking.value = false
      toast.show('浏览器拦截了自动播放，点击回复下方的语音按钮即可播放', 'info', 4200)
    })
  } catch {
    speechAudioActive = false
    speaking.value = false
  }
}

function enqueueSpeechAudio(url: string) {
  if (speechTargetMessage && !speechTargetMessage.audioUrl) {
    speechTargetMessage.audioUrl = url
  }
  speechAudioQueue.push(url)
  if (!speechAudioActive) playNextSpeechAudio()
}

function enqueueSpeechText(text: string) {
  const clean = text.trim()
  if (!clean || muted.value) return
  const epoch = speechEpoch
  speechFetchChain = speechFetchChain.then(async () => {
    if (epoch !== speechEpoch || muted.value) return
    try {
      const result = await Promise.race([
        digitalHumanApi.tts(activeCode.value, clean),
        new Promise<never>((_, reject) => {
          window.setTimeout(() => reject(new Error('TTS timeout')), 8000)
        }),
      ])
      if (epoch !== speechEpoch || muted.value) return
      if (result.audioUrl) enqueueSpeechAudio(result.audioUrl)
    } catch {
      // Keep the text; do not mix in a second, browser-generated voice.
    }
  })
}

function resetSpeechQueue() {
  stopPcmPlayback()
  speechEpoch += 1
  speechAudioQueue = []
  speechAudioActive = false
  speechFetchChain = Promise.resolve()
}

function cancelActiveStream() {
  activeStreamTurn += 1
  streamAbort?.abort()
  streamAbort = null
  sending.value = false
  streamingReply.value = false
}

function primeAudio() {
  if (!audio) {
    resetAudioElement()
  }
  connectAudioGraph()
  if (!audio) return
  if (!silentPrimeUrl) {
    silentPrimeUrl = createSilentWavUrl()
  }
  try {
    primingAudio = true
    audio.volume = 0
    audio.src = silentPrimeUrl
    void audio.play()
      .then(() => {
        audio?.pause()
        if (audio) {
          audio.currentTime = 0
          audio.volume = 1
        }
        primingAudio = false
      })
      .catch(() => {
        primingAudio = false
        if (audio) audio.volume = 1
      })
  } catch {
    primingAudio = false
    if (audio) audio.volume = 1
  }
}

function createSilentWavUrl() {
  const sampleRate = 8000
  const samples = 1200
  const buffer = new ArrayBuffer(44 + samples * 2)
  const view = new DataView(buffer)
  const writeText = (offset: number, value: string) => {
    for (let i = 0; i < value.length; i += 1) view.setUint8(offset + i, value.charCodeAt(i))
  }
  writeText(0, 'RIFF')
  view.setUint32(4, 36 + samples * 2, true)
  writeText(8, 'WAVEfmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeText(36, 'data')
  view.setUint32(40, samples * 2, true)
  return URL.createObjectURL(new Blob([buffer], { type: 'audio/wav' }))
}

function playAudio(url: string, force = false) {
  if (!audio) {
    resetAudioElement()
  }
  if (!audio) return
  if (muted.value && !force) return
  resetSpeechQueue()
  try {
    audio.pause()
    audio.src = url
    audio.currentTime = 0
    connectAudioGraph()
    void audio.play().catch(() => {
      speaking.value = false
      toast.show('浏览器拦截了自动播放，点击消息下方的语音按钮即可播放', 'info', 4200)
    })
  } catch {
    speaking.value = false
    toast.show('浏览器拦截了自动播放，点击消息下方的语音按钮即可播放', 'info', 4200)
  }
}

async function playMessage(message: DigitalHumanMessage) {
  if (message.audioUrl) {
    playAudio(message.audioUrl, true)
  } else {
    toast.show('这条回复没有可用的角色语音', 'info', 2600)
  }
}

function syncVmdState(value: boolean) {
  vmdPlaying.value = value
  if (value) {
    activeMotion.value = 'dance'
  } else if (activeMotion.value === 'dance') {
    activeMotion.value = ''
  }
}

function stopDance() {
  sceneManager?.stopVmd?.()
  syncVmdState(false)
  motionLoading.value = false
  if (!retargetLoading.value) void sceneManager?.resumeIdleMotion?.()
}

function playGesture(key: string, options: GestureOptions = {}) {
  if (!sceneReady.value || motionLoading.value) {
    options.onComplete?.()
    return
  }
  // Never cut a gesture in half: queue the next one and let the current
  // animation finish its full timeline first.
  if (gestureBusy) {
    gestureQueue.push({ key, options })
    return
  }
  void runGesture(key, options)
}

async function runGesture(key: string, options: GestureOptions) {
  gestureBusy = true
  const epoch = gestureEpoch
  const complete = () => {
    window.clearTimeout(motionTimer)
    if (epoch !== gestureEpoch) {
      options.onComplete?.()
      return
    }
    activeMotion.value = ''
    const next = gestureQueue.shift()
    gestureBusy = false
    if (next) {
      void runGesture(next.key, next.options)
    } else if (options.resumeIdle !== false) {
      void sceneManager?.resumeIdleMotion?.()
    }
    options.onComplete?.()
  }
  window.clearTimeout(packMotionTimer)
  activePackMotion.value = ''
  if (vmdPlaying.value) stopDance()
  if (activeMotion.value === 'fbx') retargetLoading.value = false
  const request = ++gestureRequest
  const vmdUrl = gestureVmdMap[key]
  if (vmdUrl) {
    try {
      const duration = await sceneManager?.playGestureVmd?.(vmdUrl)
      if (request !== gestureRequest || !duration) {
        complete()
        return
      }
      activeMotion.value = key
      window.clearTimeout(motionTimer)
      motionTimer = window.setTimeout(complete, Math.max(900, Number(duration) * 1000 + 220))
      return
    } catch {
      // Downloaded pack unavailable: fall back to the procedural gesture.
    }
  }
  if (request !== gestureRequest) {
    complete()
    return
  }
  const fallbackKey = key === 'salute' ? 'wave' : key
  const ok = sceneManager?.playGesture?.(fallbackKey)
  if (!ok) {
    complete()
    return
  }
  activeMotion.value = key
  const gestureDuration = Number(sceneManager?.motionEngine?.gesture?.duration || 2.4)
  window.clearTimeout(motionTimer)
  motionTimer = window.setTimeout(complete, Math.max(1200, gestureDuration * 1000 + 220))
}

function resetGestureState() {
  gestureEpoch += 1
  gestureRequest += 1
  gestureQueue = []
  gestureBusy = false
  window.clearTimeout(motionTimer)
}

async function playPackMotion(motion: { key: string; label: string; file: string; emotion?: string; disabled?: boolean }) {
  if (!sceneReady.value || motionLoading.value || motion.disabled) return
  window.clearTimeout(packMotionTimer)
  window.clearTimeout(motionTimer)
  activeMotion.value = ''
  if (vmdPlaying.value) stopDance()
  if (activeMotion.value === 'fbx') retargetLoading.value = false
  const request = ++gestureRequest
  const previousEmotion = sceneManager?.expressionEngine?.currentEmotion || 'neutral'
  sceneManager?.setEmotion(motion.emotion || 'neutral')
  try {
    const duration = await sceneManager?.playGestureVmd?.(motion.file)
    if (request !== gestureRequest || !duration) {
      sceneManager?.setEmotion(previousEmotion)
      return
    }
    activePackMotion.value = motion.key
    window.clearTimeout(packMotionTimer)
    packMotionTimer = window.setTimeout(() => {
      activePackMotion.value = ''
      sceneManager?.setEmotion(previousEmotion)
      if (!vmdPlaying.value && !retargetLoading.value) {
        void sceneManager?.resumeIdleMotion?.()
      }
    }, Math.max(900, Number(duration) * 1000 + 220))
  } catch (err) {
    sceneManager?.setEmotion(previousEmotion)
    toast.show(err instanceof Error ? err.message : '动作载入失败', 'error', 2800)
  }
}

async function toggleDance() {
  if (motionLoading.value) return
  if (!sceneReady.value) {
    toast.show('数字人还在加载中，请稍等一下', 'info', 2200)
    return
  }
  if (vmdPlaying.value) {
    stopDance()
    return
  }
  motionLoading.value = true
  activeMotion.value = 'dance'
  if (retargetLoading.value) retargetLoading.value = false
  toast.show('正在载入手舞动作模组…', 'info', 2200)
  try {
    await sceneManager.playVmd('/motions/wavefile_v2.vmd', { maxSeconds: 10 })
    syncVmdState(true)
    toast.show('手舞开始。动作模组：WAVEFILE / hino', 'success', 3600)
  } catch (err) {
    stopDance()
    toast.show(err instanceof Error ? err.message : '动作模组加载失败', 'error', 4000)
  } finally {
    motionLoading.value = false
  }
}

function reactToEmotion(emotion: string) {
  if (vmdPlaying.value) return
  const map: Record<string, string> = {
    happy: 'happy',
    excited: 'happy',
    sad: 'bow',
    surprised: 'stretch',
    shy: 'shy',
    thinking: 'think',
    neutral: 'nod',
  }
  playGesture(map[emotion] || 'nod')
}

function detectMotionIntent(text: string): string | null {
  const intents: Array<[RegExp, string]> = [
    [/(跳舞|手舞|舞一支|来支舞|\bdance\b)/i, 'dance'],
    [/(挥手|招手|打招呼|你好|嗨|再见|拜拜|\bhello\b|\bhi\b)/i, 'wave'],
    [/(鞠躬|谢谢|感谢|辛苦了)/i, 'bow'],
    [/(鼓掌|拍手|厉害|太棒|好棒)/i, 'happy'],
    [/(摊手|无奈|不知道|不确定|随便)/i, 'shrug'],
    [/(思考|想想|让我想|考虑)/i, 'think'],
    [/(开心|高兴|快乐|笑一个)/i, 'happy'],
    [/(害羞|不好意思|脸红)/i, 'shy'],
    [/(伸懒腰|好累|休息一下)/i, 'stretch'],
    [/(点头|好的|明白|收到|嗯嗯)/i, 'nod'],
  ]
  for (const [pattern, gesture] of intents) {
    if (pattern.test(text)) return gesture
  }
  return null
}

function applyMotionIntent(text: string) {
  const intent = detectMotionIntent(text)
  if (!intent) return
  if (intent === 'dance') {
    void toggleDance()
  } else {
    playGesture(intent)
  }
}

function openPreview(url: string) {
  previewUrl.value = url
  previewOpen.value = true
}

async function applyHunyuanMotion(url: string) {
  if (retargetLoading.value || !sceneReady.value) return
  const targetCode = activeCode.value
  retargetLoading.value = true
  activeMotion.value = 'fbx'
  if (vmdPlaying.value) stopDance()
  toast.show('正在加载 FBX 并重定向到当前 PMX 骨骼…', 'info', 3600)
  try {
    const result = await sceneManager.playRetargetedFbx(url, {
      onFinished: () => {
        retargetLoading.value = false
        if (activeMotion.value === 'fbx') activeMotion.value = ''
        void sceneManager?.resumeIdleMotion?.()
      },
    })
    if (targetCode !== activeCode.value) return
    toast.show(`混元动作已驱动${activeCharacter.value?.name || '数字人'} · ${result.duration.toFixed(1)} 秒`, 'success', 3600)
  } catch (err) {
    activeMotion.value = ''
    retargetLoading.value = false
    toast.show(err instanceof Error ? err.message : '混元动作重定向失败', 'error', 5000)
  }
}

async function pollHunyuanJob(id: string) {
  if (hunyuanPollTimer) window.clearTimeout(hunyuanPollTimer)
  try {
    const job = await digitalHumanApi.hunyuanMotion(id)
    hunyuanJob.value = job
    if (job.status === 'completed' || job.status === 'failed') {
      hunyuanLoading.value = false
      if (job.status === 'failed') {
        toast.show(job.error || '混元动作生成失败', 'error', 4200)
      } else if (job.fileUrl) {
        toast.show('混元动作已生成，可下载 FBX', 'success', 3600)
      }
      return
    }
    hunyuanPollTimer = window.setTimeout(() => pollHunyuanJob(id), 3000)
  } catch (err) {
    hunyuanLoading.value = false
    toast.show(err instanceof Error ? err.message : '查询混元动作失败', 'error')
  }
}

async function generateHunyuanMotion() {
  const prompt = hunyuanPrompt.value.trim()
  if (!prompt || hunyuanLoading.value) return
  if (!hunyuanConfigured.value) {
    const localIntent = detectMotionIntent(prompt)
    if (localIntent) {
      if (localIntent === 'dance') {
        void toggleDance()
      } else {
        playGesture(localIntent)
      }
      toast.show('混元 Key 未配置，先用本地动作库演示这个动作', 'info', 4200)
    } else {
      toast.show('混元动作 API Key 尚未配置；配置后会在这里生成动作', 'info', 4200)
    }
    return
  }
  hunyuanLoading.value = true
  hunyuanJob.value = null
  try {
    const job = await digitalHumanApi.submitHunyuanMotion(prompt, 5)
    hunyuanJob.value = job
    hunyuanConfigured.value = job.configured
    if (!job.configured) {
      hunyuanLoading.value = false
      toast.show('混元动作 API Key 尚未配置，稍后配置后即可生成', 'info', 4200)
      return
    }
    toast.show('已提交混元文生动作任务，正在排队…', 'info', 3200)
    if (job.id) pollHunyuanJob(job.id)
  } catch (err) {
    hunyuanLoading.value = false
    toast.show(err instanceof Error ? err.message : '提交混元动作失败', 'error')
  }
}

function scrollMessages() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

async function loadOrCreateSession(code: string): Promise<DigitalHumanSession> {
  const key = storageKey(code)
  const stored = localStorage.getItem(key)
  if (stored) {
    try {
      const existing = await digitalHumanApi.session(Number(stored))
      if (existing.characterCode === code) {
        return existing
      }
    } catch {
      localStorage.removeItem(key)
    }
  }
  const created = await digitalHumanApi.createSession(code)
  localStorage.setItem(key, String(created.sessionId))
  return created
}

async function initCharacter(code: string) {
  const token = ++initToken
  const character = characterOf(code)
  if (!character) return
  activeCode.value = code
  messages.value = []
  session.value = null
  loading.value = true
  error.value = ''
  progress.value = 0
  loadingText.value = `正在唤醒${character.name}…`
  resetAudioElement()
  resetSpeechQueue()
  cancelActiveStream()
  stopDance()
  resetGestureState()
  activeMotion.value = ''
  activePackMotion.value = ''
  retargetLoading.value = false
  motionPanelOpen.value = false

  await nextTick()
  try {
    if (!sceneManager && canvasRef.value) {
      sceneManager = new SceneManager(canvasRef.value, {
        onError: (err: Error) => {
          error.value = err?.message || '3D 模型加载失败'
        },
      })
      sceneManager.setCharacterPaths(new Map(characters.value.map((item) => [item.code, item.modelPath])))
    }

    const sessionPromise = loadOrCreateSession(code)
    const modelPromise = sceneManager.switchCharacter(code, (value: number) => {
      if (token === initToken) {
        progress.value = Math.round(value)
        loadingText.value = `正在载入${character.name} · ${progress.value}%`
      }
    })

    const [loaded] = await Promise.allSettled([modelPromise, sessionPromise])
    if (token !== initToken) return

    const sessionResult = await sessionPromise
    session.value = sessionResult
    messages.value = [...sessionResult.messages]
    if (loaded.status === 'rejected') {
      throw loaded.reason
    }
    sceneReady.value = true
    sceneManager.setEmotion('neutral')
    void sceneManager.startIdleMotion?.([
      '/motions/local/genshin-idle.vmd',
      '/motions/local/stand-p01.vmd',
    ])
    loading.value = false
    window.clearTimeout(greetingTimer)
    greetingTimer = window.setTimeout(() => {
      if (sceneReady.value && !vmdPlaying.value && !sending.value && !retargetLoading.value && activeMotion.value !== 'fbx') {
        playGesture('wave')
      }
    }, 900)
    scrollMessages()
  } catch (err) {
    if (token !== initToken) return
    error.value = err instanceof Error ? err.message : '数字人加载失败'
    loading.value = false
    sceneReady.value = false
  }
}

async function switchCharacter(code: string) {
  if (code === activeCode.value || loading.value || sending.value) return
  await initCharacter(code)
}

async function newSession() {
  if (!activeCode.value || sending.value || loading.value) return
  cancelActiveStream()
  try {
    if (session.value) {
      await digitalHumanApi.deleteSession(session.value.sessionId).catch(() => undefined)
    }
    localStorage.removeItem(storageKey(activeCode.value))
    const created = await digitalHumanApi.createSession(activeCode.value)
    session.value = created
    messages.value = []
    localStorage.setItem(storageKey(activeCode.value), String(created.sessionId))
    stopAudio()
    window.speechSynthesis?.cancel()
    resetSpeechQueue()
    stopDance()
    input.value = ''
    toast.show('已经开启一段新的对话 ☀')
    scrollMessages()
  } catch (err) {
    toast.show(err instanceof Error ? err.message : '开启新对话失败', 'error')
  }
}

function reactToAction(action: string, motionPrompt = '') {
  if (!action || action === 'none') return
  if (action === 'clap') action = 'shrug'
  if (action === 'hunyuan') {
    void autoHunyuanMotion(motionPrompt)
    return
  }
  if (action === 'dance') {
    void toggleDance()
    return
  }
  playGesture(action)
}

async function autoHunyuanMotion(prompt: string) {
  if (!prompt || autoHunyuanLoading.value) return
  if (!hunyuanConfigured.value) {
    playGesture('think')
    return
  }
  autoHunyuanLoading.value = true
  const targetCode = activeCode.value
  toast.show('这轮需要复杂动作，正在调用混元生成…', 'info', 4200)
  try {
    let job = await digitalHumanApi.submitHunyuanMotion(prompt, 5)
    hunyuanJob.value = job
    while (job.id && job.status !== 'completed' && job.status !== 'failed') {
      await new Promise((resolve) => setTimeout(resolve, 3000))
      job = await digitalHumanApi.hunyuanMotion(job.id)
      hunyuanJob.value = job
    }
    if (targetCode !== activeCode.value) return
    if (job.status === 'completed' && job.fileUrl) {
      await applyHunyuanMotion(job.fileUrl)
    } else if (job.status === 'failed') {
      toast.show(job.error || '混元动作生成失败', 'error', 4200)
      playGesture('think')
    }
  } catch (err) {
    toast.show(err instanceof Error ? err.message : '混元动作生成失败', 'error', 4200)
  } finally {
    autoHunyuanLoading.value = false
  }
}

async function send() {
  if (!canSend.value || !session.value) return
  const content = input.value.trim()
  if (!content) return
  const character = activeCharacter.value
  if (!character) return

  input.value = ''
  const turn = ++activeStreamTurn
  streamAbort?.abort()
  primeAudio()
  resetSpeechQueue()
  speechTargetMessage = null
  const tempMessage: DigitalHumanMessage = {
    id: -Date.now(),
    role: 'USER',
    content,
    emotion: null,
    audioUrl: null,
    createdAt: new Date().toISOString(),
  }
  messages.value.push(tempMessage)
  sending.value = true
  streamingReply.value = false
  scrollMessages()

  let assistantMessage: DigitalHumanMessage | null = null
  let assistantIndex = -1
  let streamError = ''
  let serverAudioSeen = false
  let serverAudioExpected = false
  let audioErrorShown = false
  let timedOut = false
  let firstResponseTimer: number | undefined
  const clearFirstResponseTimer = () => {
    if (firstResponseTimer !== undefined) {
      window.clearTimeout(firstResponseTimer)
      firstResponseTimer = undefined
    }
  }
  let motionSequenceStarted = false
  let motionQueueRunning = false
  const motionQueue: string[] = []
  // New user turn: drop actions queued for the previous reply, but let a
  // currently playing gesture finish instead of cutting it off.
  gestureQueue = []

  const runMotionQueue = async () => {
    if (motionQueueRunning || turn !== activeStreamTurn) return
    motionQueueRunning = true
    while (motionQueue.length > 0 && turn === activeStreamTurn) {
      const motion = motionQueue.shift()
      if (!motion) break
      await new Promise<void>((resolve) => {
        let settled = false
        const finish = () => {
          if (settled) return
          settled = true
          resolve()
        }
        void playGesture(motion, { resumeIdle: false, onComplete: finish })
        window.setTimeout(finish, 15000)
      })
    }
    motionQueueRunning = false
    if (turn === activeStreamTurn) void sceneManager?.resumeIdleMotion?.()
  }

  const startMotionQueue = () => {
    if (motionSequenceStarted) return
    motionSequenceStarted = true
    void runMotionQueue()
  }
  const abort = new AbortController()
  streamAbort = abort
  firstResponseTimer = window.setTimeout(() => {
    if (turn !== activeStreamTurn) return
    timedOut = true
    abort.abort()
  }, 15000)

  try {
    await digitalHumanApi.chatStream(
      {
        sessionId: session.value.sessionId,
        content,
        articleSlug: articleSlug.value || undefined,
      },
      {
        onMeta(meta) {
          if (turn !== activeStreamTurn) return
          serverAudioExpected = Boolean(meta.audioStreaming)
          sceneManager?.setEmotion(meta.emotion || 'neutral')
        },
        onMotion(motion) {
          if (turn !== activeStreamTurn) return
          sceneManager?.setEmotion(motion.emotion || 'neutral')
          motionQueue.push(motion.motion)
          if (motionSequenceStarted) void runMotionQueue()
        },
        onDelta(text) {
          if (turn !== activeStreamTurn || !text) return
          clearFirstResponseTimer()
          if (!serverAudioExpected) startMotionQueue()
          if (!assistantMessage) {
            streamingReply.value = true
            assistantIndex = messages.value.push({
              id: -Date.now(),
              role: 'ASSISTANT',
              content: '',
              emotion: 'neutral',
              audioUrl: null,
              createdAt: new Date().toISOString(),
            }) - 1
            assistantMessage = messages.value[assistantIndex]
            speechTargetMessage = assistantMessage
          }
          assistantMessage.content += text
          scrollMessages()
        },
        onAudio(audio) {
          if (turn !== activeStreamTurn) return
          serverAudioSeen = true
          startMotionQueue()
          if (audio.audioUrl) {
            enqueueSpeechAudio(audio.audioUrl)
          }
        },
        onAudioPcm(audio) {
          if (turn !== activeStreamTurn) return
          serverAudioSeen = true
          startMotionQueue()
          playPcmChunk(audio.pcm, audio.sampleRate || 24000)
        },
        onAudioError(message) {
          if (turn !== activeStreamTurn) return
          serverAudioSeen = true
          startMotionQueue()
          if (!audioErrorShown) {
            audioErrorShown = true
            toast.show(message || '语音流暂时不可用，文字仍会继续', 'info', 3200)
          }
        },
        onDone(done) {
          if (turn !== activeStreamTurn) return
          clearFirstResponseTimer()
          if (!assistantMessage) {
            assistantIndex = messages.value.push({
              id: done.messageId,
              role: 'ASSISTANT',
              content: done.reply,
              emotion: done.emotion,
              audioUrl: null,
              createdAt: new Date().toISOString(),
            }) - 1
            assistantMessage = messages.value[assistantIndex]
          } else {
            assistantMessage.id = done.messageId
            assistantMessage.content = done.reply
            assistantMessage.emotion = done.emotion
          }
          if (!serverAudioExpected && !serverAudioSeen && done.reply) {
            enqueueSpeechText(done.reply)
          }
          if (!serverAudioExpected) startMotionQueue()
          // Text is complete; audio events may still arrive over the same SSE.
          sending.value = false
          streamingReply.value = false
          sceneManager?.setEmotion(done.emotion || 'neutral')
          scrollMessages()
        },
        onError(message) {
          if (turn !== activeStreamTurn) return
          clearFirstResponseTimer()
          streamError = message
        },
      },
      abort.signal,
    )
    if (streamError) throw new Error(streamError)
  } catch (err) {
    clearFirstResponseTimer()
    if (turn !== activeStreamTurn) return
    const message = timedOut
      ? '等待回复超时，请再试一次。'
      : err instanceof Error ? err.message : '连接会客厅失败，请稍后再试。'
    const current = assistantIndex >= 0 ? messages.value[assistantIndex] : null
    if (!current) {
      messages.value.push({
        id: -Date.now(),
        role: 'ASSISTANT',
        content: message,
        emotion: 'sad',
        audioUrl: null,
        createdAt: new Date().toISOString(),
      })
    } else if (!current.content) {
      current.content = message
    }
    sceneManager?.setEmotion('sad')
    toast.show(message, 'error')
    scrollMessages()
  } finally {
    clearFirstResponseTimer()
    if (turn === activeStreamTurn) {
      sending.value = false
      streamingReply.value = false
      if (streamAbort === abort) streamAbort = null
    }
  }
}

function ask(prompt: string) {
  input.value = prompt
  nextTick(() => inputRef.value?.focus())
}

function toggleMute() {
  muted.value = !muted.value
  if (muted.value) {
    stopAudio()
    window.speechSynthesis?.cancel()
    toast.show('已静音，数字人仍会返回文字', 'info')
  } else {
    toast.show('已开启语音', 'success')
  }
}

function toggleFullscreen() {
  fullscreen.value = !fullscreen.value
  nextTick(() => sceneManager?.resize())
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && fullscreen.value) {
    fullscreen.value = false
    nextTick(() => sceneManager?.resize())
  }
}

onMounted(async () => {
  window.addEventListener('keydown', handleKeydown)
  try {
    const [list, hunyuan] = await Promise.all([
      digitalHumanApi.characters(),
      digitalHumanApi.hunyuanStatus().catch(() => null),
    ])
    hunyuanConfigured.value = Boolean(hunyuan?.configured)
    characters.value = list
    if (!characters.value.length) {
      throw new Error('暂时没有可用的数字人角色')
    }
    await initCharacter(characters.value[0].code)
  } catch (err) {
    loading.value = false
    error.value = err instanceof Error ? err.message : '数字人服务暂时不可用'
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  window.clearTimeout(motionTimer)
  window.clearTimeout(hunyuanPollTimer)
  window.clearTimeout(greetingTimer)
  resetGestureState()
  cancelActiveStream()
  resetSpeechQueue()
  window.speechSynthesis?.cancel()
  stopAudio()
  audio = null
  sceneManager?.stopVmd?.()
  sceneManager?.stopFbxMotion?.()
  sceneManager?.dispose()
  sceneManager = null
})
</script>

<template>
  <div class="digital-human-page page-shell">
    <div class="container">
      <header class="dh-hero">
        <div>
          <p class="section-eyebrow">DIGITAL HUMAN · SPRING AI</p>
          <h1>月白的 <span>3D 会客厅</span></h1>
          <p class="dh-hero-desc">
            和来自星穹铁道的爻光、镜流聊聊天。她们会看着你、回答你，也会开口说话。
          </p>
          <div class="dh-hero-tags">
            <span>WebGL 实时渲染</span>
            <span>Spring AI</span>
            <span>文字 + 语音</span>
          </div>
        </div>
        <div class="dh-hero-note">
          <span class="dh-hero-sun">☀</span>
          <p>第一次见面需要加载约 40MB 的 3D 模型，<br />请给她们一点点时间。</p>
        </div>
      </header>

      <div ref="shellRef" class="dh-shell" :class="{ 'is-fullscreen': fullscreen }">
        <section class="dh-stage">
          <canvas ref="canvasRef" class="dh-canvas"></canvas>
          <div class="stage-aura" aria-hidden="true"></div>
          <div class="stage-floor" aria-hidden="true"></div>
          <div class="stage-grid" aria-hidden="true"></div>
          <span class="stage-star s1">✦</span>
          <span class="stage-star s2">✧</span>
          <span class="stage-star s3">✦</span>

          <div class="stage-topbar">
            <div v-if="activeCharacter" class="stage-model">
              <span class="stage-icon">{{ activeCharacter.avatarIcon }}</span>
              <div>
                <strong>{{ activeCharacter.name }}</strong>
                <span>
                  {{
                    speaking
                      ? '正在说话…'
                      : vmdPlaying
                        ? '正在手舞…'
                        : activeMotionLabel
                          ? `正在${activeMotionLabel}…`
                          : sceneReady
                            ? '在线 · 等你开口'
                            : '连接中…'
                  }}
                </span>
              </div>
            </div>
            <div class="stage-actions">
              <button
                type="button"
                class="stage-action"
                :class="{ active: motionPanelOpen }"
                title="动作实验室"
                @click="motionPanelOpen = !motionPanelOpen"
              >
                ✦
              </button>
              <button type="button" class="stage-action" :title="muted ? '开启语音' : '静音'" @click="toggleMute">
                {{ muted ? '🔇' : '🔊' }}
              </button>
              <button type="button" class="stage-action" :title="fullscreen ? '退出全屏' : '全屏'" @click="toggleFullscreen">
                {{ fullscreen ? '⤡' : '⤢' }}
              </button>
            </div>
          </div>

          <Transition name="motion-panel">
            <div v-if="motionPanelOpen" class="motion-panel">
              <div class="motion-panel-head">
                <div>
                  <span class="motion-eyebrow">MOTION LAB</span>
                  <strong>动作实验室</strong>
                </div>
                <button type="button" class="motion-close" title="收起" @click="motionPanelOpen = false">×</button>
              </div>
              <div class="motion-grid">
                <button
                  v-for="action in gestureActions"
                  :key="action.key"
                  type="button"
                  class="motion-item"
                  :class="{ active: activeMotion === action.key }"
                  :disabled="motionLoading"
                  @click="playGesture(action.key)"
                >
                  <span>{{ action.icon }}</span>
                  {{ action.label }}
                </button>
              </div>

              <div class="motion-pack">
                <div class="motion-pack-head">
                  <span>动作包 · {{ packMotions.length }}</span>
                  <em>エモート風モーション</em>
                </div>
                <div class="pack-grid">
                  <button
                    v-for="motion in packMotions"
                    :key="motion.key"
                    type="button"
                    class="pack-motion"
                    :class="{ active: activePackMotion === motion.key }"
                    :disabled="motionLoading"
                    @click="playPackMotion(motion)"
                  >
                    {{ motion.label }}
                  </button>
                </div>
              </div>
              <button
                type="button"
                class="dance-btn"
                :class="{ active: vmdPlaying }"
                :disabled="motionLoading"
                @click="toggleDance"
              >
                <span>{{ vmdPlaying ? '■' : '♫' }}</span>
                {{ motionLoading ? '载入动作模组…' : vmdPlaying ? '停止手舞' : 'VMD 手舞 · 10 秒' }}
              </button>

              <div class="hunyuan-box">
                <div class="hunyuan-head">
                  <span>混元文生动作</span>
                  <em :class="{ ready: hunyuanConfigured }">{{ hunyuanConfigured ? '已就绪' : '待配置 Key' }}</em>
                </div>
                <div class="hunyuan-row">
                  <input
                    v-model="hunyuanPrompt"
                    maxlength="128"
                    placeholder="例如：开心地挥手打招呼"
                    :disabled="hunyuanLoading"
                    @keydown.enter.prevent="generateHunyuanMotion"
                  />
                  <button type="button" :disabled="hunyuanLoading || !hunyuanPrompt.trim()" @click="generateHunyuanMotion">
                    {{ hunyuanLoading ? '…' : '生成' }}
                  </button>
                </div>
                <div v-if="hunyuanJob" class="hunyuan-status">
                  <span>{{ hunyuanJob.status }}</span>
                  <span v-if="hunyuanJob.error" class="hunyuan-error">{{ hunyuanJob.error }}</span>
                  <button v-if="hunyuanJob.fileUrl" type="button" class="hunyuan-preview" @click="openPreview(hunyuanJob.fileUrl)">
                    预览
                  </button>
                  <button
                    v-if="hunyuanJob.fileUrl"
                    type="button"
                    class="hunyuan-apply"
                    :disabled="retargetLoading"
                    @click="applyHunyuanMotion(hunyuanJob.fileUrl)"
                  >
                    {{ retargetLoading ? '重定向中…' : '套用到角色' }}
                  </button>
                  <a v-if="hunyuanJob.fileUrl" :href="hunyuanJob.fileUrl" target="_blank" rel="noopener noreferrer">
                    下载
                  </a>
                </div>
              </div>

              <p class="motion-credit">
                模组：WAVEFILE / hino · 待机：Hotaru7MMD / かんな@MMD · 动作：射当ユウキ / 翡水 · 仅非商用展示
              </p>
            </div>
          </Transition>

          <div v-if="loading" class="stage-loading">
            <div class="stage-loading-ring"></div>
            <p>{{ loadingText }}</p>
            <div class="stage-progress"><span :style="{ width: `${progress}%` }"></span></div>
            <small>Three.js · MMDLoader · PMX</small>
          </div>

          <div v-else-if="error" class="stage-error">
            <span>☁</span>
            <h3>数字人暂时没有醒来</h3>
            <p>{{ error }}</p>
            <button type="button" class="btn btn-sm btn-primary" @click="initCharacter(activeCode)">
              再试一次
            </button>
          </div>

          <div v-else class="stage-bottom">
            <div class="stage-status">
              <span class="status-dot" :class="{ speaking }"></span>
              {{
                speaking
                  ? `${activeCharacter?.name || '数字人'}正在说话`
                  : vmdPlaying
                    ? '正在手舞'
                    : activeMotionLabel
                      ? `正在${activeMotionLabel}`
                      : '会客厅已准备好'
              }}
            </div>
            <div class="stage-hints">
              <button v-for="prompt in quickPrompts.slice(0, 3)" :key="prompt" type="button" @click="ask(prompt)">
                {{ prompt }}
              </button>
            </div>
          </div>

          <span class="stage-credit">WebGL · MMD · Three.js</span>
        </section>

        <aside class="dh-chat">
          <header class="chat-head">
            <div>
              <p class="chat-eyebrow">LIVE CONVERSATION</p>
              <h2>和{{ activeCharacter?.name || '数字人' }}说说话</h2>
            </div>
            <button type="button" class="new-chat-btn" title="开启新对话" @click="newSession">＋ 新对话</button>
          </header>

          <div class="character-switch" role="group" aria-label="选择数字人">
            <button
              v-for="character in characters"
              :key="character.code"
              type="button"
              class="character-pill"
              :class="{ active: character.code === activeCode }"
              :disabled="loading"
              @click="switchCharacter(character.code)"
            >
              <span>{{ character.avatarIcon }}</span>
              {{ character.name }}
            </button>
          </div>

          <div v-if="articleSlug" class="article-context">
            <span>✎</span>
            正在结合文章 <strong>/{{ articleSlug }}</strong> 聊天
          </div>

          <div ref="messagesRef" class="messages">
            <div v-if="!messages.length && !sending" class="welcome-card">
              <div class="welcome-avatar">{{ activeCharacter?.avatarIcon }}</div>
              <p class="welcome-name">{{ activeCharacter?.name }}</p>
              <p class="welcome-text">{{ activeCharacter?.greeting }}</p>
              <div class="welcome-prompts">
                <button v-for="prompt in quickPrompts" :key="prompt" type="button" @click="ask(prompt)">
                  {{ prompt }}
                </button>
              </div>
            </div>

            <article
              v-for="message in messages"
              :key="message.id"
              class="message"
              :class="message.role === 'USER' ? 'message-user' : 'message-assistant'"
            >
              <div v-if="message.role === 'ASSISTANT'" class="message-avatar">
                {{ activeCharacter?.avatarIcon }}
              </div>
              <div class="message-body">
                <div class="message-bubble">
                  <MarkdownView v-if="message.role === 'ASSISTANT'" :content="message.content" />
                  <p v-else>{{ message.content }}</p>
                </div>
                <div v-if="message.role === 'ASSISTANT'" class="message-meta">
                  <span>{{ chatTime(message.createdAt) }}</span>
                  <span v-if="message.emotion" class="emotion-chip">{{ emotionLabel[message.emotion] || '平静' }}</span>
                  <button v-if="message.audioUrl" type="button" class="audio-btn" @click="playMessage(message)">
                    ▶ 语音
                  </button>
                </div>
              </div>
            </article>

            <div v-if="sending && !streamingReply" class="message message-assistant typing-row">
              <div class="message-avatar">{{ activeCharacter?.avatarIcon }}</div>
              <div class="typing-bubble">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>

          <form class="composer" @submit.prevent="send">
            <textarea
              ref="inputRef"
              v-model="input"
              class="composer-input"
              rows="2"
              maxlength="500"
              placeholder="想和她们聊点什么？Enter 发送，Shift + Enter 换行"
              @keydown.enter.exact.prevent="send"
            ></textarea>
            <div class="composer-bar">
              <span>{{ muted ? '语音已静音' : '会回复文字和语音' }} · {{ input.length }}/500</span>
              <button type="submit" class="send-btn" :disabled="!canSend" title="发送">
                <span v-if="sending" class="mini-spinner"></span>
                <span v-else>↑</span>
              </button>
            </div>
          </form>

          <footer class="chat-foot">
            <span>Spring AI · DeepSeek Flash</span>
            <span>DashScope CosyVoice</span>
          </footer>
        </aside>
      </div>

      <FbxPreview v-if="previewOpen && previewUrl" :url="previewUrl" @close="previewOpen = false" />

      <section class="dh-features">
        <article class="feature-card">
          <span>◈</span>
          <h3>WebGL 实时渲染</h3>
          <p>PMX 模型、骨骼、表情与口型同步，全部在浏览器本地完成。</p>
        </article>
        <article class="feature-card">
          <span>✦</span>
          <h3>Spring AI 对话</h3>
          <p>后端通过 Spring AI 调用 DeepSeek Flash，并结合博客文章上下文回答。</p>
        </article>
        <article class="feature-card">
          <span>◉</span>
          <h3>文字 + 语音</h3>
          <p>阿里云 CosyVoice 按角色音色生成语音，不使用浏览器朗读，避免双声重叠。</p>
        </article>
      </section>
    </div>
  </div>
</template>

<style scoped>
.digital-human-page {
  overflow-x: clip;
  padding-bottom: 20px;
}

.dh-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
  padding: 20px 4px 26px;
}

.dh-hero h1 {
  margin-top: 8px;
  font-size: clamp(1.9rem, 4vw, 2.8rem);
  letter-spacing: -0.04em;
}

.dh-hero h1 span {
  color: #4d84a3;
}

.dh-hero-desc {
  max-width: 620px;
  margin-top: 10px;
  color: var(--ink-soft);
  font-size: 0.9rem;
  line-height: 1.8;
}

.dh-hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 14px;
}

.dh-hero-tags span {
  padding: 5px 11px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.9);
  color: var(--ink-soft);
  font-size: 0.66rem;
  font-weight: 800;
}

.dh-hero-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  max-width: 290px;
  padding: 12px 15px;
  border-radius: 16px;
  background: linear-gradient(145deg, #fff9ec, #fff1f5);
  border: 1px dashed rgba(246, 201, 107, 0.35);
}

.dh-hero-sun {
  color: var(--butter-strong);
}

.dh-hero-note p {
  color: #a48b5a;
  font-size: 0.68rem;
  line-height: 1.7;
}

.dh-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  height: min(760px, calc(100vh - 170px));
  min-height: 620px;
  border-radius: 32px;
  overflow: hidden;
  background: #101827;
  box-shadow: 0 30px 80px rgba(50, 70, 90, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.dh-shell.is-fullscreen {
  position: fixed;
  inset: 18px;
  z-index: 1000;
  height: auto;
  max-height: none;
  min-height: 0;
  grid-template-columns: minmax(0, 1fr) 420px;
  box-shadow: 0 40px 100px rgba(20, 30, 45, 0.4);
}

.dh-stage {
  position: relative;
  min-width: 0;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 55%, rgba(91, 129, 188, 0.25), transparent 40%),
    radial-gradient(circle at 15% 15%, rgba(240, 170, 198, 0.12), transparent 32%),
    linear-gradient(155deg, #0c1424 0%, #111c30 45%, #211a30 100%);
}

.dh-canvas {
  position: absolute;
  inset: 0;
  z-index: 1;
  width: 100%;
  height: 100%;
  display: block;
  filter: saturate(1.75) contrast(1.14) brightness(1.03);
}

.stage-aura {
  position: absolute;
  z-index: 0;
  left: 50%;
  bottom: 8%;
  width: 54%;
  height: 58%;
  transform: translateX(-50%);
  border-radius: 50%;
  background: radial-gradient(ellipse, rgba(116, 169, 218, 0.2), transparent 68%);
  filter: blur(26px);
  pointer-events: none;
}

.stage-floor {
  position: absolute;
  z-index: 0;
  left: 50%;
  bottom: 12%;
  width: 30%;
  height: 6%;
  transform: translateX(-50%);
  border-radius: 50%;
  background: radial-gradient(ellipse, rgba(4, 8, 16, 0.5), transparent 70%);
  filter: blur(9px);
  pointer-events: none;
}

.stage-grid {
  position: absolute;
  inset: 0;
  z-index: 0;
  opacity: 0.18;
  background-image:
    linear-gradient(rgba(169, 216, 245, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(169, 216, 245, 0.08) 1px, transparent 1px);
  background-size: 54px 54px;
  mask-image: linear-gradient(to bottom, transparent, black 35%, black 75%, transparent);
  pointer-events: none;
}

.stage-star {
  position: absolute;
  z-index: 0;
  color: rgba(255, 227, 163, 0.7);
  animation: float-soft 6s ease-in-out infinite;
  pointer-events: none;
}
.s1 { top: 20%; left: 14%; font-size: 0.8rem; }
.s2 { top: 32%; right: 12%; font-size: 1rem; animation-delay: -2s; color: rgba(249, 198, 213, 0.7); }
.s3 { bottom: 24%; left: 22%; font-size: 0.65rem; animation-delay: -4s; }

.stage-topbar {
  position: absolute;
  z-index: 3;
  top: 20px;
  left: 22px;
  right: 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.stage-model {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #f4f7fb;
}

.stage-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: #ffe3a3;
  font-size: 1rem;
  backdrop-filter: blur(8px);
}

.stage-model div {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.stage-model strong {
  font-size: 0.82rem;
  letter-spacing: 0.04em;
}

.stage-model span {
  color: rgba(225, 235, 245, 0.56);
  font-size: 0.62rem;
}

.stage-actions {
  display: flex;
  gap: 7px;
}

.stage-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.08);
  color: #e9f1f8;
  font-size: 0.9rem;
  backdrop-filter: blur(8px);
  transition: background 0.2s ease, transform 0.2s ease;
}

.stage-action:hover {
  background: rgba(255, 255, 255, 0.16);
  transform: translateY(-1px);
}

.stage-action.active {
  color: #ffe3a3;
  border-color: rgba(255, 227, 163, 0.45);
  background: rgba(255, 227, 163, 0.12);
}

.motion-panel {
  position: absolute;
  z-index: 6;
  left: 22px;
  bottom: 82px;
  width: 300px;
  max-height: calc(100% - 110px);
  overflow-y: auto;
  padding: 15px;
  border-radius: 18px;
  color: #eaf2f9;
  background: rgba(12, 20, 34, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow: 0 18px 45px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(16px);
  pointer-events: auto;
}

.motion-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.motion-panel-head div {
  display: flex;
  flex-direction: column;
}

.motion-eyebrow {
  color: rgba(255, 227, 163, 0.65);
  font-size: 0.5rem;
  font-weight: 900;
  letter-spacing: 0.16em;
}

.motion-panel-head strong {
  margin-top: 2px;
  font-size: 0.8rem;
}

.motion-close {
  border: 0;
  background: transparent;
  color: rgba(234, 242, 249, 0.55);
  font-size: 1rem;
  line-height: 1;
  padding: 0 2px;
}

.motion-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}

.motion-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 4px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.055);
  color: rgba(234, 242, 249, 0.8);
  font-size: 0.62rem;
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.motion-item span {
  color: #ffe3a3;
  font-size: 0.8rem;
}

.motion-item:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.12);
  transform: translateY(-1px);
}

.motion-item.active {
  border-color: rgba(255, 227, 163, 0.5);
  background: rgba(255, 227, 163, 0.12);
  color: #fff5df;
}

.motion-item:disabled {
  opacity: 0.45;
  cursor: wait;
}

.motion-pack {
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.motion-pack-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6px;
  color: rgba(234, 242, 249, 0.8);
  font-size: 0.62rem;
  font-weight: 800;
}

.motion-pack-head em {
  color: rgba(234, 242, 249, 0.38);
  font-size: 0.54rem;
  font-style: normal;
  font-weight: 500;
}

.pack-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 5px;
}

.pack-motion {
  padding: 6px 5px;
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.035);
  color: rgba(234, 242, 249, 0.66);
  font-size: 0.58rem;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.pack-motion:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.pack-motion.active {
  border-color: rgba(169, 216, 245, 0.45);
  background: rgba(169, 216, 245, 0.12);
  color: #dff2ff;
}

.pack-motion:disabled {
  opacity: 0.45;
  cursor: wait;
}

.dance-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  margin-top: 9px;
  padding: 8px 10px;
  border: 1px solid rgba(169, 216, 245, 0.25);
  border-radius: 10px;
  background: rgba(169, 216, 245, 0.08);
  color: #cfe9f8;
  font-size: 0.66rem;
  font-weight: 800;
}

.dance-btn:hover:not(:disabled) {
  background: rgba(169, 216, 245, 0.16);
}

.dance-btn.active {
  color: #ffdfe8;
  border-color: rgba(249, 198, 213, 0.4);
  background: rgba(249, 198, 213, 0.1);
}

.dance-btn:disabled {
  opacity: 0.55;
  cursor: wait;
}

.hunyuan-box {
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.hunyuan-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: rgba(234, 242, 249, 0.7);
  font-size: 0.6rem;
  font-weight: 800;
}

.hunyuan-head em {
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(249, 198, 213, 0.1);
  color: #f0b9c8;
  font-size: 0.5rem;
  font-style: normal;
  font-weight: 700;
}

.hunyuan-head em.ready {
  background: rgba(143, 210, 176, 0.12);
  color: #9edbb8;
}

.hunyuan-row {
  display: flex;
  gap: 5px;
  margin-top: 7px;
}

.hunyuan-row input {
  min-width: 0;
  flex: 1;
  padding: 7px 8px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.18);
  color: #edf5fb;
  font-size: 0.62rem;
  outline: none;
}

.hunyuan-row input::placeholder {
  color: rgba(234, 242, 249, 0.3);
}

.hunyuan-row input:focus {
  border-color: rgba(169, 216, 245, 0.45);
}

.hunyuan-row button {
  flex: 0 0 auto;
  padding: 0 10px;
  border: 1px solid rgba(255, 227, 163, 0.25);
  border-radius: 8px;
  background: rgba(255, 227, 163, 0.1);
  color: #ffe3a3;
  font-size: 0.6rem;
  font-weight: 800;
}

.hunyuan-row button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.hunyuan-status {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 6px;
  color: rgba(234, 242, 249, 0.45);
  font-size: 0.54rem;
}

.hunyuan-status span:first-child {
  color: #9edbb8;
}

.hunyuan-error {
  color: #f0b9c8 !important;
}

.hunyuan-status a {
  margin-left: 4px;
  color: #cfeeff;
  text-decoration: underline;
}

.hunyuan-preview {
  margin-left: auto;
  padding: 0;
  border: 0;
  background: transparent;
  color: #ffdf9f;
  font-size: 0.54rem;
  font-weight: 700;
  text-decoration: underline;
}

.hunyuan-apply {
  padding: 3px 6px;
  border: 1px solid rgba(255, 227, 163, 0.25);
  border-radius: 6px;
  background: rgba(255, 227, 163, 0.08);
  color: #ffe3a3;
  font-size: 0.52rem;
  font-weight: 800;
  white-space: nowrap;
}

.hunyuan-apply:disabled {
  opacity: 0.5;
  cursor: wait;
}

.motion-credit {
  margin-top: 7px;
  color: rgba(234, 242, 249, 0.32);
  font-size: 0.5rem;
  line-height: 1.5;
  text-align: center;
}

.motion-panel-enter-active,
.motion-panel-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.motion-panel-enter-from,
.motion-panel-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

.stage-loading,
.stage-error {
  position: absolute;
  z-index: 4;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30px;
  text-align: center;
  color: #edf4fb;
  background: rgba(9, 16, 28, 0.72);
  backdrop-filter: blur(8px);
}

.stage-loading-ring {
  width: 54px;
  height: 54px;
  border: 2px solid rgba(169, 216, 245, 0.18);
  border-top-color: #a9d8f5;
  border-right-color: #f9c6d5;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.stage-loading p {
  margin-top: 16px;
  font-size: 0.82rem;
  font-weight: 700;
}

.stage-loading small {
  margin-top: 10px;
  color: rgba(211, 226, 240, 0.48);
  font-size: 0.6rem;
  letter-spacing: 0.12em;
}

.stage-progress {
  width: min(260px, 70%);
  height: 4px;
  margin-top: 14px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
}

.stage-progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #8cc8e8, #f7b7c8);
  transition: width 0.25s ease;
}

.stage-error > span {
  font-size: 2.3rem;
}

.stage-error h3 {
  margin-top: 10px;
  color: #fff;
  font-size: 1rem;
}

.stage-error p {
  margin: 7px 0 16px;
  max-width: 360px;
  color: rgba(225, 235, 245, 0.6);
  font-size: 0.74rem;
  line-height: 1.7;
}

.stage-bottom {
  position: absolute;
  z-index: 3;
  left: 22px;
  right: 22px;
  bottom: 27px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  pointer-events: none;
}

.stage-status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(10, 18, 30, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.14);
  color: rgba(229, 239, 248, 0.8);
  font-size: 0.64rem;
  backdrop-filter: blur(10px);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #8fd2b0;
  box-shadow: 0 0 0 4px rgba(143, 210, 176, 0.15);
}

.status-dot.speaking {
  background: #f7b7c8;
  box-shadow: 0 0 0 4px rgba(247, 183, 200, 0.18);
  animation: pulse 1.2s ease-in-out infinite;
}

.stage-hints {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
  max-width: 65%;
  pointer-events: auto;
}

.stage-hints button {
  padding: 6px 11px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.07);
  color: rgba(229, 239, 248, 0.72);
  font-size: 0.62rem;
  backdrop-filter: blur(8px);
}

.stage-hints button:hover {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
}

.stage-credit {
  position: absolute;
  z-index: 3;
  left: 50%;
  bottom: 7px;
  transform: translateX(-50%);
  color: rgba(229, 239, 248, 0.24);
  font-size: 0.52rem;
  letter-spacing: 0.12em;
}

.dh-chat {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: linear-gradient(180deg, #fffdf8, #fff7ee);
  border-left: 1px solid rgba(255, 255, 255, 0.6);
}

.chat-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 20px 12px;
}

.chat-eyebrow {
  color: #b0a79c;
  font-size: 0.56rem;
  font-weight: 900;
  letter-spacing: 0.18em;
}

.chat-head h2 {
  margin-top: 3px;
  font-size: 1.02rem;
  letter-spacing: -0.02em;
}

.new-chat-btn {
  flex: 0 0 auto;
  padding: 6px 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: #fff;
  color: var(--ink-soft);
  font-size: 0.62rem;
  font-weight: 800;
}

.new-chat-btn:hover {
  color: #4e86a7;
  border-color: rgba(169, 216, 245, 0.7);
}

.character-switch {
  display: flex;
  gap: 6px;
  padding: 0 20px 12px;
}

.character-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.6);
  color: var(--ink-soft);
  font-size: 0.7rem;
  font-weight: 800;
}

.character-pill.active {
  background: linear-gradient(135deg, #eaf6fe, #fff0f4);
  border-color: rgba(169, 216, 245, 0.7);
  color: #4e86a7;
}

.character-pill:disabled {
  opacity: 0.5;
  cursor: wait;
}

.article-context {
  margin: 0 20px 10px;
  padding: 6px 10px;
  border-radius: 9px;
  background: #fff8e4;
  color: #a48b5a;
  font-size: 0.62rem;
}

.article-context strong {
  font-family: "Cascadia Code", Consolas, monospace;
  font-weight: 700;
}

.messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 18px 16px;
  scroll-behavior: smooth;
}

.messages::-webkit-scrollbar {
  width: 5px;
}

.messages::-webkit-scrollbar-thumb {
  background: rgba(169, 216, 245, 0.5);
  border-radius: 999px;
}

.welcome-card {
  margin: 8px 2px 16px;
  padding: 20px 18px;
  border-radius: 20px;
  background:
    radial-gradient(circle at 100% 0%, rgba(249, 198, 213, 0.35), transparent 38%),
    linear-gradient(145deg, #fffdf8, #f4fbff);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-sm);
}

.welcome-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 13px;
  background: #fff3d8;
  color: #b08935;
  font-size: 1rem;
}

.welcome-name {
  margin-top: 8px;
  color: #4e86a7;
  font-size: 0.7rem;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.welcome-text {
  margin-top: 4px;
  color: var(--ink-soft);
  font-size: 0.78rem;
  line-height: 1.75;
}

.welcome-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 12px;
}

.welcome-prompts button {
  padding: 5px 9px;
  border: 1px solid rgba(169, 216, 245, 0.4);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #5c8eaa;
  font-size: 0.6rem;
  font-weight: 700;
}

.message {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.message-user {
  justify-content: flex-end;
}

.message-body {
  max-width: 86%;
}

.message-bubble {
  padding: 10px 13px;
  border-radius: 4px 16px 16px 16px;
  background: #fff;
  border: 1px solid rgba(105, 135, 160, 0.09);
  box-shadow: 0 5px 14px rgba(112, 142, 166, 0.06);
  color: var(--ink-soft);
  font-size: 0.79rem;
  line-height: 1.7;
  word-break: break-word;
}

.message-user .message-bubble {
  border-radius: 16px 4px 16px 16px;
  background: linear-gradient(135deg, #dff1fd, #ffeaf1);
  border-color: rgba(255, 255, 255, 0.8);
  color: #4d6578;
}

.message-bubble :deep(.markdown-body) {
  font-size: 0.78rem;
  line-height: 1.75;
}

.message-bubble :deep(.markdown-body p) {
  margin: 0.45em 0;
}

.message-bubble :deep(.markdown-body h1),
.message-bubble :deep(.markdown-body h2),
.message-bubble :deep(.markdown-body h3) {
  margin: 0.7em 0 0.4em;
  font-size: 0.9rem;
}

.message-bubble :deep(.markdown-body h2::before),
.message-bubble :deep(.markdown-body h3::before) {
  display: none;
}

.message-bubble :deep(.markdown-body h2),
.message-bubble :deep(.markdown-body h3) {
  padding-left: 0;
}

.message-bubble :deep(.markdown-body ul),
.message-bubble :deep(.markdown-body ol) {
  margin: 0.45em 0;
  padding-left: 1.3em;
}

.message-bubble :deep(.markdown-body pre) {
  margin: 0.6em 0;
  padding: 12px 14px;
  font-size: 0.7rem;
}

.message-bubble :deep(.markdown-body pre code) {
  padding-top: 6px;
  font-size: 0.7rem;
}

.message-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 27px;
  height: 27px;
  flex: 0 0 auto;
  margin-top: 4px;
  border-radius: 9px;
  background: #fff3d8;
  color: #b08935;
  font-size: 0.68rem;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 4px;
  color: var(--ink-faint);
  font-size: 0.56rem;
}

.emotion-chip {
  padding: 1px 6px;
  border-radius: 999px;
  background: #f4fbff;
  color: #6d9bb5;
}

.audio-btn {
  border: 0;
  background: transparent;
  color: #d28a9e;
  font-size: 0.58rem;
  font-weight: 800;
  padding: 0;
}

.audio-btn:hover {
  color: #b76580;
}

.typing-row {
  align-items: center;
}

.typing-bubble {
  display: flex;
  gap: 4px;
  padding: 10px 14px;
  border-radius: 4px 16px 16px 16px;
  background: #fff;
}

.typing-bubble span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #b9d8e9;
  animation: typing 1s infinite ease-in-out;
}
.typing-bubble span:nth-child(2) { animation-delay: 0.15s; }
.typing-bubble span:nth-child(3) { animation-delay: 0.3s; }

.composer {
  flex: 0 0 auto;
  margin: 0 14px 10px;
  padding: 10px 11px 8px;
  border: 1px solid rgba(105, 135, 160, 0.15);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 7px 20px rgba(112, 142, 166, 0.08);
}

.composer:focus-within {
  border-color: rgba(119, 188, 229, 0.65);
  box-shadow: 0 0 0 4px rgba(169, 216, 245, 0.16);
}

.composer-input {
  width: 100%;
  min-height: 42px;
  max-height: 100px;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-size: 0.78rem;
  line-height: 1.7;
  resize: none;
  outline: none;
}

.composer-input::placeholder {
  color: #b3bac4;
}

.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 5px;
}

.composer-bar > span {
  color: var(--ink-faint);
  font-size: 0.56rem;
}

.send-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  background: linear-gradient(135deg, #9fd6f2, #f7c0d0);
  color: #fff;
  font-size: 1rem;
  font-weight: 800;
  box-shadow: 0 6px 14px rgba(140, 200, 232, 0.3);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  box-shadow: none;
}

.mini-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.chat-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px 13px;
  color: #b5aa9e;
  font-size: 0.52rem;
  letter-spacing: 0.03em;
}

.dh-features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-top: 22px;
}

.feature-card {
  padding: 18px 19px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-sm);
}

.feature-card > span {
  color: var(--butter-strong);
  font-size: 1rem;
}

.feature-card h3 {
  margin-top: 6px;
  font-size: 0.86rem;
}

.feature-card p {
  margin-top: 4px;
  color: var(--ink-faint);
  font-size: 0.68rem;
  line-height: 1.7;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.55; transform: scale(0.82); }
}

@keyframes typing {
  0%, 100% { opacity: 0.35; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-2px); }
}

@media (max-width: 1080px) {
  .dh-shell {
    grid-template-columns: minmax(0, 1fr) 350px;
  }
  .dh-hero-note {
    display: none;
  }
}

@media (max-width: 820px) {
  .dh-shell,
  .dh-shell.is-fullscreen {
    display: flex;
    flex-direction: column;
    position: relative;
    inset: auto;
    z-index: auto;
    height: auto;
    min-height: 0;
    max-height: none;
    border-radius: 26px;
  }
  .dh-stage {
    height: 58vh;
    min-height: 420px;
  }
  .dh-chat {
    min-height: 560px;
    border-left: 0;
    border-top: 1px solid rgba(255, 255, 255, 0.7);
  }
  .messages {
    max-height: 430px;
  }
  .dh-features {
    grid-template-columns: 1fr;
  }
  .stage-hints {
    max-width: 100%;
  }
  .stage-hints button:nth-child(n + 3) {
    display: none;
  }
}

@media (max-width: 560px) {
  .dh-hero {
    flex-direction: column;
    align-items: flex-start;
    padding-bottom: 18px;
  }
  .dh-hero h1 {
    font-size: 1.9rem;
  }
  .dh-stage {
    height: 54vh;
    min-height: 360px;
  }
  .stage-topbar {
    top: 14px;
    left: 14px;
    right: 14px;
  }
  .stage-bottom {
    left: 14px;
    right: 14px;
    bottom: 14px;
  }
  .motion-panel {
    left: 14px;
    right: 14px;
    bottom: 74px;
    width: auto;
  }
  .stage-hints {
    display: none;
  }
  .chat-head {
    padding: 16px 16px 10px;
  }
  .character-switch {
    padding-left: 16px;
    padding-right: 16px;
  }
  .messages {
    padding-left: 14px;
    padding-right: 14px;
  }
  .message-body {
    max-width: 92%;
  }
  .composer {
    margin-left: 10px;
    margin-right: 10px;
  }
}
</style>
