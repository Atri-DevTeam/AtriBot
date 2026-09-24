<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { PageSession, takeEntry, type State } from './session'
import type { Profile } from './profile'
import Icon from './components/Icon.vue'
import ActivityPanel from './components/ActivityPanel.vue'
import NavigationDock from './components/NavigationDock.vue'
import GroupManagement from './components/GroupManagement.vue'
import introIllustration from '../../src/main/resources/official-webui/img/atri-main.png'
import goldIcon from './assets/gold.png'
const activityPanel = ref<InstanceType<typeof ActivityPanel> | null>(null)
const apiBase = (import.meta.env.VITE_API_BASE || '/atrimeow/profile/api').replace(/\/+$/, '')
const readPrivate = <T,>(path: string) => session.get<T>(path)
const writePrivate = <T,>(path: string, body: unknown) => session.post<T>(path, body)
const readImage = (refresh = false) => session.image('inventory/image', refresh)
const refreshingAll = ref(false)
async function refreshAll() {
  if (refreshingAll.value) return
  refreshingAll.value = true
  try { await Promise.allSettled([loadProfile(), activityPanel.value?.reload()]) }
  finally { refreshingAll.value = false }
}

const state = shallowRef<State>({ phase: 'entering' })
const profile = shallowRef<Profile | null>(null)
const loading = ref(false)
const loadError = ref(false)
const avatarFailed = ref(false)
const bot = shallowRef<Profile['bot']>({ name: '机器人', avatarUrl: null })
const botAvatarFailed = ref(false)
const copyNotice = ref('')
const revealed = ref(false)
const activePage = ref<'profile' | 'groups'>('profile')
const groupsOpened = ref(false)
const scrollPositions = { profile: 0, groups: 0 }
async function selectPage(page: 'profile' | 'groups') {
  if (activePage.value === page || state.value.phase !== 'ready' || !revealed.value) return
  scrollPositions[activePage.value] = window.scrollY
  activePage.value = page
  if (page === 'groups') groupsOpened.value = true
  await nextTick()
  if (state.value.phase === 'ready' && activePage.value === page) window.scrollTo({ top: scrollPositions[page], behavior: 'instant' })
}
const profileSettled = ref(false)
const recordsSettled = ref(false)
const introVisible = computed(() => state.value.phase !== 'expired' && !revealed.value)
const introStarted = performance.now()
const minimumIntro = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 650
let revealTimer: ReturnType<typeof setTimeout> | undefined
let fallbackTimer: ReturnType<typeof setTimeout> | undefined
function clearIntroTimers() {
  if (revealTimer) clearTimeout(revealTimer)
  if (fallbackTimer) clearTimeout(fallbackTimer)
}
function reveal() {
  clearIntroTimers()
  if (state.value.phase === 'ready') revealed.value = true
}
watch([profileSettled, recordsSettled], ([profileReady, recordsReady]) => {
  if (!profileReady || !recordsReady || revealed.value || state.value.phase !== 'ready') return
  if (revealTimer) clearTimeout(revealTimer)
  revealTimer = setTimeout(reveal, Math.max(0, minimumIntro - (performance.now() - introStarted)))
})
let timer: ReturnType<typeof setInterval> | undefined
let noticeTimer: ReturnType<typeof setTimeout> | undefined
const session = new PageSession({
  apiBase,
  request: window.fetch.bind(window),
  changed: (next) => {
    state.value = next
    if (next.phase === 'ready') {
      fallbackTimer = setTimeout(reveal, 2800)
      void loadProfile()
    }
    if (next.phase === 'expired') {
      clearIntroTimers()
      if (timer) clearInterval(timer)
      profile.value = null
      copyNotice.value = ''
    }
  },
  release: (token) => {
    const endpoint = `${apiBase}/session/close`
    try { if (navigator.sendBeacon(endpoint, new Blob([token], { type: 'text/plain' }))) return } catch { /* fallback */ }
    void fetch(endpoint, { method: 'POST', body: token, keepalive: true, credentials: 'omit', referrerPolicy: 'no-referrer' }).catch(() => {})
  }
})

async function loadProfile() {
  if (loading.value || state.value.phase !== 'ready') return
  loading.value = true
  try {
    const result = await session.get<Profile>('profile')
    if (result && state.value.phase === 'ready') {
      profile.value = result
      loadError.value = false
      avatarFailed.value = false
      bot.value = result.bot
      botAvatarFailed.value = false
    }
  } catch { if (state.value.phase === 'ready') loadError.value = true }
  finally { loading.value = false; profileSettled.value = true }
}

