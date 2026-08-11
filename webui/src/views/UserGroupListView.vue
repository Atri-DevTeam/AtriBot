<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="loading" @click="refreshAll">刷新</button>
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
          <h2 class="feedback-title">用户数据</h2>
        </div>
      </header>

      <section class="content userlist-layout">
        <section class="chat-panel userlist-panel dir-panel">
          <div class="chat-head dir-chat-head">
            <div class="userlist-source-tabs" role="tablist">
              <button v-for="tab in tabs" :key="tab.key"
                      class="userlist-source-tab" :class="{ active: activeTab === tab.key }"
                      type="button" role="tab" :aria-selected="activeTab === tab.key"
                      @click="switchTab(tab.key)">{{ tab.label }}</button>
            </div>
            <div class="dir-head-right">
              <button ref="filterBtn" type="button" class="dir-filter-btn" :class="{ active: filterOpen }" @click="toggleFilter">
                <svg viewBox="0 0 24 24"><path d="M22 3H2l8 9.46V19l4 2v-8.54z"/></svg>
                筛选
              </button>
              <span class="status-pill">
                <span class="dot ok"></span>{{ activeTab === 'groups' ? `${groups.length} 个群` : `${users.length} 个用户` }}
              </span>
            </div>
          </div>

          <div class="userlist-search-bar">
            <input
              v-model="searchText"
              class="userlist-search-input"
              type="text"
              :placeholder="activeTab === 'groups' ? '搜索群信息...' : '搜索用户信息...'"
              @keyup.enter="applySearch"
            />
            <button class="primary-button" @click="applySearch">搜索</button>
            <button v-if="searchText" class="ghost-button" @click="clearSearch">清除</button>
          </div>

          <div class="userlist-content dir-content">
            <!-- ═══════ 群列表 ═══════ -->
            <template v-if="activeTab === 'groups'">
              <div v-if="loadingGroups" class="empty-state">加载中...</div>
              <div v-else-if="groupsError" class="empty-state error">{{ groupsError }}</div>
              <div v-else-if="groups.length === 0" class="empty-state">暂无群数据</div>
              <div v-else-if="visibleGroups.length === 0" class="empty-state">没有匹配的群</div>
              <div v-else class="dir-grid">
                <article v-for="g in visibleGroups" :key="g.groupOpenId" class="dir-tile">
                  <div class="dir-tile-head">
                    <span class="dir-avatar-sm" :style="tileStyle(g.groupOpenId)">{{ groupInitial(g) }}</span>
                    <span class="dir-tile-name" :title="groupName(g)">{{ groupName(g) }}</span>
                    <div class="dir-tile-actions">
                      <button class="dir-mini-btn" title="进入聊天" @click="openGroupChat(g.groupOpenId)">
                        <svg viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                      </button>
                      <button class="dir-mini-btn" title="复制群ID" @click="copyText(g.groupOpenId)">
                        <svg viewBox="0 0 24 24"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                      </button>
                    </div>
                  </div>
                  <div class="dir-tile-line">
                    <span class="dir-tile-mono">GID {{ shortId(g.groupOpenId) }}</span>
                    <span v-if="g.realGroupId">群号 {{ g.realGroupId }}</span>
                    <span>成员 {{ g.groupMemberNum || '-' }}</span>
                    <span v-if="g.joinedAt">{{ formatTime(g.joinedAt) }}</span>
                  </div>
                  <div class="dir-tile-line">
                    <span v-if="g.memberRole" class="dir-role-chip" :class="gRoleCls(g.memberRole)">{{ gRoleLabel(g.memberRole) }}</span>
                    <span class="dir-chips">
                      <span v-if="g.whitelist" class="dir-chip dir-chip-green">白名单</span>
                      <span v-if="g.blacklisted" class="dir-chip dir-chip-red">黑名单</span>
                      <span v-if="g.allowProactiveMsg" class="dir-chip dir-chip-blue">主动</span>
                    </span>
                    <span v-if="groupTypeText(g) !== '-'" class="dir-tile-type" :title="groupTypeText(g)">{{ groupTypeText(g) }}</span>
                  </div>
                </article>
              </div>
            </template>

            <!-- ═══════ 用户列表 ═══════ -->
            <template v-else>
              <div v-if="loadingUsers" class="empty-state">加载中...</div>
              <div v-else-if="usersError" class="empty-state error">{{ usersError }}</div>
              <div v-else-if="users.length === 0" class="empty-state">暂无用户数据</div>
              <div v-else-if="visibleUsers.length === 0" class="empty-state">没有匹配的用户</div>
              <div v-else class="dir-grid">
                <article v-for="u in visibleUsers" :key="u.userOpenId" class="dir-tile">
                  <div class="dir-tile-head">
                    <span class="dir-avatar-sm circle" :style="tileStyle(u.userOpenId)">
                      <span>{{ userInitial(u) }}</span>
                      <img v-if="appId" :src="`https://thirdqq.qlogo.cn/qqapp/${appId}/${u.userOpenId}/100`"
                           alt="" referrerpolicy="no-referrer" loading="lazy"
                           @error="$event.target.style.display='none'" />
                    </span>
                    <span class="dir-tile-name" :title="u.username || u.userOpenId">{{ u.username || u.userOpenId }}</span>
                    <div class="dir-tile-actions">
                      <button class="dir-mini-btn" title="更改信息" @click="openPermModal(u.userOpenId)">
                        <span style="font-size:13px;line-height:1">⚙</span>
                      </button>
                      <button class="dir-mini-btn" title="进入私聊" @click="openUserChat(u)">
                        <svg viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/><circle cx="12" cy="10" r="1.1" fill="currentColor" stroke="none"/></svg>
                      </button>
                      <button class="dir-mini-btn" title="复制用户ID" @click="copyText(u.userOpenId)">
                        <svg viewBox="0 0 24 24"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                      </button>
                    </div>
                  </div>
                  <div class="dir-tile-line">
                    <span class="dir-tile-mono">UID {{ shortId(u.userOpenId) }}</span>
                    <span>权限 {{ (u.permissions || []).length }} 项</span>
                  </div>
                  <div class="dir-tile-line">
                    <span v-if="u.role" class="dir-role-chip" :class="uRoleCls(u.role)">{{ uRoleLabel(u.role) }}</span>
                    <span class="dir-chips">
                      <span v-if="u.isBlocked" class="dir-chip dir-chip-red">拉黑</span>
                      <span v-if="u.isIgnored" class="dir-chip dir-chip-gray">屏蔽</span>
                      <span v-if="u.c2cPush" class="dir-chip dir-chip-blue">主动推送</span>
                    </span>
                  </div>
                </article>
              </div>
            </template>
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

    <Teleport to="body">
      <div v-if="filterOpen" class="dir-filter-backdrop" @click="filterOpen = false"></div>
      <div v-if="filterOpen" class="dir-filter-panel" :style="filterPanelStyle">
        <label v-if="activeTab === 'groups'" class="dir-filter-field">
          <span class="dir-filter-field-label">分类</span>
          <select v-model="groupFilterValue" class="dir-filter-select" title="群分类筛选">
            <option v-for="opt in groupFilterOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </label>
        <label v-if="activeTab === 'groups'" class="dir-filter-field">
          <span class="dir-filter-field-label">消息类型</span>
          <select v-model="groupMsgValue" class="dir-filter-select" title="按消息类型筛选">
            <option v-for="opt in groupMsgOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </label>
        <label v-if="activeTab === 'groups'" class="dir-filter-field">
          <span class="dir-filter-field-label">功能</span>
          <select v-model="groupFuncValue" class="dir-filter-select" title="按功能筛选">
            <option v-for="opt in groupFuncOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </label>
        <label v-else class="dir-filter-field">
          <span class="dir-filter-field-label">身份</span>
          <select v-model="userFilterValue" class="dir-filter-select" title="按身份筛选">
            <option v-for="opt in userFilterOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </label>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { API_BASE } from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'

