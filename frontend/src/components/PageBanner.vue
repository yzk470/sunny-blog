<script setup lang="ts">
import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme'

const props = withDefaults(
  defineProps<{
    eyebrow?: string
    title: string
    description?: string
    compact?: boolean
  }>(),
  {
    eyebrow: '',
    description: '',
    compact: false,
  },
)

const theme = useThemeStore()
const displayEyebrow = computed(() =>
  theme.isDark ? props.eyebrow.replace(/SUNNY/gi, 'MOONLIT') : props.eyebrow,
)
</script>

<template>
  <section class="page-banner" :class="{ compact }">
    <div class="banner-inner">
      <div class="banner-copy">
        <p v-if="eyebrow" class="banner-eyebrow">
          <span class="eyebrow-sun">{{ theme.isDark ? '☾' : '☀' }}</span>
          {{ displayEyebrow }}
        </p>
        <h1 class="banner-title">{{ title }}</h1>
        <p v-if="description" class="banner-description">{{ description }}</p>
        <div v-if="$slots.default" class="banner-extra">
          <slot />
        </div>
      </div>
      <div class="banner-art" aria-hidden="true">
        <div class="art-sun"></div>
        <div class="art-cloud art-cloud-a"></div>
        <div class="art-cloud art-cloud-b"></div>
        <span class="art-sparkle s1">✦</span>
        <span class="art-sparkle s2">✦</span>
        <span class="art-star">✧</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page-banner {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-xl);
  padding: 58px 54px;
  background:
    radial-gradient(circle at 88% 0%, rgba(255, 227, 163, 0.55), transparent 34%),
    radial-gradient(circle at 8% 100%, rgba(169, 216, 245, 0.4), transparent 40%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(255, 247, 236, 0.86));
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-sm);
  margin-top: 8px;
}

.page-banner::after {
  content: "";
  position: absolute;
  inset: auto -80px -120px auto;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  border: 34px solid rgba(249, 198, 213, 0.14);
  pointer-events: none;
}

.page-banner.compact {
  padding: 40px 44px;
}

.banner-inner {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 40px;
}

.banner-copy {
  max-width: 700px;
}

.banner-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 13px;
  color: #9c7a33;
  font-size: 0.7rem;
  font-weight: 900;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.eyebrow-sun {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--butter-soft);
  border: 1px solid rgba(246, 201, 107, 0.4);
  color: var(--butter-strong);
  font-size: 0.76rem;
}

.banner-title {
  font-size: clamp(1.9rem, 4vw, 3rem);
  letter-spacing: -0.03em;
  line-height: 1.2;
}

.banner-description {
  margin-top: 13px;
  max-width: 600px;
  color: var(--ink-soft);
  font-size: 0.98rem;
  line-height: 1.85;
}

.banner-extra {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
}

.banner-art {
  position: relative;
  flex: 0 0 230px;
  height: 150px;
  pointer-events: none;
}

.art-sun {
  position: absolute;
  top: 4px;
  right: 16px;
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #fff4c8, #ffe08a 58%, #ffd178);
  box-shadow: 0 0 50px rgba(255, 210, 120, 0.38);
  animation: float-y 9s ease-in-out infinite;
}

.art-sun::after {
  content: "";
  position: absolute;
  inset: -17px;
  border-radius: 50%;
  border: 1.5px dashed rgba(246, 201, 107, 0.55);
  animation: spin-slow 28s linear infinite;
}

.art-cloud {
  position: absolute;
  height: 30px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.85);
  box-shadow: 0 12px 26px rgba(112, 142, 166, 0.08);
}

.art-cloud::before,
.art-cloud::after {
  content: "";
  position: absolute;
  border-radius: 50%;
  background: inherit;
}

.art-cloud::before {
  width: 48px;
  height: 48px;
  left: 18px;
  bottom: 5px;
}

.art-cloud::after {
  width: 34px;
  height: 34px;
  right: 20px;
  bottom: 2px;
}

.art-cloud-a {
  width: 115px;
  left: 4px;
  bottom: 28px;
  animation: drift 12s ease-in-out infinite;
}

.art-cloud-b {
  width: 88px;
  right: 0;
  bottom: 0;
  opacity: 0.7;
  animation: drift 15s ease-in-out infinite reverse;
}

.art-sparkle {
  position: absolute;
  color: var(--pink-strong);
  font-size: 0.9rem;
  opacity: 0.7;
  animation: float-soft 5s ease-in-out infinite;
}

.s1 {
  top: 18px;
  left: 30px;
}
.s2 {
  top: 86px;
  right: 4px;
  font-size: 0.65rem;
  color: var(--sky-strong);
  animation-delay: -2.4s;
}

.art-star {
  position: absolute;
  bottom: 8px;
  left: 16px;
  color: var(--butter-strong);
  font-size: 1.1rem;
}

@media (max-width: 860px) {
  .page-banner {
    padding: 38px 30px;
  }
  .banner-art {
    display: none;
  }
}

@media (max-width: 560px) {
  .page-banner,
  .page-banner.compact {
    padding: 30px 22px;
    border-radius: var(--radius-lg);
  }
  .banner-description {
    font-size: 0.88rem;
  }
}
</style>
