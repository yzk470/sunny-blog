<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import MarkdownEditor from '@/components/admin/MarkdownEditor.vue'
import { adminApi } from '@/api/admin'
import { useToastStore } from '@/stores/toast'
import { coverOf } from '@/utils/constants'

const toast = useToastStore()
const loading = ref(true)
const saving = ref(false)

const form = reactive({
  nickname: '',
  avatar: '',
  bio: '',
  slogan: '',
  location: '',
  email: '',
  github: '',
  aboutContent: '',
  footerText: '',
})

async function load() {
  loading.value = true
  try {
    const profile = await adminApi.site()
    form.nickname = profile.nickname
    form.avatar = profile.avatar || ''
    form.bio = profile.bio || ''
    form.slogan = profile.slogan || ''
    form.location = profile.location || ''
    form.email = profile.email || ''
    form.github = profile.github || ''
    form.aboutContent = profile.aboutContent || ''
    form.footerText = profile.footerText || ''
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '资料加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.nickname.trim()) {
    toast.show('昵称不能为空', 'error')
    return
  }
  saving.value = true
  try {
    await adminApi.updateSite({
      nickname: form.nickname.trim(),
      avatar: form.avatar.trim() || undefined,
      bio: form.bio.trim() || undefined,
      slogan: form.slogan.trim() || undefined,
      location: form.location.trim() || undefined,
      email: form.email.trim() || undefined,
      github: form.github.trim() || undefined,
      aboutContent: form.aboutContent,
      footerText: form.footerText.trim() || undefined,
    })
    toast.show('站点资料已经更新，前台马上就能看到 ☀')
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-profile">
    <div class="admin-page-head">
      <div>
        <h1>站点资料</h1>
        <p>这些内容会出现在前台首页、关于页和页脚</p>
      </div>
      <div class="admin-page-actions">
        <a href="/about" target="_blank" rel="noopener noreferrer" class="btn btn-sm btn-ghost">
          查看关于页 ↗
        </a>
        <button type="button" class="btn btn-sm btn-primary" :disabled="saving" @click="save">
          <span v-if="saving" class="spinner small-spinner"></span>
          {{ saving ? '保存中…' : '保存资料' }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="admin-loading"><span class="spinner"></span>正在打开小站的抽屉…</div>

    <div v-else class="profile-layout">
      <div class="profile-main">
        <section class="admin-card admin-card-pad">
          <div class="section-title-row">
            <span class="section-emoji">☀</span>
            <div>
              <h2>基本资料</h2>
              <p>让来的人知道，这里住着怎样的人</p>
            </div>
          </div>

          <div class="profile-form">
            <div class="avatar-row">
              <img :src="coverOf(form.avatar || '/images/avatar-yuebai.png')" alt="头像预览" class="avatar-preview" />
              <div class="avatar-input">
                <label class="field">
                  <span class="field-label">头像地址 <span class="hint">本地或在线图片 URL</span></span>
                  <input v-model="form.avatar" class="input" placeholder="/images/avatar-yuebai.png" />
                </label>
                <div class="avatar-tips">
                  <button type="button" class="text-chip" @click="form.avatar = '/images/avatar-yuebai.png'">
                    使用默认头像
                  </button>
                  <span>建议使用正方形图片</span>
                </div>
              </div>
            </div>

            <div class="admin-form-grid">
              <label class="field">
                <span class="field-label">昵称 *</span>
                <input v-model="form.nickname" class="input" maxlength="40" placeholder="月白" />
              </label>
              <label class="field">
                <span class="field-label">所在城市</span>
                <input v-model="form.location" class="input" maxlength="80" placeholder="杭州" />
              </label>
            </div>

            <label class="field">
              <span class="field-label">一句话简介</span>
              <input v-model="form.bio" class="input" maxlength="300" placeholder="白天写代码，傍晚追晚霞。" />
            </label>

            <label class="field">
              <span class="field-label">站点标语</span>
              <input v-model="form.slogan" class="input" maxlength="200" placeholder="把日子过成一首明亮的诗" />
            </label>

            <div class="admin-form-grid">
              <label class="field">
                <span class="field-label">邮箱</span>
                <input v-model="form.email" class="input" type="email" maxlength="120" placeholder="hello@sunny.blog" />
              </label>
              <label class="field">
                <span class="field-label">GitHub</span>
                <input v-model="form.github" class="input" maxlength="300" placeholder="https://github.com/your-name" />
              </label>
            </div>
          </div>
        </section>

        <section class="admin-card admin-card-pad">
          <div class="section-title-row">
            <span class="section-emoji">📖</span>
            <div>
              <h2>关于页面</h2>
              <p>支持 Markdown，写下你的故事</p>
            </div>
          </div>
          <MarkdownEditor v-model="form.aboutContent" min-height="380px" />
        </section>

        <section class="admin-card admin-card-pad">
          <div class="section-title-row">
            <span class="section-emoji">🌿</span>
            <div>
              <h2>页脚文案</h2>
              <p>出现在每个页面最下方的小句子</p>
            </div>
          </div>
          <label class="field">
            <input
              v-model="form.footerText"
              class="input"
              maxlength="200"
              placeholder="愿你的每一天，都有阳光落在肩上。"
            />
          </label>
        </section>

        <div class="profile-save-bar">
          <span>改动会立即同步到前台（Redis 缓存会自动失效）</span>
          <button type="button" class="btn btn-primary" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存全部资料' }}
          </button>
        </div>
      </div>

      <aside class="profile-preview-side">
        <div class="preview-label">实时预览 · PROFILE CARD</div>
        <div class="profile-preview">
          <div class="preview-sun">☀</div>
          <img :src="coverOf(form.avatar || '/images/avatar-yuebai.png')" alt="头像预览" />
          <p class="preview-hi">HELLO, I AM</p>
          <h3>{{ form.nickname || '月白' }}</h3>
          <p class="preview-location">⌖ {{ form.location || '某个有阳光的城市' }}</p>
          <p class="preview-bio">{{ form.bio || '在代码与云朵之间，收集每一个亮晶晶的日子。' }}</p>
          <p class="preview-slogan">「{{ form.slogan || '把日子过成一首明亮的诗' }}」</p>
          <div class="preview-footer">{{ form.footerText || '愿你的每一天，都有阳光落在肩上。' }}</div>
        </div>

        <div class="preview-tip">
          <span>✎</span>
          <p>资料更新后，首页和关于页会在下一次请求时读取新内容。</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.profile-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  align-items: start;
}

.profile-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.section-title-row {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px dashed rgba(105, 135, 160, 0.14);
}

.section-emoji {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border-radius: 11px;
  background: #fff8e4;
  font-size: 1rem;
}

.section-title-row h2 {
  font-size: 0.92rem;
}

.section-title-row p {
  margin-top: 2px;
  color: var(--ink-faint);
  font-size: 0.64rem;
}

.profile-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar-preview {
  width: 84px;
  height: 84px;
  flex: 0 0 auto;
  border-radius: 26px;
  object-fit: cover;
  border: 3px solid #fff;
  box-shadow: var(--shadow-sm);
}

.avatar-input {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.avatar-tips {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--ink-faint);
  font-size: 0.6rem;
}

.text-chip {
  padding: 3px 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: #fff;
  color: var(--ink-soft);
  font-size: 0.6rem;
  font-weight: 700;
}

.profile-save-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(105, 135, 160, 0.1);
}

.profile-save-bar span {
  color: var(--ink-faint);
  font-size: 0.66rem;
}

.profile-preview-side {
  position: sticky;
  top: 96px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-label {
  color: var(--ink-faint);
  font-size: 0.58rem;
  font-weight: 900;
  letter-spacing: 0.16em;
  text-align: center;
}

.profile-preview {
  position: relative;
  padding: 26px 22px 18px;
  border-radius: 22px;
  background:
    radial-gradient(circle at 100% 0%, rgba(249, 198, 213, 0.35), transparent 38%),
    linear-gradient(150deg, #fff, #fff9ef);
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-sm);
  text-align: center;
  overflow: hidden;
}

.preview-sun {
  position: absolute;
  top: 12px;
  right: 15px;
  color: var(--butter-strong);
  font-size: 1rem;
}

.profile-preview img {
  width: 82px;
  height: 82px;
  margin: 0 auto;
  border-radius: 26px;
  border: 3px solid #fff;
  box-shadow: var(--shadow-sm);
}

.preview-hi {
  margin-top: 12px;
  color: var(--ink-faint);
  font-size: 0.54rem;
  font-weight: 900;
  letter-spacing: 0.18em;
}

.profile-preview h3 {
  margin-top: 2px;
  font-size: 1.1rem;
}

.preview-location {
  margin-top: 2px;
  color: var(--ink-faint);
  font-size: 0.64rem;
}

.preview-bio {
  margin-top: 10px;
  color: var(--ink-soft);
  font-size: 0.72rem;
  line-height: 1.7;
}

.preview-slogan {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed rgba(105, 135, 160, 0.15);
  color: #b08d9a;
  font-size: 0.64rem;
}

.preview-footer {
  margin-top: 12px;
  padding: 8px;
  border-radius: 9px;
  background: #fff8e4;
  color: #a48b5a;
  font-size: 0.6rem;
  line-height: 1.5;
}

.preview-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 13px;
  background: #fdfaf4;
  border: 1px dashed var(--line);
  color: var(--ink-faint);
  font-size: 0.64rem;
  line-height: 1.6;
}

.small-spinner {
  width: 15px;
  height: 15px;
  border-width: 2px;
}

@media (max-width: 980px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }
  .profile-preview-side {
    position: static;
    max-width: 420px;
  }
}

@media (max-width: 600px) {
  .avatar-row {
    flex-direction: column;
    align-items: flex-start;
  }
  .profile-save-bar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
