<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="listLoading || detailLoading" @click="refresh">刷新</button>
        <button class="ghost-button" @click="logout">退出</button>
      </template>
    </AppSidebar>
    <div class="sidebar-spacer"/>

    <main class="workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6"/>
              <line x1="3" y1="12" x2="21" y2="12"/>
              <line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <h2 class="sendlogs-title">发送日志</h2>
        </div>
      </header>

      <section class="content errors-layout">
        <div class="errors-page sendlogs-page">
          <div class="errors-summary">
            <div class="errors-hero">
              <span class="errors-hero-value">{{ stats.all }}</span>
              <span class="errors-hero-label">日志条目</span>
            </div>
            <dl class="errors-metrics">
              <div class="errors-metric">
                <dt class="errors-metric-label">发送</dt>
                <dd class="errors-metric-value">{{ stats.send }}</dd>
              </div>
              <div class="errors-metric">
                <dt class="errors-metric-label">回应</dt>
                <dd class="errors-metric-value">{{ stats.response }}</dd>
              </div>
              <div class="errors-metric">
                <dt class="errors-metric-label">错误</dt>
                <dd class="errors-metric-value">{{ stats.error }}</dd>
              </div>
              <div class="errors-metric">
                <dt class="errors-metric-label">当前结果</dt>
                <dd class="errors-metric-value">{{ mode === 'detail' ? 1 : contextAnchor ? contextItems.length : total }}</dd>
              </div>
            </dl>
          </div>

          <div class="sendlogs-tabs" role="tablist">
            <button
              v-for="tab in tabs"
              :key="tab.type"
              class="sendlogs-tab"
              :class="{ active: !contextAnchor && activeType === tab.type }"
              type="button"
              role="tab"
              :aria-selected="!contextAnchor && activeType === tab.type"
              @click="selectType(tab.type)"
            >
              <span>{{ tab.label }}</span>
              <i>{{ tabCount(tab.type) }}</i>
            </button>
          </div>

          <div class="errors-search">
            <input
              v-model="searchInput"
              class="errors-search-input"
              type="text"
              placeholder="搜索..."
              @keyup.enter="doSearch"
            />
            <button class="primary-button errors-search-btn" @click="doSearch">查询</button>
            <button v-if="keyword || contextAnchor || mode === 'detail'" class="ghost-button errors-search-btn" @click="resetSearch">
              重置
            </button>
          </div>
          <p class="errors-search-hint">请求上报数据记录 · 点击日志查看详情，点击“查看临近”排查前后事件</p>

          <template v-if="mode === 'detail'">
            <div class="errors-detail-bar">
              <button class="ghost-button" @click="backToList">{{ contextAnchor ? '返回临近事件' : '返回列表' }}</button>
              <span class="errors-detail-crumb">日志详情</span>
              <button v-if="detail" class="ghost-button" @click="openContext(detail.id)">查看临近事件</button>
            </div>

            <div v-if="detailLoading" class="empty-state">加载中...</div>
            <div v-else-if="detailError" class="empty-state error">{{ detailError }}</div>
            <div v-else-if="!detail" class="empty-state">暂无数据</div>

            <article v-else class="errors-surface errors-detail sendlogs-detail">
              <header class="errors-detail-head">
                <span class="sendlogs-type" :class="typeClass(detail.entryType)">{{ typeLabel(detail.entryType) }}</span>
                <h3 class="errors-detail-message">{{ detail.scene || '官方接口' }}</h3>
                <span class="errors-detail-time">{{ formatLogTime(detail.createTime) }}</span>
              </header>

              <dl class="errors-fields">
                <div class="errors-field">
                  <dt class="errors-field-label">trace</dt>
                  <dd class="errors-field-value errors-mono">{{ detail.traceId }}</dd>
                </div>
                <div class="errors-field">
                  <dt class="errors-field-label">接口</dt>
                  <dd class="errors-field-value sendlogs-url">
                    <span class="sendlogs-method">{{ detail.method || '-' }}</span>
                    <code class="errors-mono">{{ detail.url || '-' }}</code>
                  </dd>
                </div>
                <div class="errors-field">
                  <dt class="errors-field-label">状态</dt>
                  <dd class="errors-field-value">
                    {{ detail.responseStatus ?? '-' }}
                    <span v-if="detail.errorCode" class="sendlogs-error-code">code {{ detail.errorCode }}</span>
                    <span v-if="detail.errorReason" class="sendlogs-error-reason">{{ detail.errorReason }}</span>
                  </dd>
                </div>
                <div v-if="detail.errorMessage" class="errors-field">
                  <dt class="errors-field-label">错误</dt>
                  <dd class="errors-field-value errors-message">{{ detail.errorMessage }}</dd>
                </div>
              </dl>

              <section class="errors-block">
                <h4 class="errors-block-title">发送 JSON</h4>
                <pre class="errors-stack sendlogs-code">{{ pretty(detail.requestJson) }}</pre>
              </section>

              <section class="errors-block">
                <h4 class="errors-block-title">Response</h4>
                <pre class="errors-stack sendlogs-code">{{ pretty(detail.responseBody) }}</pre>
              </section>
            </article>
          </template>

          <template v-else>
            <div v-if="contextAnchor" class="sendlogs-context-bar">
              <button class="ghost-button" @click="closeContext">{{ keyword ? '返回搜索结果' : '返回列表' }}</button>
              <span>日志 #{{ contextAnchor }} 的临近事件：前后各最多 10 条</span>
            </div>
            <div v-if="listLoading" class="empty-state">加载中...</div>
            <div v-else-if="listError" class="empty-state error">{{ listError }}</div>
            <div v-else-if="displayItems.length === 0" class="empty-state">暂无数据</div>

            <div v-else class="errors-surface">
              <div class="sendlogs-grid sendlogs-thead">
                <span>类型</span>
                <span>场景</span>
                <span>接口</span>
                <span>状态</span>
                <span>时间</span>
                <span class="sendlogs-actions">操作</span>
              </div>

              <div class="errors-list">
                <article
                  v-for="item in displayItems"
                  :key="item.id"
                  class="sendlogs-grid sendlogs-row"
                  :class="{ 'sendlogs-row--anchor': item.id === contextAnchor }"
                  @click="openDetail(item.id)"
                >
                  <span>
                    <span class="sendlogs-type" :class="typeClass(item.entryType)">{{ typeLabel(item.entryType) }}</span>
                  </span>
                  <span class="sendlogs-scene" :title="item.scene">{{ item.scene || '-' }}</span>
                  <span class="sendlogs-endpoint" :title="item.url">
                    <i>{{ item.method || '-' }}</i>{{ shortUrl(item.url) }}
                    <b v-if="rowSnippet(item)" :title="rowSnippet(item)">{{ rowSnippet(item) }}</b>
                  </span>
                  <span class="sendlogs-status" :class="{ danger: item.entryType === 'ERROR' }">
                    {{ statusText(item) }}
                  </span>
                  <span class="sendlogs-time" :title="formatLogTime(item.createTime)">
                    <b v-if="item.id === contextAnchor" class="sendlogs-anchor-label">定位日志</b>
                    {{ contextAnchor ? formatLogTime(item.createTime) : relativeTime(item.createTime) }}
                  </span>
                  <span class="sendlogs-actions">
                    <button class="ghost-button" @click.stop="openContext(item.id)">查看临近</button>
                  </span>
                </article>
              </div>
            </div>

            <div v-if="contextAnchor && !listLoading && !listError" class="errors-pagination sendlogs-pagination">
              <button class="ghost-button" :disabled="!hasNewer" @click="openContext(contextItems[0].id)">查看更晚事件</button>
              <span class="errors-pagination-label">共 {{ contextItems.length }} 条</span>
              <button class="ghost-button" :disabled="!hasOlder" @click="openContext(contextItems[contextItems.length - 1].id)">查看更早事件</button>
            </div>
            <div v-if="!contextAnchor && !loading && !error && totalPages > 1" class="errors-pagination sendlogs-pagination">
              <button class="ghost-button" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
              <span class="errors-pagination-label">第 {{ page }} / {{ totalPages }} 页</span>
              <button class="ghost-button" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
              <form class="sendlogs-page-jump" @submit.prevent="goPage(pageInput)">
                <label for="sendlogs-page-input">跳至</label>
                <input id="sendlogs-page-input" v-model="pageInput" type="number" min="1" :max="totalPages" step="1" required />
                <span>页</span>
                <button class="ghost-button" type="submit">跳转</button>
              </form>
            </div>
          </template>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import {computed, onMounted, onBeforeUnmount, reactive, ref} from 'vue'
