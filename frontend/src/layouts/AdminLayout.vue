<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import '@/styles/admin.css'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToastStore()

const sidebarOpen = ref(false)
const pageTitle = computed(() => (typeof route.meta.title === 'string' ? route.meta.title : '后台'))
const user = computed(() => auth.user)

const navGroups = [
  {
    label: 'OVERVIEW',
    items: [{ name: 'admin-dashboard', label: '数据概览', icon: '📊' }],
  },
  {
    label: 'CONTENT',
    items: [
      { name: 'admin-posts', label: '文章管理', icon: '📝' },
      { name: 'admin-comments', label: '评论管理', icon: '💬' },
      { name: 'admin-guestbook', label: '留言管理', icon: '☁️' },
    ],
  },
  {
    label: 'AGENT',
    items: [
      { name: 'admin-agent-skills', label: 'Agent 技能', icon: '🧠' },
      { name: 'admin-agent-runs', label: '执行链路', icon: '🛠️' },
      { name: 'admin-agent-artifacts', label: '产物文件', icon: '📦' },
      { name: 'admin-rag', label: '知识库', icon: '📚' },
    ],
  },
  {
    label: 'SETTINGS',
    items: [
      { name: 'admin-taxonomy', label: '分类与标签', icon: '🏷️' },
      { name: 'admin-profile', label: '站点资料', icon: '⚙️' },
    ],
  },
]

function closeSidebar() {
  sidebarOpen.value = false
}

async function logout() {
  auth.logout()
  closeSidebar()
  toast.show('已经安全退出后台', 'info')
  await router.replace({ name: 'admin-login' })
}

onMounted(async () => {
  if (auth.token) {
    const result = await auth.fetchMe()
    if (!result) {
      await router.replace({ name: 'admin-login' })
    }
  }
})

watch(
  () => route.fullPath,
  () => closeSidebar(),
)
</script>

<template>
  <div class="admin-shell">
    <div v-if="sidebarOpen" class="admin-sidebar-backdrop" @click="closeSidebar"></div>

    <aside class="admin-sidebar" :class="{ open: sidebarOpen }">
      <RouterLink to="/admin/dashboard" class="admin-brand" @click="closeSidebar">
        <span class="admin-brand-sun">☀</span>
        <span class="admin-brand-text">
          <strong>月白小筑</strong>
          <span>SUNNY ADMIN</span>
        </span>
      </RouterLink>

      <nav class="admin-nav" aria-label="后台导航">
        <div v-for="group in navGroups" :key="group.label" class="admin-nav-group">
          <p class="admin-nav-label">{{ group.label }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.name"
            :to="{ name: item.name }"
            class="admin-nav-link"
          >
            <span class="admin-nav-icon">{{ item.icon }}</span>
            {{ item.label }}
          </RouterLink>
        </div>
      </nav>

      <div class="admin-sidebar-footer">
        <div class="admin-user">
          <img :src="user?.avatar || '/images/avatar-yuebai.png'" alt="管理员头像" />
          <div class="admin-user-text">
            <strong>{{ user?.nickname || '月白' }}</strong>
            <span>{{ user?.username || 'admin' }} · 管理员</span>
          </div>
        </div>
        <div class="admin-footer-actions">
          <a href="/" target="_blank" rel="noopener noreferrer">↗ 前台</a>
          <button type="button" @click="logout">退出</button>
        </div>
      </div>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <div class="admin-topbar-left">
          <button
            type="button"
            class="admin-mobile-toggle"
            aria-label="打开后台菜单"
            @click="sidebarOpen = !sidebarOpen"
          >
            ☰
          </button>
          <div>
            <p class="admin-page-title">{{ pageTitle }}</p>
            <p class="admin-breadcrumb">后台 / {{ pageTitle }}</p>
          </div>
        </div>
        <div class="admin-topbar-right">
          <span class="admin-top-link">☀ 今天也要开心</span>
          <a href="/" target="_blank" rel="noopener noreferrer" class="admin-top-link">
            ↗ <span>查看博客</span>
          </a>
          <img
            :src="user?.avatar || '/images/avatar-yuebai.png'"
            alt="管理员头像"
            style="width: 34px; height: 34px; border-radius: 11px; border: 2px solid #fff"
          />
        </div>
      </header>

      <main class="admin-content">
        <div class="admin-content-inner">
          <RouterView v-slot="{ Component }">
            <Transition name="admin-page" mode="out-in">
              <component :is="Component" />
            </Transition>
          </RouterView>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-page-enter-active,
.admin-page-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.admin-page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.admin-page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
