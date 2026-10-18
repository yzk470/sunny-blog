<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  page: number
  pages: number
  total?: number
}>()

const emit = defineEmits<{ (e: 'update:page', page: number): void }>()

const visiblePages = computed<(number | string)[]>(() => {
  const total = props.pages
  const current = props.page
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const result: (number | string)[] = [1]
  const start = Math.max(2, current - 1)
  const end = Math.min(total - 1, current + 1)
  if (start > 2) result.push('...')
  for (let i = start; i <= end; i++) result.push(i)
  if (end < total - 1) result.push('...')
  result.push(total)
  return result
})

function go(page: number) {
  if (page < 1 || page > props.pages || page === props.page) return
  emit('update:page', page)
}
</script>

<template>
  <nav v-if="pages > 1" class="pagination" aria-label="分页">
    <button class="page-btn arrow" type="button" :disabled="page <= 1" @click="go(page - 1)">
      <span aria-hidden="true">←</span>
      <span class="arrow-text">上一页</span>
    </button>
    <template v-for="(item, index) in visiblePages" :key="`${item}-${index}`">
      <span v-if="item === '...'" class="page-gap">…</span>
      <button
        v-else
        class="page-btn"
        type="button"
        :class="{ active: item === page }"
        :aria-current="item === page ? 'page' : undefined"
        @click="go(Number(item))"
      >
        {{ item }}
      </button>
    </template>
    <button class="page-btn arrow" type="button" :disabled="page >= pages" @click="go(page + 1)">
      <span class="arrow-text">下一页</span>
      <span aria-hidden="true">→</span>
    </button>
    <span v-if="total !== undefined" class="page-total">共 {{ total }} 篇</span>
  </nav>
</template>

<style scoped>
.pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 42px;
}

.page-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-width: 40px;
  height: 40px;
  padding: 0 13px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--ink-soft);
  font-size: 0.84rem;
  font-weight: 800;
  transition: all 0.2s ease;
}

.page-btn:hover:not(:disabled):not(.active) {
  background: #fff;
  border-color: rgba(169, 216, 245, 0.75);
  color: var(--ink);
  transform: translateY(-1px);
}

.page-btn.active {
  background: linear-gradient(135deg, #9fd6f2, #f7c0d0);
  border-color: transparent;
  color: #fff;
  box-shadow: 0 8px 18px rgba(140, 200, 232, 0.3);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-gap {
  color: var(--ink-faint);
  padding: 0 2px;
}

.page-total {
  margin-left: 6px;
  color: var(--ink-faint);
  font-size: 0.76rem;
}

@media (max-width: 560px) {
  .arrow-text {
    display: none;
  }
  .page-total {
    width: 100%;
    text-align: center;
    margin: 4px 0 0;
  }
}
</style>
