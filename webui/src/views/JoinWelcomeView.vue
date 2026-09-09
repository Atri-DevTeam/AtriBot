<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import AppSidebar from '../components/AppSidebar.vue'
import { API_BASE } from '../router.js'
import { renderMarkdown } from '../lib/markdown.js'
import { buttonStyles, createWelcomeButton, imageMarkdown, readWelcomeDraft, serializeWelcomeDraft } from '../lib/welcomeEditor.js'

/**
 * @Author YZ_Ljc_
 * @ClassName JoinWelcomeView
 * @Created_at 2026/09/07
 * @Project AtriMeow
 * @Package webui.src.views
 */
const router = useRouter()
const sidebarOpen = ref(false)
const identity = reactive({ botName: 'AtriBot', appId: '', botOpenId: '' })
const groups = ref([])
const search = ref('')
const pickerOpen = ref(false)
const picker = ref(null)
const pickerSearch = ref(null)
const pickerTrigger = ref(null)
const activeGroup = ref(0)
const groupId = ref('')
const loadingGroups = ref(true)
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const debugGroupId = ref('')
const loaded = ref(false)
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
let nextButtonId = 0
const busy = computed(() => loading.value || saving.value || testing.value)
const dirty = computed(() => loaded.value && JSON.stringify(draft.value) !== baseline.value)
const filteredGroups = computed(() => {
  const term = search.value.trim().toLowerCase()
  return groups.value.filter(group => `${group.groupName || ''} ${group.realGroupId || ''} ${group.groupOpenId}`.toLowerCase().includes(term))
})
const currentGroup = computed(() => groups.value.find(group => group.groupOpenId === groupId.value))
const selectedButton = computed(() => selected.value ? draft.value.keyboard[selected.value.row]?.[selected.value.column] : null)
const preview = computed(() => {
  const text = draft.value.text
  const message = `@新成员${text.trim() ? ` ${text}` : ''}`
  return renderMarkdown(message).replace('@新成员', '<span class="welcome-mention">@新成员</span>')
})
const allowedUsers = computed({
  get: () => selectedButton.value?.allowed_open_ids.join('\n') || '',
  set: value => { if (selectedButton.value) selectedButton.value.allowed_open_ids = [...new Set(value.split(/[\s,，]+/).filter(Boolean))] }
})

async function api(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: 'same-origin', cache: 'no-store', headers: { 'Content-Type': 'application/json' }, ...options
  })
  if (response.status === 401 || response.status === 503) {
    loaded.value = false
    router.replace('/login')
    throw new Error('登录已失效或 WebUI 已关闭，请重新登录')
  }
  const payload = await response.json().catch(() => null)
  if (!response.ok || payload?.status !== 200) throw new Error(payload?.message || `请求失败（HTTP ${response.status}）`)
  return payload.data
}

function endpoint(id = groupId.value) { return `/groups/${encodeURIComponent(id)}/join-welcome` }
function acceptDraft(config) {
  draft.value = readWelcomeDraft(config)
  baseline.value = JSON.stringify(draft.value)
  selected.value = null
  loaded.value = true
}
function mayDiscard() { return !dirty.value || window.confirm('有未保存的欢迎配置，确定放弃修改吗？') }

async function loadGroups() {
  loadingGroups.value = true
  error.value = ''
  try { groups.value = await api('/groups') || [] }
  catch (e) { error.value = e.message }
  finally { loadingGroups.value = false }
}

async function togglePicker() {
  pickerOpen.value = !pickerOpen.value
  if (pickerOpen.value) {
    search.value = ''
    activeGroup.value = 0
    await nextTick()
    pickerSearch.value?.focus()
  }
}

function closePicker() {
  pickerOpen.value = false
  pickerTrigger.value?.focus()
}

function pickerKeydown(event) {
  if (event.key === 'Escape') { event.preventDefault(); closePicker() }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    activeGroup.value = Math.max(0, Math.min(filteredGroups.value.length - 1, activeGroup.value + (event.key === 'ArrowDown' ? 1 : -1)))
    nextTick(() => picker.value?.querySelector(`[data-group-index="${activeGroup.value}"]`)?.scrollIntoView({ block: 'nearest' }))
  }
  if (event.key === 'Enter' && event.target === pickerSearch.value) {
    event.preventDefault()
    const group = filteredGroups.value[activeGroup.value]
    if (group) chooseGroup(group.groupOpenId)
  }
}

