<template>
  <div class="shell">
    <div v-if="sidebarOpen" class="sidebar-backdrop show" @click="sidebarOpen = false"/>

    <aside class="sidebar" :class="{ 'sidebar--open': sidebarOpen }">
      <div class="sidebar-head">
        <button class="sidebar-close" aria-label="关闭侧边栏" @click="sidebarOpen = false">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
      <div class="brand">
        <img v-if="botOpenId && appId" class="brand-avatar" :src="`https://thirdqq.qlogo.cn/qqapp/${appId}/${botOpenId}/100`" referrerpolicy="no-referrer" />
        <div v-else class="brand-mark">A</div>
        <div>
          <h1>{{ botName }}</h1>
          <p>官方机器人WebUI</p>
        </div>
      </div>

      <nav class="side-nav">
        <button class="side-nav-item" :class="{ active: $route.path === '/' }" @click="$router.push('/'); sidebarOpen = false">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          群聊
        </button>
        <button class="side-nav-item" :class="{ active: $route.path === '/c2c' }" @click="$router.push('/c2c'); sidebarOpen = false">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
          </svg>
          私聊
        </button>
        <button class="side-nav-item" :class="{ active: $route.path === '/feedback' }" @click="$router.push('/feedback'); sidebarOpen = false">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
          </svg>
          反馈管理
        </button>
      </nav>

      <div class="side-toolbar">
        <button class="ghost-button" :disabled="loadingUsers" @click="loadUsers">刷新</button>
        <button class="ghost-button" @click="logout">退出</button>
      </div>
    </aside>
    <div class="sidebar-spacer" />

    <main class="workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <h2>私聊消息</h2>
        </div>
        <span class="status-pill topbar-status"><span class="dot ok"></span>{{ totalMessages }} 条记录</span>
      </header>

      <section class="content">
        <section class="chat-panel">
          <div class="chat-head">
            <div class="group-picker">
              <button class="group-picker-trigger" @click="dropdownOpen = !dropdownOpen; userSearch = ''">
                <span>{{ selectedUserId || '选择用户' }}</span>
                <span class="arrow" :class="{ up: dropdownOpen }">▾</span>
              </button>
              <div v-if="dropdownOpen" class="dropdown-menu">
                <input v-model="userSearch" class="dropdown-search" placeholder="搜索用户 openId…" @click.stop />
                <button v-for="user in filteredUsers" :key="user.userOpenId"
                        class="dropdown-item" :class="{ active: user.userOpenId === selectedUserId }"
                        @click="selectUser(user.userOpenId); dropdownOpen = false">
                  <span class="item-id">{{ user.userOpenId }}</span>
                </button>
              </div>
            </div>
            <div class="chat-head-right">
              <button class="info-toggle" :class="{ active: showInspector }" @click="showInspector = !showInspector" title="用户信息" aria-label="用户信息">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="9"/>
                  <line x1="12" y1="7" x2="12" y2="13"/>
                  <circle cx="12" cy="17" r="0.8" fill="currentColor" stroke="none"/>
                </svg>
              </button>
            </div>
          </div>
          <div class="message-list" ref="messageListRef" @scroll="onScroll">
            <div v-if="loadingMore" class="load-tip">加载更早的消息…</div>
            <div v-else-if="!hasMore && messages.length > 0" class="load-tip">— 没有更早的消息了 —</div>
            <div v-if="!selectedUserId" class="empty-state">请选择一个用户</div>
            <div v-else-if="loadingMessages && messages.length === 0" class="empty-state">正在加载消息</div>
            <div v-else-if="messages.length === 0" class="empty-state">暂无消息记录</div>

            <article v-for="message in orderedMessages" :key="message.id"
                     class="message" :class="{ mine: isMe(message) }">
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
                <div class="bubble"
                     @contextmenu.prevent.stop="onContextMenu($event, message)">
                  <pre v-if="message.messageType !== 2">{{ renderContent(message) }}</pre>
                  <div v-else class="md-body" v-html="renderMd(renderContent(message))"></div>
                </div>
              </div>
            </article>
          </div>

            <div v-if="replyTo" class="reply-bar">
              <span>回复 {{ replyTo.username || '...' }}</span>
              <button @click="replyTo = null">×</button>
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
              <input ref="fileInputRef" type="file" accept="image/*" style="display:none" @change="onFilePicked" />
              <label @click="$refs.fileInputRef.click()">上传</label>
            </div>
            <img v-if="pastePreview" :src="pastePreview" class="paste-preview" @click="pastePreview = null" title="点击清除" />
            <button class="primary-button" :disabled="!canSend">{{ sending ? '发送中' : '发送' }}</button>
          </form>

          <div v-if="ctxMenu.visible" class="ctx-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }">
            <button v-if="!isMe(ctxMenu.message) && ctxMenu.message.unionOpenId"
                    @click="atUser(ctxMenu.message); ctxMenu.visible = false">@ 用户</button>
            <button @click="startReply(ctxMenu.message); ctxMenu.visible = false">回复</button>
            <button @click="copyText(ctxMenu.message.content); ctxMenu.visible = false">复制</button>
            <button v-if="isMe(ctxMenu.message) && !recalledIds[ctxMenu.message.messageOpenId]"
                    class="ctx-recall"
                    @click="recallMsg(ctxMenu.message); ctxMenu.visible = false">撤回</button>
          </div>
        </section>

        <aside class="inspector" :class="{ 'inspector--show': showInspector }">
          <div class="inspector-head">
            <h3>用户信息</h3>
            <button class="inspector-close" aria-label="关闭用户信息" @click="showInspector = false">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <dl v-if="selectedUser">
            <dt>用户开放平台ID</dt>
            <dd>{{ selectedUser.userOpenId }}</dd>
          </dl>
          <!-- 权限组 -->
          <div v-if="selectedUser" class="func-box">
            <h4>权限组</h4>
            <div class="perm-roles">
              <button v-for="r in roles" :key="r" :class="['badge', 'clickable', permRole === r ? 'green' : 'gray']"
                      @click="setPermRole(r)">{{ r }}</button>
            </div>
            <h4>权限节点</h4>
            <div v-for="p in permNodes" :key="p" class="func-row">
              <span class="func-name">{{ p }}</span>
              <button class="perm-del" @click="removePerm(p)">×</button>
            </div>
            <form class="perm-add" @submit.prevent="addPerm(newPerm)">
              <input v-model="newPerm" placeholder="新权限节点" />
              <button class="primary-button" :disabled="!newPerm.trim()">添加</button>
            </form>
          </div>
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
import { LEGACY_TOKEN_KEY, API_BASE } from '../router.js'

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
const sidebarOpen = ref(false)
const showInspector = ref(false)
const replyTo = ref(null)
const ctxMenu = reactive({ visible: false, x: 0, y: 0, message: null })
const recalledIds = reactive({})
const msgType = ref('text')
const imageType = ref('url')
const pastePreview = ref(null)
const previewImg = ref(null)
const pageSize = 80
const permRole = ref('')
const permNodes = ref([])
const roles = ['USER', 'ADMIN', 'OWNER', 'BLACKLIST']
function roleLabel(r) {
  const map = { OWNER: '群主', ADMIN: '管理员', USER: '成员', BLACKLIST: '黑名单' }
  return map[r] || r
}
const newPerm = ref('')

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
  if (eventSource) eventSource.close()
  eventSource = new EventSource(`${API_BASE}/events`, { withCredentials: true })
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
    const data = await api('/config')
    appId.value = data.appId || ''
    botOpenId.value = data.botOpenId || ''
    botName.value = data.botName || 'AtriBot'
  } catch { /* ignore */ }
}

