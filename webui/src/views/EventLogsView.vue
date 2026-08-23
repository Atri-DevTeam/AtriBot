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
          <h2 class="sendlogs-title">事件记录</h2>
        </div>
      </header>

      <section class="content errors-layout">
        <div class="errors-page sendlogs-page">
          <div class="errors-summary">
            <div class="errors-hero">
              <span class="errors-hero-value">{{ stats.all }}</span>
              <span class="errors-hero-label">原始事件</span>
            </div>
            <dl class="errors-metrics">
              <div class="errors-metric">
                <dt class="errors-metric-label">今日</dt>
                <dd class="errors-metric-value">{{ stats.today }}</dd>
              </div>
              <div class="errors-metric">
                <dt class="errors-metric-label">24 小时</dt>
                <dd class="errors-metric-value">{{ stats.last24h }}</dd>
              </div>
              <div class="errors-metric">
                <dt class="errors-metric-label">事件类型</dt>
                <dd class="errors-metric-value">{{ topTypeNames.length }}</dd>
              </div>
              <div class="errors-metric">
                <dt class="errors-metric-label">当前结果</dt>
                <dd class="errors-metric-value">{{ mode === 'detail' ? 1 : total }}</dd>
              </div>
            </dl>
          </div>

          <div class="sendlogs-tabs eventlogs-tabs" role="tablist">
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
          <p class="errors-search-hint">开放平台原始事件数据</p>

          <template v-if="mode === 'detail'">
            <div class="errors-detail-bar">
              <button class="ghost-button" @click="backToList">返回列表</button>
              <span class="errors-detail-crumb">事件详情</span>
            </div>

            <div v-if="detailLoading" class="empty-state">加载中...</div>
            <div v-else-if="detailError" class="empty-state error">{{ detailError }}</div>
            <div v-else-if="!detail" class="empty-state">暂无数据</div>

            <article v-else class="errors-surface errors-detail sendlogs-detail eventlogs-detail">
              <header class="errors-detail-head">
                <span class="eventlogs-type" :style="typeStyle(detail.eventType)">{{ detail.eventType }}</span>
                <h3 class="errors-detail-message">{{ detail.eventType }}</h3>
                <span class="errors-detail-time">{{ formatTime(detail.createTime) }}</span>
              </header>

              <dl class="errors-fields">
                <div class="errors-field">
                  <dt class="errors-field-label">id</dt>
                  <dd class="errors-field-value errors-mono">{{ detail.id }}</dd>
                </div>
                <div class="errors-field">
                  <dt class="errors-field-label">事件 id</dt>
                  <dd class="errors-field-value errors-mono">{{ detail.eventId || '-' }}</dd>
                </div>
                <div class="errors-field">
                  <dt class="errors-field-label">seq</dt>
                  <dd class="errors-field-value errors-mono">{{ detail.seq ?? '-' }}</dd>
                </div>
                <div class="errors-field">
                  <dt class="errors-field-label">记录时间</dt>
                  <dd class="errors-field-value">{{ formatTime(detail.createTime) }}</dd>
                </div>
              </dl>

              <section class="errors-block">
                <h4 class="errors-block-title">原始 JSON</h4>
                <pre class="errors-stack sendlogs-code">{{ pretty(detail.rawData) }}</pre>
              </section>
            </article>
          </template>

          <template v-else>
            <div v-if="loading" class="empty-state">加载中...</div>
            <div v-else-if="error" class="empty-state error">{{ error }}</div>
            <div v-else-if="items.length === 0" class="empty-state">暂无数据</div>

            <div v-else class="errors-surface">
              <div class="sendlogs-grid eventlogs-thead">
                <span>类型</span>
                <span>事件 id</span>
                <span>seq</span>
                <span>原始数据</span>
                <span>时间</span>
              </div>

              <div class="errors-list">
                <article
                  v-for="item in items"
                  :key="item.id"
                  class="sendlogs-grid eventlogs-row"
                  @click="openDetail(item.id)"
                >
                  <span>
                    <span class="eventlogs-type" :style="typeStyle(item.eventType)">{{ item.eventType }}</span>
                  </span>
                  <span class="eventlogs-eid" :title="item.eventId">{{ item.eventId || '-' }}</span>
                  <span class="eventlogs-seq">{{ item.seq ?? '-' }}</span>
                  <span class="eventlogs-raw" :title="item.rawData">{{ snippet(item.rawData) }}</span>
                  <span class="sendlogs-time" :title="formatTime(item.createTime)">{{ relativeTime(item.createTime) }}</span>
                </article>
              </div>
            </div>

            <div v-if="!loading && !error && totalPages > 1" class="errors-pagination">
              <button class="ghost-button" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
              <span class="errors-pagination-label">第 {{ page }} / {{ totalPages }} 页</span>
              <button class="ghost-button" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
              <div class="eventlogs-jump">
                <input
                  v-model="jumpPage"
                  class="eventlogs-jump-input"
                  type="number"
                  min="1"
                  :max="totalPages"
                  placeholder="页号"
                  @keyup.enter="jumpToPage"
                />
                <button class="ghost-button" :disabled="!jumpPage" @click="jumpToPage">跳转</button>
              </div>
            </div>
          </template>

          <!-- 清除记录 -->
          <section v-if="mode === 'list'" class="errors-surface eventlogs-clear">
            <header class="eventlogs-clear-head">
              <h3 class="eventlogs-clear-title">清除记录</h3>
              <p class="eventlogs-clear-desc">删除指定时间范围内的事件记录，此操作不可恢复</p>
            </header>

            <div class="eventlogs-clear-fields">
              <label class="eventlogs-clear-field">
                <span>开始时间</span>
                <input v-model="clearStart" class="eventlogs-clear-input" type="datetime-local"/>
              </label>
              <label class="eventlogs-clear-field">
                <span>结束时间</span>
                <input v-model="clearEnd" class="eventlogs-clear-input" type="datetime-local"/>
              </label>
              <label class="eventlogs-clear-field">
                <span>事件类型</span>
                <select v-model="clearType" class="eventlogs-clear-input">
                  <option value="">全部类型</option>
                  <option v-for="t in topTypeNames" :key="t" :value="t">{{ t }}</option>
                </select>
              </label>
            </div>

            <div class="eventlogs-clear-actions">
              <button class="ghost-button" :disabled="clearing" @click="clearRange">
                删除指定范围
              </button>
              <button class="ghost-button danger" :disabled="clearing" @click="clearAll">
                {{ clearType ? '清空此类型' : '清空全部' }}
              </button>
            </div>
            <p v-if="clearMessage" class="eventlogs-clear-message">{{ clearMessage }}</p>
          </section>
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
import {formatTime, relativeTime} from '../lib/time.js'

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
const pageSize = 30
const jumpPage = ref('')
const mode = ref('list')
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const searchInput = ref('')
const keyword = ref('')
const stats = reactive({all: 0, today: 0, last24h: 0, topTypes: {}})
const activeType = ref('ALL')
const clearing = ref(false)
const clearMessage = ref('')
const clearStart = ref('')
const clearEnd = ref('')
const clearType = ref('')
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const topTypeNames = computed(() =>
  Object.entries(stats.topTypes || {})
    .sort((a, b) => b[1] - a[1])
    .map(([name]) => name)
)
const tabs = computed(() => [
  {type: 'ALL', label: '全部'},
  ...topTypeNames.value.map(name => ({type: name, label: name}))
])

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
    const data = await api('/event-logs/stats')
    stats.all = data.all || 0
    stats.today = data.today || 0
    stats.last24h = data.last24h || 0
    stats.topTypes = data.topTypes || {}
    // 当前选中的类型若已不在统计中，回到全部
    if (activeType.value !== 'ALL' && !stats.topTypes[activeType.value]) {
      activeType.value = 'ALL'
    }
    if (clearType.value && !stats.topTypes[clearType.value]) {
      clearType.value = ''
    }
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
    if (activeType.value !== 'ALL') params.set('type', activeType.value)
    if (keyword.value) params.set('keyword', keyword.value)
    const data = await api(`/event-logs/list?${params.toString()}`)
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
    detail.value = await api(`/event-logs/${encodeURIComponent(id)}`)
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
  clearType.value = type === 'ALL' ? '' : type
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

