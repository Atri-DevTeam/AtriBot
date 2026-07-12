<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="loadingGroups" @click="loadGroups">刷新</button>
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
          <h2>Napcat功能</h2>
        </div>
        <span class="status-pill topbar-status"><span class="dot ok"></span>{{ groups.length }} 个群</span>
      </header>

      <section class="content napcat-layout">
        <section class="chat-panel napcat-panel">
          <div class="chat-head">
            <strong>群功能</strong>
            <button class="ghost-button napcat-head-btn" :disabled="loadingFeatures || !selectedGroupId" @click="loadFeatures">刷新配置</button>
          </div>
          <div class="napcat-panel-body">
            <div class="napcat-group-select">
              <label>
                <span>选择群聊</span>
                <select v-model="selectedGroupId" @change="onGroupChange">
                  <option value="" disabled>选择群聊</option>
                  <option v-for="group in groups" :key="group.groupId" :value="group.groupId">
                    {{ formatGroup(group) }}
                  </option>
                </select>
              </label>
            </div>

            <div v-if="loadingGroups" class="empty-state">正在加载群列表</div>
            <div v-else-if="groupError" class="empty-state error">{{ groupError }}</div>
            <div v-else-if="!groups.length" class="empty-state">暂无 Napcat 群数据</div>

            <template v-else>
              <div v-if="loadingFeatures" class="empty-state">正在加载功能配置</div>
              <div v-else-if="featureError" class="empty-state error">{{ featureError }}</div>
              <div v-else-if="!featureKeys.length" class="empty-state">该群暂无注册功能</div>
              <div v-else class="napcat-feature-list">
                <button
                  v-for="key in featureKeys"
                  :key="key"
                  class="napcat-feature-row"
                  :class="{ enabled: features[key] }"
                  :disabled="savingFeature === key"
                  @click="toggleFeature(key)"
                >
                  <span>{{ key }}</span>
                  <span class="napcat-switch" :class="{ on: features[key] }">
                    <span></span>
                  </span>
                </button>
              </div>
            </template>
          </div>
        </section>

        <section class="chat-panel napcat-panel">
          <div class="chat-head">
            <strong>消息预览</strong>
            <div class="napcat-head-actions">
              <button class="ghost-button napcat-head-btn" :disabled="messageLoading || !selectedGroupId" @click="fetchMessages(messagePage)">加载消息</button>
              <button class="primary-button napcat-head-btn" :disabled="!selectedMessageIds.size || batchRecalling" @click="batchRecall">
                {{ batchRecalling ? '撤回中' : `撤回所选 ${selectedMessageIds.size}` }}
              </button>
            </div>
          </div>

          <div class="napcat-panel-body napcat-messages">
            <div v-if="messageError" class="empty-state error">{{ messageError }}</div>
            <div v-else-if="!messagesLoaded && !messageLoading" class="empty-state">点击加载消息查看该群记录</div>
            <div v-else-if="messageLoading" class="empty-state">正在加载消息</div>
            <div v-else-if="messages.length === 0" class="empty-state">该页暂无消息</div>

            <template v-else>
              <div class="napcat-message-toolbar">
                <label>
                  <input type="checkbox" :checked="allRecallableSelected" :disabled="batchRecalling" @change="toggleSelectAll" />
                  全选可撤回
                </label>
                <div class="napcat-pager">
                  <button class="pager-arrow" :disabled="messagePage <= 1 || messageLoading" @click="fetchMessages(messagePage - 1)">◂</button>
                  <button v-for="page in pageNumbers" :key="page" class="pager-arrow" :class="{ active: page === messagePage }" :disabled="messageLoading" @click="fetchMessages(page)">{{ page }}</button>
                  <button class="pager-arrow" :disabled="messageLoading" @click="fetchMessages(messagePage + 1)">▸</button>
                </div>
              </div>

              <div class="napcat-message-list">
                <article v-for="msg in messages" :key="msg.messageId" class="napcat-message" :class="{ recalled: isRecalled(msg.messageId) }">
                  <label class="napcat-message-check">
                    <input
                      type="checkbox"
                      :checked="selectedMessageIds.has(msg.messageId)"
                      :disabled="isRecalled(msg.messageId) || batchRecalling"
                      @change="toggleSelectMessage(msg.messageId)"
                    />
                  </label>
                  <div class="napcat-message-main">
                    <div class="napcat-message-head">
                      <strong>{{ msg.userName || msg.userId || 'Unknown' }}</strong>
                      <span v-if="msg.admin || msg.isAdmin" class="badge green">管理</span>
                      <span v-if="isRecalled(msg.messageId)" class="badge gray">已撤回</span>
                      <time>{{ formatMessageTime(msg.time) }}</time>
                    </div>
                    <p :title="msg.content">{{ msg.content || '[空消息]' }}</p>
                  </div>
                  <button
                    v-if="!isRecalled(msg.messageId)"
                    class="ghost-button napcat-recall-btn"
                    :disabled="recallingIds.has(msg.messageId)"
                    @click="recallMessage(msg.messageId)"
                  >
                    {{ recallingIds.has(msg.messageId) ? '...' : '撤回' }}
                  </button>
                  <span v-else class="napcat-recalled-text">已撤回</span>
                </article>
              </div>
            </template>
          </div>
        </section>

        <aside class="inspector napcat-log">
          <div class="inspector-head">
            <h3>请求状态</h3>
          </div>
          <div class="log-box">
            <strong>{{ currentGroupName || '未选择群' }}</strong>
            <p>{{ notice }}</p>
          </div>
          <dl v-if="selectedGroupId">
            <dt>当前群号</dt>
            <dd>{{ selectedGroupId }}</dd>
            <dt>功能数量</dt>
            <dd>{{ featureKeys.length }}</dd>
            <dt>消息页</dt>
            <dd>{{ messagesLoaded ? messagePage : '-' }}</dd>
          </dl>
        </aside>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { API_BASE, LEGACY_TOKEN_KEY } from '../router.js'
