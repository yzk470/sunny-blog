import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import './styles/main.css'
import './styles/markdown.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

window.addEventListener('sunny:auth-expired', () => {
  const auth = useAuthStore()
  auth.logout()
  const current = router.currentRoute.value
  if (current.path.startsWith('/admin') && current.name !== 'admin-login') {
    router.replace({ name: 'admin-login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
