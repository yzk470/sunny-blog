import { defineStore } from 'pinia'
import { ref } from 'vue'
import { blogApi } from '@/api/blog'
import type { SiteProfile, SiteStats } from '@/api/types'

export const useSiteStore = defineStore('site', () => {
  const profile = ref<SiteProfile | null>(null)
  const stats = ref<SiteStats | null>(null)
  const loading = ref(false)
  const loaded = ref(false)

  async function load(force = false) {
    if (loaded.value && !force) return
    loading.value = true
    try {
      const [profileResult, statsResult] = await Promise.allSettled([
        blogApi.site(),
        blogApi.stats(),
      ])
      if (profileResult.status === 'fulfilled') profile.value = profileResult.value
      if (statsResult.status === 'fulfilled') stats.value = statsResult.value
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  return { profile, stats, loading, loaded, load }
})
