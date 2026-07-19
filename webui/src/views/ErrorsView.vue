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
          <h2 class="errors-title">错误报告</h2>
        </div>
      </header>

      <section class="content errors-layout">
        <div class="errors-page">

          <!-- 统计条：一行式，发丝线分隔 -->
          <div class="errors-summary">
            <div class="errors-hero">
              <span class="errors-hero-value">{{ stats.total }}</span>
              <span class="errors-hero-label">累计错误</span>
            </div>
            <dl class="errors-metrics">
              <div class="errors-metric">
                <dt class="errors-metric-label">近 24 小时</dt>
                <dd class="errors-metric-value">{{ stats.last24h }}</dd>
              </div>
              <div class="errors-metric">
                <dt class="errors-metric-label">近 7 天</dt>
                <dd class="errors-metric-value">{{ stats.last7d }}</dd>
              </div>
              <div class="errors-metric">
                <dt class="errors-metric-label">当前结果</dt>
                <dd class="errors-metric-value">{{ mode === 'detail' ? 1 : total }}</dd>
              </div>
            </dl>
            <div v-if="topTypes.length" class="errors-toptypes">
              <span class="errors-metric-label">高频</span>
              <span v-for="t in topTypes.slice(0, 2)" :key="t.name" class="errors-toptype" :title="t.name">
                {{ shortType(t.name) }}<i>{{ t.count }}</i>
              </span>
            </div>
          </div>

          <!-- 查询条 -->
          <div class="errors-search">
            <input
              v-model="searchInput"
              class="errors-search-input"
              type="text"
              placeholder="输入关键字搜索类名 / 异常类型 / 错误信息 / traceId"
              @keyup.enter="doSearch"
            />
            <select v-model="exceptionType" class="errors-type-select" @change="applyFilters">
              <option value="">全部异常类型</option>
              <option v-for="t in topTypes" :key="t.name" :value="t.name">{{ shortType(t.name) }}</option>
            </select>
            <button class="primary-button errors-search-btn" @click="doSearch">查询</button>
            <button v-if="keyword || exceptionType || mode === 'detail'" class="ghost-button errors-search-btn"
                    @click="resetSearch">
              重置
            </button>
          </div>
          <p class="errors-search-hint">
            <span v-if="isTraceId(searchInput)" class="errors-hint-mode errors-hint-mode--trace">traceId 精确查询</span>
            <span v-else-if="searchInput.trim()" class="errors-hint-mode">关键字过滤</span>
            <span v-else class="errors-hint-mode errors-hint-mode--idle">使用traceId以定位错误</span>
          </p>

          <!-- 详情视图 -->
          <template v-if="mode === 'detail'">
            <div class="errors-detail-bar">
              <button class="ghost-button" @click="backToList">← 返回列表</button>
              <span class="errors-detail-crumb">错误详情</span>
            </div>

            <div v-if="detailLoading" class="empty-state">加载中...</div>
            <div v-else-if="detailError" class="empty-state error">{{ detailError }}</div>
            <div v-else-if="!detail" class="empty-state">暂无数据</div>

            <article v-else class="errors-surface errors-detail">
              <header class="errors-detail-head">
                <span class="errors-type errors-type--lg" :title="detail.exceptionType">
                  {{ shortType(detail.exceptionType) }}
                </span>
                <h3 class="errors-detail-message">{{ detail.exceptionMessage || '(无错误信息)' }}</h3>
                <span class="errors-detail-time">{{ formatTime(detail.createTime) }}</span>
              </header>

              <dl class="errors-fields">
                <div class="errors-field">
                  <dt class="errors-field-label">traceId</dt>
                  <dd class="errors-field-value errors-trace-row">
                    <code class="errors-mono">{{ detail.traceId }}</code>
                    <button class="ghost-button errors-copy-btn" @click="copyTrace(detail.traceId)">
                      {{ copied ? '已复制' : '复制' }}
                    </button>
                  </dd>
                </div>
                <div class="errors-field">
                  <dt class="errors-field-label">发生位置</dt>
                  <dd class="errors-field-value errors-mono">{{ detail.className || '-' }}</dd>
                </div>
                <div class="errors-field">
                  <dt class="errors-field-label">异常类型</dt>
                  <dd class="errors-field-value errors-mono">{{ detail.exceptionType || '-' }}</dd>
                </div>
                <div class="errors-field">
                  <dt class="errors-field-label">错误信息</dt>
                  <dd class="errors-field-value errors-message">{{ detail.exceptionMessage || '-' }}</dd>
                </div>
                <div class="errors-field">
                  <dt class="errors-field-label">发生时间</dt>
                  <dd class="errors-field-value">{{ formatTime(detail.createTime) }}</dd>
                </div>
              </dl>

              <section class="errors-block">
                <h4 class="errors-block-title">堆栈信息</h4>
                <pre class="errors-stack">{{ joinStack(detail.stackTrace) }}</pre>
              </section>

              <section v-if="detail.causeType" class="errors-block">
                <h4 class="errors-block-title">根因 Caused by</h4>
                <dl class="errors-fields">
                  <div class="errors-field">
                    <dt class="errors-field-label">根因类型</dt>
                    <dd class="errors-field-value errors-mono">{{ detail.causeType }}</dd>
                  </div>
                  <div class="errors-field">
                    <dt class="errors-field-label">根因信息</dt>
                    <dd class="errors-field-value errors-message">{{ detail.causeMessage || '-' }}</dd>
                  </div>
                </dl>
                <pre class="errors-stack">{{ joinStack(detail.causeStackTrace) }}</pre>
              </section>
            </article>
          </template>

          <!-- 列表视图 -->
          <template v-else>
            <div v-if="loading" class="empty-state">加载中...</div>
            <div v-else-if="error" class="empty-state error">{{ error }}</div>
            <div v-else-if="items.length === 0" class="empty-state">暂无数据</div>

            <div v-else class="errors-surface">
              <div class="errors-grid errors-thead">
                <span class="errors-col-type">类型</span>
                <span class="errors-col-class">位置</span>
                <span class="errors-col-message">错误信息</span>
                <span class="errors-col-trace">trace</span>
                <span class="errors-col-time">时间</span>
              </div>

              <div class="errors-list">
                <article
                  v-for="err in items"
                  :key="err.traceId"
                  class="errors-grid errors-row"
                  @click="openDetail(err.traceId)"
                >
                  <span class="errors-col-type">
                    <span class="errors-type" :title="err.exceptionType">{{ badgeType(err.exceptionType) }}</span>
                  </span>
                  <span class="errors-col-class errors-mono" :title="err.className">{{ shortClass(err.className) }}</span>
                  <span class="errors-col-message" :title="rowMessageTitle(err)">
                    {{ err.exceptionMessage || '(无错误信息)' }}
                    <i v-if="err.causeType" class="errors-row-cause">⤷ {{ shortType(err.causeType) }}</i>
                  </span>
                  <span class="errors-col-trace errors-mono" :title="err.traceId">{{ shortTrace(err.traceId) }}</span>
                  <span class="errors-col-time" :title="formatTime(err.createTime)">{{ relativeTime(err.createTime) }}</span>
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
import {ref, reactive, computed, onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {API_BASE} from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'

const TRACE_ID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

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

const mode = ref('list')
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const copied = ref(false)

const searchInput = ref('')
const keyword = ref('')
const exceptionType = ref('')

const stats = reactive({total: 0, last24h: 0, last7d: 0})
const topTypes = ref([])

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function authHeaders() {
  return {'Content-Type': 'application/json'}
}

async function api(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: authHeaders(),
    credentials: 'same-origin',
    ...options
  })
  if (res.status === 503) {
    logout();
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
    logout();
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

function isTraceId(value) {
  return TRACE_ID_RE.test(String(value || '').trim())
}

async function fetchStats() {
  try {
    const data = await api('/errors/stats')
    stats.total = data.total || 0
    stats.last24h = data.last24h || 0
    stats.last7d = data.last7d || 0
    const top = data.topExceptionTypes || {}
    topTypes.value = Object.keys(top).map(name => ({name, count: top[name]}))
  } catch (e) {
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
    if (keyword.value) params.set('keyword', keyword.value)
    if (exceptionType.value) params.set('exceptionType', exceptionType.value)
    const data = await api(`/errors/list?${params.toString()}`)
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

async function fetchDetail(traceId) {
  mode.value = 'detail'
  detail.value = null
  detailLoading.value = true
  detailError.value = ''
  copied.value = false
  try {
    detail.value = await api(`/errors/${encodeURIComponent(traceId)}`)
  } catch (e) {
    detailError.value = e.message
  } finally {
    detailLoading.value = false
  }
}

function openDetail(traceId) {
  if (!traceId) return
  fetchDetail(traceId)
}

function backToList() {
  mode.value = 'list'
  detail.value = null
  detailError.value = ''
}

function doSearch() {
  const value = searchInput.value.trim()
  if (isTraceId(value)) {
    fetchDetail(value)
    return
  }
  keyword.value = value
  mode.value = 'list'
  page.value = 1
  fetchList()
}

function applyFilters() {
  mode.value = 'list'
  page.value = 1
  fetchList()
}

function resetSearch() {
  searchInput.value = ''
  keyword.value = ''
  exceptionType.value = ''
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
    await fetchDetail(detail.value.traceId)
  } else if (mode.value === 'detail') {
    backToList()
    await fetchList()
  } else {
    await fetchList()
  }
}

async function copyTrace(value) {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 1500)
  } catch (e) {
    alert('复制失败，请手动选择文本')
  }
}

