<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {ApiError} from '../session'
import Icon from './Icon.vue'

type Ticket = { id: string; status: string; content: string; createdAt: string; updatedAt: string }
type Entry = { id: number; type: string; content: string; createdAt: string }
type Page<T> = { items: T[]; hasMore: boolean }
type Detail = { ticket: Ticket; entries: Page<Entry> }
const props = defineProps<{
  active: boolean
  request: <T>(path: string) => Promise<T | null>
  post: <T>(path: string, body: unknown) => Promise<T | null>
}>()
const tickets = ref<Ticket[]>([])
const detail = ref<Detail | null>(null)
const hasMore = ref(false)
const loaded = ref(false)
const loading = ref(false)
const busy = ref(false)
const composing = ref(false)
const confirming = ref(false)
const draft = ref('')
const error = ref('')
const notice = ref('')
let generation = 0
const entries = computed(() => [...(detail.value?.entries.items ?? [])].reverse())
const statusNames: Record<string, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  WAITING_USER: '等待回复',
  CLOSED: '已关闭'
}
const entryNames: Record<string, string> = {
  SUBMIT: '我 · 提交工单', APPEND: '我 · 补充内容', REPLY: '管理员回复',
  USER_CLOSE: '我关闭了工单', ADMIN_CLOSE: '管理员关闭了工单', ADMIN_REOPEN: '管理员重新打开了工单'
}
const time = (value: string) => value?.replace('T', ' ').slice(0, 16) || '—'
const message = (failure: unknown) => failure instanceof ApiError && failure.code === 'TICKET_NOT_OPEN'
    ? '工单状态已变更，请刷新后重试'
    : failure instanceof ApiError && failure.code === 'INVALID_TICKET_REQUEST' ? '请输入 1—4000 字的纯文本内容' : '操作未完成，请稍后重试'

async function loadList(more = false) {
  if (loading.value || busy.value) return
  loading.value = true
  error.value = ''
  const token = generation
  try {
    const result = await props.request<Page<Ticket>>(`tickets?offset=${more ? tickets.value.length : 0}`)
    if (!result || token !== generation) return
    tickets.value = more ? [...tickets.value, ...result.items.filter(item => !tickets.value.some(t => t.id === item.id))] : result.items
    hasMore.value = result.hasMore
    loaded.value = true
  } catch (failure) {
    if (token === generation) error.value = message(failure)
  } finally {
    if (token === generation) loading.value = false
  }
}

async function open(id: string, older = false) {
  if (busy.value || loading.value) return
  loading.value = true
  error.value = ''
  const token = generation
  try {
    const currentEntries = detail.value?.entries.items ?? []
    const before = older ? `?before=${currentEntries[currentEntries.length - 1]?.id}` : ''
    const result = await props.request<Detail>(`tickets/${encodeURIComponent(id)}${before}`)
    if (!result || token !== generation) return
    if (older && detail.value) result.entries.items = [...detail.value.entries.items, ...result.entries.items]
    detail.value = result
    composing.value = false
    confirming.value = false
  } catch (failure) {
    if (token === generation) error.value = message(failure)
  } finally {
    if (token === generation) loading.value = false
  }
}

function back() {
  detail.value = null
  composing.value = false
  confirming.value = false
  draft.value = ''
  notice.value = ''
  void loadList()
}

async function submit() {
  if (busy.value || loading.value || !draft.value.trim()) return
  busy.value = true
  error.value = ''
  notice.value = ''
  const token = generation
  let savedId: string | undefined
  try {
    const path = detail.value ? `tickets/${detail.value.ticket.id}/append` : 'tickets'
    const result = await props.post<{ id: string; appended: boolean }>(path, {content: draft.value})
    if (!result || token !== generation) return
    draft.value = ''
    notice.value = result.appended ? '已补充到原工单' : '工单已提交'
    savedId = result.id
  } catch (failure) {
    if (token === generation) error.value = message(failure)
  } finally {
    if (token === generation) busy.value = false
  }
  if (savedId) await open(savedId)
}

async function closeTicket() {
  if (!detail.value || busy.value || loading.value) return
  busy.value = true
  error.value = ''
  const id = detail.value.ticket.id
  const token = generation
  let closed = false
  try {
    const result = await props.post<{ closed: boolean }>(`tickets/${id}/close`, {})
    if (!result || token !== generation) return
    closed = result.closed
    if (closed) {
      draft.value = '';
      notice.value = '工单已关闭';
      confirming.value = false
    }
  } catch (failure) {
    if (token === generation) error.value = message(failure)
  } finally {
    if (token === generation) busy.value = false
  }
  if (closed) await open(id)
}

