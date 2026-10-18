import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { adminApi } from '@/api/admin'
import { TOKEN_KEY, USER_KEY } from '@/api/http'
import type { AuthUser } from '@/api/types'

function readUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<AuthUser | null>(readUser())
  const loading = ref(false)

  const isLoggedIn = computed(() => Boolean(token.value))

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const result = await adminApi.login(username, password)
      token.value = result.token
      user.value = result.user
      localStorage.setItem(TOKEN_KEY, result.token)
      localStorage.setItem(USER_KEY, JSON.stringify(result.user))
      return result
    } finally {
      loading.value = false
    }
  }

  async function fetchMe() {
    if (!token.value) return null
    try {
      const result = await adminApi.me()
      user.value = result
      localStorage.setItem(USER_KEY, JSON.stringify(result))
      return result
    } catch {
      logout()
      return null
    }
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, user, loading, isLoggedIn, login, fetchMe, logout }
})