const userId = computed(() => profile.value?.userId || state.value.user?.userId || '')
const username = computed(() => profile.value?.username || '')
const hasProblem = computed(() => loadError.value || Boolean(profile.value?.unavailable.length))
const unavailable = (key: string) => loadError.value || Boolean(profile.value?.unavailable.some(item => item === key || item === 'database'))
const count = (value: number | null | undefined) => value == null ? '—' : value.toLocaleString('zh-CN')
const date = (value: string | null | undefined) => value ? value.slice(0, 10).replace(/-/g, '.') : '暂无记录'
const accountValue = (value: string | null | undefined) => unavailable('account') ? '暂时无法读取' : value || '暂无记录'
const messageNote = (key: 'privateMessages' | 'groupMessages') => unavailable(key) ? '暂时无法读取'
  : profile.value?.[key] ? `${count(profile.value[key].count)} 条已记录消息` : '暂无记录'

async function copyText(value: string, label: string) {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    if (state.value.phase !== 'ready') return
    copyNotice.value = `${label}已复制`
  } catch { if (state.value.phase === 'ready') copyNotice.value = `请长按或选中${label}复制` }
  if (noticeTimer) clearTimeout(noticeTimer)
  noticeTimer = setTimeout(() => { copyNotice.value = '' }, 2500)
}
function copyId() { return copyText(userId.value, '用户 ID ') }
function leave() {
  session.end()
  if (timer) clearInterval(timer)
}
function hidePage() {
  document.documentElement.style.visibility = 'hidden'
  leave()
}
function showPage(event: PageTransitionEvent) {
  if (event.persisted) leave()
  document.documentElement.style.visibility = ''
}
function visible() { if (document.visibilityState === 'visible') void session.heartbeat() }
onMounted(() => {
  window.addEventListener('pagehide', hidePage)
  window.addEventListener('pageshow', showPage)
  document.addEventListener('visibilitychange', visible)
  const url = new URL(window.location.href)
  const entry = takeEntry(url)
  window.history.replaceState(null, '', url.pathname + url.search + url.hash)
  timer = setInterval(() => { void session.heartbeat() }, 20_000)
  void session.open(entry.userId, entry.ticket)
})
onBeforeUnmount(() => {
  clearIntroTimers()
  leave()
  if (noticeTimer) clearTimeout(noticeTimer)
  window.removeEventListener('pagehide', hidePage)
  window.removeEventListener('pageshow', showPage)
  document.removeEventListener('visibilitychange', visible)
})
</script>