const router = useRouter()

const botName = ref('AtriBot')
const appId = ref('')
const botOpenId = ref('')
const sidebarOpen = ref(false)

/* ═══════════ 双 Tab：群列表 / 用户列表 ═══════════ */

const tabs = [
  { key: 'groups', label: '群列表' },
  { key: 'users', label: '用户列表' }
]
const activeTab = ref('groups')

function switchTab(key) {
  if (activeTab.value === key) return
  activeTab.value = key
  searchText.value = ''
  currentSearch.value = ''
}

/* ═══════════ 数据 ═══════════ */

const groups = ref([])
const loadingGroups = ref(false)
const groupsError = ref('')

const users = ref([])
const loadingUsers = ref(false)
const usersError = ref('')

const loading = computed(() => loadingGroups.value || loadingUsers.value)

const searchText = ref('')
const currentSearch = ref('')

const groupFilterValue = ref('')
const groupMsgValue = ref('')
const groupFuncValue = ref('')
const userFilterValue = ref('')

/* ═══════════ 筛选二级菜单 ═══════════ */

const filterOpen = ref(false)
const filterBtn = ref(null)
const filterPanelStyle = ref({})

function toggleFilter() {
  filterOpen.value = !filterOpen.value
  if (filterOpen.value && filterBtn.value) {
    const r = filterBtn.value.getBoundingClientRect()
    const vw = window.innerWidth
    filterPanelStyle.value = vw <= 640
      ? { top: r.bottom + 6 + 'px', left: '8px', right: '8px' }
      : { top: r.bottom + 6 + 'px', right: Math.max(8, vw - r.right) + 'px' }
  }
}

