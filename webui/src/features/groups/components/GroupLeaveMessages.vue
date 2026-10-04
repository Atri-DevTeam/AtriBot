<template>
  <button ref="trigger" type="button" class="leave-trigger" :class="{ active: open }"
          :title="`群留言 · ${notes.length} 条待发送`" :aria-label="`群留言，${notes.length} 条待发送`"
          aria-haspopup="dialog" :aria-expanded="open" @click="toggle">
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
         stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M20 11V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h6" />
      <path d="M7 8h9M7 12h5m2 5 5.5-5.5a1.4 1.4 0 0 1 2 2L16 19l-3 1 1-3Z" />
    </svg>
    <span v-if="notes.length" class="leave-count" aria-hidden="true">{{ notes.length }}</span>
  </button>
  <Teleport to="body">
    <div v-if="open" class="leave-backdrop" @click="close" />
    <Transition name="leave-menu">
      <section v-if="open" ref="menu" class="leave-menu" :style="menuStyle" role="dialog"
               aria-modal="true" aria-label="群留言设置" @keydown="onMenuKeydown">
        <header class="leave-head">
          <strong>群留言 <span>{{ notes.length }} / 20</span></strong>
          <button type="button" aria-label="关闭留言设置" @click="close">×</button>
        </header>
        <div class="leave-body">
          <p class="leave-tip">群留言消息，等待发送队列</p>
          <p v-if="loadError || actionError" class="leave-error" role="alert">{{ actionError || loadError }}</p>
          <div class="leave-list" aria-label="待发送留言">
            <p v-if="loading && !loaded" class="leave-empty">正在加载留言…</p>
            <p v-else-if="loaded && !notes.length" class="leave-empty">暂无待发送留言</p>
            <article v-for="(note, index) in notes" :key="note.id" class="leave-item">
              <div class="leave-item-head">
                <span>留言 {{ index + 1 }}<small v-if="note.attempts"> · 上次发送失败，等待重试</small></span>
                <button type="button" :disabled="busy" :aria-label="`删除留言 ${index + 1}`" @click="remove(note.id)">删除</button>
              </div>
              <pre>{{ note.content }}</pre>
            </article>
          </div>
          <label class="leave-label">新留言
            <textarea ref="editor" v-model="draft" rows="5" maxlength="16000" :disabled="busy"
                      placeholder="输入 Markdown 留言内容" @keydown.enter.stop />
          </label>
        </div>
        <footer class="leave-foot">
          <button type="button" class="leave-refresh" :disabled="busy || loading" @click="refresh">刷新</button>
          <span>{{ draft.length }} / 16000</span>
          <button type="button" class="leave-save" :disabled="busy || !loaded || !draft.trim() || notes.length >= 20" @click="add">
            {{ busy ? '处理中…' : '添加留言' }}
          </button>
        </footer>
      </section>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({ groupId: { type: String, required: true }, request: { type: Function, required: true } })
const trigger = ref(null)
const menu = ref(null)
const editor = ref(null)
const open = ref(false)
const menuStyle = ref({})
const notes = ref([])
const draft = ref('')
const loading = ref(false)
const loaded = ref(false)
const busy = ref(false)
const loadError = ref('')
const actionError = ref('')
const endpoint = `/groups/${encodeURIComponent(props.groupId)}/leave-messages`
let alive = true
let sequence = 0
let timer
let controller

async function refresh() {
  if (!alive || busy.value) return
  const seq = ++sequence
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  try {
    const data = await props.request(endpoint, { signal: controller.signal })
    if (!alive || seq !== sequence) return
    notes.value = data || []
    loaded.value = true
    loadError.value = ''
  } catch (error) {
    if (alive && seq === sequence && error.name !== 'AbortError') loadError.value = error.message || '读取留言失败'
  } finally {
    if (alive && seq === sequence) loading.value = false
  }
}

async function mutate(run) {
  if (busy.value) return
  busy.value = true
  sequence++
  controller?.abort()
  loading.value = false
  actionError.value = ''
  try {
    await run()
  } catch (error) {
    if (alive) actionError.value = error.message || '留言操作失败'
  } finally {
    if (alive) {
      busy.value = false
      await refresh()
    }
  }
}

async function add() {
  if (!draft.value.trim() || !loaded.value || notes.value.length >= 20) return
  await mutate(async () => {
    const content = draft.value
    const result = await props.request(endpoint, { method: 'POST', body: JSON.stringify({ content }) })
    if (!alive) return
    notes.value.push({ id: result.id, content, attempts: 0 })
    draft.value = ''
  })
}

async function remove(id) {
  await mutate(async () => {
    await props.request(`${endpoint}/${encodeURIComponent(id)}`, { method: 'DELETE' })
    if (alive) notes.value = notes.value.filter(note => note.id !== id)
  })
}

function positionMenu() {
  if (!open.value || !trigger.value) return
  const rect = trigger.value.getBoundingClientRect()
  const viewport = window.visualViewport
  const height = viewport?.height || window.innerHeight
  const offsetTop = viewport?.offsetTop || 0
  const bottom = Math.max(12, window.innerHeight - Math.min(rect.top - 8, offsetTop + height - 12))
  const available = window.innerHeight - bottom - offsetTop - 12
  menuStyle.value = available < 240
    ? { top: `${offsetTop + 12}px`, right: '12px', maxHeight: `${Math.max(100, height - 24)}px` }
    : { bottom: `${bottom}px`, right: `${Math.max(12, window.innerWidth - rect.right)}px`, maxHeight: `${Math.min(540, available)}px` }
}

