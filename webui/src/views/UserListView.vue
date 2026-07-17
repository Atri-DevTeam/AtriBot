<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="loading" @click="fetchList">刷新</button>
        <button class="ghost-button" @click="logout">退出</button>
      </template>
    </AppSidebar>
    <div class="sidebar-spacer" />

    <main class="workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <h2 class="feedback-title">用户列表</h2>
        </div>
      </header>

      <section class="content userlist-layout">
        <div class="userlist-search-bar">
          <input
            v-model="searchText"
            class="userlist-search-input"
            type="text"
            placeholder="搜索用户名 / 用户ID / 群ID / 聊天内容..."
            @keyup.enter="doSearch"
          />
          <button class="primary-button" :disabled="loading" @click="doSearch">搜索</button>
          <button v-if="searchText" class="ghost-button" @click="clearSearch">清除</button>
        </div>

        <section class="chat-panel userlist-panel">
          <div class="chat-head">
            <strong>全部群消息</strong>
            <div class="userlist-top-pager">
              <button class="pager-arrow" :disabled="page <= 1" @click="goPage(page - 1)">◂</button>
              <span>{{ page }} / {{ totalPages }}</span>
              <button class="pager-arrow" :disabled="page >= totalPages" @click="goPage(page + 1)">▸</button>
            </div>
            <span class="status-pill" style="margin-left:auto"><span class="dot ok"></span>{{ total }} 条记录</span>
          </div>
          <div class="userlist-content">
            <div v-if="loading" class="empty-state">加载中...</div>
            <div v-else-if="error" class="empty-state error">{{ error }}</div>
            <div v-else-if="items.length === 0" class="empty-state">暂无数据</div>

            <div v-else class="userlist-list">
              <article v-for="item in items" :key="`${item.groupOpenId}-${item.unionOpenId}-${item.createdAt}`" class="userlist-card">
                <img
                  class="userlist-avatar"
                  :src="`https://thirdqq.qlogo.cn/qqapp/${appId}/${item.unionOpenId}/100`"
                  referrerpolicy="no-referrer"
                  loading="lazy"
                  @error="$event.target.style.display='none'"
                />
                <div class="userlist-info">
                  <div class="userlist-row1">
                    <span class="userlist-name">{{ item.username || 'Unknown' }}</span>
                    <span v-if="item.memberRole" class="userlist-role" :class="roleClass(item.memberRole)">{{ roleLabel(item.memberRole) }}</span>
                    <span v-if="item.userRole && item.userRole !== '-'" class="userlist-perm" :class="permClass(item.userRole)">{{ item.userRole }}</span>
                  </div>
                  <div class="userlist-row2">
                    <span class="userlist-id" :title="item.unionOpenId">UID: {{ item.unionOpenId || '-' }}</span>
                    <span class="userlist-gid" :title="item.groupOpenId">GID: {{ item.groupOpenId || '-' }}</span>
                    <span v-if="item.eventTimestamp" class="userlist-time">{{ formatTime(item.eventTimestamp) }}</span>
                  </div>
                  <div class="userlist-row3">
                    <span class="userlist-msg">{{ renderContent(item) || '(最近一条消息无文本内容)' }}</span>
                  </div>
                </div>
                <div class="userlist-actions">
                  <button v-if="item.unionOpenId"
                    class="userlist-action-btn"
                    title="更改用户信息"
                    @click="openPermModal(item.unionOpenId)">⚙</button>
                  <button v-if="item.groupOpenId"
                    class="userlist-action-btn"
                    title="跳转到群"
                    @click="$router.push({ path: '/', query: { group: item.groupOpenId } })">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                    </svg>
                  </button>
                </div>
              </article>
            </div>

          </div>
        </section>
      </section>
    </main>

    <!-- 更改信息弹窗 -->
    <div v-if="showPermModal" class="modal-backdrop" @click="showPermModal = false">
      <div class="modal" @click.stop>
        <div class="modal-head">
          <h2>更改信息</h2>
          <button class="icon-button" @click="showPermModal = false">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="modal-body">
          <p class="perm-uid">{{ permTarget }}</p>
          <div class="perm-roles">
            <button v-for="r in roles" :key="r" :class="['badge', 'clickable', pendingPermRole === r ? 'green' : 'gray']"
                    @click="pendingPermRole = r">{{ r }}</button>
          </div>
          <h4 style="margin:10px 0 4px">权限节点</h4>
          <div v-for="p in pendingPermNodes" :key="p" class="func-row">
            <span class="func-name">{{ p }}</span>
            <button class="perm-del" @click="removePermNode(p)">×</button>
          </div>
          <form class="perm-add" @submit.prevent="addPermNode(newPermNode)">
            <input v-model="newPermNode" placeholder="新权限节点" />
            <button class="primary-button" :disabled="!newPermNode.trim()">添加</button>
          </form>
          <h4 style="margin:10px 0 4px">状态</h4>
          <label class="checkbox-label">
            <input type="checkbox" v-model="pendingIsBlocked" />
            拉黑（禁止使用指令）
          </label>
          <label class="checkbox-label">
            <input type="checkbox" v-model="pendingIsIgnored" />
            屏蔽（静默忽略所有交互）
          </label>
          <label class="checkbox-label">
            <input type="checkbox" v-model="pendingC2CPush" />
            主动消息
          </label>
        </div>
        <div class="modal-foot">
          <button class="ghost-button" @click="showPermModal = false">关闭</button>
          <button class="primary-button" @click="confirmPerm(); showPermModal = false">确认</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { API_BASE } from '../router.js'
