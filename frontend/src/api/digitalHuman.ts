import { httpDelete, httpGet, httpPost, httpPostLong } from './http'

export interface DigitalHumanCharacter {
  id: number
  code: string
  name: string
  avatarIcon: string
  modelPath: string
  greeting: string
  voice: string
}

export interface DigitalHumanMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  emotion: string | null
  audioUrl: string | null
  createdAt: string
}

export interface DigitalHumanSession {
  sessionId: number
  characterCode: string
  characterName: string
  status: string
  createdAt: string
  messages: DigitalHumanMessage[]
}

export interface DigitalHumanChatResponse {
  messageId: number
  sessionId: number
  reply: string
  emotion: string
  audioUrl: string | null
  ttsError: string | null
}

export interface DigitalHumanChatPayload {
  sessionId: number
  content: string
  articleSlug?: string
}

export interface HunyuanMotionJob {
  id: string | null
  status: string
  prompt: string | null
  fileUrl: string | null
  remoteUrl: string | null
  error: string | null
  configured: boolean
}

export interface DigitalHumanActionMeta {
  sessionId: number
  action: string
  emotion: string
  motionPrompt: string
  audioStreaming?: boolean
}

export interface DigitalHumanStreamDone {
  messageId: number
  sessionId: number
  reply: string
  emotion: string
  action: string
}

export interface DigitalHumanTtsResult {
  audioUrl: string | null
  error: string | null
}

export interface DigitalHumanAudioChunk {
  seq: number
  text: string
  audioUrl: string | null
  error: string | null
}

export interface DigitalHumanPcmChunk {
  seq: number
  sampleRate: number
  pcm: string
}

export interface DigitalHumanMotion {
  seq: number
  motion: string
  emotion: string
}

export interface DigitalHumanStreamHandlers {
  onMeta?: (meta: DigitalHumanActionMeta) => void
  onMotion?: (motion: DigitalHumanMotion) => void
  onDelta?: (text: string) => void
  onAudio?: (audio: DigitalHumanAudioChunk) => void
  onAudioPcm?: (audio: DigitalHumanPcmChunk) => void
  onAudioError?: (message: string) => void
  onDone?: (done: DigitalHumanStreamDone) => void
  onError?: (message: string) => void
}

export const digitalHumanApi = {
  characters() {
    return httpGet<DigitalHumanCharacter[]>('/digital-human/characters')
  },
  createSession(characterCode: string) {
    return httpPost<DigitalHumanSession>('/digital-human/sessions', { characterCode })
  },
  session(id: number) {
    return httpGet<DigitalHumanSession>(`/digital-human/sessions/${id}`)
  },
  deleteSession(id: number) {
    return httpDelete<void>(`/digital-human/sessions/${id}`)
  },
  chat(payload: DigitalHumanChatPayload) {
    return httpPostLong<DigitalHumanChatResponse>('/digital-human/chat', payload)
  },
  status() {
    return httpGet<Record<string, unknown>>('/digital-human/status')
  },
  hunyuanStatus() {
    return httpGet<HunyuanMotionJob>('/digital-human/motions/hunyuan/status')
  },
  submitHunyuanMotion(prompt: string, duration = 5) {
    return httpPost<HunyuanMotionJob>('/digital-human/motions/hunyuan', { prompt, duration })
  },
  hunyuanMotion(id: string) {
    return httpGet<HunyuanMotionJob>(`/digital-human/motions/hunyuan/${id}`)
  },
  tts(characterCode: string, text: string) {
    return httpPostLong<DigitalHumanTtsResult>('/digital-human/tts', { characterCode, text }, 60000)
  },
  async chatStream(payload: DigitalHumanChatPayload, handlers: DigitalHumanStreamHandlers, signal?: AbortSignal) {
    const response = await fetch('/api/digital-human/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(payload),
      signal,
    })
    if (!response.ok || !response.body) {
      const errorBody = await response.text().catch(() => '')
      throw new Error(errorBody || `流式对话失败（HTTP ${response.status}）`)
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() || ''
      for (const block of blocks) {
        const lines = block.split('\n')
        const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim()
        const raw = lines
          .filter((line) => line.startsWith('data:'))
          .map((line) => line.slice(5).trim())
          .join('\n')
        if (!event || !raw) continue
        let data: Record<string, unknown>
        try {
          data = JSON.parse(raw)
        } catch {
          continue
        }
        if (event === 'meta') handlers.onMeta?.(data as unknown as DigitalHumanActionMeta)
        else if (event === 'delta') handlers.onDelta?.(String(data.text || ''))
        else if (event === 'audio') handlers.onAudio?.(data as unknown as DigitalHumanAudioChunk)
        else if (event === 'motion') handlers.onMotion?.(data as unknown as DigitalHumanMotion)
        else if (event === 'audio-pcm') handlers.onAudioPcm?.(data as unknown as DigitalHumanPcmChunk)
        else if (event === 'audio-pcm-error') handlers.onAudioError?.(String(data.message || '语音流中断'))
        else if (event === 'done') handlers.onDone?.(data as unknown as DigitalHumanStreamDone)
        else if (event === 'error') handlers.onError?.(String(data.message || '对话流中断'))
      }
    }
  },
}
