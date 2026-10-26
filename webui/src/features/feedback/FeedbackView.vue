<template>
  <AppShell v-model:open="sidebarOpen" :feedback-badge="counts.pending">
    <main class="workspace ticket-workspace">
      <header class="topbar ticket-topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round">
              <path d="M3 6h18M3 12h18M3 18h18"/>
            </svg>
          </button>
          <h2>工单管理</h2>
        </div>
        <button class="icon-button" aria-label="刷新工单" title="刷新" :disabled="loading || loadingCounts"
                @click="refresh">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
               stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 7v5h-5M4 17v-5h5M6.1 7a7 7 0 0 1 11.6-1L20 9M4 15l2.3 3A7 7 0 0 0 17.9 17"/>
          </svg>
        </button>
      </header>

      <nav class="ticket-filters" aria-label="工单状态">
        <button v-for="tab in filters" :key="tab.value" :class="{active: filter === tab.value}"
                :aria-pressed="filter === tab.value" @click="setFilter(tab.value)">
          {{ tab.label }}<span>{{ counts[tab.value] }}</span>
        </button>
      </nav>

      <section class="ticket-content" :aria-busy="loading" aria-label="工单列表">
        <div class="ticket-list-heading"><h3>{{ currentFilterName }}</h3><span>共 {{ total }} 条</span></div>
        <div v-if="loading" class="ticket-empty" role="status">正在加载工单…</div>
        <div v-else-if="error" class="ticket-empty ticket-error" role="alert">
          <p>{{ error }}</p>
          <button class="ghost-button" @click="fetchList">重新加载</button>
        </div>
        <div v-else-if="items.length === 0" class="ticket-empty">暂无{{ currentFilterName }}</div>
        <div v-else class="ticket-list">
          <article v-for="fb in items" :key="fb.id" class="ticket-card">
            <header class="ticket-card-header">
              <span class="ticket-id" :title="fb.id">#{{ shortId(fb.id) }}</span>
              <span class="ticket-tag"
                    :class="hasNewSupplement(fb) ? 'ticket-tag--updated' : 'ticket-tag--' + fb.status.toLowerCase()">
                <span class="ticket-status-dot"/>{{ hasNewSupplement(fb) ? '新补充 · 待处理' : statusName(fb.status) }}
              </span>
              <time class="ticket-created">{{ formatTime(fb.createTime) }}</time>
            </header>
            <div class="ticket-submitter">
              <span class="ticket-user-icon">
                <img v-if="avatarUrl(fb) && !avatarFailed[fb.id]" :src="avatarUrl(fb)" :alt="fb.username || '用户头像'"
                     loading="lazy" referrerpolicy="no-referrer" @error="avatarFailed[fb.id] = true"/>
                <span v-else>{{ (fb.username || '用户').slice(0, 1) }}</span>
              </span>
              <strong class="ticket-user-name">{{ fb.username || '未命名用户' }}</strong>
              <dl class="ticket-identifiers">
                <div>
                  <dt>用户 ID</dt>
                  <dd>{{ fb.userId || '—' }}</dd>
                </div>
                <div v-if="fb.groupId">
                  <dt>群 ID</dt>
                  <dd>{{ fb.groupId }}</dd>
                </div>
              </dl>
            </div>
            <p class="ticket-text ticket-submission">{{ fb.submitContent }}</p>
            <div v-if="fb.latestContent" class="ticket-reply ticket-supplement">
              <div class="ticket-reply-heading"><strong>最新补充</strong>
                <time>{{ formatTime(fb.latestContentTime) }}</time>
              </div>
              <p class="ticket-text">{{ fb.latestContent }}</p>
            </div>
            <div v-if="fb.replyContent && !hasNewSupplement(fb)" class="ticket-reply">
              <div class="ticket-reply-heading"><strong>{{ fb.replyActorName || '历史记录未记录回复人' }} ·
                回复</strong>
                <time>{{ formatTime(fb.replyTime) }}</time>
              </div>
              <p class="ticket-text">{{ fb.replyContent }}</p>
            </div>
            <details class="ticket-history" @toggle="onHistoryToggle(fb.id, $event)">
              <summary>工单记录<span v-if="fb.updateTime">更新于 {{ formatTime(fb.updateTime) }}</span></summary>
              <div v-if="histories[fb.id]" class="ticket-timeline">
                <button v-if="histories[fb.id].cursor" class="ticket-history-more" :disabled="histories[fb.id].loading"
                        @click="loadHistory(fb.id, true)">加载更早记录
                </button>
                <div v-for="entry in histories[fb.id].items" :key="entry.id" class="ticket-entry"
                     :class="{'ticket-entry--staff': entry.type === 'REPLY'}">
                  <div class="ticket-entry-heading">
                    <span class="ticket-entry-author">
                      <span class="ticket-entry-avatar">
                        <img v-if="entryAvatarUrl(entry, fb) && !entryAvatarFailed[entry.id]"
                             :src="entryAvatarUrl(entry, fb)"
                             :alt="entry.actorName || '用户头像'" loading="lazy" referrerpolicy="no-referrer"
                             @error="entryAvatarFailed[entry.id] = true"/>
                        <span v-else>{{ (entry.actorName || '用户').slice(0, 1) }}</span>
                      </span>
                      <strong :title="entry.actorId || ''">{{
                          entry.actorName || (entry.type === 'REPLY' ? '历史记录未记录回复人' : '未命名用户')
                        }}</strong>
                    </span>
                    <span class="ticket-tag ticket-tag--neutral">{{ entryName(entry.type) }}</span>
                    <span v-if="entry.type === 'REPLY' && entry.silent"
                          class="ticket-tag ticket-tag--neutral">静默</span>
                    <span v-if="entry.canDelete" class="ticket-tag ticket-tag--updated">尚未送达</span>
                    <time>{{ formatTime(entry.createTime) }}</time>
                    <button v-if="entry.canDelete" type="button" class="ticket-delete-reply"
                            :disabled="deletingReplies[entry.id]"
                            @click="deleteReply(fb, entry)">{{ deletingReplies[entry.id] ? '删除中…' : '删除回复' }}
                    </button>
                  </div>
                  <p v-if="entry.content" class="ticket-text">{{ entry.content }}</p>
                </div>
                <p v-if="histories[fb.id].loading" class="ticket-history-note" role="status">正在加载记录…</p>
                <p v-if="histories[fb.id].error" class="ticket-error" role="alert">{{ histories[fb.id].error }}
                  <button @click="loadHistory(fb.id, !!histories[fb.id].items.length)">重试</button>
                </p>
              </div>
            </details>
            <p v-if="actionErrors[fb.id]" class="ticket-error" role="alert">{{ actionErrors[fb.id] }}</p>
            <footer class="ticket-actions">
              <template v-if="fb.status === 'CLOSED'">
                <span class="ticket-closed-time">关闭于 {{ formatTime(fb.closedTime) }}</span>
                <button class="ticket-reply-button" :disabled="reopening[fb.id]" @click="reopenTicket(fb)">
                  {{ reopening[fb.id] ? '打开中…' : '重新打开' }}
                </button>
              </template>
              <template v-else>
                <button class="ticket-close-button" :disabled="closing[fb.id]" @click="closeTicket(fb)">
                  {{ closing[fb.id] ? '关闭中…' : '关闭工单' }}
                </button>
                <button class="ticket-reply-button" :disabled="isNapcatFeedback(fb)" @click="openReply(fb)">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                       stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="m9 5-6 6 6 6M3 11h10a7 7 0 0 1 7 7v1"/>
                  </svg>
                  {{ isNapcatFeedback(fb) ? '暂不支持回复' : '回复工单' }}
                </button>
              </template>
            </footer>
          </article>
        </div>
        <nav v-if="totalPages > 1" class="ticket-pagination" aria-label="工单分页">
          <button class="ghost-button" :disabled="loading || page <= 1" @click="goPage(page - 1)">上一页</button>
          <span>{{ page }} / {{ totalPages }}</span>
          <button class="ghost-button" :disabled="loading || page >= totalPages" @click="goPage(page + 1)">下一页
          </button>
        </nav>
      </section>
    </main>

    <dialog ref="replyDialog" class="ticket-dialog" aria-labelledby="ticket-reply-title" @cancel.prevent="closeReply"
            @click="onDialogClick">
      <form v-if="replyTarget" class="ticket-reply-form" @submit.prevent="doReply">
        <header class="ticket-dialog-header">
          <div><h2 id="ticket-reply-title">回复工单</h2>
            <span class="ticket-id">#{{ shortId(replyTarget.id) }}</span>
          </div>
          <button type="button" class="icon-button" aria-label="关闭回复窗口" :disabled="submitting"
                  @click="closeReply">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="m6 6 12 12M6 18 18 6"/>
            </svg>
          </button>
        </header>
        <div class="ticket-dialog-body">
          <blockquote class="ticket-context"><strong>{{ replyTarget.username || '未命名用户' }}</strong>
            <p class="ticket-text">{{ replyTarget.submitContent }}</p>
          </blockquote>
          <fieldset class="ticket-editor" :disabled="submitting">
            <label for="ticket-reply-content">回复内容</label>
            <div class="ticket-quick-replies" aria-label="快捷回复">
              <button v-for="qr in quickReplies" :key="qr.label" type="button" :title="qr.content"
                      @click="replyContent = qr.content">{{ qr.label }}
              </button>
            </div>
            <textarea id="ticket-reply-content" v-model="replyContent" rows="6" placeholder="输入回复内容"
                      maxlength="4000" required/>
            <div class="ticket-options">
              <label><input type="checkbox" v-model="replySilent" @change="replySilent && (replyPrivate = false)"/>静默回复<span
                  class="ticket-option-note">不通知用户</span></label>
              <label><input type="checkbox" v-model="replyCloseTicket"/>同时关闭工单</label>
              <label :class="{'ticket-option-disabled': !replyTarget.canPrivateReply || replySilent}">
                <input type="checkbox" v-model="replyPrivate" :disabled="!replyTarget.canPrivateReply || replySilent"/>私聊回复
              </label>
            </div>
          </fieldset>
          <p v-if="replyError" class="ticket-error" role="alert">{{ replyError }}</p>
        </div>
        <footer class="ticket-dialog-footer">
          <button type="button" class="ghost-button" :disabled="submitting" @click="closeReply">取消</button>
          <button type="submit" class="primary-button" :disabled="!replyContent.trim() || submitting">
            {{ submitting ? '提交中…' : '提交回复' }}
          </button>
        </footer>
      </form>
    </dialog>
  </AppShell>