import { renderFaceTags } from '../messageRender.js'
import AppSidebar from '../components/AppSidebar.vue'

const router = useRouter()

const botName = ref('AtriBot')
const appId = ref('')
const botOpenId = ref('')

const sidebarOpen = ref(false)
const loading = ref(false)
const error = ref('')
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const searchText = ref('')
const currentSearch = ref('')

// perm modal
const showPermModal = ref(false)
const permTarget = ref('')
const pendingPermRole = ref('')
const pendingPermNodes = ref([])
const pendingIsBlocked = ref(false)
const pendingIsIgnored = ref(false)
const pendingC2CPush = ref(true)
const newPermNode = ref('')
const roles = ['USER', 'ADMIN', 'OWNER']

async function openPermModal(unionOpenId) {
  permTarget.value = unionOpenId
  try {
    const data = await api(`/c2c/${encodeURIComponent(unionOpenId)}/permissions`)
    pendingPermRole.value = data?.role || 'USER'
    pendingPermNodes.value = [...(data?.permissions || [])]
    pendingIsBlocked.value = data?.isBlocked || false
    pendingIsIgnored.value = data?.isIgnored || false
    pendingC2CPush.value = data?.c2cPush !== false
  } catch { pendingPermRole.value = 'USER'; pendingPermNodes.value = []; pendingIsBlocked.value = false; pendingIsIgnored.value = false; pendingC2CPush.value = true }
  newPermNode.value = ''
  showPermModal.value = true
}

async function confirmPerm() {
  if (!permTarget.value) return
  try {
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/role?role=${pendingPermRole.value}`, { method: 'POST' })
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/blocked?value=${pendingIsBlocked.value}`, { method: 'POST' })
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/ignored?value=${pendingIsIgnored.value}`, { method: 'POST' })
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/push?value=${pendingC2CPush.value}`, { method: 'POST' })
  } catch (e) { /* ignore */ }
}

