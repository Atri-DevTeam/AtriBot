<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="loading" @click="refresh">刷新</button>
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
                <dd class="errors-metric-value">{{ mode === 'detail' ? 1 : total }}</dd>
              </div>
            </dl>
          </div>

          <div class="sendlogs-tabs" role="tablist">
            <button
              v-for="tab in tabs"
              :key="tab.type"
              class="sendlogs-tab"
              :class="{ active: activeType === tab.type }"
              type="button"
              role="tab"
              :aria-selected="activeType === tab.type"
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
            <button v-if="keyword || mode === 'detail'" class="ghost-button errors-search-btn" @click="resetSearch">
              重置
            </button>
          </div>
          <p class="errors-search-hint">请求上报数据记录</p>

          <template v-if="mode === 'detail'">
            <div class="errors-detail-bar">
              <button class="ghost-button" @click="backToList">返回列表</button>
              <span class="errors-detail-crumb">日志详情</span>
            </div>

            <div v-if="detailLoading" class="empty-state">加载中...</div>
            <div v-else-if="detailError" class="empty-state error">{{ detailError }}</div>
            <div v-else-if="!detail" class="empty-state">暂无数据</div>

            <article v-else class="errors-surface errors-detail sendlogs-detail">
              <header class="errors-detail-head">
                <span class="sendlogs-type" :class="typeClass(detail.entryType)">{{ typeLabel(detail.entryType) }}</span>
                <h3 class="errors-detail-message">{{ detail.scene || '官方接口' }}</h3>
                <span class="errors-detail-time">{{ formatTime(detail.createTime) }}</span>
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
            <div v-if="loading" class="empty-state">加载中...</div>
            <div v-else-if="error" class="empty-state error">{{ error }}</div>
            <div v-else-if="items.length === 0" class="empty-state">暂无数据</div>

            <div v-else class="errors-surface">
              <div class="sendlogs-grid sendlogs-thead">
                <span>类型</span>
                <span>场景</span>
                <span>接口</span>
                <span>状态</span>
                <span>时间</span>
              </div>

              <div class="errors-list">
                <article
                  v-for="item in items"
                  :key="item.id"
                  class="sendlogs-grid sendlogs-row"
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
                  <span class="sendlogs-time" :title="formatTime(item.createTime)">{{ relativeTime(item.createTime) }}</span>
                </article>
              </div>
            </div>

            <div v-if="!loading && !error && totalPages > 1" class="errors-pagination">
              <button class="ghost-button" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
              <span class="errors-pagination-label">第 {{ page }} / {{ totalPages }} 页</span>
              <button class="ghost-button" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
            </div>
          </template>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import {computed, onMounted, reactive, ref} from 'vue'
import {useRouter} from 'vue-router'
import {API_BASE} from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'

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
const pageSize = 20
const mode = ref('list')
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const searchInput = ref('')
const keyword = ref('')
const stats = reactive({all: 0, send: 0, response: 0, error: 0})
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

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
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    params.set('page', String(page.value))
    params.set('pageSize', String(pageSize))
    params.set('type', activeType.value)
    if (keyword.value) params.set('keyword', keyword.value)
    const data = await api(`/send-logs/list?${params.toString()}`)
    items.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    error.value = e.message
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function fetchDetail(id) {
  mode.value = 'detail'
  detail.value = null
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await api(`/send-logs/${encodeURIComponent(id)}`)
  } catch (e) {
    detailError.value = e.message
  } finally {
    detailLoading.value = false
  }
}

function openDetail(id) {
  if (id) fetchDetail(id)
}

function backToList() {
  mode.value = 'list'
  detail.value = null
  detailError.value = ''
}

function selectType(type) {
  if (activeType.value === type) return
  activeType.value = type
  mode.value = 'list'
  page.value = 1
  fetchList()
}

function doSearch() {
  keyword.value = searchInput.value.trim()
  mode.value = 'list'
  page.value = 1
  fetchList()
}

function resetSearch() {
  searchInput.value = ''
  keyword.value = ''
  mode.value = 'list'
  page.value = 1
  fetchList()
}

function goPage(p) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  fetchList()
}

async function refresh() {
  await fetchStats()
  if (mode.value === 'detail' && detail.value) {
    await fetchDetail(detail.value.id)
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

function parseTime(value) {
  if (!value) return null
  const raw = String(value)
  const date = new Date(raw.includes('T') ? raw : raw.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

function formatTime(value) {
  if (!value) return '-'
  const date = parseTime(value)
  if (!date) return String(value)
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function relativeTime(value) {
  const date = parseTime(value)
  if (!date) return formatTime(value)
  const diff = Date.now() - date.getTime()
  if (diff < 0) return formatTime(value)
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 30) return `${day} 天前`
  const pad = n => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

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