function outsidePicker(event) {
  if (pickerOpen.value && !picker.value?.contains(event.target)) pickerOpen.value = false
}

async function chooseGroup(id) {
  if (busy.value || !mayDiscard()) return
  closePicker()
  groupId.value = id
  loaded.value = false
  selected.value = null
  notice.value = ''
  error.value = ''
  imageForm.url = ''
  imageError.value = ''
  if (!id) return
  loading.value = true
  try {
    const result = await api(endpoint(id))
    acceptDraft(result.config)
    custom.value = result.custom
    enabled.value = result.enabled
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

async function save() {
  if (busy.value || !loaded.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await api(endpoint(), { method: 'PUT', body: JSON.stringify(serializeWelcomeDraft(draft.value)) })
    acceptDraft(result)
    custom.value = true
    notice.value = enabled.value ? '已保存到数据库，下次成员入群时生效，无需重启。' : '已保存到数据库；本群欢迎开关仍为关闭状态。'
  } catch (e) { error.value = e.message }
  finally { saving.value = false }
}

async function restoreDefault() {
  if (busy.value || !window.confirm('确定删除本群自定义欢迎并恢复默认？当前未保存的修改也会丢弃，欢迎开关保持不变。')) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    await api(endpoint(), { method: 'DELETE' })
    acceptDraft({})
    custom.value = false
    notice.value = '已恢复系统默认欢迎，欢迎开关未改变。'
  } catch (e) { error.value = e.message }
  finally { saving.value = false }
}

async function sendTest() {
  if (busy.value || testing.value) return
  if (!window.confirm(`将当前草稿发送到 QQ 调试群 ${debugGroupId.value || '（以后端配置为准）'}，不保存配置。测试按钮可以触发真实指令，确定发送吗？`)) return
  testing.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await api('/join-welcome/test', { method: 'POST', body: JSON.stringify(serializeWelcomeDraft(draft.value)) })
    notice.value = `测试已发送到调试群 ${result.debugGroupId}，返回消息 ID：${result.messageId}`
  } catch (e) { error.value = e.message }
  finally { testing.value = false }
}

async function toggleEnabled() {
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    await api(`/groups/${encodeURIComponent(groupId.value)}/functions/member_add_welcome?enabled=${!enabled.value}`, { method: 'POST' })
    enabled.value = !enabled.value
    notice.value = `欢迎已${enabled.value ? '开启' : '关闭'}，编辑中的内容未自动保存。`
  } catch (e) { error.value = e.message }
  finally { saving.value = false }
}

function addButton(row) {
  if (draft.value.keyboard[row].length >= 5) return
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
async function logout() {
  if (!mayDiscard()) return
  try {
    await api('/auth/logout', { method: 'POST' })
    loaded.value = false
    router.replace('/login')
  } catch (e) { error.value = e.message }
}
function beforeUnload(event) {
  if (dirty.value || saving.value || testing.value) { event.preventDefault(); event.returnValue = '' }
}
onBeforeRouteLeave(() => !loaded.value || (!saving.value && !testing.value && mayDiscard()))
onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  document.addEventListener('pointerdown', outsidePicker)
  loadGroups()
  api('/config').then(config => {
    Object.assign(identity, { botName: config.botName || 'AtriBot', appId: config.appId || '', botOpenId: config.botOpenId || '' })
    debugGroupId.value = config.debugGroupId && config.debugGroupId !== 'null' ? config.debugGroupId : ''
  }).catch(() => {})
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  document.removeEventListener('pointerdown', outsidePicker)
})
</script>