import router from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'

const RECALLED_STORAGE_KEY = 'atri.napcat.recalled_msg_ids'
const VISIBLE_PAGES = 5

const sidebarOpen = ref(false)
const groups = ref([])
const selectedGroupId = ref('')
const features = ref({})
const featureKeys = ref([])
const loadingGroups = ref(false)
const loadingFeatures = ref(false)
const savingFeature = ref('')
const groupError = ref('')
const featureError = ref('')
const notice = ref('等待操作')
const appId = ref('')
const botOpenId = ref('')
const botName = ref('AtriBot')

const messages = ref([])
const messagePage = ref(1)
const messageLoading = ref(false)
const messageError = ref('')
const messagesLoaded = ref(false)
const selectedMessageIds = ref(new Set())
const recallingIds = ref(new Set())
const batchRecalling = ref(false)
const recalledIds = ref(loadRecalledIds())

const selectedGroup = computed(() => groups.value.find(g => g.groupId === selectedGroupId.value))
const currentGroupName = computed(() => selectedGroup.value ? formatGroup(selectedGroup.value) : '')
const recallableMessages = computed(() => messages.value.filter(m => !isRecalled(m.messageId)))
const allRecallableSelected = computed(() => {
  const list = recallableMessages.value
  return list.length > 0 && list.every(m => selectedMessageIds.value.has(m.messageId))
})
const pageNumbers = computed(() => {
  const start = Math.max(1, messagePage.value - Math.floor(VISIBLE_PAGES / 2))
  return Array.from({ length: VISIBLE_PAGES }, (_, i) => start + i)
})

onMounted(async () => {
  await loadConfig()
  await loadGroups()
})

function authHeaders() {
  return { 'Content-Type': 'application/json' }
}

async function api(path, options) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: authHeaders(),
    credentials: 'same-origin',
    ...options
  })
  if (response.status === 503) {
    logout()
    throw new Error('WebUI 已关闭')
  }
  let payload
  try {
    payload = await response.json()
  } catch {
    const text = await response.text()
    throw new Error(text || `HTTP ${response.status}`)
  }
  if (response.status === 401) {
    logout()
    throw new Error(payload.message || '未授权')
  }
  if (payload.status !== 200) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

async function loadConfig() {
  try {
    const data = await api('/config')
    appId.value = data.appId || ''
    botOpenId.value = data.botOpenId || ''
    botName.value = data.botName || 'AtriBot'
  } catch { /* ignore */ }
}

async function loadGroups() {
  loadingGroups.value = true
  groupError.value = ''
  try {
    groups.value = await api('/napcat/groups')
    notice.value = `已加载 ${groups.value.length} 个 Napcat 群`
    if (!selectedGroupId.value && groups.value.length) {
      selectedGroupId.value = groups.value[0].groupId
      await loadFeatures()
    }
  } catch (error) {
    groupError.value = error.message
    notice.value = error.message
  } finally {
    loadingGroups.value = false
  }
}

async function loadFeatures() {
  if (!selectedGroupId.value) return
  loadingFeatures.value = true
  featureError.value = ''
  try {
    const data = await api(`/napcat/groups/${encodeURIComponent(selectedGroupId.value)}/features`)
    features.value = data.features || {}
    featureKeys.value = Object.keys(features.value).sort((a, b) => a.localeCompare(b))
    notice.value = `已加载 ${featureKeys.value.length} 项功能`
  } catch (error) {
    featureError.value = error.message
    notice.value = error.message
  } finally {
    loadingFeatures.value = false
  }
}

