<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref, shallowRef} from 'vue'
import App from './App.vue'
import Icon from './components/Icon.vue'
import type {Grant} from './session'
import {takeDashboardEntry} from './dashboard'
import qqLogo from './assets/qq-connect.png'
import illustration from './assets/atri-main.png'

const url = new URL(window.location.href)
const mobile = window.matchMedia('(max-width: 768px)')
const entry = takeDashboardEntry(url)
window.history.replaceState(null, '', url.pathname + url.search + url.hash)

const grant = shallowRef<Grant | null>(null)
const pendingGrant = shallowRef<Grant | null>(null)
const choosing = ref(false)
const webuiEnabled = ref(false)
const personalEntry = url.searchParams.get('panel') === 'personal'
const busy = ref(false)
const phase = ref<'anonymous' | 'unbound'>('anonymous')
const bindingCopyState = ref<'idle' | 'copying' | 'copied' | 'failed'>('idle')
const bot = shallowRef<{name: string; avatarUrl: string | null}>({name: '亚托莉喵', avatarUrl: null})
const botAvatarFailed = ref(false)
const botController = new AbortController()
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

async function loadBot() {
  const timer = setTimeout(() => botController.abort(), 5_000)
  try {
    const response = await fetch('/dashboard/api/bot', {credentials: 'omit', referrerPolicy: 'no-referrer', signal: botController.signal})
    if (!response.ok) return
    const data = await response.json()
    if (!disposed) bot.value = {
      name: typeof data.name === 'string' && data.name.trim() ? data.name.trim() : '亚托莉喵',
      avatarUrl: typeof data.avatarUrl === 'string' ? data.avatarUrl : null
    }
  } catch {} finally {
    clearTimeout(timer)
  }
}

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

async function copyBindingCommand() {
  if (bindingCopyState.value === 'copying') return
  bindingCopyState.value = 'copying'
  let copied = false
  try {
    await navigator.clipboard.writeText('/dash')
    copied = true
  } catch {
    const previous = document.activeElement
    const input = document.createElement('textarea')
    input.value = '/dash'
    input.readOnly = true
    input.style.cssText = 'position:fixed;left:-9999px;opacity:0'
    document.body.append(input)
    try {
      input.select()
      input.setSelectionRange(0, input.value.length)
      copied = document.execCommand('copy')
    } catch {} finally {
      input.remove()
      if (previous instanceof HTMLElement && previous.isConnected) previous.focus({preventScroll: true})
    }
  }
  if (!disposed) bindingCopyState.value = copied ? 'copied' : 'failed'
}

function showError(error: unknown) {
  if (!disposed) message.value = errors[error instanceof Error ? error.message : ''] || '暂时无法连接，请稍后重试'
}