import {useRouter} from 'vue-router'
import {API_BASE} from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'
import {formatTime, parseTime, relativeTime} from '../lib/time.js'

const router = useRouter()
const botName = ref('AtriBot')
const appId = ref('')
const botOpenId = ref('')
const sidebarOpen = ref(false)

const tabs = [
  {type: 'ALL', label: '全部'},
  {type: 'SEND', label: '发送'},
  {type: 'RESPONSE', label: '回应'},
  {type: 'ERROR', label: '错误'}
]

const activeType = ref('ALL')
const loading = ref(false)
const error = ref('')
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageInput = ref(1)
const pageSize = 20
const mode = ref('list')
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const searchInput = ref('')
const keyword = ref('')
const stats = reactive({all: 0, send: 0, response: 0, error: 0})
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const contextAnchor = ref(null)
const contextItems = ref([])
const contextLoading = ref(false)
const contextError = ref('')
const hasNewer = ref(false)
const hasOlder = ref(false)
const displayItems = computed(() => contextAnchor.value ? contextItems.value : items.value)
const listLoading = computed(() => contextAnchor.value ? contextLoading.value : loading.value)
const listError = computed(() => contextAnchor.value ? contextError.value : error.value)
let listRequest = 0
let detailRequest = 0
let contextRequest = 0

