<script setup lang="ts">
import { nextTick, onUnmounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    subtitle?: string
    width?: string
    danger?: boolean
  }>(),
  {
    title: '',
    subtitle: '',
    width: '520px',
    danger: false,
  },
)

const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()
const panel = ref<HTMLElement | null>(null)

function close() {
  emit('update:modelValue', false)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.modelValue) close()
}

watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      document.body.style.overflow = 'hidden'
      window.addEventListener('keydown', onKeydown)
      await nextTick()
      panel.value?.focus()
    } else {
      document.body.style.overflow = ''
      window.removeEventListener('keydown', onKeydown)
    }
  },
)

onUnmounted(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modelValue" class="modal-overlay" @click.self="close">
        <div
          ref="panel"
          class="modal-panel"
          role="dialog"
          aria-modal="true"
          :aria-label="title || '弹窗'"
          tabindex="-1"
          :style="{ width }"
        >
          <div class="modal-head">
            <div>
              <h3 :class="{ 'danger-title': danger }">{{ title }}</h3>
              <p v-if="subtitle" class="modal-subtitle">{{ subtitle }}</p>
            </div>
            <button type="button" class="modal-close" aria-label="关闭" @click="close">×</button>
          </div>
          <div class="modal-body">
            <slot />
          </div>
          <div v-if="$slots.footer" class="modal-footer">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(58, 66, 78, 0.28);
  backdrop-filter: blur(5px);
}

.modal-panel {
  max-width: 100%;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  border-radius: 26px;
  background: #fffdf9;
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 30px 80px rgba(70, 92, 110, 0.24);
  animation: modal-in 0.25s cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

.modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 26px 10px;
}

.modal-head h3 {
  font-size: 1.2rem;
}

.danger-title {
  color: #c75f78;
}

.modal-subtitle {
  margin-top: 6px;
  color: var(--ink-faint);
  font-size: 0.82rem;
}

.modal-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink-faint);
  font-size: 1.2rem;
  line-height: 1;
  flex: 0 0 auto;
}

.modal-close:hover {
  color: var(--ink);
  background: var(--pink-soft);
}

.modal-body {
  padding: 12px 26px 8px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 18px 26px 24px;
}

@keyframes modal-in {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
</style>
