import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'sunny-theme'

function preferredMode(): ThemeMode {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') return saved
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(preferredMode())
  const isDark = computed(() => mode.value === 'dark')

  function apply() {
    document.documentElement.dataset.theme = mode.value
    document.documentElement.style.colorScheme = mode.value
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute('content', mode.value === 'dark' ? '#0B1220' : '#FFFCF5')
  }

  function clear() {
    delete document.documentElement.dataset.theme
    document.documentElement.style.colorScheme = ''
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', '#FFFCF5')
  }

  function setMode(next: ThemeMode) {
    mode.value = next
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Theme still applies for this session when storage is unavailable.
    }
    apply()
  }

  function toggle() {
    setMode(mode.value === 'dark' ? 'light' : 'dark')
  }

  return { mode, isDark, apply, clear, setMode, toggle }
})
