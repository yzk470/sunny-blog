<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import '@/styles/admin.css'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToastStore()

const form = reactive({ username: 'admin', password: '' })
const showPassword = ref(false)
const error = ref('')
const submitting = ref(false)

function fillDemo() {
  form.username = 'admin'
  form.password = 'admin123'
  error.value = ''
}

async function submit() {
  error.value = ''
  if (!form.username.trim() || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  submitting.value = true
  try {
    const result = await auth.login(form.username.trim(), form.password)
    toast.show(`欢迎回来，${result.user.nickname} ☀`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target = redirect.startsWith('/admin') ? redirect : '/admin/dashboard'
    await router.replace(target)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (auth.token) router.replace({ name: 'admin-dashboard' })
})
</script>

<template>
  <div class="login-page">
    <div class="login-visual">
      <div class="visual-sky" aria-hidden="true">
        <div class="big-sun">
          <span class="sun-eye left"></span>
          <span class="sun-eye right"></span>
          <span class="sun-smile"></span>
        </div>
        <div class="login-cloud cloud-a"></div>
        <div class="login-cloud cloud-b"></div>
        <div class="login-cloud cloud-c"></div>
        <span class="sky-spark s1">✦</span>
        <span class="sky-spark s2">✦</span>
        <span class="sky-star">✧</span>
      </div>

      <div class="visual-content">
        <RouterLink to="/" class="login-brand">
          <span class="login-brand-sun">☀</span>
          <span>
            <strong>晴日小筑</strong>
            <small>SUNNY NOTES</small>
          </span>
        </RouterLink>
        <div class="visual-quote">
          <p class="quote-eyebrow">WELCOME BACK, SUNSHINE</p>
          <h1>把每一段<br /><span>好天气</span>收进文字里。</h1>
          <p class="quote-desc">
            这里是晴日小筑的后台。写下新的故事，回复远方的回声，或者只是看看今天的阳光。
          </p>
          <div class="visual-chips">
            <span>📝 文章管理</span>
            <span>💬 评论审核</span>
            <span>☁ 留言回复</span>
            <span>📊 数据概览</span>
          </div>
        </div>

        <div class="visual-bottom">
          <span>☀ 慢慢写，认真生活</span>
          <span>Vue 3 · Spring Boot · Redis</span>
        </div>
      </div>
    </div>

    <div class="login-panel">
      <div class="login-card">
        <div class="login-card-head">
          <span class="login-sun">☀</span>
          <p class="login-eyebrow">ADMIN LOGIN</p>
          <h2>欢迎回来</h2>
          <p class="login-sub">登录后继续打理你的小花园。</p>
        </div>

        <form class="login-form" @submit.prevent="submit">
          <label class="field">
            <span class="field-label">用户名</span>
            <div class="input-with-icon">
              <span class="input-icon">◯</span>
              <input
                v-model="form.username"
                class="input"
                autocomplete="username"
                placeholder="请输入用户名"
              />
            </div>
          </label>

          <label class="field">
            <span class="field-label">
              密码
              <button type="button" class="show-password" @click="showPassword = !showPassword">
                {{ showPassword ? '隐藏' : '显示' }}
              </button>
            </span>
            <div class="input-with-icon">
              <span class="input-icon">✦</span>
              <input
                v-model="form.password"
                class="input"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
                @keyup.enter="submit"
              />
            </div>
          </label>

          <p v-if="error" class="login-error">
            <span>!</span>
            {{ error }}
          </p>

          <button class="btn btn-primary btn-block login-submit" type="submit" :disabled="submitting">
            <span v-if="submitting" class="spinner small-spinner"></span>
            {{ submitting ? '正在推开门…' : '进入后台 ☀' }}
          </button>
        </form>

        <div class="demo-hint">
          <div>
            <strong>演示账号</strong>
            <p>admin / admin123</p>
          </div>
          <button type="button" class="demo-fill" @click="fillDemo">一键填入</button>
        </div>

        <div class="login-footer">
          <RouterLink to="/">← 回到博客首页</RouterLink>
          <span>忘记密码？请查看 README 重置</span>
        </div>
      </div>

      <p class="login-copyright">© {{ new Date().getFullYear() }} 晴日小筑 · 愿今天也有好心情</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  background: #fffdf8;
}

/* Left visual */
.login-visual {
  position: relative;
  overflow: hidden;
  min-height: 100vh;
  background:
    radial-gradient(circle at 78% 16%, rgba(255, 227, 163, 0.65), transparent 28%),
    radial-gradient(circle at 10% 88%, rgba(169, 216, 245, 0.55), transparent 36%),
    linear-gradient(145deg, #fff8e9, #ffeef3 55%, #eaf6fe);
}

.visual-sky {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.big-sun {
  position: absolute;
  top: 12%;
  right: 10%;
  width: 170px;
  height: 170px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #fff4c8, #ffe08a 58%, #ffd178);
  box-shadow: 0 0 80px rgba(255, 210, 120, 0.45);
  animation: float-y 9s ease-in-out infinite;
}

.big-sun::before {
  content: "";
  position: absolute;
  inset: -24px;
  border-radius: 50%;
  border: 2px dashed rgba(246, 201, 107, 0.5);
  animation: spin-slow 34s linear infinite;
}

.sun-eye {
  position: absolute;
  top: 68px;
  width: 7px;
  height: 8px;
  border-radius: 50%;
  background: rgba(140, 108, 55, 0.55);
}
.sun-eye.left {
  left: 58px;
}
.sun-eye.right {
  right: 58px;
}
.sun-smile {
  position: absolute;
  left: 50%;
  top: 83px;
  width: 22px;
  height: 11px;
  transform: translateX(-50%);
  border-bottom: 3px solid rgba(140, 108, 55, 0.45);
  border-radius: 0 0 30px 30px;
}

.login-cloud {
  position: absolute;
  height: 40px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.6);
  animation: drift 18s ease-in-out infinite;
}

.login-cloud::before,
.login-cloud::after {
  content: "";
  position: absolute;
  border-radius: 50%;
  background: inherit;
}

.login-cloud::before {
  width: 66px;
  height: 66px;
  left: 22px;
  bottom: 8px;
}
.login-cloud::after {
  width: 44px;
  height: 44px;
  right: 24px;
  bottom: 4px;
}

.cloud-a {
  width: 150px;
  top: 34%;
  left: 6%;
}
.cloud-b {
  width: 110px;
  top: 58%;
  right: 4%;
  opacity: 0.7;
  animation-delay: -6s;
}
.cloud-c {
  width: 90px;
  bottom: 19%;
  left: 12%;
  opacity: 0.55;
  animation-delay: -11s;
}

.sky-spark {
  position: absolute;
  color: rgba(238, 155, 182, 0.7);
  animation: float-soft 5s ease-in-out infinite;
}
.s1 {
  top: 30%;
  right: 30%;
}
.s2 {
  bottom: 30%;
  left: 40%;
  font-size: 0.8rem;
  color: rgba(119, 188, 229, 0.75);
  animation-delay: -2s;
}

.sky-star {
  position: absolute;
  top: 47%;
  left: 10%;
  color: var(--butter-strong);
  font-size: 1.2rem;
}

.visual-content {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 38px 52px 30px;
}

.login-brand {
  display: inline-flex;
  align-items: center;
  gap: 11px;
  width: fit-content;
}

.login-brand-sun {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: linear-gradient(135deg, #ffe9a8, #ffcd73);
  color: #a37a2d;
  font-size: 1.15rem;
  box-shadow: 0 8px 20px rgba(246, 201, 107, 0.3);
}

.login-brand span:last-child {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.login-brand strong {
  font-size: 1.08rem;
  font-weight: 900;
  letter-spacing: 0.05em;
}

.login-brand small {
  margin-top: 3px;
  color: #b0a69b;
  font-size: 0.54rem;
  font-weight: 900;
  letter-spacing: 0.26em;
}

.visual-quote {
  max-width: 520px;
  margin-top: auto;
  margin-bottom: auto;
  padding: 40px 0;
}

.quote-eyebrow {
  color: #a98c5e;
  font-size: 0.66rem;
  font-weight: 900;
  letter-spacing: 0.22em;
}

.visual-quote h1 {
  margin-top: 14px;
  font-size: clamp(2.2rem, 3.6vw, 3.3rem);
  line-height: 1.28;
  letter-spacing: -0.04em;
}

.visual-quote h1 span {
  position: relative;
  color: #4d84a3;
}

.visual-quote h1 span::after {
  content: "";
  position: absolute;
  left: -2%;
  right: -2%;
  bottom: 4px;
  height: 13px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(255, 227, 163, 0.85), rgba(249, 198, 213, 0.75));
  z-index: -1;
}

.quote-desc {
  max-width: 430px;
  margin-top: 18px;
  color: #7c7b80;
  font-size: 0.9rem;
  line-height: 1.95;
}

.visual-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 24px;
}

.visual-chips span {
  padding: 7px 13px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.9);
  color: #7b7880;
  font-size: 0.7rem;
  font-weight: 700;
  backdrop-filter: blur(6px);
}

.visual-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 18px;
  border-top: 1px dashed rgba(120, 120, 125, 0.18);
  color: #a09a95;
  font-size: 0.66rem;
}

