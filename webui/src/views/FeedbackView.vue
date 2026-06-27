<template>
  <div class="shell">
    <div v-if="sidebarOpen" class="sidebar-backdrop show" @click="sidebarOpen = false" />

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
          <span v-if="counts.unreplied > 0" class="feedback-nav-badge">{{ counts.unreplied }}</span>
        </button>
      </nav>
      <div class="side-toolbar">
        <button class="ghost-button" :disabled="loadingCounts" @click="fetchCounts">刷新</button>
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
          <h2 class="feedback-title">反馈管理</h2>
          <div class="feedback-tabs">
            <button :class="{ active: filter === 'unreplied' }" @click="setFilter('unreplied')">
              待回复 {{ counts.unreplied }}
            </button>
            <button :class="{ active: filter === 'replied' }" @click="setFilter('replied')">
              已回复 {{ counts.replied }}
            </button>
            <button :class="{ active: filter === 'all' }" @click="setFilter('all')">
              全部 {{ counts.all }}
            </button>
          </div>
        </div>
      </header>

      <section class="content feedback-layout">
        <section class="chat-panel feedback-panel">
          <div class="chat-head">
            <strong>{{ currentFilterName }}</strong>
            <span class="status-pill" style="margin-left:auto"><span class="dot ok"></span>{{ total }} 条记录</span>
          </div>
          <div class="feedback-content">
            <div v-if="loading" class="empty-state">加载中...</div>
            <div v-else-if="error" class="empty-state error">{{ error }}</div>
            <div v-else-if="items.length === 0" class="empty-state">暂无数据</div>

            <div v-else class="feedback-list">
              <article v-for="fb in items" :key="fb.id" class="feedback-card" :class="{ 'feedback-card--hidden': fb.isHidden, 'feedback-card--replied': fb.replyContent }">
                <div class="feedback-card-head">
                  <span class="feedback-id" :title="fb.id">#{{ shortId(fb.id) }}</span>
                  <span class="feedback-platform">{{ fb.platform || '-' }}</span>
                  <span class="feedback-user">{{ fb.username || 'Unknown' }} ({{ fb.userId || '-' }})</span>
                  <span v-if="fb.groupId" class="feedback-group">群: {{ fb.groupId }}</span>
                  <span class="feedback-time">{{ formatTime(fb.createTime) }}</span>
                  <span v-if="fb.replyContent" class="feedback-tag feedback-tag--replied">已回复</span>
                  <span v-else class="feedback-tag feedback-tag--pending">待回复</span>
                  <span v-if="fb.isHidden" class="feedback-tag feedback-tag--hidden">已隐藏</span>
                </div>
                <div class="feedback-card-body">
                  <div class="feedback-section">
                    <div class="feedback-label">反馈内容</div>
                    <div class="feedback-text">{{ fb.submitContent }}</div>
                  </div>
                  <div v-if="fb.replyContent" class="feedback-section">
                    <div class="feedback-label">回复内容 · {{ formatTime(fb.replyTime) }}</div>
                    <div class="feedback-text feedback-reply">{{ fb.replyContent }}</div>
                  </div>
                </div>
                <div class="feedback-card-actions">
                  <button class="primary-button feedback-action" @click="openReply(fb)">{{ fb.replyContent ? '重新回复' : '回复' }}</button>
                </div>
              </article>
            </div>

            <div v-if="totalPages > 1" class="feedback-pagination">
              <button class="ghost-button" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
              <span>第 {{ page }} / {{ totalPages }} 页</span>
              <button class="ghost-button" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
            </div>
          </div>
        </section>
      </section>
    </main>

    <div v-if="replyTarget" class="modal-backdrop" @click.self="closeReply">
      <div class="modal">
        <div class="modal-head">
          <h2>{{ replyTarget.replyContent ? '重新回复' : '回复反馈' }}</h2>
          <button class="icon-button" @click="closeReply">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="reply-context">
            <div class="feedback-label">#{{ replyTarget.id.substring(0, 8) }} · {{ replyTarget.username }}</div>
            <div class="feedback-text">{{ replyTarget.submitContent }}</div>
          </div>
          <textarea v-model="replyContent" class="reply-textarea" rows="5" placeholder="输入回复内容..." />
          <label class="checkbox-label">
            <input type="checkbox" v-model="replyHidden" />
            隐藏用户原始内容
          </label>
        </div>
        <div class="modal-foot">
          <button class="ghost-button" @click="closeReply">取消</button>
          <button class="primary-button" :disabled="!replyContent.trim() || submitting" @click="doReply">
            {{ submitting ? '提交中...' : '提交回复' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { API_BASE } from '../router.js'

const router = useRouter()

const botName = ref('AtriBot')
const appId = ref('')
const botOpenId = ref('')

const sidebarOpen = ref(false)
const loading = ref(false)
const loadingCounts = ref(false)
const error = ref('')
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const filter = ref('unreplied')
const counts = reactive({ unreplied: 0, replied: 0, all: 0 })

const replyTarget = ref(null)
const replyContent = ref('')
const replyHidden = ref(false)
const submitting = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const currentFilterName = computed(() => {
  if (filter.value === 'replied') return '已回复反馈'
  if (filter.value === 'all') return '全部反馈'
  return '待回复反馈'
})

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
  const payload = await res.json()
  if (res.status === 401) { logout(); throw new Error('未授权') }
  if (payload.status !== 200) throw new Error(payload.message || '请求失败')
  return payload.data
}

function logout() {
  fetch(`${API_BASE}/auth/logout`, { method: 'POST', credentials: 'same-origin' }).finally(() => {
    router.replace('/login')
  })
}

async function fetchCounts() {
  loadingCounts.value = true
  try {
    const data = await api('/feedback/count')
    counts.unreplied = data.unreplied
    counts.replied = data.replied
    counts.all = data.all
  } catch (e) {
    // ignore
  } finally {
    loadingCounts.value = false
  }
}

async function fetchList() {
  loading.value = true
  error.value = ''
  try {
    const data = await api(`/feedback/list?page=${page.value}&pageSize=${pageSize}&filter=${filter.value}`)
    items.value = data.items
    total.value = data.total
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function setFilter(f) {
  filter.value = f
  page.value = 1
  fetchList()
}

function goPage(p) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  fetchList()
}

function openReply(fb) {
  replyTarget.value = fb
  replyContent.value = ''
  replyHidden.value = fb.isHidden
}

function closeReply() {
  replyTarget.value = null
  replyContent.value = ''
  replyHidden.value = false
}

async function doReply() {
  if (!replyContent.value.trim()) return
  submitting.value = true
  try {
    await api('/feedback/reply', {
      method: 'POST',
      body: JSON.stringify({
        id: replyTarget.value.id,
        replyContent: replyContent.value.trim(),
        isHidden: replyHidden.value
      })
    })
    closeReply()
    fetchList()
    fetchCounts()
  } catch (e) {
    alert('回复失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

function shortId(value) {
  if (!value) return '-'
  return value.length <= 8 ? value : value.substring(0, 8)
}

function formatTime(value) {
  if (!value) return '-'
  const raw = String(value)
  const date = new Date(raw.includes('T') ? raw : raw.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return raw
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
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
  await fetchCounts()
  await fetchList()
})
</script>