async function enter(personal = false) {
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
    if (data.status === 'ready' && data.access) {
      webuiEnabled.value = data.webuiEnabled === true
      if (data.canManage === true && !personal && !personalEntry) {
        pendingGrant.value = data.access
        choosing.value = true
      } else grant.value = data.access
    } else {
      choosing.value = false
      phase.value = data.status === 'unbound' ? 'unbound' : 'anonymous'
    }
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

function openPersonal() {
  grant.value = pendingGrant.value
  pendingGrant.value = null
  choosing.value = false
}

function closePending() {
  if (pendingGrant.value?.token) navigator.sendBeacon('/atrimeow/profile/api/session/close', pendingGrant.value.token)
  pendingGrant.value = null
}

function openWebui() {
  if (!webuiEnabled.value) return
  closePending()
  window.location.assign(entry.qq ? '/webui/?qq=1' : '/webui/')
}

async function login() {
  if (busy.value || !entry.valid) return
  busy.value = true
  message.value = ''
  try {
    const data = await request('start', {
      userOpenId: entry.userOpenId, ticket: entry.ticket, nav: entry.nav, wwv: entry.wwv,
      mobile: mobile.matches, qq: entry.qq
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
    closePending()
    choosing.value = false
    grant.value = null
    phase.value = 'anonymous'
  } catch (error) {
    grant.value = null
    showError(error)
  } finally {
    busy.value = false
  }
}

async function initialize() {
  if (!entry.valid || entry.error) return
  if (!entry.binding) await enter()
  if (!disposed && entry.qq && !grant.value && !choosing.value && phase.value === 'anonymous' && !message.value) {
    await login()
  }
}

onMounted(() => {
  void loadBot()
  window.addEventListener('pagehide', closePending)
  void initialize()
})
onBeforeUnmount(() => {
  disposed = true
  controller?.abort()
  botController.abort()
  window.removeEventListener('pagehide', closePending)
  closePending()
})
</script>

<template>
  <App v-if="grant" :key="grant.token" :dashboard-grant="grant" @renew="enter(true)"/>
  <div v-else class="dashboard-entry" :class="{'login-entry': !choosing}">
    <header class="entry-header">
      <div class="entry-brand">
        <img v-if="bot.avatarUrl && !botAvatarFailed" class="entry-brand-avatar" :src="bot.avatarUrl" alt="机器人头像" width="36" height="36" referrerpolicy="no-referrer" @error="botAvatarFailed = true"/>
        <span v-else class="entry-brand-avatar entry-brand-fallback" aria-hidden="true">{{ bot.name.slice(0, 1) }}</span>
        <span class="entry-brand-name">{{ bot.name }}</span>
        <span class="entry-brand-divider" aria-hidden="true"></span>
        <span class="entry-brand-caption">个人中心</span>
      </div>
    </header>
    <main class="entry-layout">
      <div class="entry-surface" :class="{'workspace-surface': choosing}">
        <div class="entry-illustration" aria-hidden="true">
          <img :src="illustration" width="1024" height="1536" alt="" fetchpriority="high" decoding="async" draggable="false"/>
        </div>
        <section class="entry-card" :aria-busy="busy" aria-labelledby="entry-title">
          <Transition name="entry-panel" mode="out-in">
            <div v-if="choosing" key="workspace" class="entry-content workspace-content">
              <p class="entry-eyebrow">{{ pendingGrant?.user.displayName || '管理账号' }}</p>
              <h1 id="entry-title">选择工作台</h1>
              <div v-if="message" class="login-message" role="alert"><Icon name="info"/><span>{{ message }}</span></div>
              <div class="workspace-choices">
                <button class="workspace-choice" :disabled="busy" @click="openPersonal">
                  <span class="workspace-choice-icon"><Icon name="user"/></span>
                  <span><strong>个人面板</strong><small>个人资料与群设置</small></span>
                  <Icon class="workspace-choice-arrow" name="chevron"/>
                </button>
                <button class="workspace-choice" :disabled="busy || !webuiEnabled" @click="openWebui">
                  <span class="workspace-choice-icon"><Icon name="shield-check"/></span>
                  <span><strong>WebUI 管理</strong><small>{{ webuiEnabled ? '机器人管理工作台' : 'WebUI 未开启' }}</small></span>
                  <Icon class="workspace-choice-arrow" name="chevron"/>
                </button>
              </div>
              <button class="entry-logout" :disabled="busy" @click="logout"><Icon name="exit"/>退出登录</button>
            </div>
            <div v-else :key="entry.binding ? 'binding' : phase" class="entry-content">
              <h1 id="entry-title">{{ entry.binding ? '绑定机器人账号' : phase === 'unbound' ? '尚未绑定机器人账号' : '统一账号登陆' }}</h1>
              <p v-if="entry.binding" class="entry-description">登录 QQ 后，绑定当前私聊中的机器人账号<br>绑定完成后可直接登录查看个人资料与群设置</p>
              <p v-else-if="phase === 'unbound'" class="entry-description">请私聊机器人发送以下指令<br>打开机器人返回的链接完成绑定</p>
              <p v-else class="entry-description">请通过下方方式完成登陆</p>
              <div v-if="message" class="login-message" role="alert">
                <Icon name="info"/><span>{{ message }}</span>
              </div>
              <button v-if="phase !== 'unbound'" class="qq-login" :disabled="busy || !entry.valid" :aria-busy="busy" @click="login">
                <img :src="qqLogo" width="16" height="16" alt=""/>
                {{ entry.binding ? 'QQ 登录并绑定' : 'QQ 登录' }}
                <span v-if="busy" class="entry-spinner" aria-hidden="true"></span>
              </button>
              <template v-else>
                <button class="binding-command" aria-label="复制 /dash 指令" :disabled="bindingCopyState === 'copying'" @click="copyBindingCommand">
                  <code>/dash</code><span aria-live="polite"><Icon name="copy"/>{{ bindingCopyState === 'copied' ? '已复制' : bindingCopyState === 'copying' ? '复制中…' : '复制指令' }}</span>
                </button>
                <p v-if="bindingCopyState === 'failed'" class="binding-copy-message" role="status">复制失败，请长按上方指令复制</p>
                <button class="entry-logout" :disabled="busy" @click="logout">
                  <Icon name="exit"/>退出当前 QQ 登录
                </button>
              </template>
              <p v-if="entry.binding" class="entry-footnote">绑定链接有效期为 5 分钟，仅可使用一次</p>
            </div>
          </Transition>
          <div class="entry-card-footer"><Icon name="lock"/><span>登陆服务由QQ互联平台提供</span></div>
        </section>
      </div>
    </main>
    <footer class="entry-footer">Developed By YZ_Ljc_ & Atri-DevTeam</footer>
  </div>
</template>

<style scoped>
.dashboard-entry {
  --entry-blue: #0099ff;
  --entry-text: #1a1d23;
  --entry-muted: #767f8f;
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 100svh;
  color: var(--entry-text);
  color-scheme: light;
  background: linear-gradient(120deg, #f2f5f8 10%, #e4f1fb 48%, #f3f6fa 85%);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
.entry-header { border-bottom: 1px solid #e5eaf0; background: #ffffffb3; }
.entry-brand { display: flex; align-items: center; gap: 12px; width: min(100%, 1200px); margin: 0 auto; padding: calc(11px + env(safe-area-inset-top)) max(40px, env(safe-area-inset-right)) 11px max(40px, env(safe-area-inset-left)); }
.entry-brand-avatar { display: block; flex: 0 0 36px; width: 36px; height: 36px; border: 1px solid #e1e9f0; border-radius: 50%; object-fit: cover; }
.entry-brand-fallback { display: grid; place-items: center; color: #168cdb; background: #e3f3ff; font-size: 16px; font-weight: 500; }
.entry-brand-name { font-size: 16px; font-weight: 600; color: #263346; }
.entry-brand-divider { width: 1px; height: 16px; margin: 0 4px; background: #d8e0e8; }
.entry-brand-caption { font-size: 13px; color: var(--entry-muted); }
.dashboard-entry .entry-layout { display: grid; place-items: center; width: min(100%, 1100px); min-width: 0; min-height: 0; margin: 0 auto; padding: 36px max(40px, env(safe-area-inset-right)) 32px max(40px, env(safe-area-inset-left)); }
.entry-surface {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 430px);
  align-items: center;
  width: 100%;
  border: 1px solid #ffffffb3;
  border-radius: 20px;
  background: linear-gradient(125deg, #ffffff80, #ffffff35 55%, #ffffff70);
  box-shadow: inset 0 1px 0 #ffffffe6, inset 0 -1px 0 #e0edf7a6, 0 16px 48px #4066800d;
  -webkit-backdrop-filter: blur(22px) saturate(115%);
  backdrop-filter: blur(22px) saturate(115%);
  overflow: hidden;
}
.entry-surface::before { content: ''; position: absolute; inset: 0; border-radius: inherit; box-shadow: inset 1px 0 0 #ffffff80, inset -1px 0 0 #ffffff40; pointer-events: none; }
.entry-illustration { display: grid; place-items: center; width: 100%; min-width: 0; height: 480px; padding: 24px 24px 20px; pointer-events: none; user-select: none; background: linear-gradient(145deg, #d7efff22, #ddecf522 55%, transparent); }
.entry-illustration img { display: block; width: 100%; height: 100%; min-height: 0; object-fit: contain; }
.dashboard-entry .entry-card { position: relative; width: 100%; min-width: 0; padding: 0; border: 0; border-radius: 0; text-align: left; background: transparent; box-shadow: none; }
.entry-content { padding: 36px 38px 24px; }
.entry-eyebrow { margin: 0 0 12px; font-size: 13px; font-weight: 400; color: #7a8b9c; }
.dashboard-entry h1 { margin: 0; font-size: 26px; font-weight: 600; line-height: 1.45; letter-spacing: -.015em; color: #253449; }
.dashboard-entry .entry-description { margin: 16px 0 30px; font-size: 13px; color: var(--entry-muted); line-height: 1.9; overflow-wrap: anywhere; }
.dashboard-entry .entry-description code { padding: 3px 6px; border: 1px solid #d9e9f6; border-radius: 5px; color: #267fb7; background: #f0f8ff; font-size: 12px; white-space: nowrap; }
.qq-login, .entry-logout { display: inline-flex; align-items: center; justify-content: center; gap: 10px; width: 100%; min-height: 48px; padding: 12px 18px; border: 1px solid transparent; border-radius: 12px; font: inherit; font-size: 14px; font-weight: 500; line-height: 1.5; cursor: pointer; transition: background .18s, border-color .18s, box-shadow .18s; }
.qq-login { position: relative; color: #fff; background: var(--entry-blue); box-shadow: none; }
.qq-login:hover:not(:disabled) { background: #008eea; }
.qq-login:active:not(:disabled) { background: #0082db; }
.qq-login:disabled { opacity: 1; cursor: progress; }
.qq-login:disabled:not([aria-busy="true"]) { opacity: .45; cursor: not-allowed; }
.qq-login img { flex-shrink: 0; }
.entry-logout { min-height: 40px; color: #7b8998; background: transparent; font-size: 12px; font-weight: 400; }
.entry-logout:hover:not(:disabled) { background: #f4f8fc; color: #247fb4; }
.entry-logout:disabled { opacity: .45; cursor: not-allowed; }
.entry-logout svg { width: 15px; height: 15px; }
.binding-command { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; min-height: 44px; margin-bottom: 10px; padding: 10px 14px; border: 1px solid #d9e9f6; border-radius: 10px; color: #267fb7; background: #f0f8ff; font: inherit; text-align: left; cursor: pointer; transition: background .18s, border-color .18s; }
.binding-command:hover:not(:disabled) { background: #e6f4ff; border-color: #aad9f5; }
.binding-command code { font-size: 14px; user-select: all; }
.binding-command span { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; }
.binding-command svg { width: 14px; height: 14px; }
.binding-copy-message { margin: 0 0 10px; color: var(--entry-muted); font-size: 11px; line-height: 1.6; }
.dashboard-entry button:focus-visible { outline: 2px solid #0099ff; outline-offset: 4px; }
.workspace-surface .entry-illustration { height: 460px; }
.workspace-surface .entry-card-footer { padding: 14px 4px 16px; }
.workspace-content { padding-top: 26px; padding-bottom: 12px; }
.workspace-content .entry-eyebrow { margin-bottom: 8px; font-size: 12px; }
.dashboard-entry .workspace-content h1 { font-size: 24px; }
.workspace-choices { display: grid; gap: 10px; margin: 18px 0 12px; }
.workspace-choice { display: flex; align-items: center; gap: 12px; width: 100%; padding: 15px 16px; border: 1px solid #e0e8f0; border-radius: 12px; color: #31445a; text-align: left; cursor: pointer; background: #fafcfe; transition: background .18s, border-color .18s; }
.workspace-choice:hover:not(:disabled) { border-color: #aad9f5; background: #f0f8ff; }
.workspace-choice:active:not(:disabled) { background: #e9f5fe; }
.workspace-choice:disabled { opacity: .42; cursor: not-allowed; }
.workspace-choice-icon { display: grid; place-items: center; flex: 0 0 31px; height: 34px; color: #249bdf; }
.workspace-choice-icon svg { width: 23px; height: 23px; }
.workspace-choice > span:nth-child(2) { display: grid; gap: 4px; min-width: 0; flex: 1; }
.workspace-choice strong { font-size: 14px; font-weight: 500; }
.workspace-choice small { font-size: 11px; color: #8b99a8; }
.workspace-choice-arrow { flex: 0 0 16px; width: 16px; height: 16px; color: #a1afbc; transition: transform .18s; }
.workspace-choice:hover:not(:disabled) .workspace-choice-arrow { transform: translateX(3px); color: #369dd6; }
.login-message { display: flex; align-items: flex-start; gap: 8px; margin: 18px 0; padding: 12px; border: 1px solid #f2d7d8; border-radius: 10px; background: #fff7f7; color: #ba5b61; font-size: 12px; line-height: 1.8; text-align: left; overflow-wrap: anywhere; }
.login-message svg { flex: 0 0 16px; width: 16px; height: 16px; margin-top: 3px; }
.entry-footnote { margin: 16px 0 0; font-size: 11px; color: #94a0ad; line-height: 1.8; text-align: center; }
.entry-card-footer { display: flex; align-items: center; justify-content: center; gap: 7px; margin: 0 30px; padding: 18px 4px 22px; border-top: 1px solid #edf1f5; color: #8a99a8; font-size: 11px; line-height: 1.6; }
.entry-card-footer svg { flex: 0 0 13px; width: 13px; height: 13px; }
.entry-footer { padding: 12px 24px max(24px, env(safe-area-inset-bottom)); color: #9aa6b2; text-align: center; font-size: 11px; line-height: 1.8; }
.entry-spinner { position: absolute; right: 18px; width: 16px; height: 16px; border: 2px solid #ffffff50; border-top-color: #fff; border-radius: 50%; animation: entry-spin .8s linear infinite; }
.entry-panel-enter-active { transition: opacity .22s ease, transform .28s cubic-bezier(.2, .7, .2, 1); }
.entry-panel-leave-active { transition: opacity .14s ease; }
.entry-panel-enter-from { opacity: 0; transform: translateY(5px); }
.entry-panel-leave-to { opacity: 0; }
@keyframes entry-spin { to { transform: rotate(360deg); } }
@media (max-width: 900px) and (min-width: 761px) {
  .entry-surface { grid-template-columns: minmax(0, 1fr) minmax(0, 380px); }
  .entry-illustration { height: 440px; padding: 20px 8px; }
  .workspace-surface .entry-illustration { height: 420px; }
  .entry-content { padding-left: 28px; padding-right: 28px; }
}
@media (max-width: 760px) {
  .entry-brand { padding: calc(10px + env(safe-area-inset-top)) max(22px, env(safe-area-inset-right)) 10px max(22px, env(safe-area-inset-left)); gap: 10px; }
  .entry-brand-name { font-size: 15px; }
  .dashboard-entry .entry-layout { padding: 24px max(20px, env(safe-area-inset-right)) 24px max(20px, env(safe-area-inset-left)); }
  .entry-surface { grid-template-columns: minmax(0, 1fr); border-radius: 18px; }
  .entry-illustration { height: clamp(235px, 30svh, 290px); padding: 14px 24px 8px; }
  .workspace-surface .entry-illustration { height: clamp(210px, 27svh, 260px); }
  .entry-content { padding: 26px 26px 24px; }
  .workspace-content { padding-top: 22px; padding-bottom: 12px; }
  .dashboard-entry .workspace-content h1 { font-size: 22px; }
  .dashboard-entry h1 { font-size: 24px; }
  .entry-card-footer { margin: 0 24px; padding-bottom: 20px; }
  .entry-footer { padding-top: 4px; }
}
@media (max-width: 380px) {
  .dashboard-entry .entry-layout { padding-left: 14px; padding-right: 14px; }
  .entry-brand-caption { font-size: 12px; }
  .entry-content { padding-left: 22px; padding-right: 22px; }
}
@media (max-width: 760px) {
  .login-entry .entry-layout { padding-top: 18px; padding-bottom: 18px; }
  .login-entry .entry-illustration { height: clamp(180px, 26svh, 240px); }
  .login-entry .entry-content { padding-top: 22px; padding-bottom: 18px; }
  .login-entry h1 { font-size: 22px; }
  .login-entry .entry-description { margin: 12px 0 22px; }
  .login-entry .entry-card-footer { padding-top: 12px; padding-bottom: 16px; }
  .login-entry .entry-footer { padding-bottom: max(16px, env(safe-area-inset-bottom)); }
}
@media (max-width: 760px) and (max-height: 640px) {
  .login-entry .entry-layout { padding-top: 12px; padding-bottom: 12px; }
  .login-entry .entry-illustration { height: clamp(112px, 22svh, 156px); padding-top: 8px; padding-bottom: 4px; }
  .login-entry .entry-content { padding-top: 14px; padding-bottom: 12px; }
  .login-entry .entry-description { margin: 8px 0 12px; line-height: 1.8; }
  .login-entry .entry-footnote { margin-top: 10px; }
  .login-entry .entry-card-footer { padding-top: 10px; padding-bottom: 14px; }
  .login-entry .entry-footer { padding-bottom: max(12px, env(safe-area-inset-bottom)); }
}
@media (prefers-reduced-motion: reduce) {
  .dashboard-entry *, .dashboard-entry *::before { animation: none; transition: none; }
}
@media (prefers-reduced-transparency: reduce) {
  .entry-surface { background: #f8fbfe; -webkit-backdrop-filter: none; backdrop-filter: none; }
}
@supports not (backdrop-filter: blur(1px)) {
  .entry-surface { background: #f8fbfe; }
}
</style>