<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="identity.appId" :bot-open-id="identity.botOpenId" :bot-name="identity.botName">
      <template #toolbar><button class="ghost-button" :disabled="busy" @click="logout">退出</button></template>
    </AppSidebar>
    <div class="sidebar-spacer" />
    <main class="workspace welcome-page">
      <header class="topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <line x1="3" y1="6" x2="21" y2="6"/>
              <line x1="3" y1="12" x2="21" y2="12"/>
              <line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <h2 class="feedback-title">入群欢迎</h2>
        </div>
      </header>
      <section class="content feedback-layout">
        <section class="chat-panel feedback-panel welcome-panel">
          <div class="chat-head welcome-group-picker">
            <div class="welcome-picker-copy">
              <strong>群欢迎配置</strong>
            </div>
            <div ref="picker" class="welcome-picker" @keydown="pickerKeydown">
              <button ref="pickerTrigger" class="welcome-picker-trigger" :class="{ open: pickerOpen }" :disabled="busy || loadingGroups" aria-haspopup="listbox" :aria-expanded="pickerOpen" aria-controls="welcome-group-options" @click="togglePicker">
                <span class="welcome-group-icon" aria-hidden="true">
                  <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                </span>
                <span class="welcome-group-caption" :title="currentGroup ? `${currentGroup.groupName || '未命名群'} · ${currentGroup.realGroupId || currentGroup.groupOpenId}` : '选择群聊'">
                  <strong>{{ loadingGroups ? '正在加载群列表…' : currentGroup?.groupName || (groupId ? '未命名群' : '选择群聊') }}</strong>
                </span>
                <svg class="welcome-picker-chevron" :class="{ open: pickerOpen }" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>
              </button>
              <div v-if="pickerOpen" class="welcome-picker-popover">
                <div class="welcome-picker-search">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4.5 4.5"/></svg>
                  <input ref="pickerSearch" v-model="search" role="combobox" aria-label="搜索群" aria-expanded="true" aria-controls="welcome-group-options" :aria-activedescendant="filteredGroups.length ? `welcome-group-option-${activeGroup}` : undefined" autocomplete="off" placeholder="搜索群名、群号或 OpenID" @input="activeGroup = 0" />
                  <button v-if="search" type="button" class="welcome-picker-clear" aria-label="清空搜索" @click="search = ''; activeGroup = 0; pickerSearch?.focus()">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>
                  </button>
                </div>
                <div class="welcome-picker-meta"><span>{{ filteredGroups.length }} 个群</span><button :disabled="loadingGroups" @click="loadGroups">{{ loadingGroups ? '刷新中…' : '刷新列表' }}</button></div>
                <div id="welcome-group-options" class="welcome-picker-options" role="listbox" aria-label="可配置的群">
                  <button v-for="(group, index) in filteredGroups" :id="`welcome-group-option-${index}`" :key="group.groupOpenId" role="option" :aria-selected="group.groupOpenId === groupId" :data-group-index="index" class="welcome-picker-option" :class="{ chosen: group.groupOpenId === groupId, highlighted: index === activeGroup }" @mouseenter="activeGroup = index" @click="chooseGroup(group.groupOpenId)">
                    <span class="welcome-group-letter" aria-hidden="true">{{ (group.groupName || '群').slice(0, 1) }}</span>
                    <span class="welcome-group-caption"><strong>{{ group.groupName || '未命名群' }}</strong><span>{{ group.realGroupId ? `群号 ${group.realGroupId} · ` : '' }}{{ group.groupOpenId }}</span></span>
                    <svg v-if="group.groupOpenId === groupId" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg>
                  </button>
                  <div v-if="!filteredGroups.length" class="welcome-picker-empty">{{ search ? '没有找到匹配的群，试试其他关键词' : '暂无群数据，可刷新列表重试' }}</div>
                </div>
              </div>
            </div>
          </div>
          <div class="feedback-content welcome-scroll">
            <p v-if="error" class="welcome-alert welcome-error" role="alert">{{ error }}</p>
            <p v-if="notice" class="welcome-alert welcome-success" role="status">{{ notice }}</p>
            <div v-if="loading" class="welcome-card empty-state">正在读取本群欢迎配置…</div>
            <div v-else-if="!loaded" class="welcome-card empty-state">
              <p>{{ groupId ? '配置加载失败，请重试' : '选择目标群聊...' }}</p>
              <button v-if="groupId" class="ghost-button" @click="chooseGroup(groupId)">重新加载</button>
            </div>
            <template v-else>
              <section class="welcome-card welcome-status">
                <div>
                  <strong>{{ currentGroup?.groupName || '当前群' }}</strong>
                  <span class="welcome-badge">{{ custom ? '已配置自定义欢迎' : '当前使用系统默认欢迎' }}</span>
                  <span class="welcome-badge" :class="{ 'welcome-off': !enabled }">{{ enabled ? '欢迎已开启' : '欢迎已关闭' }}</span>
                  <p class="welcome-muted">群开放平台标识：{{ groupId }}</p>
                </div>
                <button class="ghost-button" :disabled="busy" @click="toggleEnabled">{{ enabled ? '关闭欢迎' : '开启欢迎' }}</button>
              </section>
              <fieldset class="welcome-editor" :disabled="saving || testing">
                <div class="welcome-edit-column">
                  <section class="welcome-card">
                    <div class="welcome-section-head"><h3>Markdown 正文</h3><span class="welcome-muted">{{ draft.text.length }} / 16000</span></div>
                    <p class="welcome-muted">在此处编辑欢迎内容</p>
                    <textarea ref="markdownInput" v-model="draft.text" class="welcome-markdown-input" rows="11" maxlength="16000" aria-label="Markdown 正文" placeholder="欢迎来到本群喵~&#10;&#10;**入群须知**&#10;- 请先阅读群公告&#10;- 点击下方按钮查看帮助" />
                    <details class="welcome-image-tools">
                      <summary>插入图片</summary>
                      <p class="welcome-muted">使用 QQ 可访问的图片直链；此处不上传本地文件。尺寸将写入 QQ Markdown 图片语法。</p>
                      <label>图片 URL<input v-model="imageForm.url" type="url" placeholder="https://example.com/welcome.png" /></label>
                      <div class="welcome-fields">
                        <label>宽度（px）<input v-model.number="imageForm.width" type="number" min="1" max="4096" /></label>
                        <label>高度（px）<input v-model.number="imageForm.height" type="number" min="1" max="4096" /></label>
                        <button class="ghost-button" @click="insertImage">插入正文</button>
                      </div>
                      <p v-if="imageError" class="welcome-error" role="alert">{{ imageError }}</p>
                    </details>
                  </section>
                  <section class="welcome-card">
                    <div class="welcome-section-head"><h3>按钮布局</h3><button class="ghost-button" :disabled="draft.keyboard.length >= 5" @click="addRow">＋ 添加一行</button></div>
                    <p class="welcome-muted">编辑按钮属性</p>
                    <label>按钮字号<select v-model="draft.button_size"><option value="UNDEFINED">默认</option><option value="SMALL">小按钮</option></select></label>
                    <div v-if="!draft.keyboard.length" class="welcome-empty-buttons">尚未配置任何点击按钮</div>
                    <div v-for="(row, rowIndex) in draft.keyboard" :key="rowIndex" class="welcome-layout-row">
                      <div class="welcome-row-tools">
                        <span class="welcome-muted">第 {{ rowIndex + 1 }} 行</span>
                        <button class="ghost-button" :disabled="rowIndex === 0" :aria-label="`上移第 ${rowIndex + 1} 行`" @click="moveRow(rowIndex, -1)">↑</button>
                        <button class="ghost-button" :disabled="rowIndex === draft.keyboard.length - 1" :aria-label="`下移第 ${rowIndex + 1} 行`" @click="moveRow(rowIndex, 1)">↓</button>
                        <button class="ghost-button" :disabled="row.length >= 5" @click="addButton(rowIndex)">＋ 按钮</button>
                      </div>
                      <div class="welcome-button-row">
                        <button v-for="(button, column) in row" :key="column" class="welcome-qq-button" :class="[button.style, { selected: selected?.row === rowIndex && selected?.column === column }]" :aria-pressed="selected?.row === rowIndex && selected?.column === column" @click="selected = { row: rowIndex, column }">{{ button.display_text || '未命名按钮' }}</button>
                      </div>
                    </div>
                    <div v-if="selectedButton" class="welcome-button-editor">
                      <div class="welcome-section-head"><h4>编辑按钮 · 第 {{ selected.row + 1 }} 行 / 第 {{ selected.column + 1 }} 个</h4><button class="ghost-button danger" @click="removeButton">删除按钮</button></div>
                      <div class="welcome-fields">
                        <label>显示文字<input v-model="selectedButton.display_text" maxlength="128" /></label>
                        <label>点击后文字<input v-model="selectedButton.visited_display_text" maxlength="128" placeholder="留空则保持显示文字" /></label>
                        <label>按钮样式<select v-model="selectedButton.style"><option v-for="(label, value) in buttonStyles" :key="value" :value="value">{{ label }}</option></select></label>
                        <label>动作类型<select v-model="selectedButton.type"><option value="COMMAND">输入 / 执行指令</option><option value="LINK">打开链接</option><option value="CALLBACK">回调</option></select></label>
                      </div>
                      <label>{{ selectedButton.type === 'LINK' ? '链接 URL' : selectedButton.type === 'CALLBACK' ? '回调数据' : '指令内容' }}<textarea v-model="selectedButton.data" rows="2" maxlength="4096" :placeholder="selectedButton.type === 'LINK' ? 'https://example.com' : '/help'" /></label>
                      <p v-if="selectedButton.type === 'CALLBACK'" class="welcome-muted">只配置回调数据不会新增功能，需由已有后端回调处理逻辑接收。</p>
                      <div v-if="selectedButton.type === 'COMMAND'" class="welcome-checks">
                        <label><input v-model="selectedButton.enter" type="checkbox" /> 点击后直接发送（关闭则填入输入框）</label>
                        <label><input v-model="selectedButton.reply" type="checkbox" /> 携带引用回复</label>
                      </div>
                      <label>谁可以点击<select v-model="selectedButton.permission"><option value="ALL">所有人</option><option value="ADMIN">群管理员</option><option value="SPECIFIC_USER">指定用户</option></select></label>
                      <label v-if="selectedButton.permission === 'SPECIFIC_USER'">允许用户的 OpenID<textarea v-model="allowedUsers" rows="3" placeholder="每行一个，也支持逗号分隔；不是 QQ 号码" /></label>
                      <details>
                        <summary>高级设置 · 按钮 ID / 分组 / 确认弹窗</summary>
                        <div class="welcome-fields">
                          <label>按钮 ID<input v-model="selectedButton.button_id" maxlength="64" /></label>
                          <label>按钮分组 ID<input v-model="selectedButton.button_group_id" maxlength="64" placeholder="可选" /></label>
                        </div>
                        <label>确认弹窗内容<textarea v-model="selectedButton.modal.content" rows="2" maxlength="1024" placeholder="留空不弹窗" /></label>
                        <div class="welcome-fields">
                          <label>确认文字<input v-model="selectedButton.modal.confirm_text" maxlength="4" placeholder="留空使用默认" /></label>
                          <label>取消文字<input v-model="selectedButton.modal.cancel_text" maxlength="4" placeholder="留空使用默认" /></label>
                        </div>
                      </details>
                      <div class="welcome-row-tools">
                        <button class="ghost-button" :disabled="selected.column === 0" @click="moveButton(-1)">← 左移</button>
                        <button class="ghost-button" :disabled="selected.column === draft.keyboard[selected.row].length - 1" @click="moveButton(1)">右移 →</button>
                      </div>
                    </div>
                  </section>
                </div>
                <aside class="welcome-preview-column">
                  <section class="welcome-card welcome-preview-card">
                    <div class="welcome-section-head"><h3>消息预览</h3><span class="welcome-badge">编辑草稿</span></div>
                    <p class="welcome-muted">显示样例</p>
                    <div v-if="!draft.text.trim() && !draft.keyboard.length" class="welcome-empty-buttons">尚未设置自定义内容...</div>
                    <div v-else class="welcome-chat-preview">
                      <div class="welcome-bot-name">{{ identity.botName }}</div>
                      <div class="welcome-message-bubble">
                        <div class="welcome-markdown" v-html="preview" />
                        <div class="welcome-preview-keyboard" :class="{ small: draft.button_size === 'SMALL' }">
                          <div v-for="(row, rowIndex) in draft.keyboard" :key="rowIndex" class="welcome-button-row">
                            <button v-for="(button, column) in row" :key="column" class="welcome-qq-button" :class="button.style" :title="'点击编辑：' + button.data" @click="selected = { row: rowIndex, column }">{{ button.display_text || '未命名按钮' }}</button>
                          </div>
                        </div>
                      </div>
                    </div>
                    <p class="welcome-muted">调试群：{{ debugGroupId || '未配置，请前往机器人设置填写调试群 OpenId' }}</p>
                    <button class="ghost-button welcome-debug-button" title="发送当前草稿到调试群" :disabled="busy || testing || (!draft.text.trim() && !draft.keyboard.length)" @click="sendTest">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <rect x="7" y="7" width="10" height="14" rx="5"/>
                        <path d="M9 7V5a3 3 0 0 1 6 0v2M12 11v10M7 10H4L2 8M17 10h3l2-2M7 14H2M17 14h5M7 18H4l-2 2M17 18h3l2 2"/>
                      </svg>
                      <span>{{ testing ? '调试中…' : '调试' }}</span>
                    </button>
                  </section>
                </aside>
              </fieldset>
            </template>
          </div>
          <footer v-if="loaded && !loading" class="welcome-save-bar">
            <span class="welcome-muted">{{ dirty ? '有未保存的修改' : custom ? '暂无修改' : '尚未设置自定义欢迎' }}</span>
            <div class="welcome-row-tools">
              <button class="ghost-button danger" :disabled="busy || (!custom && !dirty)" @click="restoreDefault">恢复默认欢迎</button>
              <button class="primary-button" :disabled="busy || (!draft.text.trim() && !draft.keyboard.length) || (custom && !dirty)" @click="save">{{ saving ? '处理中…' : '保存欢迎配置' }}</button>
            </div>
          </footer>
        </section>
      </section>
    </main>
  </div>
