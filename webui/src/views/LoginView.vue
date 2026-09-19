<template>
  <div class="login-screen">
    <div class="login-hero">
      <img :src="atriImg" alt="Atri" class="login-char" />
    </div>
    <form class="login-panel" @submit.prevent="login">
      <label>
        <span>登录到WebUI</span>
        <input
          v-model="tokenInput"
          type="password"
          autocomplete="current-password"
          placeholder="输入 Token 以登录…"
        />
      </label>
      <button class="login-btn" :disabled="!tokenInput.trim() || loading || retrySeconds > 0">
        <svg v-if="!loading" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>
        </svg>
        {{ loading ? '验证中…' : retrySeconds > 0 ? `${retrySeconds} 秒后重试` : '登录' }}
      </button>
      <p class="login-notice" v-if="notice">{{ notice }}</p>
    </form>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { API_BASE } from '../router.js'

const router = useRouter()

const atriImg = import.meta.env.BASE_URL + 'img/atri-main.png'
const tokenInput = ref('')
const loading = ref(false)
const notice = ref('')
const retrySeconds = ref(0)
let retryTimer = null
let loginController = null
let disposed = false

onBeforeUnmount(() => {
  disposed = true
  if (retryTimer) clearTimeout(retryTimer)
  loginController?.abort()
})

function handleUnavailable(response) {
  if (response.status === 503) {
    notice.value = 'WebUI 暂不可用，请稍后再试'
    return true
  }
  if (response.status !== 429) return false

  const retryAfter = (response.headers.get('Retry-After') || '').trim()
  const seconds = /^\d+$/.test(retryAfter) ? Number(retryAfter) : (Date.parse(retryAfter) - Date.now()) / 1000
  const delay = Number.isFinite(seconds) ? Math.max(1, seconds) : 60
  const deadline = Date.now() + delay * 1000
  notice.value = '登录请求过于频繁，请等待后再试'
  if (retryTimer) clearTimeout(retryTimer)
  const update = () => {
    if (disposed) return
    retrySeconds.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
    retryTimer = retrySeconds.value > 0 ? setTimeout(update, 1000) : null
    if (!retrySeconds.value) notice.value = ''
  }
  update()
  return true
}

async function login() {
  if (disposed || loading.value || retrySeconds.value > 0) return
  const token = tokenInput.value.trim()
  if (!token) return

  loading.value = true
  const controller = new AbortController()
  loginController = controller
  notice.value = '正在验证 Token…'

  try {
    const challengeRes = await fetch(`${API_BASE}/auth/challenge`, {
      credentials: 'same-origin',
      cache: 'no-store',
      signal: controller.signal
    })
    if (disposed || controller.signal.aborted) return
    if (handleUnavailable(challengeRes)) return
    if (challengeRes.status !== 200) {
      notice.value = 'WebUI 未配置或服务不可用'
      return
    }

    const challengePayload = await challengeRes.json()
    if (disposed || controller.signal.aborted) return
    const nonce = challengePayload.data?.nonce
    if (!nonce) {
      notice.value = '认证无效'
      return
    }

    const proof = await hmacSha256Base64Url(token, nonce)
    if (disposed || controller.signal.aborted) return
    const res = await fetch(`${API_BASE}/auth/verify`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nonce, proof }),
      signal: controller.signal
    })
    if (disposed || controller.signal.aborted) return
    if (handleUnavailable(res)) return
    if (res.status === 200) {
      tokenInput.value = ''
      router.replace('/')
    } else if (res.status === 401 || res.status === 403) {
      notice.value = 'Token 无效，请重试'
    } else {
      notice.value = '认证请求未成功，请稍后重试'
    }
  } catch {
    if (!disposed && !controller.signal.aborted) notice.value = '无法连接到服务器'
  } finally {
    if (!disposed) loading.value = false
    if (loginController === controller) loginController = null
  }
}

async function hmacSha256Base64Url(secret, message) {
  const encoder = new TextEncoder()
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  )
  const signature = await crypto.subtle.sign('HMAC', key, encoder.encode(message))
  return base64Url(signature)
}

function base64Url(buffer) {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}
</script>
