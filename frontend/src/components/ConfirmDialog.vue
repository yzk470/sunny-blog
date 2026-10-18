<script setup lang="ts">
import ModalDialog from './ModalDialog.vue'

withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    message?: string
    confirmText?: string
    loading?: boolean
    danger?: boolean
  }>(),
  {
    title: '确认操作',
    message: '这个操作无法撤销，确定要继续吗？',
    confirmText: '确定',
    loading: false,
    danger: true,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
}>()
</script>

<template>
  <ModalDialog
    :model-value="modelValue"
    :title="title"
    :danger="danger"
    width="420px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="confirm-message">{{ message }}</p>
    <template #footer>
      <button class="btn btn-ghost" type="button" @click="emit('update:modelValue', false)">
        再想想
      </button>
      <button
        class="btn"
        :class="danger ? 'btn-danger' : 'btn-primary'"
        type="button"
        :disabled="loading"
        @click="emit('confirm')"
      >
        <span v-if="loading" class="spinner small-spinner"></span>
        {{ confirmText }}
      </button>
    </template>
  </ModalDialog>
</template>

<style scoped>
.confirm-message {
  color: var(--ink-soft);
  font-size: 0.9rem;
  line-height: 1.8;
  padding-bottom: 6px;
}

.small-spinner {
  width: 15px;
  height: 15px;
  border-width: 2px;
}
</style>