</template>

<style scoped>
.welcome-panel { container-type: inline-size; }
.welcome-scroll { min-height: 0; }
.welcome-debug-button { display: inline-flex; align-items: center; gap: 6px; }
.welcome-debug-button svg { flex-shrink: 0; }
.welcome-card { background: var(--color-surface); border: 1px solid var(--color-hairline); border-radius: var(--radius-md); padding: var(--space-4); margin-bottom: var(--space-4); min-width: 0; }
.welcome-card.empty-state { padding: 48px var(--space-4); }
.welcome-muted { color: var(--color-text-muted); font-size: 12px; line-height: 1.7; overflow-wrap: anywhere; }
.welcome-page label { display: flex; flex-direction: column; gap: 7px; font-size: 13px; min-width: 0; }
.welcome-page input:not([type=checkbox]), .welcome-page textarea, .welcome-page select { width: 100%; min-width: 0; padding: 9px 11px; border: 1px solid var(--color-border-input); border-radius: var(--radius-md); color: var(--color-text); background: var(--color-surface); font: inherit; }
.welcome-page textarea { resize: vertical; line-height: 1.65; }
.welcome-page input:focus-visible, .welcome-page textarea:focus-visible, .welcome-page select:focus-visible, .welcome-page button:focus-visible, summary:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 2px; }
.welcome-group-picker, .welcome-status, .welcome-section-head, .welcome-save-bar, .welcome-row-tools { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.welcome-page .welcome-group-picker { position: relative; z-index: 2; flex-wrap: nowrap; gap: var(--space-3); padding: 6px var(--space-4); box-shadow: none; }
.welcome-picker-copy { flex-shrink: 0; }
.welcome-picker-copy strong { font-size: var(--text-base); font-weight: 600; }
.welcome-picker { position: relative; flex: 0 1 300px; width: min(300px, 100%); min-width: 0; }
.welcome-picker-trigger { display: flex; align-items: center; width: 100%; height: 32px; gap: var(--space-2); text-align: left; padding: 3px var(--space-2); background: var(--color-surface); border: 1px solid var(--color-border-input); border-radius: var(--radius-md); transition: border-color .15s, background .15s; }
.welcome-picker-trigger:hover, .welcome-picker-trigger.open { border-color: var(--color-accent-border); background: var(--color-accent-soft); }
.welcome-group-icon, .welcome-group-letter { display: grid; place-items: center; flex-shrink: 0; width: 32px; height: 32px; border-radius: var(--radius-md); color: var(--color-accent-text); background: var(--color-accent-soft); }
.welcome-group-icon { width: 24px; height: 24px; }
.welcome-group-icon svg { width: 16px; height: 16px; }
.welcome-picker-trigger .welcome-group-caption strong { font-size: var(--text-base); }
.welcome-group-caption { display: flex; flex-direction: column; gap: 4px; flex: 1; min-width: 0; }
.welcome-group-caption strong { font-size: 14px; font-weight: 600; color: var(--color-text); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.welcome-group-caption > span { font-size: 11px; color: var(--color-text-muted); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.welcome-picker-chevron { color: var(--color-text-muted); flex-shrink: 0; transition: transform .15s; }
.welcome-picker-chevron.open { transform: rotate(180deg); }
.welcome-picker-popover { position: absolute; z-index: 20; top: calc(100% + 8px); right: 0; width: 100%; padding: 10px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-lg); }
.welcome-picker-search {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  height: 36px;
  padding: 0 var(--space-2);
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-muted);
  transition: border-color .12s ease, box-shadow .12s ease;
}
.welcome-picker-search svg { display: block; flex-shrink: 0; }
.welcome-picker-search:hover { border-color: var(--color-border-strong); }
.welcome-picker-search:focus-within { border-color: var(--color-accent); box-shadow: var(--focus-ring); }
/* 搜索框由外层统一绘制边框和焦点态，避免叠加页面及全局表单样式。 */
.welcome-page .welcome-picker-search input,
.welcome-page .welcome-picker-search input:focus {
  flex: 1 1 0;
  width: 0;
  min-width: 0;
  height: 100%;
  min-height: 0;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  outline: none;
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
}
.welcome-picker-clear {
  display: grid;
  place-items: center;
  flex: 0 0 24px;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
}
.welcome-picker-clear:hover { color: var(--color-text); background: var(--color-surface-sunken); }
.welcome-picker-meta { display: flex; justify-content: space-between; align-items: center; padding: 12px 5px 8px; font-size: 11px; color: var(--color-text-muted); }
.welcome-picker-meta button { border: none; color: var(--color-accent-text); background: transparent; font-size: 11px; }
.welcome-picker-options { max-height: min(320px, 45vh); overflow-y: auto; }
.welcome-picker-option { width: 100%; display: flex; gap: 11px; align-items: center; padding: 10px; background: transparent; border: none; border-radius: 6px; text-align: left; color: var(--color-accent); }
.welcome-picker-option.highlighted { background: var(--color-surface-hover); }
.welcome-picker-option.chosen { background: var(--color-accent-soft); }
.welcome-group-letter { font-size: var(--text-md); }
.welcome-picker-empty { padding: 28px 8px; text-align: center; color: var(--color-text-muted); font-size: 12px; }
.welcome-status { flex-wrap: wrap; }
.welcome-status > div { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.welcome-status > button { flex-shrink: 0; }
.welcome-status p { margin-bottom: 0; }
.welcome-badge { display: inline-block; padding: 4px 8px; border-radius: var(--radius-md); background: var(--color-accent-soft); color: var(--color-accent-text); font-size: 11px; margin-left: 8px; }
.welcome-off { background: var(--color-warning-soft); color: var(--color-warning); }
.welcome-editor { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(300px, 1fr); align-items: start; gap: var(--space-4); padding: 0; margin: 0; border: 0; min-width: 0; }
.welcome-edit-column, .welcome-preview-column { min-width: 0; }
.welcome-preview-column { position: sticky; top: 0; }
.welcome-section-head h3, .welcome-section-head h4 { margin: 0; }
.welcome-section-head { flex-wrap: wrap; }
.welcome-section-head h3 { font-size: var(--text-md); font-weight: 600; }
.welcome-markdown-input { font-family: var(--font-mono) !important; }
.welcome-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin: 12px 0; align-items: end; }
.welcome-page details { border-top: 1px solid var(--color-hairline); padding-top: 14px; margin-top: 14px; }
.welcome-page summary { cursor: pointer; font-size: 13px; color: var(--color-accent-text); }
.welcome-layout-row { margin-top: 16px; border: 1px solid var(--color-hairline); border-radius: 5px; padding: 10px; }
.welcome-row-tools { justify-content: flex-end; flex-wrap: wrap; }
.welcome-row-tools .welcome-muted { margin-right: auto; }
.welcome-row-tools .ghost-button { padding: 5px 10px; }
.welcome-button-row { display: flex; gap: 6px; margin-top: 9px; }
.welcome-qq-button { flex: 1; min-width: 0; overflow-wrap: anywhere; border: 1px solid #b5cce9; background: #fff; color: #2473cd; border-radius: 5px; padding: 9px 5px; font-size: 13px; line-height: 1.4; }
.welcome-qq-button.GRAY { border-color: #d7dce3; color: #596273; }
.welcome-qq-button.RED { color: #c1362f; border-color: #e7b5b2; }
.welcome-qq-button.BLUE_WITH_BACKGROUND { color: white; background: #337cd9; border-color: #337cd9; }
.welcome-qq-button.ICON_BUTTON { color: #2473cd; background: #edf4fe; }
.welcome-qq-button.selected { outline: 2px solid var(--color-accent); outline-offset: 2px; }
.welcome-button-editor { padding-top: 18px; margin-top: 18px; border-top: 1px solid var(--color-border); display: grid; gap: 12px; }
.welcome-checks { display: grid; gap: 8px; }
.welcome-checks label { flex-direction: row; align-items: center; }
.welcome-empty-buttons { padding: 28px 12px; margin-top: 14px; border: 1px dashed var(--color-border); color: var(--color-text-muted); text-align: center; line-height: 1.7; font-size: 13px; }
.welcome-chat-preview { background: var(--color-surface-sunken); padding: 16px; border-radius: 6px; }
.welcome-bot-name { font-size: 12px; color: var(--color-text-muted); margin-bottom: 8px; }
.welcome-message-bubble { background: white; border: 1px solid var(--color-hairline); padding: 14px; border-radius: 0 10px 10px; }
.welcome-markdown :deep(.welcome-mention) { color: #2473cd; }
.welcome-markdown { font-size: 14px; line-height: 1.7; overflow-wrap: anywhere; }
.welcome-markdown :deep(> :first-child) { margin-top: 0; }
.welcome-markdown :deep(img) { max-width: 100%; height: auto; display: block; }
.welcome-markdown :deep(pre) { white-space: pre-wrap; background: #f5f6f8; padding: 10px; }
.welcome-markdown :deep(blockquote) { border-left: 3px solid #d4d9e1; margin-left: 0; padding-left: 12px; color: #767f8f; }
.welcome-markdown :deep(a) { color: #2473cd; }
.welcome-preview-keyboard.small .welcome-qq-button { font-size: 11px; }
.welcome-alert { padding: 12px 16px; border-radius: 5px; font-size: 13px; }
.welcome-error { color: var(--color-danger); background: var(--color-danger-soft); }
.welcome-success { color: var(--color-success); background: var(--color-success-soft); }
.welcome-save-bar { padding: var(--space-3) var(--space-4); border-top: 1px solid var(--color-hairline); background: var(--color-surface); flex-wrap: wrap; }
@container (max-width: 820px) {
  .welcome-editor { grid-template-columns: minmax(0, 1fr); }
  .welcome-preview-column { position: static; }
}
@media (max-width: 640px) {
  .welcome-card { padding: var(--space-3); }
  .welcome-fields { grid-template-columns: minmax(0, 1fr); }
  .welcome-status .welcome-badge { margin-top: var(--space-2); }
  .welcome-save-bar { padding: var(--space-3); }
  .welcome-save-bar > .welcome-row-tools { width: 100%; }
}
</style>
