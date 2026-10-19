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

export const werewolfApi = {
  create(payload: WerewolfCreatePayload) {
    return httpPost<WerewolfGame>('/werewolf/games', payload)
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
  remove(id: string) {
    return httpDelete<void>(`/werewolf/games/${id}`)
  },
}
