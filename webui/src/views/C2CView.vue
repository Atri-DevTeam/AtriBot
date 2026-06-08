<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <img v-if="botOpenId && appId" class="brand-avatar" :src="`https://thirdqq.qlogo.cn/qqapp/${appId}/${botOpenId}/100`" referrerpolicy="no-referrer" />
        <div v-else class="brand-mark">A</div>
        <div>
          <h1>{{ botName }}</h1>
          <p>官方机器人WebUI</p>
        </div>
      </div>

      <div class="side-toolbar">
        <button class="ghost-button" @click="$router.push('/')">群聊</button>
        <button class="ghost-button active" @click="$router.push('/c2c')">私聊</button>
        <button class="ghost-button" :disabled="loadingUsers" @click="loadUsers">刷新</button>
        <button class="ghost-button" @click="logout">退出</button>
      </div>

    </aside>

    <main class="workspace">
      <header class="topbar">
        <div class="topbar-left">
          <h2>私聊消息</h2>
          <div class="group-picker">
            <button class="group-picker-trigger" @click="dropdownOpen = !dropdownOpen; userSearch = ''">
              <span>{{ selectedUserId || '选择用户' }}</span>
              <span class="arrow" :class="{ up: dropdownOpen }">▾</span>
            </button>
            <div v-if="dropdownOpen" class="dropdown-menu">
              <input v-model="userSearch" class="dropdown-search" placeholder="搜索用户 openId…" @click.stop />
              <button v-for="user in filteredUsers" :key="user.userOpenId"
                      class="dropdown-item"
                      :class="{ active: user.userOpenId === selectedUserId }"
                      @click="selectUser(user.userOpenId); dropdownOpen = false">
                <span class="item-id">{{ user.userOpenId }}</span>
              </button>
            </div>
          </div>
        </div>
        <div class="status-pill"><span class="dot ok"></span>{{ totalMessages }} 条记录</div>
      </header>

      <section class="content">
        <section class="chat-panel">
          <div class="chat-head"><strong>{{ selectedUserId || '未选择用户' }}</strong></div>

          <div class="message-list" ref="messageListRef" @scroll="onScroll">
            <div v-if="loadingMore" class="load-tip">加载更早的消息…</div>
            <div v-else-if="!hasMore && messages.length > 0" class="load-tip">— 没有更早的消息了 —</div>
            <div v-if="!selectedUserId" class="empty-state">请选择一个用户</div>
            <div v-else-if="loadingMessages && messages.length === 0" class="empty-state">正在加载消息</div>
            <div v-else-if="messages.length === 0" class="empty-state">暂无消息记录</div>

            <article v-for="message in orderedMessages" :key="message.id"
                     class="message" :class="{ mine: isMe(message) }"
                     @contextmenu.prevent="onContextMenu($event, message)">
              <div class="avatar">
                <img v-show="!avatarFailed[message.id] && avatarUrl(message)" :src="avatarUrl(message)"
                     :alt="message.username" referrerpolicy="no-referrer" @error="avatarFailed[message.id] = true" />
                <span v-show="!avatarUrl(message) || avatarFailed[message.id]">{{ avatarText(message) }}</span>
              </div>
              <div class="message-main">
                <div class="msg-header">
                  <template v-if="isMe(message)">
                    <svg v-if="message.senderIsBot" class="bot-icon" width="14" height="14" viewBox="0 0 64 64">
                      <line x1="32" y1="10" x2="32" y2="18" stroke="#12B7F5" stroke-width="3.5" stroke-linecap="round"/>
                      <circle cx="32" cy="8" r="4" fill="none" stroke="#12B7F5" stroke-width="3.5"/>
                      <rect x="16" y="18" width="32" height="28" rx="10" fill="none" stroke="#12B7F5" stroke-width="3.5"/>
                      <rect x="24" y="28" width="4" height="8" rx="2" fill="#12B7F5"/>
                      <rect x="36" y="28" width="4" height="8" rx="2" fill="#12B7F5"/>
                    </svg>
                    <strong class="msg-name">{{ message.username || 'AtriBot' }}</strong>
                  </template>
                  <template v-else>
                    <strong class="msg-name">{{ message.username || 'Unknown' }}</strong>
                    <svg v-if="message.senderIsBot" class="bot-icon" width="14" height="14" viewBox="0 0 64 64">
                      <line x1="32" y1="10" x2="32" y2="18" stroke="#12B7F5" stroke-width="3.5" stroke-linecap="round"/>
                      <circle cx="32" cy="8" r="4" fill="none" stroke="#12B7F5" stroke-width="3.5"/>
                      <rect x="16" y="18" width="32" height="28" rx="10" fill="none" stroke="#12B7F5" stroke-width="3.5"/>
                      <rect x="24" y="28" width="4" height="8" rx="2" fill="#12B7F5"/>
                      <rect x="36" y="28" width="4" height="8" rx="2" fill="#12B7F5"/>
                    </svg>
                  </template>
                  <span class="msg-time">{{ fmtTime(message.eventTimestamp || message.createdAt) }}</span>
                </div>
                <div class="bubble">
                  <pre v-if="message.messageType !== 2">{{ renderContent(message) }}</pre>
                  <div v-else class="md-body" v-html="renderMd(renderContent(message))"></div>
                </div>
              </div>
            </article>
          </div>

          <form class="composer" @submit.prevent="sendMessage">
            <div class="composer-type">
              <label :class="{ active: msgType === 'text' }"><input type="radio" v-model="msgType" value="text" />文本</label>
              <label :class="{ active: msgType === 'markdown' }"><input type="radio" v-model="msgType" value="markdown" />Markdown</label>
              <label :class="{ active: msgType === 'image' }"><input type="radio" v-model="msgType" value="image" />图片</label>
            </div>
            <textarea v-model="draft" :disabled="!selectedUserId || sending"
                      :placeholder="msgType === 'image' ? '图片 URL / Base64 / 直接粘贴图片' : msgType === 'markdown' ? 'Markdown 内容' : '文本消息'"
                      rows="3" @paste="onPaste"></textarea>
            <div class="composer-image-opts" v-if="msgType === 'image'">
              <label :class="{ active: imageType === 'url' }"><input type="radio" v-model="imageType" value="url" />URL</label>
              <label :class="{ active: imageType === 'base64' }"><input type="radio" v-model="imageType" value="base64" />Base64</label>
            </div>
            <img v-if="pastePreview" :src="pastePreview" class="paste-preview" @click="pastePreview = null" title="点击清除" />
            <button class="primary-button" :disabled="!canSend">{{ sending ? '发送中' : '发送' }}</button>
          </form>

          <div v-if="ctxMenu.visible" class="ctx-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }">
            <button v-if="!isMe(ctxMenu.message) && ctxMenu.message.unionOpenId"
                    @click="atUser(ctxMenu.message); ctxMenu.visible = false">@ 用户</button>
          </div>
        </section>

        <aside class="inspector">
          <h3>用户信息</h3>
          <dl v-if="selectedUser">
            <dt>用户开放平台ID</dt>
            <dd>{{ selectedUser.userOpenId }}</dd>
            <dt>角色</dt>
            <dd>{{ selectedUser.role }}</dd>
          </dl>
          <div v-else class="hint">选择用户后显示详情</div>
          <div class="log-box"><strong>请求状态</strong><p>{{ notice }}</p></div>
        </aside>
      </section>
    </main>

    <div v-if="previewImg" class="lightbox" @click="previewImg = null">
      <img :src="previewImg" referrerpolicy="no-referrer" @click.stop />
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { TOKEN_KEY, API_BASE } from '../router.js'

