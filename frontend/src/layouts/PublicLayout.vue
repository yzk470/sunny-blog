<script setup lang="ts">
import { onMounted, onUnmounted, watch } from 'vue'
import { RouterView } from 'vue-router'
import SiteHeader from '@/components/SiteHeader.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import SunnyDecor from '@/components/SunnyDecor.vue'
import { useThemeStore } from '@/stores/theme'

const theme = useThemeStore()

onMounted(() => theme.apply())
watch(
  () => theme.mode,
  () => theme.apply(),
)
onUnmounted(() => theme.clear())
</script>

<template>
  <div class="public-layout">
    <SunnyDecor />
    <SiteHeader />
    <main class="public-main">
      <RouterView v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
.public-layout {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  z-index: 1;
}

.public-main {
  position: relative;
  z-index: 1;
  flex: 1;
}

.page-enter-active,
.page-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