function authHeaders() {
  return { 'Content-Type': 'application/json' }
}

async function api(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: authHeaders(),
    credentials: 'same-origin',
    ...options
  })
  if (res.status === 503) {
    logout()
    throw new Error('WebUI 已关闭')
  }
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

async function logout() {
  try {
    await fetch(`${API_BASE}/auth/logout`, {
      method: 'POST',
      credentials: 'same-origin'
    })
  } catch { /* ignore */ }
  localStorage.removeItem(LEGACY_TOKEN_KEY)
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
  loadPerms()
  await loadLatestMessages()
}

async function loadPerms() {
  if (!selectedUserId.value) return
  try {
    const data = await api(`/c2c/${encodeURIComponent(selectedUserId.value)}/permissions`)
    permRole.value = data?.role || 'USER'
    permNodes.value = data?.permissions || []
  } catch { permRole.value = 'USER'; permNodes.value = [] }
}

async function setPermRole(role) {
  if (!selectedUserId.value) return
  try {
    await api(`/c2c/${encodeURIComponent(selectedUserId.value)}/role?role=${role}`, { method: 'POST' })
    permRole.value = role
  } catch (e) { notice.value = e.message }
}

async function addPerm(perm) {
  if (!selectedUserId.value || !perm?.trim()) return
  try {
    await api(`/c2c/${encodeURIComponent(selectedUserId.value)}/permissions/${encodeURIComponent(perm.trim())}?enabled=true`, { method: 'POST' })
    permNodes.value.push(perm.trim())
    newPerm.value = ''
  } catch (e) { notice.value = e.message }
}