function shortType(value) {
  if (!value) return '-'
  const raw = String(value)
  const idx = raw.lastIndexOf('.')
  return idx >= 0 ? raw.substring(idx + 1) : raw
}

/**
 * 徽章里省掉结尾的 Exception，否则 SQLTransientConnectionException 这类会被拦腰截断。
 * 去掉后过短（如 IOException → IO）则保留原样。
 */
function badgeType(value) {
  const name = shortType(value)
  if (name === '-') return name
  const trimmed = name.replace(/Exception$/, '')
  return trimmed.length >= 4 ? trimmed : name
}

/**
 * 类名从右往左保留两段。包名前缀对定位没帮助，尾部的类名才是有效信息，
 * 直接从右截断会得到 top.yzljc.atribot.functio… 这种毫无用处的结果。
 */
function shortClass(value) {
  if (!value) return '-'
  const parts = String(value).split('.')
  return parts.length <= 2 ? String(value) : parts.slice(-2).join('.')
}

function shortTrace(value) {
  if (!value) return '-'
  return String(value).slice(0, 8)
}

function joinStack(lines) {
  if (!lines || lines.length === 0) return '(无堆栈信息)'
  return lines.join('\n')
}

function rowMessageTitle(err) {
  if (!err) return ''
  const parts = [err.exceptionMessage || '(无错误信息)']
  if (err.causeType) {
    parts.push(`Caused by: ${err.causeType}${err.causeMessage ? ': ' + err.causeMessage : ''}`)
  }
  return parts.join('\n')
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
  } catch (e) {
    // ignore
  }
  await fetchStats()
  await fetchList()
})
</script>
