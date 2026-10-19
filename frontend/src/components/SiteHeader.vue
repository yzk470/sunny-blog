<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useThemeStore } from '@/stores/theme'

const route = useRoute()
const theme = useThemeStore()
const scrolled = ref(false)
const menuOpen = ref(false)

const navItems = [
  { label: '首页', to: '/' },
  { label: '文章', to: '/posts' },
  { label: '归档', to: '/archives' },
  { label: '数字人', to: '/digital-human' },
  { label: '狼人杀', to: '/werewolf' },
  { label: '关于', to: '/about' },
  { label: '留言板', to: '/guestbook' },
]

function isActive(to: string): boolean {
  if (to === '/') return route.path === '/'
  return route.path.startsWith(to)
}

function onScroll() {
  scrolled.value = window.scrollY > 12
}

function closeMenu() {
  menuOpen.value = false
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  document.body.style.overflow = ''
})

watch(menuOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})

watch(
  () => route.fullPath,
  () => closeMenu(),
)
</script>

<template>
  <header class="site-header" :class="{ scrolled, 'menu-open': menuOpen }">
    <div class="container header-inner">
      <RouterLink to="/" class="brand" aria-label="月白小筑首页">
        <span class="brand-sun" aria-hidden="true"></span>
        <span class="brand-text">
          <strong>月白小筑</strong>
          <small>MOONLIT NOTES</small>
        </span>
      </RouterLink>

      <nav class="main-nav" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-link"
          :class="{ active: isActive(item.to) }"
        >
          {{ item.label }}
          <span class="nav-dot"></span>
        </RouterLink>
      </nav>

      <div class="header-actions">
        <button
          type="button"
          class="theme-toggle"
          :class="{ 'is-dark': theme.isDark }"
          :aria-label="theme.isDark ? '切换到白天模式' : '切换到黑夜模式'"
          :title="theme.isDark ? '切换到白天模式' : '切换到黑夜模式'"
          @click="theme.toggle()"
        >
          <span class="theme-toggle-icon" aria-hidden="true">{{ theme.isDark ? '☾' : '☀' }}</span>
          <span class="theme-toggle-text">{{ theme.isDark ? '黑夜' : '白天' }}</span>
        </button>
        <span class="weather-pill">
          <span class="weather-sun" :class="{ moon: theme.isDark }">{{ theme.isDark ? '☾' : '☀' }}</span>
          {{ theme.isDark ? '今夜有月' : '今日晴朗' }}
        </span>
        <RouterLink to="/admin" class="admin-entry" title="进入后台">
          <svg viewBox="0 0 24 24" width="17" height="17" aria-hidden="true">
            <path
              d="M4 20h4l10.5-10.5a2.1 2.1 0 0 0-3-3L5 17v3z"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linejoin="round"
            />
            <path d="M13.5 6.5l4 4" stroke="currentColor" stroke-width="1.8" />
          </svg>
          <span>管理</span>
        </RouterLink>
        <button
          class="menu-toggle"
          type="button"
          :aria-expanded="menuOpen"
          aria-label="打开导航菜单"
          @click="menuOpen = !menuOpen"
        >
          <span :class="{ open: menuOpen }"></span>
        </button>
      </div>
    </div>

    <Transition name="mobile-menu">
      <div v-if="menuOpen" class="mobile-menu">
        <nav class="mobile-nav" aria-label="移动端导航">
          <RouterLink
            v-for="(item, index) in navItems"
            :key="item.to"
            :to="item.to"
            class="mobile-link"
            :class="{ active: isActive(item.to) }"
            :style="{ animationDelay: `${index * 45}ms` }"
          >
            <span>{{ item.label }}</span>
            <span class="mobile-arrow">→</span>
          </RouterLink>
        </nav>
        <div class="mobile-footer">
          <span>☀ 把日子过成一首明亮的诗</span>
          <RouterLink to="/admin" @click="closeMenu">进入后台 →</RouterLink>
        </div>
      </div>
    </Transition>
  </header>
</template>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 100;
  transition: background 0.3s ease, box-shadow 0.3s ease, border-color 0.3s ease;
  border-bottom: 1px solid transparent;
}

.site-header::before {
  content: "";
  position: absolute;
  inset: 0;
  background: rgba(255, 252, 245, 0);
  backdrop-filter: blur(0);
  transition: all 0.3s ease;
  pointer-events: none;
}

.site-header.scrolled {
  border-bottom-color: rgba(105, 135, 160, 0.1);
}

.site-header.scrolled::before {
  background: rgba(255, 252, 245, 0.82);
  backdrop-filter: blur(20px) saturate(1.25);
  box-shadow: 0 10px 30px rgba(112, 142, 166, 0.06);
}

.header-inner {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  height: var(--header-height);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex: 0 0 auto;
}

