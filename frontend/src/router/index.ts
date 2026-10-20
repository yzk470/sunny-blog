import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/PublicLayout.vue'),
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'posts',
        name: 'posts',
        component: () => import('@/views/PostListView.vue'),
        meta: { title: '文章' },
      },
      {
        path: 'posts/:slug',
        name: 'post-detail',
        component: () => import('@/views/PostDetailView.vue'),
        meta: { title: '文章详情' },
      },
      {
        path: 'archives',
        name: 'archives',
        component: () => import('@/views/ArchiveView.vue'),
        meta: { title: '归档' },
      },
      {
        path: 'about',
        name: 'about',
        component: () => import('@/views/AboutView.vue'),
        meta: { title: '关于' },
      },
      {
        path: 'digital-human',
        name: 'digital-human',
        component: () => import('@/views/DigitalHumanView.vue'),
        meta: { title: '3D 数字人会客厅' },
      },
      {
        path: 'werewolf',
        name: 'werewolf',
        component: () => import('@/views/WerewolfView.vue'),
        meta: { title: '狼人杀 · 月白小镇' },
      },
      {
        path: 'agent',
        name: 'agent',
        component: () => import('@/views/AgentView.vue'),
        meta: { title: '智能体 · 月白' },
      },
      {
        path: 'guestbook',
        name: 'guestbook',
        component: () => import('@/views/GuestbookView.vue'),
        meta: { title: '留言板' },
      },
    ],
  },
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/views/admin/LoginView.vue'),
    meta: { title: '后台登录' },
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true },
    redirect: { name: 'admin-dashboard' },
    children: [
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('@/views/admin/DashboardView.vue'),
        meta: { title: '数据概览', requiresAuth: true },
      },
      {
        path: 'posts',
        name: 'admin-posts',
        component: () => import('@/views/admin/PostListView.vue'),
        meta: { title: '文章管理', requiresAuth: true },
      },
      {
        path: 'posts/new',
        name: 'admin-post-new',
        component: () => import('@/views/admin/PostEditorView.vue'),
        meta: { title: '写新文章', requiresAuth: true },
      },
      {
        path: 'posts/:id/edit',
        name: 'admin-post-edit',
        component: () => import('@/views/admin/PostEditorView.vue'),
        meta: { title: '编辑文章', requiresAuth: true },
      },
      {
        path: 'comments',
        name: 'admin-comments',
        component: () => import('@/views/admin/CommentListView.vue'),
        meta: { title: '评论管理', requiresAuth: true },
      },
      {
        path: 'guestbook',
        name: 'admin-guestbook',
        component: () => import('@/views/admin/GuestbookListView.vue'),
        meta: { title: '留言管理', requiresAuth: true },
      },
      {
        path: 'taxonomy',
        name: 'admin-taxonomy',
        component: () => import('@/views/admin/TaxonomyView.vue'),
        meta: { title: '分类与标签', requiresAuth: true },
      },
      {
        path: 'profile',
        name: 'admin-profile',
        component: () => import('@/views/admin/ProfileView.vue'),
        meta: { title: '站点资料', requiresAuth: true },
      },
      {
        path: 'agent-skills',
        name: 'admin-agent-skills',
        component: () => import('@/views/admin/AgentSkillsView.vue'),
        meta: { title: 'Agent 技能', requiresAuth: true },
      },
      {
        path: 'agent-runs',
        name: 'admin-agent-runs',
        component: () => import('@/views/admin/AgentRunsView.vue'),
        meta: { title: 'Agent 执行链路', requiresAuth: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面走丢了' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, top: 96, behavior: 'smooth' }
    if (to.path === from.path) return { top: 0 }
    return { top: 0, behavior: 'smooth' }
  },
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
  if (requiresAuth && !auth.token) {
    return { name: 'admin-login', query: { redirect: to.fullPath } }
  }
  if (requiresAuth && auth.token && !auth.user) {
    const user = await auth.fetchMe()
    if (!user) {
      return { name: 'admin-login', query: { redirect: to.fullPath } }
    }
  }
  if (to.name === 'admin-login' && auth.token) {
    return { name: 'admin-dashboard' }
  }
  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : ''
  document.title = title && to.name !== 'home'
    ? `${title} · 月白小筑`
    : '月白小筑 · 把日子过成一首明亮的诗'
})

export default router