async function toggleFeature(key) {
  if (!selectedGroupId.value || savingFeature.value) return
  const next = !features.value[key]
  features.value = { ...features.value, [key]: next }
  savingFeature.value = key
  try {
    const data = await api(`/napcat/groups/${encodeURIComponent(selectedGroupId.value)}/features/${encodeURIComponent(key)}?enabled=${next}`, {
      method: 'POST'
    })
    features.value = { ...features.value, [key]: data.enabled }
    notice.value = `${key} 已${data.enabled ? '开启' : '关闭'}`
  } catch (error) {
    features.value = { ...features.value, [key]: !next }
    notice.value = error.message
  } finally {
    savingFeature.value = ''
  }
}

async function onGroupChange() {
  features.value = {}
  featureKeys.value = []
  messages.value = []
  messagePage.value = 1
  messagesLoaded.value = false
  selectedMessageIds.value = new Set()
  messageError.value = ''
  await loadFeatures()
}

async function fetchMessages(page) {
  if (!selectedGroupId.value) return
  messageLoading.value = true
  messageError.value = ''
  try {
    const data = await api('/napcat/messages', {
      method: 'POST',
      body: JSON.stringify({ groupId: selectedGroupId.value, page })
    })
    messages.value = Array.isArray(data) ? data : []
    messagePage.value = page
    messagesLoaded.value = true
    selectedMessageIds.value = new Set()
    notice.value = `已加载第 ${page} 页消息`
  } catch (error) {
    messageError.value = error.message
    notice.value = error.message
  } finally {
    messageLoading.value = false
  }
}

async function recallMessage(messageId) {
  if (!messageId || isRecalled(messageId)) return
  recallingIds.value = new Set([...recallingIds.value, messageId])
  try {
    await api('/napcat/recall', {
      method: 'POST',
      body: JSON.stringify({ messageIds: [messageId] })
    })
    markRecalled([messageId])
    removeSelected([messageId])
    notice.value = '消息已撤回'
  } catch (error) {
    messageError.value = error.message
    notice.value = error.message
  } finally {
    const next = new Set(recallingIds.value)
    next.delete(messageId)
    recallingIds.value = next
  }
}

async function batchRecall() {
  const ids = [...selectedMessageIds.value]
  if (!ids.length) return
  batchRecalling.value = true
  messageError.value = ''
  try {
    await api('/napcat/recall', {
      method: 'POST',
      body: JSON.stringify({ messageIds: ids })
    })
    markRecalled(ids)
    selectedMessageIds.value = new Set()
    notice.value = `已撤回 ${ids.length} 条消息`
  } catch (error) {
    messageError.value = error.message
    notice.value = error.message
  } finally {
    batchRecalling.value = false
  }
}

function toggleSelectMessage(messageId) {
  const next = new Set(selectedMessageIds.value)
  if (next.has(messageId)) next.delete(messageId)
  else next.add(messageId)
  selectedMessageIds.value = next
}

function toggleSelectAll() {
  if (allRecallableSelected.value) {
    selectedMessageIds.value = new Set()
  } else {
    selectedMessageIds.value = new Set(recallableMessages.value.map(m => m.messageId))
  }
}

function loadRecalledIds() {
  try {
    const raw = localStorage.getItem(RECALLED_STORAGE_KEY)
    return raw ? new Set(JSON.parse(raw)) : new Set()
  } catch {
    return new Set()
  }
}

function persistRecalledIds() {
  try {
    localStorage.setItem(RECALLED_STORAGE_KEY, JSON.stringify([...recalledIds.value]))
  } catch { /* ignore */ }
}

function markRecalled(ids) {
  recalledIds.value = new Set([...recalledIds.value, ...ids])
  persistRecalledIds()
}

function removeSelected(ids) {
  const remove = new Set(ids)
  selectedMessageIds.value = new Set([...selectedMessageIds.value].filter(id => !remove.has(id)))
}

function isRecalled(messageId) {
  return recalledIds.value.has(messageId)
}

function formatGroup(group) {
  return group?.name && group.name !== group.groupId ? `${group.name} (${group.groupId})` : String(group?.groupId || '')
}

function formatMessageTime(ts) {
  const value = Number(ts)
  if (!Number.isFinite(value)) return String(ts || '')
  const date = new Date(value < 10000000000 ? value * 1000 : value)
  if (Number.isNaN(date.getTime())) return String(ts)
  const pad = n => String(n).padStart(2, '0')
  const now = new Date()
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  if (date.toDateString() === now.toDateString()) return time
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${time}`
}

async function logout() {
  try {
    await fetch(`${API_BASE}/auth/logout`, { method: 'POST', credentials: 'same-origin' })
  } catch { /* ignore */ }
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  router.replace('/login')
}
</script>