async function removePerm(perm) {
  if (!selectedUserId.value) return
  try {
    await api(`/c2c/${encodeURIComponent(selectedUserId.value)}/permissions/${encodeURIComponent(perm)}?enabled=false`, { method: 'POST' })
    permNodes.value = permNodes.value.filter(p => p !== perm)
  } catch (e) { notice.value = e.message }
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
    if (replyTo.value) body.replyMessageId = replyTo.value.messageOpenId
    await api('/c2c/send', { method: 'POST', body: JSON.stringify(body) })
    draft.value = ''; pastePreview.value = null; replyTo.value = null; notice.value = '消息已发送'
    await loadLatestMessages()
  } catch (e) { notice.value = e.message }
  finally { sending.value = false }
}

function renderContent(message) {
  let text = message.content || ''
  text = text.replace(/<faceType=\d+,faceId="[^"]*",ext="[^"]*">/g, '[表情]')
  text = text.replace(/<qqbot-at-user id="([A-F0-9]+)"\s*\/>/g, '@$1')
  text = text.replace(/<qqbot-cmd-input[^>]*show="([^"]*)"[^>]*\/>/g, '$1')
  return text
}

function renderMd(text) {
  if (!text) return ''
  let html = text
  // 图片 ![alt #Wpx #Hpx](url) — 在 HTML 转义前处理
  html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (_, alt, url) => {
    const m = alt.match(/#(\d+)px\s*#(\d+)px/)
    let style = ''
    let cleanAlt = alt
    if (m) {
      style = `max-width:100%;max-height:320px;width:${m[1]}px;height:auto`
      cleanAlt = alt.replace(m[0], '').trim()
    }
    return `<img src="${url}" alt="${cleanAlt}" style="${style}">`
  })
  html = html.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
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

function onFilePicked(e) {
  const file = e.target.files?.[0]
  if (!file || !file.type.startsWith('image/')) return
  const reader = new FileReader()
  reader.onload = () => {
    const b64 = reader.result.split(',')[1]
    draft.value = b64
    imageType.value = 'base64'
    pastePreview.value = reader.result
  }
  reader.readAsDataURL(file)
  e.target.value = ''
}

function onContextMenu(e, message) { ctxMenu.visible = true; ctxMenu.x = e.clientX; ctxMenu.y = e.clientY; ctxMenu.message = message }

async function copyText(text) {
  try { await navigator.clipboard.writeText(text || '') } catch { /* ignore */ }
}

function startReply(message) {
  replyTo.value = message
}

async function recallMsg(message) {
  try {
    await api('/c2c/recall', {
      method: 'POST',
      body: JSON.stringify({ userOpenId: message.unionOpenId, messageId: message.messageOpenId })
    })
    recalledIds[message.messageOpenId] = true
    notice.value = '消息已撤回'
  } catch (e) {
    notice.value = e.message
  }
}

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