async function api(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {'Content-Type': 'application/json'},
    credentials: 'same-origin',
    ...options
  })
  if (res.status === 503) {
    logout()
    throw new Error('WebUI 已关闭')
  }
  let payload
  try {
    payload = await res.json()
  } catch {
    const text = await res.text()
    throw new Error(text || `HTTP ${res.status}`)
  }
  if (res.status === 401) {
    logout()
    throw new Error('未授权')
  }
  if (payload.status !== 200) throw new Error(payload.message || '请求失败')
  return payload.data
}

function logout() {
  fetch(`${API_BASE}/auth/logout`, {method: 'POST', credentials: 'same-origin'}).finally(() => {
    router.replace('/login')
  })
}

async function fetchStats() {
  try {
    const data = await api('/send-logs/stats')
    stats.all = data.all || 0
    stats.send = data.send || 0
    stats.response = data.response || 0
    stats.error = data.error || 0
  } catch {
    // ignore
  }
}

async function fetchList() {
  const request = ++listRequest
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    params.set('page', String(page.value))
    params.set('pageSize', String(pageSize))
    params.set('type', activeType.value)
    if (keyword.value) params.set('keyword', keyword.value)
    const data = await api(`/send-logs/list?${params.toString()}`)
    if (request !== listRequest) return
    items.value = data.items || []
    total.value = data.total || 0
    page.value = data.page || 1
    pageInput.value = page.value
  } catch (e) {
    if (request !== listRequest) return
    error.value = e.message
    items.value = []
    total.value = 0
  } finally {
    if (request === listRequest) loading.value = false
  }
}

