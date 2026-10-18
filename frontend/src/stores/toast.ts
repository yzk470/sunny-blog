import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastType = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  message: string
  type: ToastType
}

let seed = 0

export const useToastStore = defineStore('toast', () => {
  const items = ref<ToastItem[]>([])

  function dismiss(id: number) {
    items.value = items.value.filter((item) => item.id !== id)
  }

  function show(message: string, type: ToastType = 'success', duration = 3200) {
    const id = ++seed
    items.value.push({ id, message, type })
    window.setTimeout(() => dismiss(id), duration)
  }

  return { items, show, dismiss }
})