const groupFilterOptions = [
  { value: '', label: '全部分类' },
  { value: 'whitelist', label: '白名单' },
  { value: 'blacklist', label: '黑名单' },
  { value: 'admin', label: '是管理员' },
  { value: 'active', label: '主动消息' }
]
const groupMsgOptions = [
  { value: '', label: '全部' },
  { value: 'only_mention', label: '仅@' },
  { value: 'mention_and_context', label: '@+上下文' },
  { value: 'all', label: '全部消息' }
]
const userFilterOptions = [
  { value: '', label: '全部身份' },
  { value: 'owner', label: '开发' },
  { value: 'admin', label: '管理' },
  { value: 'user', label: '用户' }
]

const groupFuncKeys = ref([])
const groupFuncOptions = computed(() => [
  { value: '', label: '全部功能' },
  ...groupFuncKeys.value.map(k => ({ value: k, label: k }))
])

/* ═══════════ 群列表筛选/排序（全量本地） ═══════════ */

const visibleGroups = computed(() => {
  let list = groups.value
  if (groupFilterValue.value === 'whitelist') list = list.filter(g => g.whitelist)
  else if (groupFilterValue.value === 'blacklist') list = list.filter(g => g.blacklisted)
  else if (groupFilterValue.value === 'admin') {
    // memberRole 是机器人在群内的身份，群主也算管理
    list = list.filter(g => ['owner', 'admin', 'administrator'].includes((g.memberRole || '').toLowerCase()))
  }
  else if (groupFilterValue.value === 'active') list = list.filter(g => g.allowProactiveMsg)
  if (groupMsgValue.value) {
    list = list.filter(g => (g.recvMsgSetting || '') === groupMsgValue.value)
  }
  if (groupFuncValue.value) {
    list = list.filter(g => (g.enabledFunctions || []).includes(groupFuncValue.value))
  }
  const q = currentSearch.value.trim().toLowerCase()
  if (q) {
    list = list.filter(g =>
      (g.groupOpenId || '').toLowerCase().includes(q) ||
      (g.groupName || '').toLowerCase().includes(q) ||
      (g.realGroupId != null && String(g.realGroupId).includes(q))
    )
  }
  return [...list].sort((a, b) => {
    if (a.whitelist !== b.whitelist) return a.whitelist ? -1 : 1
    return parseTime(b.joinedAt) - parseTime(a.joinedAt)
  })
})

function groupName(g) {
  return g.groupName || g.groupOpenId || '未命名群'
}

function groupInitial(g) {
  return (groupName(g) || '?').slice(0, 1).toUpperCase()
}

function groupTypeText(g) {
  return [g.groupClassText, ...(g.groupTags || [])].filter(Boolean).join(' · ') || '-'
}

/* ═══════════ 用户列表筛选/排序（全量本地） ═══════════ */

const visibleUsers = computed(() => {
  let list = users.value
  const r = userFilterValue.value
  if (r) {
    list = list.filter(u => {
      const role = (u.role || '').toLowerCase()
      if (r === 'owner') return role === 'owner'
      if (r === 'admin') return role === 'admin'
      if (r === 'user') return role === 'user'
      return true
    })
  }
  const q = currentSearch.value.trim().toLowerCase()
  if (q) {
    list = list.filter(u =>
      (u.userOpenId || '').toLowerCase().includes(q) ||
      (u.username || '').toLowerCase().includes(q)
    )
  }
  return [...list].sort((a, b) =>
    (a.username || a.userOpenId || '').localeCompare(b.username || b.userOpenId || '')
  )
})

