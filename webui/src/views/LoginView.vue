<template>
  <div class="login-screen">
    <form class="login-panel" @submit.prevent="login">
      <div class="brand login-brand">
        <img v-if="avatarUrl" class="brand-avatar" :src="avatarUrl" referrerpolicy="no-referrer" />
        <div v-else class="brand-mark">A</div>
        <div>
          <h1>{{ botName }}</h1>
          <p>官方机器人WebUI</p>
        </div>
      </div>
      <label>
        <span>访问 Token</span>
        <input
          v-model="tokenInput"
          type="password"
          autocomplete="current-password"
          placeholder="official-webui-token"
        />
      </label>
      <button class="primary-button" :disabled="!tokenInput.trim() || loading">
        {{ loading ? '验证中…' : '登录' }}
      </button>
      <p class="login-notice">{{ notice }}</p>
    </form>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { TOKEN_KEY, API_BASE } from '../router.js'

const router = useRouter()

const tokenInput = ref('')
const loading = ref(false)
const notice = ref('')
const avatarUrl = ref('')
const botName = ref('AtriBot')

onMounted(async () => {
  try {
    const [a, n] = await Promise.all([
      fetch('/webui/meta/avatar').then(r => r.json()),
      fetch('/webui/meta/name').then(r => r.text())
    ])
    if (a.appId && a.botOpenId) {
      avatarUrl.value = `https://thirdqq.qlogo.cn/qqapp/${a.appId}/${a.botOpenId}/100`
    }
    if (n) botName.value = n
  } catch { /* ignore */ }
})

async function login() {
  const token = tokenInput.value.trim()
  if (!token) return

  loading.value = true
  notice.value = '正在验证 Token…'

  try {
    const res = await fetch(`${API_BASE}/auth/verify`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (res.status === 200) {
      localStorage.setItem(TOKEN_KEY, token)
      tokenInput.value = ''
      router.replace('/')
    } else {
      notice.value = 'Token 无效，请重试。'
    }
  } catch {
    notice.value = '无法连接到服务器。'
  } finally {
    loading.value = false
  }
}
</script>