/* Right panel */
.login-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 32px;
  position: relative;
}

.login-card {
  width: min(390px, 100%);
}

.login-card-head {
  text-align: center;
}

.login-sun {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 16px;
  background: var(--butter-soft);
  color: var(--butter-strong);
  font-size: 1.3rem;
  margin-bottom: 14px;
}

.login-eyebrow {
  color: #b0b6c0;
  font-size: 0.6rem;
  font-weight: 900;
  letter-spacing: 0.24em;
}

.login-card-head h2 {
  margin-top: 7px;
  font-size: 1.8rem;
}

.login-sub {
  margin-top: 6px;
  color: var(--ink-faint);
  font-size: 0.8rem;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 28px;
}

.input-with-icon {
  position: relative;
}

.input-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: #b3bac4;
  font-size: 0.8rem;
}

.input-with-icon .input {
  padding-left: 38px;
  height: 46px;
  border-radius: 12px;
  background: #fff;
}

.show-password {
  border: 0;
  background: transparent;
  color: #7fa7bd;
  font-size: 0.66rem;
  font-weight: 800;
  padding: 0;
}

.login-error {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 12px;
  border-radius: 10px;
  background: #fff0f2;
  color: #c75f78;
  font-size: 0.74rem;
  font-weight: 700;
}

