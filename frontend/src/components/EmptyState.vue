<script setup lang="ts">
withDefaults(
  defineProps<{
    emoji?: string
    title?: string
    description?: string
  }>(),
  {
    emoji: '🌤',
    title: '这里还空空的',
    description: '等一阵风，也等一个故事。',
  },
)
</script>

<template>
  <div class="empty-state">
    <div class="empty-emoji">{{ emoji }}</div>
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <slot />
  </div>
</template>
