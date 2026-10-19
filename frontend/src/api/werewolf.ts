import { httpDelete, httpGet, httpPost } from './http'

export interface WerewolfOption {
  id: string
  label: string
}

export interface WerewolfWaiting {
  type: 'WOLF_KILL' | 'WITCH_SAVE' | 'WITCH_POISON' | 'SEER_CHECK' | 'VOTE' | 'HUNTER_SHOOT'
  title: string
  prompt: string
  options: WerewolfOption[]
}

export interface WerewolfPlayer {
  id: string
  seat: number
  name: string
  alive: boolean
  human: boolean
  role: string | null
  roleLabel: string | null
  roleIcon: string | null
  team: string | null
  deathCause: string | null
  claimedRole: string | null
  claimedRoleLabel: string | null
  suspicion: number
}

export interface WerewolfLog {
  seq: number
  round: number
  phase: string
  phaseLabel: string
  type: string
  text: string
  privateLog: boolean
}

export interface WerewolfAbilities {
  antidote: boolean
  poison: boolean
  canShoot: boolean
}

export interface WerewolfGame {
  id: string
  round: number
  phase: string
  phaseLabel: string
  phaseHint: string
  over: boolean
  winner: string | null
  winnerLabel: string | null
  humanId: string
  humanAlive: boolean
  myRole: string
  myRoleLabel: string
  myRoleIcon: string
  myTeam: string
  myRoleDescription: string
  teammates: string[]
  abilities: WerewolfAbilities
  waiting: WerewolfWaiting | null
  players: WerewolfPlayer[]
  logs: WerewolfLog[]
  summary: string
}

export interface WerewolfCreatePayload {
  playerName: string
  playerCount: number
  role: string
}

export interface WerewolfStreamPayload {
  action: 'ADVANCE' | 'ACTION' | 'AUTO' | 'SPEAK'
  type?: string
  targetId?: string
  text?: string
}

export interface WerewolfStreamHandlers {
  onThinking?: (text: string) => void
  onSpeechStart?: (data: { speakerId: string; speakerName: string; seat: number }) => void
  onToken?: (text: string) => void
  onSpeechEnd?: (text: string) => void
  onState?: (game: WerewolfGame) => void
  onDone?: () => void
}

export const werewolfApi = {
  create(payload: WerewolfCreatePayload) {
    return httpPost<WerewolfGame>('/werewolf/games', payload)
  },
  status() {
    return httpGet<{ configured: boolean }>('/werewolf/status')
  },
  get(id: string) {
    return httpGet<WerewolfGame>(`/werewolf/games/${id}`)
  },
  advance(id: string) {
    return httpPost<WerewolfGame>(`/werewolf/games/${id}/advance`)
  },
  action(id: string, type: string, targetId?: string, text?: string) {
    return httpPost<WerewolfGame>(`/werewolf/games/${id}/action`, { type, targetId, text })
  },
  auto(id: string) {
    return httpPost<WerewolfGame>(`/werewolf/games/${id}/auto`)
  },
  async stream(
    id: string,
    payload: WerewolfStreamPayload,
    handlers: WerewolfStreamHandlers,
    signal?: AbortSignal,
  ) {
    const response = await fetch(`/api/werewolf/games/${id}/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(payload),
      signal,
    })
    if (!response.ok || !response.body) {
      const error = await response.json().catch(() => null)
      throw new Error(error?.message || `流式响应失败（HTTP ${response.status}）`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let serverError = ''
    let done = false

    const dispatch = (block: string) => {
      let event = 'message'
      const dataLines: string[] = []
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
      }
      if (!dataLines.length) return
      let data: any = null
      try {
        data = JSON.parse(dataLines.join('\n'))
      } catch {
        data = dataLines.join('\n')
      }
      if (event === 'thinking') handlers.onThinking?.(String(data?.text || ''))
      else if (event === 'speech_start') handlers.onSpeechStart?.(data)
      else if (event === 'token') handlers.onToken?.(String(data?.text || ''))
      else if (event === 'speech_end') handlers.onSpeechEnd?.(String(data?.text || ''))
      else if (event === 'state') handlers.onState?.(data as WerewolfGame)
      else if (event === 'done') {
        done = true
        handlers.onDone?.()
      } else if (event === 'error') {
        serverError = String(data?.message || 'AI 回合处理失败')
      }
    }

    while (true) {
      const { value, done: readerDone } = await reader.read()
      if (readerDone) break
      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replace(/\r\n/g, '\n')
      let separator = buffer.indexOf('\n\n')
      while (separator >= 0) {
        const block = buffer.slice(0, separator)
        buffer = buffer.slice(separator + 2)
        if (block.trim()) dispatch(block)
        separator = buffer.indexOf('\n\n')
      }
    }
    if (buffer.trim()) dispatch(buffer)
    if (serverError) throw new Error(serverError)
    if (!done) handlers.onDone?.()
  },
  remove(id: string) {
    return httpDelete<void>(`/werewolf/games/${id}`)
  },
}
