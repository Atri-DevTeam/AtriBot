<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import botBlue from '../assets/bot-blue.svg'
import { renderMarkdown } from '../lib/markdown.js'
import { buttonStyles, createWelcomeButton, imageMarkdown, readWelcomeDraft, serializeWelcomeDraft } from '../lib/welcomeEditor.js'

const props = defineProps({
  groupOpenId: { type: String, required: true },
  botName: { type: String, default: 'AtriBot' },
  botAvatarUrl: { type: String, default: '' },
  debugGroupId: { type: String, default: '' },
  request: { type: Function, required: true }
})
const emit = defineEmits(['enabled-change'])
const avatarFailed = ref(false)
watch(() => props.botAvatarUrl, () => { avatarFailed.value = false })
const loading = ref(true)
const loaded = ref(false)
const saving = ref(false)
const testing = ref(false)
const custom = ref(false)
const enabled = ref(true)
const error = ref('')
const notice = ref('')
const draft = ref(readWelcomeDraft())
const baseline = ref(JSON.stringify(draft.value))
const selected = ref(null)
const markdownInput = ref(null)
const imageForm = reactive({ url: '', width: 800, height: 450 })
const imageError = ref('')
const busy = computed(() => loading.value || saving.value || testing.value)
const dirty = computed(() => loaded.value && JSON.stringify(draft.value) !== baseline.value)
const hasContent = computed(() => !!draft.value.text.trim() || draft.value.keyboard.length > 0)
const selectedButton = computed(() => selected.value ? draft.value.keyboard[selected.value.row]?.[selected.value.column] : null)
const preview = computed(() => renderMarkdown(`@新成员${draft.value.text.trim() ? ` ${draft.value.text}` : ''}`)
  .replace('@新成员', '<span class="welcome-mention">@新成员</span>'))
const allowedUsers = computed({
  get: () => selectedButton.value?.allowed_open_ids.join('\n') || '',
  set: value => {
    if (selectedButton.value) selectedButton.value.allowed_open_ids = [...new Set(value.split(/[\s,，]+/).filter(Boolean))]
  }
})
let nextButtonId = 0
let disposed = false
let loadController = null
// 父组件按群 OpenID 设置 key，每次打开都只编辑这一群。
const groupOpenId = props.groupOpenId
const endpoint = `/groups/${encodeURIComponent(groupOpenId)}/join-welcome`

function acceptDraft(config) {
  draft.value = readWelcomeDraft(config)
  baseline.value = JSON.stringify(draft.value)
  selected.value = null
  loaded.value = true
}

function mayLeave() {
  if (saving.value || testing.value) {
    notice.value = testing.value ? '正在发送调试消息，请稍候。' : '正在保存设置，请稍候。'
    return false
  }
  return !dirty.value || window.confirm('有未保存的欢迎配置，确定放弃修改吗？')
}

// 会话失效和已确认退出时，允许父页面离开而不重复弹出浏览器提示。
function releaseLeaveGuard() {
  loaded.value = false
  window.removeEventListener('beforeunload', beforeUnload)
}
defineExpose({ mayLeave, releaseLeaveGuard })

async function load() {
  if (disposed || loadController) return
  loading.value = true
  error.value = ''
  loadController = new AbortController()
  try {
    const result = await props.request(endpoint, { signal: loadController.signal })
    if (disposed) return
    acceptDraft(result.config)
    custom.value = result.custom
    enabled.value = result.enabled
    emit('enabled-change', enabled.value)
  } catch (e) {
    if (!disposed) error.value = e.message
  } finally {
    loadController = null
    if (!disposed) loading.value = false
  }
}

async function save() {
  if (busy.value || !loaded.value || !hasContent.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await props.request(endpoint, { method: 'PUT', body: JSON.stringify(serializeWelcomeDraft(draft.value)) })
    if (disposed) return
    acceptDraft(result)
    custom.value = true
    notice.value = enabled.value ? '已保存，下次成员入群时生效。' : '已保存，本群入群欢迎仍为关闭状态。'
  } catch (e) { if (!disposed) error.value = e.message }
  finally { saving.value = false }
}

