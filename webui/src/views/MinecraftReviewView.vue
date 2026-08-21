<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="loading" @click="loadApplications">刷新</button>
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
          <h2 class="feedback-title">Minecraft 玩家名审核</h2>
        </div>
        <span class="status-pill"><span class="dot ok" />{{ counts.all }} 条申请</span>
      </header>

      <section class="content name-review-layout">
        <section class="chat-panel name-review-panel">
          <div class="name-review-head">
            <div class="name-review-tabs" role="tablist">
              <button v-for="tab in tabs" :key="tab.key" type="button" role="tab"
                      class="name-review-tab" :class="{ active: filter === tab.key }"
                      :aria-selected="filter === tab.key" @click="filter = tab.key">
                {{ tab.label }} <span>{{ counts[tab.count] }}</span>
              </button>
            </div>
            <label class="name-review-search">
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>
              <input v-model="search" type="search" aria-label="搜索玩家名或申请人 ID" placeholder="搜索玩家名或申请人 ID" />
            </label>
          </div>

          <form class="name-review-add" @submit.prevent="submitApplication">
            <div class="name-review-add-title">录入申请</div>
            <label>
              <span>申请人 ID</span>
              <input v-model="draft.userId" type="text" placeholder="user_id" autocomplete="off" />
            </label>
            <label>
              <span>Minecraft 玩家名</span>
              <input v-model="draft.username" type="text" placeholder="username" autocomplete="off" />
            </label>
            <button class="primary-button" type="submit" :disabled="submitting || !draft.userId.trim() || !draft.username.trim()">
              {{ submitting ? '提交中...' : '添加申请' }}
            </button>
          </form>

          <div class="name-review-content">
            <div v-if="loading" class="empty-state">加载中...</div>
            <div v-else-if="error" class="empty-state error">{{ error }}</div>
            <div v-else-if="visibleItems.length === 0" class="empty-state">{{ search ? '没有匹配的玩家名申请' : '当前没有申请记录' }}</div>
            <div v-else class="name-review-grid">
              <article v-for="item in visibleItems" :key="item.username.toLowerCase()" class="name-review-card">
                <div class="name-review-avatar">
                  <img v-if="item.avatarUrl && !failedAvatars.has(item.username)"
                       :src="item.avatarUrl" :alt="`${item.username} 的玩家头像`"
                       loading="lazy" @error="failedAvatars.add(item.username)" />
                  <div v-else class="name-review-avatar-fallback" :title="item.avatarUrl ? '头像加载失败' : '未配置头像数据源'">
                    {{ item.username.slice(0, 1).toUpperCase() }}
                  </div>
                </div>

                <div class="name-review-info">
                  <div class="name-review-name-row">
                    <h3>{{ item.username }}</h3>
                    <span class="name-review-status" :class="item.status.toLowerCase()">
                      {{ item.status === 'APPROVED' ? '已通过' : '待审核' }}
                    </span>
                  </div>
                  <dl>
                    <div><dt>申请人</dt><dd class="name-review-mono">{{ item.userId }}</dd></div>
                    <div><dt>申请时间</dt><dd>{{ formatTime(item.appliedAt) }}</dd></div>
                    <div v-if="item.approvedAt"><dt>通过时间</dt><dd>{{ formatTime(item.approvedAt) }}</dd></div>
                  </dl>
                </div>

                <div class="name-review-actions">
                  <button v-if="item.status === 'PENDING'" class="primary-button" type="button"
                          :disabled="busy === item.username" @click="approve(item)">通过</button>
                  <button class="danger-button" type="button" :disabled="busy === item.username" @click="remove(item)">
                    {{ item.status === 'PENDING' ? '拒绝' : '移除' }}
                  </button>
                </div>
              </article>
            </div>
          </div>
        </section>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { API_BASE } from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'

const router = useRouter()
const botName = ref('AtriBot')
const appId = ref('')
const botOpenId = ref('')
const sidebarOpen = ref(false)

const tabs = [
  { key: 'PENDING', label: '待审核', count: 'pending' },
  { key: 'APPROVED', label: '已通过', count: 'approved' },
  { key: 'ALL', label: '全部', count: 'all' }
]
const filter = ref('PENDING')
const search = ref('')
const items = ref([])
const counts = reactive({ pending: 0, approved: 0, all: 0 })
const loading = ref(false)
const error = ref('')
const submitting = ref(false)
const busy = ref('')
const failedAvatars = reactive(new Set())
const draft = reactive({ userId: '', username: '' })

const visibleItems = computed(() => {
  const query = search.value.trim().toLowerCase()
  return items.value.filter(item => {
    if (filter.value !== 'ALL' && item.status !== filter.value) return false
    if (!query) return true
    return item.username.toLowerCase().includes(query) || item.userId.toLowerCase().includes(query)
  })
})

async function api(path, options = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  })
  let payload
  try {
    payload = await res.json()
  } catch {
    throw new Error(`HTTP ${res.status}`)
  }
  if (res.status === 401) {
    router.replace('/login')
    throw new Error('未授权')
  }
  if (res.status === 503) {
    router.replace('/login')
    throw new Error('WebUI 已关闭')
  }
  if (payload.status !== 200) throw new Error(payload.message || '请求失败')
  return payload.data
}

async function loadApplications() {
  loading.value = true
  error.value = ''
  try {
    const data = await api('/minecraft/name-whitelist')
    items.value = data.items || []
    counts.pending = Number(data.pending) || 0
    counts.approved = Number(data.approved) || 0
    counts.all = Number(data.all) || 0
    failedAvatars.clear()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function submitApplication() {
  submitting.value = true
  try {
    await api('/minecraft/name-whitelist', {
      method: 'POST',
      body: JSON.stringify({ userId: draft.userId, username: draft.username })
    })
    draft.userId = ''
    draft.username = ''
    filter.value = 'PENDING'
    await loadApplications()
  } catch (e) {
    alert('添加失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

async function approve(item) {
  busy.value = item.username
  try {
    await api(`/minecraft/name-whitelist/${encodeURIComponent(item.username)}/approve`, { method: 'POST' })
    await loadApplications()
  } catch (e) {
    alert('审核失败: ' + e.message)
  } finally {
    busy.value = ''
  }
}

async function remove(item) {
  const action = item.status === 'PENDING' ? '拒绝这条申请' : '移除这个已通过的玩家名'
  if (!confirm(`确定${action}吗？\n${item.username}`)) return
  busy.value = item.username
  try {
    await api(`/minecraft/name-whitelist/${encodeURIComponent(item.username)}`, { method: 'DELETE' })
    await loadApplications()
  } catch (e) {
    alert('操作失败: ' + e.message)
  } finally {
    busy.value = ''
  }
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = number => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function logout() {
  fetch(`${API_BASE}/auth/logout`, { method: 'POST', credentials: 'same-origin' })
    .finally(() => router.replace('/login'))
}

onMounted(async () => {
  try {
    const config = await api('/config')
    botName.value = config.botName || 'AtriBot'
    appId.value = config.appId || ''
    botOpenId.value = config.botOpenId || ''
  } catch {
    // 列表加载会展示实际错误。
  }
  await loadApplications()
})
</script>