async function toggle() {
  if (open.value) return close()
  open.value = true
  positionMenu()
  refresh()
  await nextTick()
  editor.value?.focus()
}

function close() {
  open.value = false
  trigger.value?.focus()
}

function onMenuKeydown(event) {
  if (event.key === 'Escape') { event.stopPropagation(); close(); return }
  if (event.key !== 'Tab') return
  const fields = [...menu.value.querySelectorAll('button:not(:disabled), textarea:not(:disabled)')]
  const first = fields[0], last = fields.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}

onMounted(() => {
  refresh()
  timer = setInterval(() => { if (!document.hidden && !loading.value) refresh() }, 5000)
  window.addEventListener('resize', positionMenu)
  window.visualViewport?.addEventListener('resize', positionMenu)
  window.visualViewport?.addEventListener('scroll', positionMenu)
})
onBeforeUnmount(() => {
  alive = false
  sequence++
  controller?.abort()
  clearInterval(timer)
  window.removeEventListener('resize', positionMenu)
  window.visualViewport?.removeEventListener('resize', positionMenu)
  window.visualViewport?.removeEventListener('scroll', positionMenu)
})
</script>

<style scoped>
.leave-trigger { position: relative; display: grid; place-items: center; width: 30px; height: 30px; flex-shrink: 0; padding: 0; border: 0; border-radius: 6px; background: transparent; color: #6b7686; }
.leave-trigger:hover, .leave-trigger.active { background: #e0f0ff; color: #0099ff; }
.leave-trigger:focus-visible { outline: 2px solid #0099ff; outline-offset: 2px; }
.leave-count { position: absolute; right: -3px; bottom: -4px; min-width: 14px; padding: 0 2px; color: #e53935; background: #f7f8fa; border-radius: 4px; font-size: 11px; font-weight: 700; line-height: 14px; text-align: center; pointer-events: none; }
.leave-backdrop { position: fixed; inset: 0; z-index: 1199; }
.leave-menu { position: fixed; z-index: 1200; display: flex; flex-direction: column; width: min(380px, calc(100vw - 24px)); overflow: hidden; border: 1px solid #e9eaed; border-radius: 12px; background: #fff; color: #262626; box-shadow: 0 10px 36px rgb(22 37 58 / 16%); font-size: 13px; }
.leave-head, .leave-foot { display: flex; align-items: center; gap: 10px; padding: 12px 14px; flex-shrink: 0; }
.leave-head { justify-content: space-between; border-bottom: 1px solid #f0f1f3; }
.leave-head strong { font-size: 14px; }
.leave-head strong span { margin-left: 6px; font-size: 12px; font-weight: 400; color: #84878e; }
.leave-head button { width: 26px; height: 26px; padding: 0; border: 0; border-radius: 6px; background: #f7f8fa; color: #6b7686; font-size: 20px; }
.leave-body { min-height: 0; overflow-y: auto; padding: 12px 14px; }
.leave-tip { margin: 0 0 12px; color: #84878e; font-size: 12px; line-height: 1.6; }
.leave-error { margin: 0 0 10px; color: #c1362f; overflow-wrap: anywhere; }
.leave-empty { margin: 12px 0; color: #95989f; text-align: center; }
.leave-list { display: grid; gap: 8px; margin-bottom: 12px; }
.leave-item { padding: 10px; border: 1px solid #e9eaed; border-radius: 8px; background: #fafbfc; }
.leave-item-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; color: #84878e; font-size: 12px; }
.leave-item-head small { color: #a76100; }
.leave-item-head button { flex-shrink: 0; border: 0; background: transparent; color: #c1362f; font-size: 12px; }
.leave-item pre { max-height: 130px; overflow-y: auto; margin: 8px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; line-height: 1.6; }
.leave-label { display: grid; gap: 7px; }
.leave-label textarea { display: block; box-sizing: border-box; width: 100%; min-height: 100px; padding: 10px; resize: vertical; border: 1px solid #dedfe3; border-radius: 8px; background: #fff; color: #262626; font: inherit; line-height: 1.6; }
.leave-label textarea:focus { outline: none; border-color: #0099ff; box-shadow: 0 0 0 3px rgb(0 153 255 / 12%); }
.leave-foot { border-top: 1px solid #f0f1f3; }
.leave-foot span { flex: 1; color: #95989f; font-size: 11px; }
.leave-foot button { padding: 6px 10px; border: 1px solid #dedfe3; border-radius: 7px; background: #fff; color: #51545a; font-size: 12px; }
.leave-foot .leave-save { border-color: #0099ff; background: #0099ff; color: #fff; }
.leave-foot .leave-save:hover:not(:disabled) { background: #008bea; }
.leave-menu-enter-active, .leave-menu-leave-active { transition: opacity 150ms ease, transform 150ms ease; }
.leave-menu-enter-from, .leave-menu-leave-to { opacity: 0; transform: translateY(6px); }
@media (prefers-reduced-motion: reduce) { .leave-menu-enter-active, .leave-menu-leave-active { transition: none; } }
</style>