async function restoreDefault() {
  if (busy.value || !loaded.value || !window.confirm('确定恢复默认欢迎？本群自定义内容及未保存的修改将被清除，欢迎开关保持不变。')) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    await props.request(endpoint, { method: 'DELETE' })
    if (disposed) return
    acceptDraft({})
    custom.value = false
    notice.value = '已恢复默认欢迎。'
  } catch (e) { if (!disposed) error.value = e.message }
  finally { saving.value = false }
}

async function toggleEnabled() {
  if (busy.value || !loaded.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  const next = !enabled.value
  try {
    await props.request(`/groups/${encodeURIComponent(groupOpenId)}/functions/member_add_welcome?enabled=${next}`, { method: 'POST' })
    if (disposed) return
    enabled.value = next
    emit('enabled-change', next)
    notice.value = `入群欢迎已${next ? '开启' : '关闭'}，正文和按钮需单独保存。`
  } catch (e) { if (!disposed) error.value = e.message }
  finally { saving.value = false }
}

async function sendTest() {
  if (busy.value || !loaded.value || !hasContent.value) return
  if (!window.confirm(`将当前草稿发送到 QQ 调试群 ${props.debugGroupId || '（以后端配置为准）'}，不保存配置。测试按钮可以触发真实指令，确定发送吗？`)) return
  testing.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await props.request('/join-welcome/test', { method: 'POST', body: JSON.stringify(serializeWelcomeDraft(draft.value)) })
    if (!disposed) notice.value = `测试已发送到调试群 ${result.debugGroupId}，消息 ID：${result.messageId}`
  } catch (e) { if (!disposed) error.value = e.message }
  finally { testing.value = false }
}