.brand-sun {
  position: relative;
  display: inline-block;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: radial-gradient(circle at 34% 30%, #fff3bd, #ffd67f 66%, #ffc35f);
  box-shadow: 0 6px 16px rgba(246, 201, 107, 0.38);
  transition: transform 0.4s ease;
}

.brand-sun::after {
  content: "";
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  border: 1px dashed rgba(246, 201, 107, 0.65);
}

.brand:hover .brand-sun {
  transform: rotate(16deg) scale(1.06);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand-text strong {
  font-family: var(--font-display);
  font-size: 1.13rem;
  font-weight: 900;
  letter-spacing: 0.06em;
  color: var(--ink);
}

.brand-text small {
  margin-top: 3px;
  color: #a6adb8;
  font-size: 0.56rem;
  font-weight: 800;
  letter-spacing: 0.3em;
}

.main-nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-link {
  position: relative;
  padding: 9px 15px;
  border-radius: 999px;
  color: var(--ink-soft);
  font-size: 0.9rem;
  font-weight: 700;
  transition: color 0.2s ease, background 0.2s ease;
}

.nav-link:hover {
  color: var(--ink);
  background: rgba(255, 255, 255, 0.62);
}

.nav-link.active {
  color: #4e86a7;
  background: rgba(234, 246, 254, 0.9);
}

.nav-dot {
  position: absolute;
  left: 50%;
  bottom: 2px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--sky-strong);
  transform: translateX(-50%) scale(0);
  transition: transform 0.2s ease;
}

.nav-link.active .nav-dot {
  transform: translateX(-50%) scale(1);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
}

.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 7px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface-glass);
  color: var(--ink-soft);
  font-size: 0.72rem;
  font-weight: 800;
  white-space: nowrap;
  transition: color 0.2s ease, background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.theme-toggle:hover {
  transform: translateY(-1px);
  color: var(--ink);
  border-color: var(--line-strong);
}

.theme-toggle.is-dark {
  background: rgba(30, 41, 59, 0.78);
  border-color: rgba(191, 219, 254, 0.2);
  color: #bfdbfe;
}

.theme-toggle-icon {
  color: var(--butter-strong);
  font-size: 0.95rem;
  line-height: 1;
}

.theme-toggle.is-dark .theme-toggle-icon {
  color: #bfdbfe;
  text-shadow: 0 0 12px rgba(147, 197, 253, 0.65);
}

.weather-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border-radius: 999px;
  background: rgba(255, 248, 228, 0.9);
  border: 1px solid rgba(255, 224, 160, 0.55);
  color: #9c7a33;
  font-size: 0.74rem;
  font-weight: 800;
  white-space: nowrap;
}

.weather-sun {
  color: #f6c96b;
  font-size: 0.9rem;
  animation: spin-slow 18s linear infinite;
}

.weather-sun.moon {
  color: #bfdbfe;
  animation: none;
  text-shadow: 0 0 10px rgba(147, 197, 253, 0.6);
}

.admin-entry {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 13px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.66);
  color: var(--ink-soft);
  font-size: 0.78rem;
  font-weight: 800;
  transition: all 0.2s ease;
}

.admin-entry:hover {
  color: var(--ink);
  background: #fff;
  border-color: rgba(169, 216, 245, 0.7);
  transform: translateY(-1px);
}

.menu-toggle {
  display: none;
  width: 42px;
  height: 42px;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.7);
  position: relative;
}

.menu-toggle span,
.menu-toggle span::before,
.menu-toggle span::after {
  position: absolute;
  left: 50%;
  width: 17px;
  height: 2px;
  border-radius: 999px;
  background: var(--ink);
  transform: translateX(-50%);
  transition: all 0.25s ease;
}

.menu-toggle span {
  top: 50%;
  transform: translate(-50%, -50%);
}
.menu-toggle span::before {
  content: "";
  top: -6px;
}
.menu-toggle span::after {
  content: "";
  top: 6px;
}
.menu-toggle span.open {
  background: transparent;
}
.menu-toggle span.open::before {
  top: 0;
  transform: rotate(45deg);
}
.menu-toggle span.open::after {
  top: 0;
  transform: rotate(-45deg);
}

.mobile-menu {
  position: fixed;
  inset: var(--header-height) 0 0;
  z-index: 99;
  padding: 22px 22px 32px;
  background: rgba(255, 252, 245, 0.97);
  backdrop-filter: blur(22px);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow-y: auto;
}

.mobile-nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mobile-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 17px 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.9);
  font-size: 1.05rem;
  font-weight: 800;
  color: var(--ink);
  animation: fade-up 0.4s both;
}

.mobile-link.active {
  background: linear-gradient(120deg, #eaf6fe, #fff0f4);
  color: #4e86a7;
}

.mobile-arrow {
  color: var(--ink-faint);
}

.mobile-footer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 24px;
  color: var(--ink-faint);
  font-size: 0.82rem;
  text-align: center;
}

.mobile-footer a {
  color: #4e86a7;
  font-weight: 800;
}

.mobile-menu-enter-active,
.mobile-menu-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.mobile-menu-enter-from,
.mobile-menu-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

@media (max-width: 980px) {
  .weather-pill {
    display: none;
  }
}

@media (min-width: 821px) and (max-width: 1080px) {
  .nav-link {
    padding: 8px 9px;
    font-size: 0.82rem;
  }

  .main-nav {
    gap: 2px;
  }
}

@media (max-width: 820px) {
  .main-nav {
    display: none;
  }
  .menu-toggle {
    display: inline-block;
  }
  .admin-entry span {
    display: none;
  }
  .admin-entry {
    width: 42px;
    height: 42px;
    justify-content: center;
    padding: 0;
  }
}

@media (max-width: 560px) {
  .theme-toggle-text {
    display: none;
  }
  .theme-toggle {
    width: 40px;
    min-height: 40px;
    padding: 0;
    justify-content: center;
  }
}

@media (min-width: 821px) {
  .mobile-menu {
    display: none;
  }
}
</style>