</template>

<script setup>
import {useLatestRequest} from '../../shared/lib/latestRequest.js'
import {createWebuiApi, logoutWebui} from '../../shared/lib/webuiApi.js'
import {ref, reactive, computed, onMounted, nextTick} from 'vue'
import {useRouter} from 'vue-router'
import {API_BASE} from '../../router.js'
import AppShell from '../../shared/components/AppShell.vue'
import {useBotConfig} from '../../shared/lib/botConfig.js'

import {formatTime} from '../../shared/lib/time.js'

const {loadBotConfig, appId} = useBotConfig()

const filters = [
  {value: 'pending', label: '待处理'},
  {value: 'processing', label: '处理中'},
  {value: 'waiting', label: '等待用户回复'},
  {value: 'closed', label: '已关闭'},
  {value: 'all', label: '全部工单'}
]
const quickReplies = [
  {label: '审核完成', content: '已完成审核。'},
  {label: '无法提供', content: '你好，根据开放平台用户安全策略，相关内容无法提供'},
  {label: '无效反馈', content: '相关反馈内容无效'},
  {label: '处理中', content: '您的反馈已收到，但我们还需进一步处理，我们将在问题处理后予以再次答复，感谢您的支持'},
  {label: '已修复', content: '问题已修复'}
]

const router = useRouter()