function addButton(row) {
  if (draft.value.keyboard[row].length >= 10) return
  draft.value.keyboard[row].push(createWelcomeButton(`welcome_${Date.now().toString(36)}_${nextButtonId++}`))
  selected.value = { row, column: draft.value.keyboard[row].length - 1 }
}
function addRow() {
  if (draft.value.keyboard.length >= 5) return
  draft.value.keyboard.push([])
  addButton(draft.value.keyboard.length - 1)
}
function removeButton() {
  const { row, column } = selected.value
  draft.value.keyboard[row].splice(column, 1)
  if (!draft.value.keyboard[row].length) draft.value.keyboard.splice(row, 1)
  selected.value = null
}
function moveButton(direction) {
  const { row, column } = selected.value
  const buttons = draft.value.keyboard[row]
  const target = column + direction
  if (target < 0 || target >= buttons.length) return
  buttons.splice(target, 0, buttons.splice(column, 1)[0])
  selected.value = { row, column: target }
}
function moveRow(row, direction) {
  const target = row + direction
  if (target < 0 || target >= draft.value.keyboard.length) return
  draft.value.keyboard.splice(target, 0, draft.value.keyboard.splice(row, 1)[0])
  selected.value = null
}
async function insertImage() {
  imageError.value = ''
  try {
    const snippet = imageMarkdown(imageForm.url, imageForm.width, imageForm.height)
    const input = markdownInput.value
    const start = input?.selectionStart ?? draft.value.text.length
    const end = input?.selectionEnd ?? start
    const insertion = `\n\n${snippet}\n\n`
    if (draft.value.text.length - (end - start) + insertion.length > 16000) throw new Error('插入后正文超过 16000 字符')
    draft.value.text = draft.value.text.slice(0, start) + insertion + draft.value.text.slice(end)
    await nextTick()
    input?.focus()
    input?.setSelectionRange(start + insertion.length, start + insertion.length)
    imageForm.url = ''
  } catch (e) { imageError.value = e.message }
}
function beforeUnload(event) {
  if (dirty.value || saving.value || testing.value) { event.preventDefault(); event.returnValue = '' }
}
onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  load()
})
onBeforeUnmount(() => {
  disposed = true
  loadController?.abort()
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<template>
  <div class="welcome-panel">
    <div class="chatnt-members-list chatnt-info welcome-scroll">
      <p v-if="error" class="welcome-alert welcome-error" role="alert">{{ error }}</p>
      <p v-if="notice" class="welcome-alert" role="status">{{ notice }}</p>
      <div v-if="loading" class="chatnt-members-empty">正在加载欢迎配置</div>
      <div v-else-if="!loaded" class="welcome-load-error">
        <span>欢迎配置加载失败</span>
        <button type="button" class="nt-mini-btn" @click="load">重新加载</button>
      </div>
      <template v-else>
        <section class="chatnt-info-section">
          <div class="chatnt-info-label">欢迎设置</div>
          <div class="nt-card">
            <div class="nt-row">
              <span class="nt-row-label">入群欢迎<span class="nt-row-note">{{ custom ? '已配置自定义欢迎' : '当前使用默认欢迎' }}</span></span>
              <button type="button" class="nt-switch" :class="{ on: enabled }" role="switch"
                      aria-label="入群欢迎" :aria-checked="enabled" :disabled="busy" @click="toggleEnabled"><span class="nt-switch-knob" /></button>
            </div>
          </div>
        </section>
        <fieldset class="welcome-editor" :disabled="busy" aria-label="入群欢迎内容">
          <section class="chatnt-info-section">
            <div class="chatnt-info-label chatnt-info-label-line"><label for="welcome-markdown">Markdown 正文</label><span>{{ draft.text.length }} / 16000</span></div>
            <div class="nt-card welcome-form">
              <textarea id="welcome-markdown" ref="markdownInput" v-model="draft.text" rows="8" maxlength="16000"
                        placeholder="欢迎来到本群喵~&#10;&#10;**入群须知**&#10;- 请先阅读群公告" />
              <details>
                <summary>插入图片</summary>
                <div class="welcome-fields">
                  <label>图片 URL<input v-model="imageForm.url" type="url" placeholder="https://example.com/welcome.png" /></label>
                  <div class="welcome-dimensions">
                    <label>宽度（px）<input v-model.number="imageForm.width" type="number" min="1" max="4096" /></label>
                    <label>高度（px）<input v-model.number="imageForm.height" type="number" min="1" max="4096" /></label>
                  </div>
                  <span class="welcome-muted">使用 QQ 可访问的图片直链。</span>
                  <button type="button" class="nt-mini-btn" @click="insertImage">插入正文</button>
                  <span v-if="imageError" class="welcome-error" role="alert">{{ imageError }}</span>
                </div>
              </details>
            </div>
          </section>
          <section class="chatnt-info-section">
            <div class="chatnt-info-label chatnt-info-label-line">
              <span>按钮布局</span><button type="button" class="nt-mini-btn" :disabled="draft.keyboard.length >= 5" @click="addRow">＋ 添加一行</button>
            </div>
            <div class="nt-card welcome-form">
              <label>按钮字号<select v-model="draft.button_size"><option value="UNDEFINED">默认</option><option value="SMALL">小按钮</option></select></label>
              <div v-if="!draft.keyboard.length" class="nt-empty">尚未配置按钮</div>
              <div v-for="(row, rowIndex) in draft.keyboard" :key="rowIndex" class="welcome-layout-row">
                <div class="welcome-row-tools">
                  <span class="welcome-muted">第 {{ rowIndex + 1 }} 行</span>
                  <button type="button" class="nt-mini-btn" :disabled="rowIndex === 0" :aria-label="`上移第 ${rowIndex + 1} 行`" @click="moveRow(rowIndex, -1)">↑</button>
                  <button type="button" class="nt-mini-btn" :disabled="rowIndex === draft.keyboard.length - 1" :aria-label="`下移第 ${rowIndex + 1} 行`" @click="moveRow(rowIndex, 1)">↓</button>
                  <button type="button" class="nt-mini-btn" :disabled="row.length >= 10" @click="addButton(rowIndex)">＋ 按钮</button>
                </div>
                <div class="welcome-button-row">
                  <button v-for="(button, column) in row" :key="column" type="button" class="welcome-qq-button"
                          :class="[button.style, { selected: selected?.row === rowIndex && selected?.column === column }]"
                          :aria-pressed="selected?.row === rowIndex && selected?.column === column"
                          @click="selected = { row: rowIndex, column }">{{ button.display_text || '未命名按钮' }}</button>
                </div>
              </div>
            </div>
            <div v-if="selectedButton" class="nt-card welcome-form welcome-button-editor">
              <div class="welcome-row-tools">
                <span>第 {{ selected.row + 1 }} 行 · 按钮 {{ selected.column + 1 }}</span>
                <button type="button" class="nt-mini-btn welcome-danger" @click="removeButton">删除</button>
              </div>
              <label>显示文字<input v-model="selectedButton.display_text" maxlength="128" /></label>
              <label>点击后文字<input v-model="selectedButton.visited_display_text" maxlength="128" placeholder="留空保持显示文字" /></label>
              <label>按钮样式<select v-model="selectedButton.style"><option v-for="(label, value) in buttonStyles" :key="value" :value="value">{{ label }}</option></select></label>
              <label>动作类型<select v-model="selectedButton.type"><option value="COMMAND">输入 / 执行指令</option><option value="LINK">打开链接</option><option value="CALLBACK">回调</option></select></label>
              <label>{{ selectedButton.type === 'LINK' ? '链接 URL' : selectedButton.type === 'CALLBACK' ? '回调数据' : '指令内容' }}
                <textarea v-model="selectedButton.data" rows="2" maxlength="4096" :placeholder="selectedButton.type === 'LINK' ? 'https://example.com' : '/help'" />
              </label>
              <span v-if="selectedButton.type === 'CALLBACK'" class="welcome-muted">回调数据需由已有后端回调逻辑处理。</span>
              <template v-if="selectedButton.type === 'COMMAND'">
                <label class="welcome-check"><input v-model="selectedButton.enter" type="checkbox" />点击后直接发送</label>
                <label class="welcome-check"><input v-model="selectedButton.reply" type="checkbox" />携带引用回复</label>
              </template>
              <label>谁可以点击<select v-model="selectedButton.permission"><option value="ALL">所有人</option><option value="ADMIN">群管理员</option><option value="SPECIFIC_USER">指定用户</option></select></label>
              <label v-if="selectedButton.permission === 'SPECIFIC_USER'">允许用户的 OpenID<textarea v-model="allowedUsers" rows="3" placeholder="每行一个或用逗号分隔，不是 QQ 号码" /></label>
              <details>
                <summary>高级设置</summary>
                <div class="welcome-fields">
                  <label>按钮 ID<input v-model="selectedButton.button_id" maxlength="64" /></label>
                  <label>按钮分组 ID<input v-model="selectedButton.button_group_id" maxlength="64" placeholder="可选" /></label>
                  <label>确认弹窗内容<textarea v-model="selectedButton.modal.content" rows="2" maxlength="1024" placeholder="留空不弹窗" /></label>
                  <label>确认文字<input v-model="selectedButton.modal.confirm_text" maxlength="4" placeholder="留空使用默认" /></label>
                  <label>取消文字<input v-model="selectedButton.modal.cancel_text" maxlength="4" placeholder="留空使用默认" /></label>
                </div>
              </details>
              <div class="welcome-row-tools">
                <button type="button" class="nt-mini-btn" :disabled="selected.column === 0" @click="moveButton(-1)">← 左移</button>
                <button type="button" class="nt-mini-btn" :disabled="selected.column === draft.keyboard[selected.row].length - 1" @click="moveButton(1)">右移 →</button>
              </div>
            </div>
          </section>
          <section class="chatnt-info-section">
            <details class="nt-card welcome-form" open>
              <summary>消息预览与调试</summary>
              <div v-if="!hasContent" class="nt-empty">尚未设置自定义内容</div>
              <div v-else class="welcome-preview welcome-message">
                <span class="welcome-bot">
                  <img v-if="botAvatarUrl && !avatarFailed" :src="botAvatarUrl" alt="发送者头像"
                       referrerpolicy="no-referrer" @error="avatarFailed = true" />
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="#90969D" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M12 7a8 8 0 0 1 8 8v2.4a2.6 2.6 0 0 1-2.6 2.6H6.6A2.6 2.6 0 0 1 4 17.4V15a8 8 0 0 1 8-8ZM12 4.4V7M9.4 12.2v2.4M14.6 12.2v2.4" />
                    <circle cx="12" cy="3.3" r="1.5" fill="#90969D" stroke="none" />
                  </svg>
                </span>
                <div class="welcome-message-content">
                  <div class="welcome-sender"><span>{{ botName || '机器人' }}</span><img :src="botBlue" alt="机器人" /></div>
                  <div class="welcome-bubble">
                    <div class="welcome-markdown" v-html="preview" />
                    <div v-if="draft.keyboard.length" class="welcome-keyboard" :class="{ small: draft.button_size === 'SMALL' }" aria-label="欢迎按钮预览">
                      <div v-for="(row, rowIndex) in draft.keyboard" :key="rowIndex" class="welcome-button-row">
                        <button v-for="(button, column) in row" :key="column" type="button" class="welcome-qq-button" :class="button.style"
                                title="点击编辑按钮" @click="selected = { row: rowIndex, column }">{{ button.display_text || '未命名按钮' }}</button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <p class="welcome-muted">调试群：{{ debugGroupId || '未配置，请前往机器人设置填写调试群 OpenId' }}</p>
              <button type="button" class="nt-mini-btn welcome-debug-button" title="发送当前草稿到调试群" :disabled="busy || !hasContent" @click="sendTest">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <rect x="7" y="7" width="10" height="14" rx="5" />
                  <path d="M9 7V5a3 3 0 0 1 6 0v2M12 11v10M7 10H4L2 8M17 10h3l2-2M7 14H2M17 14h5M7 18H4l-2 2M17 18h3l2 2" />
                </svg>
                <span>{{ testing ? '调试中…' : '调试' }}</span>
              </button>
            </details>
          </section>
        </fieldset>
      </template>
    </div>
    <footer v-if="loaded && !loading" class="welcome-save-bar">
      <span class="welcome-muted" aria-live="polite">{{ dirty ? '有未保存的修改' : custom ? '暂无修改' : '当前使用默认欢迎' }}</span>
      <div class="welcome-row-tools">
        <button type="button" class="nt-mini-btn welcome-danger" :disabled="busy || (!custom && !dirty)" @click="restoreDefault">恢复默认</button>
        <button type="button" class="nt-btn-primary" :disabled="busy || !hasContent || (custom && !dirty)" @click="save">{{ saving ? '处理中…' : '保存配置' }}</button>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.welcome-panel { display: flex; flex: 1; flex-direction: column; min-height: 0; min-width: 0; color: #1f2329; font-size: 13px; }
.welcome-scroll { overscroll-behavior: contain; }
.welcome-debug-button { display: inline-flex; align-items: center; gap: 6px; }
.welcome-debug-button svg { flex-shrink: 0; }
.welcome-editor { margin: 0; padding: 0; border: 0; min-width: 0; }
.welcome-form { display: flex; flex-direction: column; gap: 12px; padding: 12px; }
.welcome-form label, .welcome-fields { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.welcome-fields { gap: 12px; margin-top: 12px; }
.welcome-form input:not([type=checkbox]), .welcome-form textarea, .welcome-form select { width: 100%; min-width: 0; padding: 7px 9px; border: 1px solid #e4e6ea; border-radius: 6px; background: #fff; color: #1f2329; font: inherit; }
.welcome-form textarea { resize: vertical; line-height: 1.6; }
#welcome-markdown { font-family: var(--font-mono); }
.welcome-panel :is(input, textarea, select, button, summary):focus-visible { outline: 2px solid #0099ff; outline-offset: 2px; }
.welcome-form .welcome-check { flex-direction: row; align-items: center; gap: 8px; }
.welcome-check input { margin: 0; accent-color: #0099ff; }
.welcome-dimensions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.welcome-muted { color: #9aa3b2; font-size: 11px; line-height: 1.6; overflow-wrap: anywhere; }
.welcome-form summary { cursor: pointer; color: #6b7686; font-size: 12px; }
.welcome-layout-row { border-top: 1px solid #eceef1; padding-top: 10px; }
.welcome-row-tools { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 6px; }
.welcome-row-tools > span { margin-right: auto; }
.welcome-button-row { display: flex; gap: 4px; margin-top: 8px; }
.welcome-button-editor { margin-top: 10px; }
.welcome-qq-button { flex: 1; min-width: 0; overflow-wrap: anywhere; border: 1px solid #b5cce9; background: #fff; color: #2473cd; border-radius: 5px; padding: 7px 3px; font-size: 12px; line-height: 1.4; }
.welcome-qq-button.GRAY { border-color: #d7dce3; color: #596273; }
.welcome-qq-button.RED { color: #c1362f; border-color: #e7b5b2; }
.welcome-qq-button.BLUE_WITH_BACKGROUND { color: #fff; background: #337cd9; border-color: #337cd9; }
.welcome-qq-button.ICON_BUTTON { background: #edf4fe; }
.welcome-qq-button.selected { outline: 2px solid #0099ff; outline-offset: 1px; }
.welcome-preview { margin-top: 12px; }
/* 与 miniapp 群资料的欢迎消息预览保持相同的头像、署名和气泡样式。 */
.welcome-message { display: flex; align-items: flex-start; gap: 11px; }
.welcome-bot { display: grid; place-items: center; width: 36px; height: 36px; border: 1px solid #fff; border-radius: 50%; background: #ffffff80; flex-shrink: 0; overflow: hidden; }
.welcome-bot img { width: 100%; height: 100%; object-fit: cover; }
.welcome-bot svg { width: 20px; height: 20px; }
.welcome-message-content { flex: 1; min-width: 0; }
.welcome-sender { display: flex; align-items: center; gap: 5px; margin: 0 0 6px 2px; min-height: 16px; font-size: 11px; line-height: 16px; color: #8a9288; }
.welcome-sender span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.welcome-sender img { width: 14px; height: 14px; flex-shrink: 0; }
.welcome-bubble { min-width: 0; background: #ffffff91; border: 1px solid #fff; border-radius: 4px 16px 16px; padding: 16px; }
.welcome-markdown { font-size: 12px; line-height: 1.85; overflow-wrap: anywhere; color: #64735e; }
.welcome-markdown :deep(> :first-child) { margin-top: 0; }
.welcome-markdown :deep(> :last-child) { margin-bottom: 0; }
.welcome-markdown :deep(p) { margin: 10px 0; }
.welcome-markdown :deep(h1), .welcome-markdown :deep(h2), .welcome-markdown :deep(h3), .welcome-markdown :deep(h4), .welcome-markdown :deep(h5), .welcome-markdown :deep(h6) { font-size: 14px; font-weight: 600; margin: 14px 0 8px; }
.welcome-markdown :deep(img) { display: block; max-width: 100%; height: auto; border-radius: 9px; }
.welcome-markdown :deep(img.md-inline-icon) { display: inline-block; vertical-align: middle; border-radius: 0; }
.welcome-markdown :deep(ul), .welcome-markdown :deep(ol) { padding-left: 20px; }
.welcome-markdown :deep(pre) { white-space: pre-wrap; background: #eef1e880; padding: 10px; border-radius: 8px; }
.welcome-markdown :deep(blockquote) { border-left: 2px solid #c9d5bf; margin: 10px 0; padding-left: 10px; color: #89917f; }
.welcome-markdown :deep(hr) { border: 0; border-top: 1px solid #b5c3a740; }
.welcome-markdown :deep(a), .welcome-markdown :deep(.welcome-mention) { color: #6e93b4; }
.welcome-markdown :deep(.katex) { max-width: 100%; overflow-x: auto; overflow-y: hidden; }
.welcome-keyboard { margin-top: 12px; }
.welcome-keyboard .welcome-button-row { gap: 6px; margin-top: 7px; }
.welcome-keyboard .welcome-qq-button { text-align: center; background: #ffffffb0; border-radius: 6px; padding: 7px 5px; line-height: 1.5; }
.welcome-keyboard .welcome-qq-button.BLUE_WITH_BACKGROUND { background: #337cd9; }
.welcome-keyboard .welcome-qq-button.ICON_BUTTON { background: #edf4fe; }
.welcome-keyboard.small .welcome-qq-button { font-size: 11px; }
.welcome-alert { padding: 10px 12px; border-radius: 6px; background: #f2f7fc; color: #42729d; font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.welcome-error, .welcome-danger { color: #d14b4b; }
.welcome-load-error { display: grid; justify-items: center; gap: 12px; padding: 24px 0; color: #9aa3b2; }
.welcome-save-bar { flex-shrink: 0; display: grid; gap: 8px; padding: 12px 14px; border-top: 1px solid #e4e6ea; background: #fff; }
@media (max-width: 760px) {
  .welcome-message { gap: 8px; }
  .welcome-bubble { padding: 12px; }
}
</style>
