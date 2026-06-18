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
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { API_BASE } from '../router.js'

const router = useRouter()

const tokenInput = ref('')
const loading = ref(false)
const notice = ref('')
const avatarUrl = ref('')
const botName = ref('AtriBot')

async function login() {
  const token = tokenInput.value.trim()
  if (!token) return

  loading.value = true
  notice.value = '正在验证 Token…'

  try {
    const challengeRes = await fetch(`${API_BASE}/auth/challenge`, {
      credentials: 'same-origin',
      cache: 'no-store'
    })
    if (challengeRes.status === 503) {
      notice.value = 'WebUI 未开启。'
      return
    }
    if (challengeRes.status !== 200) {
      notice.value = 'Token 未配置或服务不可用。'
      return
    }

    const challengePayload = await challengeRes.json()
    const nonce = challengePayload.data?.nonce
    if (!nonce) {
      notice.value = '认证挑战无效。'
      return
    }

    const proof = await hmacSha256Base64Url(token, nonce)
    const res = await fetch(`${API_BASE}/auth/verify`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nonce, proof })
    })
    if (res.status === 200) {
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