watch(() => props.active, active => {
  if (active && !detail.value && !composing.value) void loadList()
}, {immediate: true})
onBeforeUnmount(() => {
  generation++
})
</script>

<template>
  <div class="tickets-panel">
    <header class="page-heading">
      <div><p class="page-eyebrow">问题与建议</p>
        <h2>工单</h2></div>
      <button class="text-button" :disabled="busy || loading" @click="detail || composing ? back() : loadList()">
        <Icon :name="detail || composing ? 'left' : 'refresh'"/>
        {{ detail || composing ? '返回列表' : '刷新' }}
      </button>
    </header>
    <p v-if="error" class="ticket-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="ticket-notice" role="status">{{ notice }}</p>
    <template v-if="!detail && !composing">
      <div class="ticket-toolbar"><span>{{ loaded ? '我的工单' : '正在读取…' }}</span>
        <button class="ticket-button primary" :disabled="busy || loading"
                @click="composing = true; notice = ''; draft = ''">
          <Icon name="plus"/>
          提交工单
        </button>
      </div>
      <p v-if="loaded && !tickets.length" class="ticket-empty">暂无工单记录</p>
      <div class="ticket-list">
        <button v-for="ticket in tickets" :key="ticket.id" class="ticket-row" :disabled="busy || loading"
                @click="open(ticket.id)">
          <div class="ticket-meta"><span class="ticket-number">#{{ ticket.id.slice(0, 8) }}</span>
            <span class="ticket-tag" :data-status="ticket.status">{{
                statusNames[ticket.status] || ticket.status
              }}</span></div>
          <p>{{ ticket.content }}</p>
          <div class="ticket-meta">
            <time>{{ time(ticket.updatedAt || ticket.createdAt) }}</time>
            <Icon name="chevron"/>
          </div>
        </button>
      </div>
      <button v-if="hasMore" class="text-button ticket-more" :disabled="loading" @click="loadList(true)">加载更多
      </button>
    </template>
    <template v-else>
      <section v-if="detail" class="ticket-detail">
        <header class="ticket-detail-heading">
          <div class="ticket-meta"><span class="ticket-number">#{{ detail.ticket.id.slice(0, 8) }}</span>
            <span class="ticket-tag" :data-status="detail.ticket.status">{{
                statusNames[detail.ticket.status] || detail.ticket.status
              }}</span></div>
          <button v-if="detail.ticket.status !== 'CLOSED'" class="text-button danger" :disabled="busy || loading"
                  @click="confirming = !confirming">关闭工单
          </button>
        </header>
        <div v-if="confirming" class="ticket-confirm"><span>确定关闭此工单？关闭后不能自行重新打开</span>
          <div>
            <button class="ticket-button" :disabled="busy" @click="confirming = false">取消</button>
            <button class="ticket-button danger" :disabled="busy" @click="closeTicket">确认关闭</button>
          </div>
        </div>
        <button v-if="detail.entries.hasMore" class="text-button ticket-more" :disabled="loading || busy"
                @click="open(detail.ticket.id, true)">查看更早记录
        </button>
        <div class="ticket-timeline">
          <article v-for="entry in entries" :key="entry.id" class="ticket-entry"
                   :class="{reply: entry.type === 'REPLY'}">
            <header><span>{{ entryNames[entry.type] || '状态更新' }}</span>
              <time>{{ time(entry.createdAt) }}</time>
            </header>
            <p v-if="entry.content">{{ entry.content }}</p>
          </article>
        </div>
      </section>
      <form v-if="!detail || detail.ticket.status !== 'CLOSED'" class="ticket-composer" @submit.prevent="submit">
        <label for="ticket-content">{{ detail ? '补充内容' : '提交内容' }}</label>
        <p v-if="!detail" class="ticket-hint">已有未关闭工单时，内容将追加到原工单</p>
        <textarea id="ticket-content" v-model="draft" maxlength="4000" rows="6" :disabled="busy || loading"
                  placeholder="请描述遇到的问题或建议" required/>
        <footer><span>{{ draft.length }} / 4000</span>
          <button class="ticket-button primary" :disabled="busy || loading || !draft.trim()" type="submit">
            {{ busy ? '提交中…' : detail ? '提交补充' : '提交工单' }}
          </button>
        </footer>
      </form>
      <p v-else class="ticket-hint">此工单已关闭，如有其他问题，请返回列表提交新工单</p>
    </template>
  </div>
