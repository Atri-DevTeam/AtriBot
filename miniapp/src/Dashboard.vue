<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref, shallowRef} from 'vue'
import App from './App.vue'
import Icon from './components/Icon.vue'
import type {Grant} from './session'
import {takeDashboardEntry} from './dashboard'
import qqLogo from './assets/qq-connect.png'

const url = new URL(window.location.href)
const entry = takeDashboardEntry(url)
window.history.replaceState(null, '', url.pathname + url.search + url.hash)

const grant = shallowRef<Grant | null>(null)
const busy = ref(false)
const phase = ref<'anonymous' | 'unbound'>('anonymous')
const errors: Record<string, string> = {
  BINDING_EXPIRED: '绑定链接已失效，请私聊机器人发送 /dash 获取新链接',
  BINDING_CONFLICT: 'QQ 账号或机器人账号已有其他绑定，无法覆盖',
  LOGIN_EXPIRED: '授权已过期，请重新登录',
  LOGIN_CANCELLED: '已取消 QQ 授权，可重新登录',
  LOGIN_FAILED: 'QQ 登录失败，请稍后重试',
  LOGIN_BUSY: '当前登录人数较多，请稍后重试',
  ACCOUNT_RESTRICTED: '当前机器人账号无法访问个人面板',
  DASHBOARD_DISABLED: '个人面板暂未开放'
}
const message = ref(!entry.valid ? errors.BINDING_EXPIRED : errors[entry.error || ''] || '')
let disposed = false
let controller: AbortController | undefined

async function request(path: string, body: unknown = {}) {
  controller = new AbortController()
  const timer = setTimeout(() => controller?.abort(), 15_000)
  try {
    const response = await fetch(`/dashboard/api/auth/${path}`, {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body),
      credentials: 'same-origin', cache: 'no-store', referrerPolicy: 'no-referrer', signal: controller.signal
    })
    if (response.status === 204) return {}
    const data = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(data.error || 'LOGIN_FAILED')
    return data
  } finally {
    clearTimeout(timer)
  }
}

function showError(error: unknown) {
  if (!disposed) message.value = errors[error instanceof Error ? error.message : ''] || '暂时无法连接，请稍后重试'
}

async function enter() {
  if (busy.value) return
  grant.value = null
  busy.value = true
  message.value = ''
  try {
    const data = await request('session')
    if (disposed) {
      if (data.access?.token) navigator.sendBeacon('/atrimeow/profile/api/session/close', data.access.token)
      return
    }
    if (data.status === 'ready' && data.access) grant.value = data.access
    else phase.value = data.status === 'unbound' ? 'unbound' : 'anonymous'
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function login() {
  if (busy.value || !entry.valid) return
  busy.value = true
  message.value = ''
  try {
    const data = await request('start', {
      userOpenId: entry.userOpenId, ticket: entry.ticket, nav: entry.nav, navAlpha: entry.navAlpha,
      mobile: window.matchMedia('(max-width: 640px)').matches
    })
    if (disposed) return
    const authorize = new URL(data.url)
    if (authorize.origin !== 'https://graph.qq.com' || authorize.pathname !== '/oauth2.0/authorize') throw new Error('LOGIN_FAILED')
    window.location.assign(authorize.toString())
  } catch (error) {
    showError(error)
    busy.value = false
  }
}

async function logout() {
  if (busy.value) return
  busy.value = true
  message.value = ''
  try {
    await request('logout')
    grant.value = null
    phase.value = 'anonymous'
  } catch (error) {
    grant.value = null
    showError(error)
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  document.title = '个人面板'
  if (!entry.binding && !entry.error) void enter()
})
onBeforeUnmount(() => {
  disposed = true
  controller?.abort()
})
</script>

<template>
  <App v-if="grant" :key="grant.token" :dashboard-grant="grant" @logout="logout" @renew="enter"/>
  <div v-else class="dashboard-entry app-shell">
    <div class="backdrop" aria-hidden="true"><i class="wash wash-lilac"></i><i class="wash wash-peach"></i></div>
    <main class="entry-layout">
      <section class="entry-card glass" :aria-busy="busy">
        <div class="entry-status"><Icon name="user"/></div>
        <p class="entry-eyebrow">个人面板</p>
        <h1>{{ entry.binding ? '绑定机器人账号' : phase === 'unbound' ? '尚未绑定机器人账号' : '使用 QQ 登录' }}</h1>
        <p v-if="entry.binding" class="entry-description">登录 QQ 后，绑定当前私聊中的机器人账号<br>绑定完成后可直接登录查看个人资料与群设置</p>
        <p v-else-if="phase === 'unbound'" class="entry-description">请私聊机器人发送 <code>/dash</code> 或 <code>/dashboard</code><br>打开机器人返回的链接完成绑定</p>
        <p v-else class="entry-description">登录已绑定的 QQ 账号，查看个人资料与群设置</p>
        <p v-if="message" class="login-message" role="alert">{{ message }}</p>
        <button v-if="phase !== 'unbound'" class="qq-login" :disabled="busy || !entry.valid" @click="login">
          <img :src="qqLogo" width="16" height="16" alt=""/>
          {{ busy ? '正在处理…' : entry.binding ? 'QQ 登录并绑定' : 'QQ 登录' }}
        </button>
        <button v-else class="text-button" :disabled="busy" @click="logout">退出当前 QQ 登录</button>
        <p v-if="entry.binding" class="entry-footnote">绑定链接有效期为 5 分钟，仅可使用一次</p>
      </section>
    </main>
  </div>
</template>

<style scoped>
.dashboard-entry .entry-layout { min-height: 100dvh; padding: 32px 20px; }
.dashboard-entry .entry-card { width: min(100%, 460px); padding: 38px 28px; }
.entry-eyebrow { margin: 20px 0 10px; color: #7b857b; font-size: 13px; letter-spacing: .12em; }
h1 { margin: 0; font-size: 23px; font-weight: 650; line-height: 1.4; }
.entry-description { line-height: 1.9; margin: 18px 0 26px; }
.qq-login { display: inline-flex; align-items: center; justify-content: center; gap: 10px; width: 100%; max-width: 280px; min-height: 44px; padding: 12px 22px; border: 1px solid #1575d2; border-radius: 10px; background: #1676d2; color: #fff; font: inherit; font-weight: 600; cursor: pointer; transition: background .18s; }
.qq-login:hover { background: #1065bb; }
.qq-login:disabled { opacity: .6; cursor: default; }
.qq-login img { flex-shrink: 0; }
.login-message { color: #9b4b3e; background: #faeeea; padding: 12px 14px; border-radius: 10px; font-size: 13px; line-height: 1.8; overflow-wrap: anywhere; }
.entry-footnote { margin: 22px 0 0; font-size: 12px; color: #7b857b; }
@media (max-width: 480px) {
  .dashboard-entry .entry-card { padding: 30px 20px; }
  h1 { font-size: 21px; }
}
</style>