const sidebarOpen = ref(false)
const loading = ref(false)
const loadingCounts = ref(false)
const error = ref('')
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const filter = ref('pending')
const counts = reactive({pending: 0, processing: 0, waiting: 0, closed: 0, all: 0})

const replyDialog = ref(null)
const replyError = ref('')
const replyTarget = ref(null)
const replyContent = ref('')
const replySilent = ref(false)
const replyPrivate = ref(false)
const replyCloseTicket = ref(false)
const submitting = ref(false)
const avatarFailed = reactive({})
const entryAvatarFailed = reactive({})
const histories = reactive({})
const historyOpen = new Set()
const closing = reactive({})
const reopening = reactive({})
const deletingReplies = reactive({})
const actionErrors = reactive({})


const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const currentFilterName = computed(() => {
  if (filter.value === 'processing') return '处理中工单'
  if (filter.value === 'waiting') return '等待用户回复的工单'
  if (filter.value === 'closed') return '已关闭工单'
  if (filter.value === 'all') return '全部工单'
  return '待处理工单'
})


const api = createWebuiApi({baseUrl: API_BASE, onSessionExpired: logout})

function logout() {
  return logoutWebui(API_BASE, router)
}

const fetchCountsRequests = useLatestRequest()

async function fetchCounts() {
  const request = fetchCountsRequests.begin()
  if (!request.isCurrent()) return
  loadingCounts.value = true
  try {
    const data = await api('/feedback/count', {signal: request.signal})
    if (!request.isCurrent()) return
    counts.pending = data.pending
    counts.processing = data.processing
    counts.waiting = data.waiting ?? 0
    counts.closed = data.closed
    counts.all = data.all
  } catch (e) {
    if (!request.isCurrent()) return
    // ignore
  } finally {
    if (request.isCurrent()) {
      loadingCounts.value = false
    }
  }
}