const router = useRouter()

const users = ref([])
const messages = ref([])
const selectedUserId = ref('')
const loadingUsers = ref(false)
const loadingMessages = ref(false)
const loadingMore = ref(false)
const sending = ref(false)
const draft = ref('')
const totalMessages = ref(0)
const notice = ref('')
const appId = ref('')
const botOpenId = ref('')
const botName = ref('AtriBot')
const avatarFailed = reactive({})
const currentPage = ref(0)
const dropdownOpen = ref(false)
const userSearch = ref('')
const ctxMenu = reactive({ visible: false, x: 0, y: 0, message: null })
const msgType = ref('text')
const imageType = ref('url')
const pastePreview = ref(null)
const previewImg = ref(null)
const pageSize = 80

const messageListRef = ref(null)
const selectedUser = computed(() => users.value.find(u => u.userOpenId === selectedUserId.value))
const filteredUsers = computed(() => {
  const q = userSearch.value.toLowerCase()
  return q ? users.value.filter(u => u.userOpenId.toLowerCase().includes(q)) : users.value
})
const hasMore = computed(() => messages.value.length < totalMessages.value)
const orderedMessages = computed(() => [...messages.value].reverse())
const canSend = computed(() => {
  if (!selectedUserId.value || !draft.value.trim() || sending.value) return false
  return true
})

