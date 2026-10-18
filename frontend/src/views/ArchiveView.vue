<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import PageBanner from '@/components/PageBanner.vue'
import EmptyState from '@/components/EmptyState.vue'
import { blogApi } from '@/api/blog'
import type { ArchiveItem } from '@/api/types'
import { formatShortDate } from '@/utils/format'

interface MonthGroup {
  key: string
  label: string
  items: ArchiveItem[]
}
interface YearGroup {
  year: string
  count: number
  months: MonthGroup[]
}

const items = ref<ArchiveItem[]>([])
const loading = ref(true)

const grouped = computed<YearGroup[]>(() => {
  const yearMap = new Map<string, Map<string, ArchiveItem[]>>()
  for (const item of items.value) {
    const date = new Date(item.publishedAt.replace(' ', 'T'))
    if (Number.isNaN(date.getTime())) continue
    const year = String(date.getFullYear())
    const month = String(date.getMonth() + 1).padStart(2, '0')
    if (!yearMap.has(year)) yearMap.set(year, new Map())
    const monthMap = yearMap.get(year)!
    if (!monthMap.has(month)) monthMap.set(month, [])
    monthMap.get(month)!.push(item)
  }
  return [...yearMap.entries()]
    .sort((a, b) => Number(b[0]) - Number(a[0]))
    .map(([year, monthMap]) => {
      const months = [...monthMap.entries()]
        .sort((a, b) => Number(b[0]) - Number(a[0]))
        .map(([month, list]) => ({
          key: `${year}-${month}`,
          label: `${Number(month)} 月`,
          items: list,
        }))
      return { year, count: months.reduce((sum, m) => sum + m.items.length, 0), months }
    })
})

onMounted(async () => {
  try {
    items.value = await blogApi.archive()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="archive-page page-shell">
    <div class="container">
      <PageBanner
        eyebrow="TIME IN A POCKET"
        title="时光归档"
        description="所有认真写下的日子，都在这里排成一条安静的小路。点开任意一篇，就能回到那一天。"
      >
        <span class="archive-count">{{ items.length }} 篇文章</span>
        <span class="archive-tip">☀ 日子会发光，文字会记得</span>
      </PageBanner>

      <div v-if="loading" class="archive-loading">
        <div v-for="i in 3" :key="i" class="skeleton year-skeleton"></div>
      </div>

      <template v-else-if="grouped.length">
        <div class="year-nav">
          <a v-for="group in grouped" :key="group.year" :href="`#year-${group.year}`" class="year-pill">
            {{ group.year }}
            <span>{{ group.count }}</span>
          </a>
        </div>

        <div class="timeline">
          <section v-for="group in grouped" :id="`year-${group.year}`" :key="group.year" class="year-block">
            <div class="year-side">
              <span class="year-number">{{ group.year }}</span>
              <span class="year-label">{{ group.count }} 篇记录</span>
            </div>

            <div class="year-content">
              <div v-for="month in group.months" :key="month.key" class="month-block">
                <div class="month-head">
                  <span class="month-dot"></span>
                  <h3>{{ month.label }}</h3>
                  <span class="month-count">{{ month.items.length }}</span>
                </div>
                <div class="month-list">
                  <RouterLink
                    v-for="item in month.items"
                    :key="item.id"
                    :to="`/posts/${item.slug}`"
                    class="archive-item"
                  >
                    <span class="item-date">{{ formatShortDate(item.publishedAt) }}</span>
                    <span class="item-title">{{ item.title }}</span>
                    <span class="item-arrow">→</span>
                  </RouterLink>
                </div>
              </div>
            </div>
          </section>
        </div>
      </template>

      <div v-else class="card">
        <EmptyState emoji="🗓" title="还没有可以归档的故事" description="等第一篇文字落地，这里就会亮起来。" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.archive-page {
  overflow-x: clip;
}

.archive-count {
  display: inline-flex;
  padding: 7px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.9);
  color: var(--ink-soft);
  font-size: 0.76rem;
  font-weight: 800;
}

.archive-tip {
  color: #a48b5a;
  font-size: 0.74rem;
  font-weight: 700;
}

.archive-loading {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-top: 34px;
}
.year-skeleton {
  height: 220px;
}

.year-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  margin: 30px 0 20px;
}

.year-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid var(--line);
  color: var(--ink-soft);
  font-size: 0.78rem;
  font-weight: 800;
  transition: all 0.2s ease;
}

.year-pill span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 19px;
  height: 19px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--sky-soft);
  color: #5b8dab;
  font-size: 0.62rem;
}

.year-pill:hover {
  background: #fff;
  border-color: rgba(169, 216, 245, 0.7);
  transform: translateY(-1px);
}

.timeline {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 26px;
}

.year-block {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 30px;
  padding: 30px 34px;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow-sm);
  scroll-margin-top: 110px;
}

.year-side {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-right: 22px;
  border-right: 1px dashed rgba(105, 135, 160, 0.18);
}

.year-number {
  font-family: var(--font-display);
  font-size: 2.7rem;
  font-weight: 900;
  line-height: 1;
  letter-spacing: -0.05em;
  color: transparent;
  background: linear-gradient(135deg, #7db9da, #e9a8bd);
  -webkit-background-clip: text;
  background-clip: text;
}

.year-label {
  color: var(--ink-faint);
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.year-content {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.month-head {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 8px;
}

.month-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--pink);
  box-shadow: 0 0 0 4px rgba(249, 198, 213, 0.26);
}

.month-head h3 {
  font-size: 0.9rem;
  color: var(--ink);
}

.month-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: rgba(169, 216, 245, 0.18);
  color: #6b94ac;
  font-size: 0.58rem;
  font-weight: 800;
}

.month-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.archive-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 12px;
  border-radius: 12px;
  transition: background 0.2s ease, transform 0.2s ease;
}

.archive-item:hover {
  background: linear-gradient(90deg, rgba(234, 246, 254, 0.75), rgba(255, 240, 244, 0.45));
  transform: translateX(3px);
}

.item-date {
  flex: 0 0 58px;
  color: var(--ink-faint);
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.item-title {
  flex: 1;
  font-size: 0.86rem;
  font-weight: 700;
  color: var(--ink-soft);
  transition: color 0.2s ease;
}

.archive-item:hover .item-title {
  color: #4e86a7;
}

.item-arrow {
  color: var(--ink-faint);
  font-size: 0.8rem;
  opacity: 0;
  transform: translateX(-4px);
  transition: all 0.2s ease;
}

.archive-item:hover .item-arrow {
  opacity: 1;
  transform: translateX(0);
}

@media (max-width: 760px) {
  .year-block {
    grid-template-columns: 1fr;
    gap: 18px;
    padding: 24px 20px;
  }
  .year-side {
    flex-direction: row;
    align-items: baseline;
    gap: 12px;
    padding-right: 0;
    padding-bottom: 12px;
    border-right: 0;
    border-bottom: 1px dashed rgba(105, 135, 160, 0.18);
  }
  .year-number {
    font-size: 2rem;
  }
}

@media (max-width: 480px) {
  .item-date {
    flex-basis: 48px;
  }
  .archive-item {
    gap: 10px;
    padding: 9px 6px;
  }
}
</style>