const fetchListRequests = useLatestRequest()

async function fetchList() {
  const request = fetchListRequests.begin()
  if (!request.isCurrent()) return
  loading.value = true
  error.value = ''
  try {
    const data = await api(`/feedback/list?page=${page.value}&pageSize=${pageSize}&filter=${filter.value}`, {signal: request.signal})
    if (!request.isCurrent()) return
    for (const id of Object.keys(histories)) delete histories[id]
    items.value = data.items
    total.value = data.total
    if (page.value > totalPages.value) {
      page.value = totalPages.value
      return fetchList()
    }
    for (const fb of data.items) if (historyOpen.has(fb.id)) loadHistory(fb.id)
  } catch (e) {
    if (!request.isCurrent()) return
    error.value = e.message
  } finally {
    if (request.isCurrent()) {
      loading.value = false
    }
  }
}

function refresh() {
  fetchCounts()
  fetchList()
}

function setFilter(f) {
  if (filter.value === f) return
  filter.value = f
  page.value = 1
  fetchList()
}

function goPage(p) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  fetchList()
}

async function openReply(fb) {
  if (isNapcatFeedback(fb) || fb.status === 'CLOSED') return
  replyError.value = ''
  replyTarget.value = fb
  replyContent.value = ''
  replySilent.value = false
  replyPrivate.value = false
  replyCloseTicket.value = false
  await nextTick()
  replyDialog.value?.showModal()
}

function closeReply() {
  if (submitting.value) return
  replyDialog.value?.close()
  replyError.value = ''
  replyTarget.value = null
  replyContent.value = ''
  replySilent.value = false
  replyPrivate.value = false
  replyCloseTicket.value = false
}

function onDialogClick(event) {
  if (event.target !== replyDialog.value) return
  const rect = replyDialog.value.getBoundingClientRect()
  if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) closeReply()
}

async function doReply() {
  if (submitting.value || !replyTarget.value || !replyContent.value.trim()) return
  submitting.value = true
  replyError.value = ''
  try {
    await api('/feedback/reply', {
      method: 'POST',
      body: JSON.stringify({
        id: replyTarget.value.id,
        replyContent: replyContent.value.trim(),
        silent: replySilent.value,
        privateReply: !replySilent.value && !!replyTarget.value.canPrivateReply && replyPrivate.value,
        closeTicket: replyCloseTicket.value
      })
    })
    submitting.value = false
    closeReply()
    fetchList()
    fetchCounts()
  } catch (e) {
    replyError.value = '回复失败：' + e.message
  } finally {
    submitting.value = false
  }
}

function statusName(status) {
  return {PENDING: '待处理', PROCESSING: '处理中', WAITING_USER: '等待用户回复', CLOSED: '已关闭'}[status] || status
}