let eventSource = null

onMounted(async () => {
  document.addEventListener('click', onDocumentClick)
  await loadMeta()
  await loadUsers()
  connectSse()
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
})

function connectSse() {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return
  if (eventSource) eventSource.close()
  eventSource = new EventSource(`${API_BASE}/events`)
  eventSource.onmessage = async (e) => {
    try {
      const payload = JSON.parse(e.data)
      if (payload.type !== 'c2c_refresh') return
      if (payload.userOpenId !== selectedUserId.value) return
      const data = await api(`/c2c/${encodeURIComponent(selectedUserId.value)}/messages?page=1&pageSize=${pageSize}`)
      const latest = data.records || []
      const seen = new Set(messages.value.map(m => m.messageOpenId))
      const fresh = latest.filter(m => !seen.has(m.messageOpenId))
      if (fresh.length > 0) {
        messages.value = [...fresh, ...messages.value]
        totalMessages.value = data.total || totalMessages.value
        if (isNearBottom()) { await nextTick(); scrollToBottom() }
      }
    } catch { /* ignore */ }
  }
  eventSource.onerror = () => {
    eventSource.close()
    setTimeout(connectSse, 5000)
  }
}

function isNearBottom() {
  const el = messageListRef.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function onDocumentClick(e) {
  if (!e.target.closest('.group-picker')) dropdownOpen.value = false
  if (!e.target.closest('.ctx-menu')) ctxMenu.visible = false
}

async function loadMeta() {
  try {
    const [a, n] = await Promise.all([
      fetch('/webui/meta/avatar').then(r => r.json()),
      fetch('/webui/meta/name').then(r => r.text())
    ])
    appId.value = a.appId || ''
    botOpenId.value = a.botOpenId || ''
    botName.value = n || 'AtriBot'
  } catch { /* ignore */ }
}

function authHeaders() {
  const token = localStorage.getItem(TOKEN_KEY)
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
}

async function api(path, options) {
  const res = await fetch(`${API_BASE}${path}`, { headers: authHeaders(), ...options })
  const payload = await res.json()
  if (res.status === 401) { logout(); throw new Error('未授权') }
  if (payload.status !== 200) throw new Error(payload.message || '请求失败')
  return payload.data
}

function isMe(message) {
  return message.senderIsBot || (botOpenId.value && message.unionOpenId === botOpenId.value)
}

function avatarUrl(message) {
  if (!appId.value) return null
  if (isMe(message)) return `https://thirdqq.qlogo.cn/qqapp/${appId.value}/${botOpenId.value}/640`
  if (!message.unionOpenId) return null
  return `https://thirdqq.qlogo.cn/qqapp/${appId.value}/${message.unionOpenId}/640`
}

function logout() {
  localStorage.removeItem(TOKEN_KEY)
  router.replace('/login')
}

async function loadUsers() {
  loadingUsers.value = true
  try { users.value = await api('/c2c/users') } catch (e) { notice.value = e.message }
  finally { loadingUsers.value = false }
}

async function selectUser(userOpenId) {
  if (selectedUserId.value === userOpenId) return
  selectedUserId.value = userOpenId
  messages.value = []
  totalMessages.value = 0
  currentPage.value = 0
  await loadLatestMessages()
}

async function loadLatestMessages() {
  if (!selectedUserId.value) return
  loadingMessages.value = true
  currentPage.value = 1
  try {
    const data = await api(`/c2c/${encodeURIComponent(selectedUserId.value)}/messages?page=1&pageSize=${pageSize}`)
    messages.value = data.records || []
    totalMessages.value = data.total || 0
    await nextTick(); scrollToBottom()
  } catch (e) { notice.value = e.message }
  finally { loadingMessages.value = false }
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value || loadingMessages.value) return
  loadingMore.value = true
  const el = messageListRef.value
  const prevHeight = el ? el.scrollHeight : 0
  currentPage.value++
  try {
    const data = await api(`/c2c/${encodeURIComponent(selectedUserId.value)}/messages?page=${currentPage.value}&pageSize=${pageSize}`)
    messages.value = [...messages.value, ...(data.records || [])]
    await nextTick()
    if (el) el.scrollTop = el.scrollHeight - prevHeight
  } catch (e) { notice.value = e.message; currentPage.value-- }
  finally { loadingMore.value = false }
}