function jumpToPage() {
  const p = parseInt(jumpPage.value, 10)
  if (Number.isNaN(p)) return
  goPage(p)
  jumpPage.value = ''
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
  if (type === 'ALL') return stats.all
  return stats.topTypes[type] || 0
}

const TYPE_COLORS = [
  {bg: 'rgba(59, 130, 246, 0.12)', fg: '#2563eb'},
  {bg: 'rgba(22, 163, 74, 0.12)', fg: '#15803d'},
  {bg: 'rgba(139, 92, 246, 0.12)', fg: '#7c3aed'},
  {bg: 'rgba(234, 88, 12, 0.12)', fg: '#c2410c'},
  {bg: 'rgba(220, 38, 38, 0.12)', fg: '#b91c1c'},
  {bg: 'rgba(8, 145, 178, 0.12)', fg: '#0e7490'},
  {bg: 'rgba(202, 138, 4, 0.12)', fg: '#a16207'},
  {bg: 'rgba(190, 24, 93, 0.12)', fg: '#be185d'},
  {bg: 'rgba(13, 148, 136, 0.12)', fg: '#0f766e'},
  {bg: 'rgba(71, 85, 105, 0.14)', fg: '#475569'}
]

function typeStyle(type) {
  const key = String(type || '')
  let h = 0
  for (let i = 0; i < key.length; i++) {
    h = (h * 31 + key.charCodeAt(i)) >>> 0
  }
  const c = TYPE_COLORS[h % TYPE_COLORS.length]
  return {background: c.bg, color: c.fg}
}