.login-error span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #f3b6c4;
  color: #fff;
  font-size: 0.6rem;
  font-weight: 900;
}

.login-submit {
  height: 46px;
  margin-top: 4px;
}

.small-spinner {
  width: 16px;
  height: 16px;
  border-width: 2px;
}

.demo-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 22px;
  padding: 13px 16px;
  border-radius: 13px;
  background: #fff9ec;
  border: 1px dashed rgba(246, 201, 107, 0.4);
}

.demo-hint strong {
  color: #9c7a33;
  font-size: 0.68rem;
}

.demo-hint p {
  margin-top: 1px;
  color: #a48b5a;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 0.72rem;
}

.demo-fill {
  padding: 5px 11px;
  border: 1px solid rgba(246, 201, 107, 0.5);
  border-radius: 999px;
  background: #fff;
  color: #9c7a33;
  font-size: 0.64rem;
  font-weight: 800;
}

.demo-fill:hover {
  background: var(--butter);
}

.login-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 24px;
  color: var(--ink-faint);
  font-size: 0.66rem;
}

.login-footer a {
  color: #6d9bb5;
  font-weight: 700;
}

.login-footer a:hover {
  color: #4e86a7;
}

.login-copyright {
  margin-top: 32px;
  color: #b3b9c1;
  font-size: 0.62rem;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }
  .login-visual {
    min-height: auto;
    padding-bottom: 10px;
  }
  .visual-content {
    min-height: auto;
    padding: 24px 24px 30px;
    gap: 28px;
  }
  .big-sun {
    width: 110px;
    height: 110px;
    top: 8%;
    right: 8%;
  }
  .sun-eye {
    top: 42px;
  }
  .sun-eye.left {
    left: 36px;
  }
  .sun-eye.right {
    right: 36px;
  }
  .sun-smile {
    top: 54px;
  }
  .visual-quote {
    padding: 10px 0;
  }
  .visual-quote h1 {
    font-size: 2rem;
  }
  .quote-desc {
    font-size: 0.82rem;
  }
  .visual-bottom {
    display: none;
  }
  .login-panel {
    padding: 34px 24px 50px;
  }
}
</style>