function userInitial(u) {
  const v = u.username || u.userOpenId || '?'
  return v.slice(0, 1).toUpperCase()
}

/* ═══════════ 身份徽标 ── */

function gRoleCls(role) {
  const r = (role || '').toLowerCase()
  if (r === 'owner') return 'owner'
  if (r === 'admin' || r === 'administrator') return 'admin'
  return 'member'
}

function gRoleLabel(role) {
  const r = (role || '').toLowerCase()
  if (r === 'owner') return '群主'
  if (r === 'admin' || r === 'administrator') return '管理员'
  if (r === 'member') return '成员'
  return role
}

function uRoleCls(role) {
  const r = (role || '').toLowerCase()
  if (r === 'owner') return 'owner'
  if (r === 'admin' || r === 'administrator') return 'admin'
  return 'user'
}

function uRoleLabel(role) {
  const r = (role || '').toLowerCase()
  if (r === 'owner') return '开发'
  if (r === 'admin' || r === 'administrator') return '管理'
  return '用户'
}

/* ═══════════ 工具 ═══════════ */

function shortId(id) {
  if (!id) return '-'
  return id.length > 16 ? `${id.slice(0, 16)}…` : id
}

function tileStyle(openId) {
  let h = 0
  for (const ch of openId || '') h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return { background: `hsl(${h % 360}, 42%, 62%)` }
}

// joinedAt 存的是秒级时间戳，统一转毫秒
function parseTime(value) {
  if (!value) return 0
  const raw = String(value)
  if (/^\d+$/.test(raw.trim())) {
    const n = Number(raw.trim())
    return n < 1e12 ? n * 1000 : n
  }
  const date = new Date(raw.includes('T') ? raw : raw.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? 0 : date.getTime()
}

function formatTime(value) {
  if (!value) return '-'
  const ms = parseTime(value)
  if (!ms) return String(value)
  const date = new Date(ms)
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/* ═══════════ 跳转（深链到聊天页） ═══════════ */

function openGroupChat(openId) {
  router.push({ path: '/', query: { group: openId } })
}

function openUserChat(u) {
  router.push({ path: '/', query: { user: u.userOpenId } })
}

/* ═══════════ 复制 ═══════════ */

const copyState = ref('')
let copyTimer = null

function copyText(text) {
  if (!text) return
  const done = () => {
    copyState.value = text
    if (copyTimer) clearTimeout(copyTimer)
    copyTimer = setTimeout(() => { copyState.value = '' }, 1200)
  }
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(text).then(done).catch(() => fallbackCopy(text, done))
  } else {
    fallbackCopy(text, done)
  }
}

function fallbackCopy(text, done) {
  const ta = document.createElement('textarea')
  ta.value = text
  ta.style.position = 'fixed'
  ta.style.opacity = '0'
  document.body.appendChild(ta)
  ta.select()
  try { document.execCommand('copy'); done() } catch { /* ignore */ }
  document.body.removeChild(ta)
}

/* ═══════════ 权限弹窗 ═══════════ */

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

/* ═══════════ 请求 ═══════════ */

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

function applySearch() {
  currentSearch.value = searchText.value.trim()
}

function clearSearch() {
  searchText.value = ''
  currentSearch.value = ''
}

async function loadGroups() {
  loadingGroups.value = true
  groupsError.value = ''
  try {
    groups.value = await api('/groups') || []
  } catch (e) {
    groupsError.value = e.message
  } finally {
    loadingGroups.value = false
  }
}

async function loadUsers() {
  loadingUsers.value = true
  usersError.value = ''
  try {
    users.value = await api('/c2c/users') || []
  } catch (e) {
    usersError.value = e.message
  } finally {
    loadingUsers.value = false
  }
}

async function loadGroupFuncKeys() {
  try {
    groupFuncKeys.value = await api('/groups/functions/keys') || []
  } catch (e) {
    groupFuncKeys.value = []
  }
}

function refreshAll() {
  return Promise.all([loadGroups(), loadUsers(), loadGroupFuncKeys()])
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
  await Promise.all([loadGroups(), loadUsers(), loadGroupFuncKeys()])
})

onBeforeUnmount(() => {
  if (copyTimer) clearTimeout(copyTimer)
})
</script>