async function addPermNode(perm) {
  if (!permTarget.value || !perm?.trim()) return
  try {
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/permissions/${encodeURIComponent(perm.trim())}?enabled=true`, { method: 'POST' })
    pendingPermNodes.value.push(perm.trim())
    newPermNode.value = ''
  } catch {}
}

async function removePermNode(perm) {
  if (!permTarget.value) return
  try {
    await api(`/c2c/${encodeURIComponent(permTarget.value)}/permissions/${encodeURIComponent(perm)}?enabled=false`, { method: 'POST' })
    pendingPermNodes.value = pendingPermNodes.value.filter(p => p !== perm)
  } catch {}
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function authHeaders() {
  return { 'Content-Type': 'application/json' }
}

async function api(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: authHeaders(),
    credentials: 'same-origin',
    ...options
  })
  if (res.status === 503) { logout(); throw new Error('WebUI 已关闭') }
  let payload
  try {
    payload = await res.json()
  } catch {
    const text = await res.text()
    throw new Error(text || `HTTP ${res.status}`)
  }
  if (res.status === 401) { logout(); throw new Error('未授权') }
  if (payload.status !== 200) throw new Error(payload.message || '请求失败')
  return payload.data
}

function logout() {
  fetch(`${API_BASE}/auth/logout`, { method: 'POST', credentials: 'same-origin' }).finally(() => {
    router.replace('/login')
  })
}

async function fetchList() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ page: page.value, pageSize })
    if (currentSearch.value) params.set('search', currentSearch.value)
    const data = await api(`/users/messages?${params}`)
    items.value = data.items
    total.value = data.total
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function doSearch() {
  currentSearch.value = searchText.value.trim()
  page.value = 1
  fetchList()
}

function clearSearch() {
  searchText.value = ''
  currentSearch.value = ''
  page.value = 1
  fetchList()
}

function goPage(p) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  fetchList()
}

function roleClass(role) {
  const r = (role || '').toLowerCase()
  if (r === 'owner') return 'role-owner'
  if (r === 'admin' || r === 'administrator') return 'role-admin'
  return 'role-member'
}

function roleLabel(role) {
  const r = (role || '').toLowerCase()
  if (r === 'owner') return '群主'
  if (r === 'admin' || r === 'administrator') return '管理员'
  if (r === 'member') return '成员'
  return role
}

function permClass(role) {
  const r = (role || '').toLowerCase()
  if (r === 'owner') return 'perm-owner'
  if (r === 'admin') return 'perm-admin'
  if (r === 'blacklist') return 'perm-blacklist'
  return 'perm-user'
}

function formatTime(value) {
  if (!value) return '-'
  const raw = String(value)
  const date = new Date(raw.includes('T') ? raw : raw.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return raw
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function renderContent(item) {
  let text = item.content || ''
  text = renderFaceTags(text)
  text = text.replace(/<qqbot-at-user id="([A-F0-9]+)"\s*\/>/g, '@$1')
  text = text.replace(/<qqbot-cmd-input[^>]*show="([^"]*)"[^>]*\/>/g, '$1')
  text = text.replace(/(<@[A-F0-9]+>)\s+\1/g, '$1')
  if (text.trim()) return text
  return renderAttachmentSummary(item.attachments)
}

function renderAttachmentSummary(raw) {
  const attachments = parseAttachments(raw)
  const voice = attachments.find(att => att.content_type === 'voice')
  if (voice) return voice.asr_refer_text || '[语音]'
  if (attachments.some(att => (att.content_type || '').startsWith('image/'))) return '[图片]'
  if (attachments.length > 0) return '[附件]'
  return ''
}

function parseAttachments(raw) {
  try {
    const attachments = typeof raw === 'string' ? JSON.parse(raw) : raw
    return Array.isArray(attachments) ? attachments : []
  } catch {
    return []
  }
}

let eventSource = null

function connectSSE() {
  if (eventSource) eventSource.close()
  eventSource = new EventSource(`${API_BASE}/events`, { withCredentials: true })
  eventSource.onmessage = async (e) => {
    try {
      const payload = JSON.parse(e.data)
      if (payload.type !== 'refresh') return
      // 任何群有新消息，静默刷新第一页（仅在无搜索且首页时）
      if (page.value === 1 && !currentSearch.value) {
        const params = new URLSearchParams({ page: 1, pageSize: String(pageSize) })
        const data = await api(`/users/messages?${params}`)
        items.value = data.items
        total.value = data.total
      }
    } catch { /* ignore */ }
  }
  eventSource.onerror = () => {
    eventSource.close()
    setTimeout(connectSSE, 5000)
  }
}

onMounted(async () => {
  try {
    const config = await api('/config')
    botName.value = config.botName || 'AtriBot'
    appId.value = config.appId || ''
    botOpenId.value = config.botOpenId || ''
  } catch (e) {
    // ignore
  }
  await fetchList()
  connectSSE()
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
})
</script>
