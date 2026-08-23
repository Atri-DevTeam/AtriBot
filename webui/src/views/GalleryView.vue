<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName"
                :gallery-badge="counts.pending">
      <template #toolbar>
        <button class="ghost-button" :disabled="loading" @click="refresh">刷新</button>
        <button class="ghost-button" @click="logout">退出</button>
      </template>
    </AppSidebar>
    <div class="sidebar-spacer"/>

    <main class="workspace">
      <header class="topbar gallery-topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6"/>
              <line x1="3" y1="12" x2="21" y2="12"/>
              <line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <h2 class="feedback-title">图源管理</h2>
          <div class="feedback-tabs">
            <button :class="{ active: filter === 'PENDING' }" @click="setFilter('PENDING')">
              未审查 {{ counts.pending }}
            </button>
            <button :class="{ active: filter === 'REVIEWED' }" @click="setFilter('REVIEWED')">
              图库 {{ counts.reviewed }}
            </button>
            <button :class="{ active: filter === 'DENIED' }" @click="setFilter('DENIED')">
              已拒绝 {{ counts.denied }}
            </button>
            <button :class="{ active: filter === 'all' }" @click="setFilter('all')">
              全部 {{ counts.all }}
            </button>
          </div>
        </div>
        <div class="topbar-right">
          <div class="gallery-density" role="group" aria-label="视图密度">
            <button :class="{ active: density === 'comfy' }" title="大图" @click="density = 'comfy'">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                   stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="7" height="7"/>
                <rect x="14" y="3" width="7" height="7"/>
                <rect x="3" y="14" width="7" height="7"/>
                <rect x="14" y="14" width="7" height="7"/>
              </svg>
            </button>
            <button :class="{ active: density === 'dense' }" title="小图" @click="density = 'dense'">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                   stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="4" height="4"/>
                <rect x="10" y="3" width="4" height="4"/>
                <rect x="17" y="3" width="4" height="4"/>
                <rect x="3" y="10" width="4" height="4"/>
                <rect x="10" y="10" width="4" height="4"/>
                <rect x="17" y="10" width="4" height="4"/>
                <rect x="3" y="17" width="4" height="4"/>
                <rect x="10" y="17" width="4" height="4"/>
                <rect x="17" y="17" width="4" height="4"/>
              </svg>
            </button>
          </div>
        </div>
      </header>

      <section class="content feedback-layout">
        <section class="chat-panel feedback-panel">
          <div class="chat-head">
            <strong>{{ currentFilterName }}</strong>
            <span v-if="selection.size" class="gallery-bulk">
              已选 {{ selection.size }} 张
              <button class="primary-button gallery-bulk-btn" :disabled="bulkBusy" @click="bulkReview('REVIEWED')">
                批量通过
              </button>
              <button class="ghost-button danger gallery-bulk-btn" :disabled="bulkBusy" @click="bulkReview('DENIED')">
                批量拒绝
              </button>
              <button class="ghost-button gallery-bulk-btn" @click="clearSelection">取消</button>
            </span>
            <span class="status-pill" style="margin-left:auto"><span class="dot ok"></span>{{ total }} 张</span>
          </div>

          <div class="feedback-content">
            <div v-if="loading" class="gallery-grid" :class="`gallery-grid--${density}`">
              <div v-for="n in 8" :key="n" class="gallery-skeleton"/>
            </div>
            <div v-else-if="error" class="empty-state error">{{ error }}</div>
            <div v-else-if="items.length === 0" class="empty-state">
              {{ filter === 'PENDING' ? '没有待审查的投稿，喵~' : '这里还没有图片' }}
            </div>

            <div v-else class="gallery-grid" :class="`gallery-grid--${density}`">
              <figure v-for="item in items" :key="item.id" class="gallery-card"
                      :class="[`gallery-card--${item.reviewStatus.toLowerCase()}`, { selected: selection.has(item.id) }]">
                <div class="gallery-thumb" @click="openViewer(item)">
                  <img v-if="item.displayUrl && !failed.has(item.id)" :src="item.displayUrl"
                       :alt="item.fileName || '投稿图片'"
                       loading="lazy" decoding="async" @error="failed.add(item.id)"/>
                  <div v-else class="gallery-broken">
                    <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"
                         stroke-linecap="round" stroke-linejoin="round">
                      <rect x="3" y="3" width="18" height="18" rx="2"/>
                      <circle cx="8.5" cy="8.5" r="1.5"/>
                      <path d="M21 15l-5-5L5 21"/>
                    </svg>
                    <span>图片不可用</span>
                  </div>
                  <span class="gallery-status" :class="`gallery-status--${item.reviewStatus.toLowerCase()}`">
                    {{ statusText(item.reviewStatus) }}
                  </span>
                  <label class="gallery-check" @click.stop>
                    <input type="checkbox" :checked="selection.has(item.id)" @change="toggleSelect(item.id)"/>
                  </label>
                </div>
                <figcaption class="gallery-meta">
                  <div class="gallery-meta-row">
                    <span class="gallery-uploader" :title="item.uploaderId">{{ item.uploaderName || '匿名' }}</span>
                    <span class="gallery-time">{{ formatTime(item.createTime) }}</span>
                  </div>
                  <div class="gallery-meta-row gallery-meta-sub">
                    <span class="gallery-dim">原 {{ formatDimensions(item.width, item.height) }}</span>
                    <span class="gallery-size">{{ formatSize(item.fileSize) }}</span>
                    <span v-if="hasProcessedInfo(item)" class="gallery-dim">
                      存 {{ formatDimensions(item.processedWidth, item.processedHeight) }}
                    </span>
                    <span v-if="hasProcessedInfo(item)" class="gallery-size">{{
                        formatSize(item.processedFileSize)
                      }}</span>
                    <span class="gallery-id" :title="item.imageUuid || item.id">#{{
                        shortId(item.imageUuid || item.id)
                      }}</span>
                  </div>
                  <div v-if="item.reviewStatus === 'PENDING'" class="gallery-actions">
                    <button class="primary-button gallery-act" :disabled="busy === item.id"
                            @click="review(item, 'REVIEWED')">通过
                    </button>
                    <button class="ghost-button danger gallery-act" :disabled="busy === item.id"
                            @click="openDeny(item)">拒绝
                    </button>
                    <button class="ghost-button danger gallery-act" :disabled="busy === item.id"
                            @click="deleteImage(item)">删除
                    </button>
                  </div>
                  <div v-else class="gallery-review-note">
                    <span>{{ item.reviewer || '系统' }} · {{ formatTime(item.reviewTime) }}</span>
                    <button class="link-button" :disabled="busy === item.id" @click="review(item, 'PENDING')">撤销
                    </button>
                    <button class="link-button danger" :disabled="busy === item.id" @click="deleteImage(item)">删除
                    </button>
                  </div>
                  <div v-if="item.reviewRemark" class="gallery-remark" :title="item.reviewRemark">
                    备注：{{ item.reviewRemark }}
                  </div>
                </figcaption>
              </figure>
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

    <!-- 大图查看器 -->
    <div v-if="viewer" class="gallery-viewer" @click.self="closeViewer">
      <button class="gallery-viewer-close" aria-label="关闭" @click="closeViewer">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/>
          <line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
      <button v-if="viewerIndex > 0" class="gallery-viewer-nav prev" aria-label="上一张" @click.stop="step(-1)">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round">
          <polyline points="15 18 9 12 15 6"/>
        </svg>
      </button>
      <button v-if="viewerIndex < items.length - 1" class="gallery-viewer-nav next" aria-label="下一张"
              @click.stop="step(1)">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
      </button>

      <figure class="gallery-viewer-stage" @click.self="closeViewer">
        <img v-if="viewer.displayUrl && !failed.has(viewer.id)" :src="viewer.displayUrl" :alt="viewer.fileName || ''"/>
        <div v-else class="gallery-broken large">
          <span>图片不可用（原始链接可能已过期）</span>
        </div>
      </figure>

      <aside class="gallery-viewer-side">
        <div class="gallery-viewer-head">
          <span class="gallery-status" :class="`gallery-status--${viewer.reviewStatus.toLowerCase()}`">
            {{ statusText(viewer.reviewStatus) }}
          </span>
          <span class="gallery-viewer-count">{{ viewerIndex + 1 }} / {{ items.length }}</span>
        </div>
        <dl class="gallery-facts">
          <div>
            <dt>投稿人</dt>
            <dd>{{ viewer.uploaderName || '匿名' }}</dd>
          </div>
          <div>
            <dt>用户 ID</dt>
            <dd class="mono break">{{ viewer.uploaderId }}</dd>
          </div>
          <div v-if="viewer.groupId">
            <dt>来源群</dt>
            <dd class="mono break">{{ viewer.groupId }}</dd>
          </div>
          <div>
            <dt>投稿时间</dt>
            <dd>{{ formatTime(viewer.createTime) }}</dd>
          </div>
          <div>
            <dt>原始尺寸</dt>
            <dd>{{ formatDimensions(viewer.width, viewer.height) }}</dd>
          </div>
          <div>
            <dt>原始大小</dt>
            <dd>{{ formatSize(viewer.fileSize) }}</dd>
          </div>
          <div v-if="hasProcessedInfo(viewer)">
            <dt>转储尺寸</dt>
            <dd>{{ formatDimensions(viewer.processedWidth, viewer.processedHeight) }}</dd>
          </div>
          <div v-if="hasProcessedInfo(viewer)">
            <dt>转储大小</dt>
            <dd>{{ formatSize(viewer.processedFileSize) }}</dd>
          </div>
          <div v-if="viewer.fileName">
            <dt>文件名</dt>
            <dd class="mono break">{{ viewer.fileName }}</dd>
          </div>
          <div v-if="viewer.imageUuid">
            <dt>图片 UUID</dt>
            <dd class="mono break">{{ viewer.imageUuid }}</dd>
          </div>
          <div v-if="viewer.hash">
            <dt>Hash</dt>
            <dd class="mono break">{{ viewer.hash }}</dd>
          </div>
          <div v-if="viewer.reviewer">
            <dt>审核人</dt>
            <dd>{{ viewer.reviewer }}</dd>
          </div>
          <div v-if="viewer.reviewTime">
            <dt>审核时间</dt>
            <dd>{{ formatTime(viewer.reviewTime) }}</dd>
          </div>
          <div v-if="viewer.reviewRemark">
            <dt>备注</dt>
            <dd>{{ viewer.reviewRemark }}</dd>
          </div>
          <div>
            <dt>结果送达</dt>
            <dd>{{ viewer.isNotified ? '已送达' : (viewer.reviewStatus === 'PENDING' ? '—' : '待送达') }}</dd>
          </div>
        </dl>
        <div class="gallery-viewer-actions">
          <template v-if="viewer.reviewStatus === 'PENDING'">
            <button class="primary-button" :disabled="busy === viewer.id" @click="review(viewer, 'REVIEWED')">通过
            </button>
            <button class="ghost-button danger" :disabled="busy === viewer.id" @click="openDeny(viewer)">拒绝</button>
          </template>
          <template v-else>
            <button class="ghost-button" :disabled="busy === viewer.id" @click="review(viewer, 'PENDING')">撤销审核
            </button>
          </template>
          <button class="ghost-button danger" :disabled="busy === viewer.id" @click="deleteImage(viewer)">删除</button>
          <a v-if="viewer.displayUrl" class="ghost-button" :href="viewer.displayUrl" target="_blank"
             rel="noopener noreferrer">原图</a>
        </div>
      </aside>
    </div>

    <!-- 拒绝理由 -->
    <div v-if="denyTarget" class="modal-backdrop" @click.self="closeDeny">
      <div class="modal">
        <div class="modal-head">
          <h2>拒绝投稿</h2>
          <button class="icon-button" @click="closeDeny">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="reply-context">
            <div class="feedback-label">#{{ shortId(denyTarget.id) }} · {{ denyTarget.uploaderName || '匿名' }}</div>
            <div class="feedback-text">拒绝理由会随审核结果一并通知</div>
          </div>
          <select v-if="denyReasons.length" v-model="denyRemark" class="quick-reply-select">
            <option value="" disabled>快捷理由…</option>
            <option v-for="r in denyReasons" :key="r" :value="r">{{ r }}</option>
          </select>
          <textarea v-model="denyRemark" class="reply-textarea" rows="4" placeholder="填写拒绝理由（可留空）"/>
        </div>
        <div class="modal-foot">
          <button class="ghost-button" @click="closeDeny">取消</button>
          <button class="primary-button" :disabled="submitting" @click="doDeny">
            {{ submitting ? '提交中...' : '确认拒绝' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {ref, reactive, computed, onMounted, onUnmounted} from 'vue'
import {useRouter} from 'vue-router'
import {API_BASE} from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'
import {formatTime} from '../lib/time.js'

const denyReasons = [
  '图片内容与图源主题无关',
  '图片过于模糊',
  '涉及版权或未授权内容',
  '内容不适宜公开展示',
  '与图库中已有图片重复',
  '何意味'
]

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
const pageSize = 24
const filter = ref('PENDING')
const density = ref('comfy')
const counts = reactive({pending: 0, reviewed: 0, denied: 0, all: 0})

const failed = reactive(new Set())
const selection = reactive(new Set())
const busy = ref('')
const bulkBusy = ref(false)

const viewer = ref(null)
const denyTarget = ref(null)
const denyRemark = ref('')
const submitting = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const viewerIndex = computed(() => viewer.value ? items.value.findIndex(i => i.id === viewer.value.id) : -1)
const currentFilterName = computed(() => ({
  PENDING: '未审查投稿',
  REVIEWED: '图库',
  DENIED: '已拒绝投稿',
  all: '全部投稿'
}[filter.value] || '图源'))

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

async function fetchCounts() {
  try {
    const data = await api('/gallery/count')
    counts.pending = data.pending
    counts.reviewed = data.reviewed
    counts.denied = data.denied
    counts.all = data.all
  } catch (e) {
    // ignore
  }
}

async function fetchList() {
  loading.value = true
  error.value = ''
  try {
    const data = await api(`/gallery/list?page=${page.value}&pageSize=${pageSize}&status=${filter.value}`)
    items.value = data.items
    total.value = data.total
    failed.clear()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await fetchCounts()
  await fetchList()
}

function setFilter(f) {
  filter.value = f
  page.value = 1
  clearSelection()
  fetchList()
}

function goPage(p) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  clearSelection()
  fetchList()
}

function toggleSelect(id) {
  if (selection.has(id)) selection.delete(id)
  else selection.add(id)
}

function clearSelection() {
  selection.clear()
}

async function review(item, status, remark) {
  busy.value = item.id
  try {
    await api('/gallery/review', {
      method: 'POST',
      body: JSON.stringify({id: item.id, status, remark: remark || ''})
    })
    if (viewer.value && viewer.value.id === item.id) closeViewer()
    await refresh()
  } catch (e) {
    alert('操作失败: ' + e.message)
  } finally {
    busy.value = ''
  }
}

async function bulkReview(status) {
  bulkBusy.value = true
  try {
    await api('/gallery/review-batch', {
      method: 'POST',
      body: JSON.stringify({ids: [...selection], status, remark: ''})
    })
    clearSelection()
    await refresh()
  } catch (e) {
    alert('批量操作失败: ' + e.message)
  } finally {
    bulkBusy.value = false
  }
}

async function deleteImage(item) {
  if (!item || !confirm(`确定删除 #${shortId(item.id)} 吗？这个操作会从图源管理里移除该记录。`)) return
  busy.value = item.id
  try {
    await api('/gallery/delete', {
      method: 'POST',
      body: JSON.stringify({id: item.id})
    })
    selection.delete(item.id)
    if (viewer.value && viewer.value.id === item.id) closeViewer()
    await refresh()
  } catch (e) {
    alert('删除失败: ' + e.message)
  } finally {
    busy.value = ''
  }
}

function openViewer(item) {
  viewer.value = item
}

function closeViewer() {
  viewer.value = null
}

function step(delta) {
  const next = viewerIndex.value + delta
  if (next < 0 || next >= items.value.length) return
  viewer.value = items.value[next]
}

function openDeny(item) {
  denyTarget.value = item
  denyRemark.value = ''
}

function closeDeny() {
  denyTarget.value = null
  denyRemark.value = ''
}

async function doDeny() {
  submitting.value = true
  const target = denyTarget.value
  const remark = denyRemark.value.trim()
  try {
    await api('/gallery/review', {
      method: 'POST',
      body: JSON.stringify({id: target.id, status: 'DENIED', remark})
    })
    closeDeny()
    if (viewer.value && viewer.value.id === target.id) closeViewer()
    await refresh()
  } catch (e) {
    alert('操作失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

function statusText(status) {
  return {PENDING: '未审核', REVIEWED: '已通过', DENIED: '已拒绝'}[status] || status
}

function shortId(value) {
  if (!value) return '-'
  return value.length <= 8 ? value : value.substring(0, 8)
}

function formatSize(bytes) {
  const n = Number(bytes)
  if (!n) return '-'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(2)} MB`
}

function formatDimensions(width, height) {
  const w = Number(width)
  const h = Number(height)
  return w > 0 && h > 0 ? `${w}×${h}` : '-'
}

function hasProcessedInfo(item) {
  return Number(item?.processedWidth) > 0 || Number(item?.processedHeight) > 0 || Number(item?.processedFileSize) > 0
}

function onKey(e) {
  if (!viewer.value) return
  if (e.key === 'Escape') closeViewer()
  else if (e.key === 'ArrowLeft') step(-1)
  else if (e.key === 'ArrowRight') step(1)
}

onMounted(async () => {
  window.addEventListener('keydown', onKey)
  try {
    const config = await api('/config')
    botName.value = config.botName || 'AtriBot'
    appId.value = config.appId || ''
    botOpenId.value = config.botOpenId || ''
  } catch (e) {
    // ignore
  }
  await refresh()
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
})
</script>
