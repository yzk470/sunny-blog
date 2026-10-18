<script setup lang="ts">
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const icons: Record<string, string> = {
  success: '✓',
  error: '!',
  info: 'i',
}
</script>

<template>
  <Teleport to="body">
    <TransitionGroup name="toast" tag="div" class="toast-stack" aria-live="polite">
      <div v-for="item in toast.items" :key="item.id" class="toast-item" :class="`toast-${item.type}`">
        <span class="toast-icon">{{ icons[item.type] }}</span>
        <span class="toast-text">{{ item.message }}</span>
        <button class="toast-close" type="button" aria-label="关闭提示" @click="toast.dismiss(item.id)">
          ×
        </button>
      </div>
    </TransitionGroup>
  </Teleport>
</template>

<style scoped>
.toast-stack {
  position: fixed;
  top: 24px;
  right: 24px;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: min(360px, calc(100vw - 32px));
  pointer-events: none;
}

.toast-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 13px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(16px);
  font-size: 0.86rem;
  font-weight: 700;
  line-height: 1.5;
  color: var(--ink);
  pointer-events: auto;
}

.toast-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  flex: 0 0 auto;
  font-size: 0.76rem;
  font-weight: 900;
  color: #fff;
  margin-top: 1px;
}

.toast-success .toast-icon {
  background: linear-gradient(135deg, #9ed9b9, #6cc39a);
}
.toast-error .toast-icon {
  background: linear-gradient(135deg, #f5a8b9, #e57f98);
}
.toast-info .toast-icon {
  background: linear-gradient(135deg, #a9d8f5, #77bce5);
}

.toast-text {
  flex: 1;
  padding-top: 1px;
}

.toast-close {
  border: 0;
  background: transparent;
  color: var(--ink-faint);
  font-size: 1.1rem;
  line-height: 1;
  padding: 2px;
}

.toast-close:hover {
  color: var(--ink);
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.28s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(18px) scale(0.96);
}

@media (max-width: 560px) {
  .toast-stack {
    top: 14px;
    right: 14px;
  }
}
</style>