function snippet(raw) {
  if (!raw) return '-'
  return String(raw).replace(/\s+/g, ' ').trim().slice(0, 160)
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

function toEpochMillis(value) {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.getTime()
}

async function clearRange() {
  const start = toEpochMillis(clearStart.value)
  const end = toEpochMillis(clearEnd.value)
  if (start == null && end == null && !clearType.value) {
    clearMessage.value = '请先选择时间范围或事件类型'
    return
  }
  if (start != null && end != null && start > end) {
    clearMessage.value = '开始时间不能晚于结束时间'
    return
  }
  const body = {type: clearType.value || null}
  if (start != null) body.start = start
  if (end != null) body.end = end
  if (!confirm('确认删除该范围内的所有事件记录？此操作不可恢复')) return
  clearing.value = true
  clearMessage.value = ''
  try {
    const data = await api('/event-logs/clear', {method: 'POST', body: JSON.stringify(body)})
    clearMessage.value = `已删除 ${data.deleted} 条记录`
    clearStart.value = ''
    clearEnd.value = ''
    await fetchStats()
    await fetchList()
  } catch (e) {
    clearMessage.value = `删除失败: ${e.message}`
  } finally {
    clearing.value = false
  }
}

async function clearAll() {
  const type = clearType.value || null
  const target = type ? `事件类型「${type}」的全部记录` : '全部事件记录'
  if (!confirm(`确认清空${target}？此操作不可恢复`)) return
  clearing.value = true
  clearMessage.value = ''
  try {
    const data = await api('/event-logs/clear', {
      method: 'POST',
      body: JSON.stringify({type})
    })
    clearMessage.value = `已清空${target}，共删除 ${data.deleted} 条`
    await fetchStats()
    await fetchList()
  } catch (e) {
    clearMessage.value = `清空失败: ${e.message}`
  } finally {
    clearing.value = false
  }
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