<template>
  <div class="app-shell" :class="{ 'intro-pending': introVisible, 'intro-revealed': revealed && state.phase === 'ready', 'has-dock': state.phase === 'ready' }">
    <div class="backdrop" aria-hidden="true"><i class="wash wash-lilac"></i><i class="wash wash-peach"></i></div>
    <Transition name="intro-screen">
      <div v-if="introVisible" class="intro-screen" role="status" aria-live="polite" aria-label="正在加载用户档案">
        <div class="intro-content">
          <div class="intro-art" aria-hidden="true">
            <div class="intro-portrait"><img :src="introIllustration" alt="" width="1024" height="1536" fetchpriority="high" decoding="async" /></div>
          </div>
          <p class="intro-title">用户档案</p>
          <p class="intro-caption">{{ state.phase === 'entering' ? '正在验证会话' : '正在读取资料' }}</p>
          <span class="intro-track" aria-hidden="true"><i></i></span>
        </div>
      </div>
    </Transition>
    <header class="topbar glass" :inert="introVisible" :aria-hidden="introVisible">
      <div class="topbar-inner">
      <div class="bot-brand">
        <div class="bot-avatar"><img v-if="bot.avatarUrl && !botAvatarFailed" :src="bot.avatarUrl" alt="机器人头像" referrerpolicy="no-referrer" @error="botAvatarFailed = true"><Icon v-else name="bot" /></div>
        <h1 class="bot-title"><span class="bot-name">{{ bot.name }}</span><span class="bot-subtitle">{{ activePage === 'groups' ? '群管理' : '用户档案' }}</span></h1>
      </div>
      </div>
    </header>

    <main v-if="state.phase === 'ready'" v-show="activePage === 'profile'" id="profile-page" class="dashboard" :aria-busy="loading || !revealed" :inert="!revealed || activePage !== 'profile'" :aria-hidden="!revealed || activePage !== 'profile'">
      <section class="profile-card glass">
        <div class="profile-main">
          <div class="avatar-frame">
            <img v-if="profile?.avatarUrl && !avatarFailed" :src="profile.avatarUrl" alt="用户头像" referrerpolicy="no-referrer" @error="avatarFailed = true">
            <div v-else class="avatar-fallback" role="img" aria-label="默认头像"><Icon name="user" /></div>
          </div>
          <div class="profile-identity"><h2 :class="{ skeleton: loading && !profile }">{{ username || (loading ? '读取中…' : '暂无记录') }}</h2><div class="user-id"><span class="field-label">USER ID</span><code>{{ userId }}</code><button class="copy-button" aria-label="复制用户 ID" @click="copyId"><Icon name="copy" /></button></div></div>
        </div>
      </section>

      <div v-if="hasProblem" class="notice glass" role="status"><span>部分记录暂时无法读取，已保留可用资料</span><button class="text-button" :disabled="loading" @click="loadProfile">{{ loading ? '读取中…' : '重试' }} <Icon name="refresh" :class="{ 'refresh-spinning': loading }" /></button></div>
      <div class="detail-grid profile-details">
        <section class="detail-card glass">
          <header class="section-heading"><div><span class="section-icon"><Icon name="user" /></span><h2>基本资料</h2></div></header>
          <dl class="profile-fields">
            <div><dt>用户名 <span>USERNAME</span></dt><dd :class="{ muted: !username }">{{ loading && !profile ? '读取中…' : unavailable('username') && !username ? '暂时无法读取' : username || '暂无记录' }}</dd></div>
            <div><dt>用户标识 <span>USER ID</span></dt><dd class="mono">{{ userId }}</dd></div>
            <div><dt>绑定 QQ 号</dt><dd class="mono" :class="{ muted: !profile?.account?.qqNumber }">{{ loading && !profile ? '读取中…' : unavailable('account') ? '暂时无法读取' : profile?.account?.qqNumber || '未绑定' }}</dd></div>
            <div><dt>统一账号 <span>ACCOUNT ID</span></dt><dd class="mono" :class="{ muted: !profile?.account }">{{ accountValue(profile?.account?.uuid) }}</dd></div>
            <div><dt>账号创建时间</dt><dd :class="{ muted: !profile?.account?.createdAt }">{{ unavailable('account') ? '暂时无法读取' : date(profile?.account?.createdAt) }}</dd></div>
            <div><dt>Minecraft 绑定</dt><dd class="mono" :class="{ muted: !profile?.account?.minecraftUuid }">{{ accountValue(profile?.account?.minecraftUuid) }}</dd></div>
          </dl>
        </section>
        <section class="detail-card glass activity-card">
          <header class="section-heading"><div><span class="section-icon"><Icon name="message" /></span><h2>互动记录</h2></div></header>
          <div class="activity-list">
            <div class="activity-row"><span class="activity-icon sand"><img class="gold-icon" :src="goldIcon" alt="" /></span><div><h3>金粒余额</h3><p v-if="unavailable('coins') || profile?.coins == null">{{ unavailable('coins') ? '暂时无法读取' : loading && !profile ? '读取中…' : '暂无记录' }}</p></div><span class="activity-value">{{ unavailable('coins') ? '—' : count(profile?.coins) }}<small v-if="!unavailable('coins') && profile?.coins != null">粒</small></span></div>
            <div class="activity-row"><span class="activity-icon sage"><Icon name="message" /></span><div><h3>私聊消息</h3><p>{{ messageNote('privateMessages') }}</p></div><span class="activity-date">{{ unavailable('privateMessages') ? '—' : date(profile?.privateMessages?.lastAt) }}</span></div>
            <div class="activity-row"><span class="activity-icon lilac"><Icon name="users" /></span><div><h3>群聊消息</h3><p>{{ messageNote('groupMessages') }}</p></div><span class="activity-date">{{ unavailable('groupMessages') ? '—' : date(profile?.groupMessages?.lastAt) }}</span></div>
            <div class="activity-row"><span class="activity-icon sand"><Icon name="calendar" /></span><div><h3>最近签到</h3><p>{{ unavailable('signIn') ? '暂时无法读取' : profile?.signIn ? `累计 ${count(profile.signIn.days)} 天` : '暂无记录' }}</p></div><span class="activity-date">{{ unavailable('signIn') ? '—' : date(profile?.signIn?.lastDate) }}</span></div>
          </div>
        </section>
      </div>
      <ActivityPanel ref="activityPanel" :request="readPrivate" :request-image="readImage" :collection="profile?.collection || null" @ready="recordsSettled = true" @copy="copyText($event, '分享链接')" />
      <div class="sync-line"><span>{{ loading ? '读取中…' : '' }}</span><button class="text-button" :disabled="loading || refreshingAll" :aria-busy="refreshingAll" @click="refreshAll"><Icon name="refresh" :class="{ 'refresh-spinning': loading || refreshingAll }" /> 更新记录</button></div>
    </main>

    <main v-if="state.phase === 'ready'" v-show="activePage === 'groups'" id="groups-page" class="groups-page" :inert="activePage !== 'groups'" :aria-hidden="activePage !== 'groups'">
      <GroupManagement v-if="groupsOpened" :bot="bot" :request="readPrivate" :post="writePrivate" :active="activePage === 'groups'" @copy="copyText($event, '验证指令')" />
    </main>
    <NavigationDock v-if="state.phase === 'ready' && revealed" :active="activePage" @select="selectPage" />

    <main v-if="state.phase === 'expired'" class="entry-layout" aria-live="polite">
      <section class="entry-card glass"><div class="entry-status"><Icon name="lock" /></div><h2>当前会话已过期</h2><p class="entry-description">请私聊{{bot.name}}发送 <code>/profile</code><br>获取新的会话</p></section>
    </main>
    <div v-if="copyNotice && state.phase === 'ready'" class="toast glass" role="status">{{ copyNotice }}</div>
  </div>
</template>