function entryName(type) {
  return {
    SUBMIT: '提交',
    APPEND: '补充',
    REPLY: '回复',
    REPLY_DELETED: '回复已删除',
    ADMIN_DELETE: '删除回复',
    USER_CLOSE: '用户关闭',
    ADMIN_CLOSE: '管理员关闭',
    ADMIN_REOPEN: '重新打开'
  }[type] || type
}

function hasNewSupplement(fb) {
  return fb.status === 'PENDING' && !!fb.latestContent
}

function avatarUrl(fb) {
  if (!fb.userId) return ''
  if (['OFFICIAL_GROUP', 'OFFICIAL_C2C'].includes(fb.platform) && appId.value)
    return `https://thirdqq.qlogo.cn/qqapp/${encodeURIComponent(appId.value)}/${encodeURIComponent(fb.userId)}/640`
  if (isNapcatFeedback(fb)) return `https://q.qlogo.cn/headimg_dl?dst_uin=${encodeURIComponent(fb.userId)}&spec=100`
  return ''
}

function entryAvatarUrl(entry, fb) {
  if (['SUBMIT', 'APPEND', 'USER_CLOSE'].includes(entry.type))
    return avatarUrl({userId: entry.actorId, platform: entry.platform || fb.platform})
  return entry.avatarUrl || ''
}

function onHistoryToggle(id, event) {
  if (event.target.open) {
    historyOpen.add(id)
    if (!histories[id]) loadHistory(id)
  } else {
    historyOpen.delete(id)
  }
}

async function loadHistory(id, earlier = false) {
  if (histories[id]?.loading) return
  if (!histories[id]) histories[id] = {items: [], cursor: '', loading: false, error: ''}
  const state = histories[id]
  state.loading = true
  state.error = ''
  try {
    const before = earlier && state.cursor ? `?before=${encodeURIComponent(state.cursor)}` : ''
    const data = await api(`/feedback/${encodeURIComponent(id)}/entries${before}`)
    const entries = [...data.items].reverse()
    state.items = earlier ? [...entries, ...state.items] : entries
    state.cursor = data.nextCursor
  } catch (e) {
    state.error = e.message
  } finally {
    state.loading = false
  }
}

async function closeTicket(fb) {
  if (closing[fb.id] || !window.confirm(`确认关闭工单 #${shortId(fb.id)}？`)) return
  closing[fb.id] = true
  actionErrors[fb.id] = ''
  try {
    await api('/feedback/close', {method: 'POST', body: JSON.stringify({id: fb.id})})
    refresh()
  } catch (e) {
    actionErrors[fb.id] = e.message
  } finally {
    closing[fb.id] = false
  }
}

async function deleteReply(fb, entry) {
  if (deletingReplies[entry.id] || !window.confirm('确认删除这条尚未送达的回复？删除后将取消补发。')) return
  deletingReplies[entry.id] = true
  actionErrors[fb.id] = ''
  try {
    await api(`/feedback/${encodeURIComponent(fb.id)}/entries/${encodeURIComponent(entry.id)}`, {method: 'DELETE'})
    refresh()
  } catch (e) {
    actionErrors[fb.id] = e.message
  } finally {
    deletingReplies[entry.id] = false
  }
}

async function reopenTicket(fb) {
  if (reopening[fb.id]) return
  reopening[fb.id] = true
  actionErrors[fb.id] = ''
  try {
    await api('/feedback/reopen', {method: 'POST', body: JSON.stringify({id: fb.id})})
    refresh()
  } catch (e) {
    actionErrors[fb.id] = e.message
  } finally {
    reopening[fb.id] = false
  }
}

function shortId(value) {
  if (!value) return '-'
  return value.length <= 8 ? value : value.substring(0, 8)
}

function isNapcatFeedback(feedback) {
  return String(feedback?.platform || '').toUpperCase().startsWith('NAPCAT')
}

onMounted(async () => {
  try {
    await loadBotConfig(api)

  } catch (e) {
    // ignore
  }
  await fetchCounts()
  await fetchList()
})
</script>

<style scoped src="./styles/tickets.css"></style>