function onScroll() {
  const el = messageListRef.value
  if (!el || loadingMore.value || !hasMore.value) return
  if (el.scrollTop < 60) loadMore()
}

function scrollToBottom() {
  const el = messageListRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function sendMessage() {
  if (!canSend.value) return
  sending.value = true
  try {
    const body = { userOpenId: selectedUserId.value, msgType: msgType.value, content: draft.value.trim() }
    if (msgType.value === 'markdown') body.content = body.content.replace(/@([A-F0-9]{32})/g, '<qqbot-at-user id="$1" />')
    if (msgType.value === 'image') { body.imageType = imageType.value; body.imageValue = draft.value.trim() }
    await api('/c2c/send', { method: 'POST', body: JSON.stringify(body) })
    draft.value = ''; pastePreview.value = null; notice.value = '消息已发送。'
    await loadLatestMessages()
  } catch (e) { notice.value = e.message }
  finally { sending.value = false }
}

function renderContent(message) {
  let text = message.content || ''
  return text
}

function renderMd(text) {
  if (!text) return ''
  let html = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.+?)\*/g, '<em>$1</em>')
  html = html.replace(/`(.+?)`/g, '<code>$1</code>')
  return html.replace(/\n/g, '<br>')
}

function onPaste(e) {
  if (msgType.value !== 'image') return
  const items = e.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      e.preventDefault()
      const blob = item.getAsFile()
      const reader = new FileReader()
      reader.onload = () => { draft.value = reader.result.split(',')[1]; imageType.value = 'base64'; pastePreview.value = reader.result }
      reader.readAsDataURL(blob)
      return
    }
  }
}

function onContextMenu(e, message) { ctxMenu.visible = true; ctxMenu.x = e.clientX; ctxMenu.y = e.clientY; ctxMenu.message = message }

function atUser(message) {
  const tag = `@${message.unionOpenId}`
  draft.value = draft.value ? draft.value + ' ' + tag : tag
}

function fmtTime(ts) {
  if (!ts) return ''
  const d = new Date(ts.includes('T') ? ts : ts.replace(' ', 'T'))
  if (isNaN(d.getTime())) return ts
  const pad = n => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function avatarText(message) {
  const name = message.username || (isMe(message) ? 'Bot' : '?')
  return name.slice(0, 1).toUpperCase()
}

watch(msgType, () => { pastePreview.value = null })
</script>