async function fetchDetail(id) {
  const request = ++detailRequest
  mode.value = 'detail'
  detail.value = null
  detailLoading.value = true
  detailError.value = ''
  try {
    const data = await api(`/send-logs/${encodeURIComponent(id)}`)
    if (request === detailRequest) detail.value = data
  } catch (e) {
    if (request === detailRequest) detailError.value = e.message
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}

async function openContext(id) {
  if (!id) return
  backToList()
  const request = ++contextRequest
  contextAnchor.value = id
  contextItems.value = []
  contextLoading.value = true
  contextError.value = ''
  hasNewer.value = false
  hasOlder.value = false
  try {
    const data = await api(`/send-logs/${encodeURIComponent(id)}/context`)
    if (request !== contextRequest) return
    contextItems.value = data.items || []
    hasNewer.value = data.hasNewer
    hasOlder.value = data.hasOlder
  } catch (e) {
    if (request === contextRequest) contextError.value = e.message
  } finally {
    if (request === contextRequest) contextLoading.value = false
  }
}

function closeContext() {
  ++contextRequest
  contextAnchor.value = null
  contextItems.value = []
  contextLoading.value = false
  contextError.value = ''
  backToList()
}

function openDetail(id) {
  if (id) fetchDetail(id)
}

function backToList() {
  ++detailRequest
  detailLoading.value = false
  mode.value = 'list'
  detail.value = null
  detailError.value = ''
}

function selectType(type) {
  if (activeType.value === type && !contextAnchor.value) return
  closeContext()
  activeType.value = type
  mode.value = 'list'
  page.value = 1
  fetchList()
}

function doSearch() {
  closeContext()
  keyword.value = searchInput.value.trim()
  mode.value = 'list'
  page.value = 1
  fetchList()
}

function resetSearch() {
  closeContext()
  searchInput.value = ''
  keyword.value = ''
  mode.value = 'list'
  page.value = 1
  fetchList()
}

function goPage(p) {
  const target = Number(p)
  if (loading.value || !Number.isInteger(target) || target < 1 || target > totalPages.value) return
  pageInput.value = target
  if (target === page.value) return
  page.value = target
  fetchList()
}

async function refresh() {
  await fetchStats()
  if (mode.value === 'detail' && detail.value) {
    await fetchDetail(detail.value.id)
  } else if (contextAnchor.value) {
    await openContext(contextAnchor.value)
  } else {
    await fetchList()
  }
}

function tabCount(type) {
  if (type === 'SEND') return stats.send
  if (type === 'RESPONSE') return stats.response
  if (type === 'ERROR') return stats.error
  return stats.all
}

function typeLabel(type) {
  if (type === 'SEND') return '发送'
  if (type === 'RESPONSE') return '回应'
  if (type === 'ERROR') return '错误'
  return type || '-'
}

function typeClass(type) {
  return {
    'sendlogs-type--send': type === 'SEND',
    'sendlogs-type--response': type === 'RESPONSE',
    'sendlogs-type--error': type === 'ERROR'
  }
}

function statusText(item) {
  if (!item) return '-'
  if (item.entryType === 'SEND') return '已发出'
  if (item.errorCode) return `code ${item.errorCode}`
  if (item.responseStatus != null) return `HTTP ${item.responseStatus}`
  return item.entryType === 'ERROR' ? '失败' : '-'
}

function shortUrl(url) {
  if (!url) return '-'
  return String(url).replace(/^https?:\/\/[^/]+/i, '')
}

function formatLogTime(value) {
  const date = parseTime(value)
  return date ? `${formatTime(value)}:${String(date.getSeconds()).padStart(2, '0')}` : formatTime(value)
}

function rowSnippet(item) {
  const raw = item.entryType === 'ERROR'
    ? (item.errorReason || item.errorMessage || item.responseBody)
    : item.entryType === 'RESPONSE'
      ? item.responseBody
      : item.requestJson
  if (!raw) return ''
  return String(raw).replace(/\s+/g, ' ').trim().slice(0, 140)
}

function pretty(value) {
  if (!value) return '(空)'
  const raw = String(value)
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

onBeforeUnmount(() => {
  ++listRequest
  ++detailRequest
  ++contextRequest
})

onMounted(async () => {
  try {
    const config = await api('/config')
    botName.value = config.botName || 'AtriBot'
    appId.value = config.appId || ''
    botOpenId.value = config.botOpenId || ''
  } catch {
    // ignore
  }
  await fetchStats()
  await fetchList()
})
</script>