</template>

<style scoped>
.tickets-panel {
  color: var(--ui-text);
}

.tickets-panel svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.ticket-toolbar, .ticket-meta, .ticket-detail-heading, .ticket-composer footer, .ticket-entry header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ticket-toolbar {
  margin-bottom: 18px;
  color: var(--ui-muted);
  font-size: 12px;
}

.ticket-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 9px 14px;
  border: 1px solid var(--ui-border);
  border-radius: 6px;
  color: var(--ui-text);
  background: var(--ui-raised);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.ticket-button.primary {
  background: var(--ui-accent);
  color: var(--ui-on-accent);
  border-color: transparent;
}

button:disabled {
  opacity: .5;
  cursor: default;
}

.ticket-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.ticket-row {
  padding: 18px;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  background: var(--ui-panel);
  color: var(--ui-text);
  text-align: left;
  cursor: pointer;
  min-width: 0;
}

.ticket-row:hover {
  border-color: var(--ui-muted);
}

.ticket-row p {
  margin: 14px 0;
  line-height: 1.8;
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.ticket-number {
  font: 12px ui-monospace, monospace;
  color: var(--ui-secondary);
}

.ticket-tag {
  padding: 3px 7px;
  border-radius: 4px;
  font-size: 11px;
  color: var(--ui-accent);
  background: var(--ui-accent-soft);
  white-space: nowrap;
}

.ticket-tag[data-status="CLOSED"] {
  color: var(--ui-danger);
  background: var(--ui-danger-soft);
}

.ticket-tag[data-status="WAITING_USER"] {
  color: #eab37d;
  background: #30251e;
}

.ticket-tag[data-status="PROCESSING"] {
  color: var(--ui-warning);
  background: #2c2820;
}

time, .ticket-composer footer, .ticket-hint {
  color: var(--ui-muted);
  font-size: 11px;
  line-height: 1.7;
}

.ticket-detail, .ticket-composer {
  padding: 20px;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  background: var(--ui-panel);
  margin-bottom: 16px;
}

.ticket-detail-heading {
  padding-bottom: 18px;
  border-bottom: 1px solid var(--ui-border);
}

.ticket-timeline {
  margin-top: 8px;
}

.ticket-entry {
  padding: 18px 0;
  border-bottom: 1px solid var(--ui-border);
}

.ticket-entry:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.ticket-entry header {
  font-size: 12px;
  color: var(--ui-muted);
  flex-wrap: wrap;
}

.ticket-entry.reply header > span {
  color: var(--ui-accent);
}

.ticket-entry p {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.ticket-composer label {
  font-size: 13px;
  font-weight: 500;
}

.ticket-composer textarea {
  display: block;
  width: 100%;
  margin: 12px 0;
  padding: 12px;
  resize: vertical;
  min-height: 130px;
  border: 1px solid var(--ui-border);
  border-radius: 6px;
  color: var(--ui-text);
  background: var(--ui-input);
  font: inherit;
  font-size: 16px;
  line-height: 1.7;
}

.ticket-composer textarea:focus {
  outline: 1px solid var(--ui-focus);
}

.ticket-confirm {
  margin-top: 16px;
  padding: 12px;
  background: var(--ui-danger-soft);
  font-size: 12px;
  line-height: 1.7;
}

.ticket-confirm > div {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}

.danger, .ticket-error {
  color: var(--ui-danger);
}

.ticket-notice {
  color: var(--ui-accent);
}

.ticket-error, .ticket-notice {
  font-size: 12px;
  line-height: 1.7;
}

.ticket-empty {
  padding: 48px 0;
  text-align: center;
  color: var(--ui-muted);
  font-size: 13px;
}

.ticket-more {
  margin: 18px auto;
  display: flex;
}

@media (max-width: 760px) {
  .ticket-list {
    grid-template-columns: 1fr;
  }

  .ticket-detail, .ticket-composer, .ticket-row {
    padding: 16px;
  }
}
</style>
