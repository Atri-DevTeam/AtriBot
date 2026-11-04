<script setup>
import {computed, onMounted, onBeforeUnmount, ref} from 'vue'
import AppShell from '../../shared/components/AppShell.vue'
import {createWebuiApi, logoutWebui} from '../../shared/lib/webuiApi.js'
import {renderMarkdown} from '../../shared/lib/markdown.js'
import {renderCommandInputTags} from '../../shared/lib/messageRender.js'
import {useBotConfig} from '../../shared/lib/botConfig.js'
import botBlue from '../../assets/bot-blue.svg'
import InlineKeyboardButton from '../groups/components/InlineKeyboardButton.vue'
import {API_BASE} from '../../router.js'

const sidebarOpen = ref(false)
const tab = ref('body')
const format = ref('MARKDOWN')
const text = ref('')
const rows = ref([])
const defaultLayout = () => ({header: '{botName}近期更新日志:\n> {time}', footer: '遇到了一些问题？使用/feedback向开发者反馈！'})
const textLayout = ref(defaultLayout())
const markdownLayout = ref(defaultLayout())
const currentLayout = computed(() => format.value === 'MARKDOWN' ? markdownLayout.value : textLayout.value)
const status = ref({createdAt: 0, notifiedGroups: 0, botName: '机器人', enabled: true, joinEventPush: false, passiveMessagePush: false})
const identity = useBotConfig()
const avatarFailed = ref(false)
const botAvatar = computed(() => identity.appId.value && identity.botOpenId.value
    ? `https://thirdqq.qlogo.cn/qqapp/${encodeURIComponent(identity.appId.value)}/${encodeURIComponent(identity.botOpenId.value)}/100`
    : '')
const loading = ref(true)
const saving = ref(false)
const savingLayouts = ref(false)
const savingButtons = ref(false)
const savingSettings = ref(false)
const error = ref('')
const notice = ref('')
const confirm = ref(false)
const snapshot = ref('')
const layoutSnapshot = ref('')
const buttonSnapshot = ref('')
const lifetime = new AbortController()
const api = createWebuiApi({baseUrl: API_BASE, onSessionExpired: logoutWebui})
const payload = computed(() => ({text: text.value, format: format.value}))
const buttonsDirty = computed(() => JSON.stringify(rows.value) !== buttonSnapshot.value)
const layoutPayload = computed(() => ({textLayout: textLayout.value, markdownLayout: markdownLayout.value}))
const layoutsDirty = computed(() => JSON.stringify(layoutPayload.value) !== layoutSnapshot.value)
const layoutsValid = computed(() => [textLayout.value, markdownLayout.value].every(layout => layout.header.length <= 4000 && layout.footer.length <= 4000))
const dirty = computed(() => JSON.stringify(payload.value) !== snapshot.value)
const buttonCount = computed(() => rows.value.reduce((count, row) => count + row.length, 0))
const timestamp = computed(() => new Date(status.value.createdAt || Date.now()).toLocaleString('sv-SE', {timeZone: 'Asia/Shanghai'}))
const expandLayout = value => value.trim().replace(/\{(botName|time)\}/g, (_, key) => key === 'botName' ? status.value.botName : timestamp.value)
const fullText = computed(() => [expandLayout(currentLayout.value.header), text.value.trim(), expandLayout(currentLayout.value.footer)].filter(part => part.trim()).join('\n\n'))
const preview = computed(() => renderMarkdown(renderCommandInputTags(fullText.value)))
const valid = computed(() => !!text.value.trim() && text.value.length <= 16000)
const buttonsValid = computed(() => {
  return rows.value.every(row => row.every(b => {
    if (!b.label.trim() || !b.data.trim()) return false
    if (b.type !== 'LINK') return true
    try { const url = new URL(b.data); return ['http:', 'https:'].includes(url.protocol) } catch { return false }
  }))
})
const newButton = () => ({label: '', data: '', type: 'COMMAND', style: 'BLUE', enter: true})

function apply(data, preserveLayouts = false) {
  status.value = data
  text.value = data.content?.text || ''
  format.value = data.content?.text ? data.content.format || 'TEXT' : 'MARKDOWN'
  if (!preserveLayouts) {
    rows.value = data.content?.buttons || []
    buttonSnapshot.value = JSON.stringify(rows.value)
    textLayout.value = {...(data.content?.textLayout ?? defaultLayout())}
    markdownLayout.value = {...(data.content?.markdownLayout ?? defaultLayout())}
    layoutSnapshot.value = JSON.stringify(layoutPayload.value)
  }
  snapshot.value = JSON.stringify(payload.value)
}
async function load() {
  loading.value = true
  error.value = ''
  try { apply(await api('/update-notice', {signal: lifetime.signal})) }
  catch (e) { if (e.name !== 'AbortError') error.value = e.message }
  finally { loading.value = false }
}
async function save() {
  if (saving.value || !valid.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    apply(await api('/update-notice', {method: 'PUT', body: JSON.stringify(payload.value), signal: lifetime.signal}), true)
    notice.value = '已保存，后续群指令触发时将发送此内容'
    confirm.value = false
  } catch (e) { if (e.name !== 'AbortError') { error.value = e.message; confirm.value = false } }
  finally { saving.value = false }
}
async function saveLayouts() {
  if (savingLayouts.value || !layoutsValid.value) return
  savingLayouts.value = true
  error.value = ''
  notice.value = ''
  try {
    const data = await api('/update-notice/layouts', {method: 'PUT', body: JSON.stringify(layoutPayload.value), signal: lifetime.signal})
    textLayout.value = {...data.textLayout}
    markdownLayout.value = {...data.markdownLayout}
    layoutSnapshot.value = JSON.stringify(layoutPayload.value)
    notice.value = '标题与落款已保存'
  } catch (e) { if (e.name !== 'AbortError') error.value = e.message }
  finally { savingLayouts.value = false }
}
async function saveButtons() {
  if (savingButtons.value || !buttonsValid.value) return
  savingButtons.value = true
  error.value = ''
  notice.value = ''
  try {
    const data = await api('/update-notice/buttons', {method: 'PUT', body: JSON.stringify({buttons: rows.value}), signal: lifetime.signal})
    rows.value = data.buttons
    buttonSnapshot.value = JSON.stringify(rows.value)
    notice.value = '默认按钮已保存'
  } catch (e) { if (e.name !== 'AbortError') error.value = e.message }
  finally { savingButtons.value = false }
}
async function togglePushSetting(key) {
  if (savingSettings.value) return
  savingSettings.value = true
  error.value = ''
  try {
    const data = await api('/update-notice/settings', {method: 'PUT', body: JSON.stringify({[key]: !status.value[key]}), signal: lifetime.signal})
    status.value = {...status.value, joinEventPush: data.joinEventPush, passiveMessagePush: data.passiveMessagePush, enabled: data.enabled}
  } catch (e) { if (e.name !== 'AbortError') error.value = e.message }
  finally { savingSettings.value = false }
}
function removeButton(row, column) {
  rows.value[row].splice(column, 1)
  if (!rows.value[row].length) rows.value.splice(row, 1)
}
function moveRow(index, delta) {
  const target = index + delta
  if (target < 0 || target >= rows.value.length) return
  const [row] = rows.value.splice(index, 1)
  rows.value.splice(target, 0, row)
}
const markdownPresets = [
  {label: '粗体', text: '**标题**'},
  {label: '引用', text: '> 引用内容'},
  {label: '链接', text: '[链接文字](https://example.com)'},
  {label: '图片', text: '![图片 #800px #400px](https://example.com/image.png)'},
  {label: '交互文本', text: '<qqbot-cmd-input text="/question" show="今日问题" reference="false" />', title: '插入 QQ 指令交互文本，text 为指令，show 为显示文字'}
]
function insert(sample, target = 'body') {
  const value = target === 'body' ? text.value : currentLayout.value[target]
  const next = `${value}${value ? '\n\n' : ''}${sample}`
  if (target === 'body') text.value = next
  else currentLayout.value[target] = next
}
function beforeUnload(e) { if (dirty.value || layoutsDirty.value || buttonsDirty.value) { e.preventDefault(); e.returnValue = '' } }
onMounted(() => {
  load()
  identity.loadBotConfig(path => api(path, {signal: lifetime.signal})).catch(() => {
    avatarFailed.value = true
  })
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => { lifetime.abort(); window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <AppShell v-model:open="sidebarOpen">
    <main class="workspace connect-workspace debug-workspace update-workspace">
      <header class="topbar"><div class="topbar-left">
        <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M3 12h18M3 18h18"/></svg></button>
        <h2>更新日志</h2>
      </div></header>
      <section class="content connect-layout update-layout">
        <aside class="connect-catalog update-catalog">
          <div class="update-stats"><dl><dt>已通知群数</dt><dd>{{status.notifiedGroups}}</dd><dt>发布时间</dt><dd>{{status.createdAt ? timestamp : '—'}}</dd></dl></div>
          <div class="update-trigger-setting">
            <div><span>更新通知</span><button type="button" class="update-switch" role="switch" aria-label="更新通知" :title="status.enabled ? '关闭更新通知' : '启用更新通知'" :aria-checked="!!status.enabled" :disabled="loading || saving || savingSettings || !snapshot" @click="togglePushSetting('enabled')"><span/></button></div>
            <p>{{status.enabled ? '已启用' : '已关闭'}}</p>
          </div>
          <div class="update-trigger-setting">
            <div><span>入群事件推送</span><button type="button" class="update-switch" role="switch" aria-label="入群事件推送" :aria-checked="!!status.joinEventPush" :disabled="loading || saving || savingSettings || !snapshot" @click="togglePushSetting('joinEventPush')"><span/></button></div>
            <p>用户进群后延迟 3 分钟推送</p>
          </div>
          <div class="update-trigger-setting">
            <div><span>被动消息推送</span><button type="button" class="update-switch" role="switch" aria-label="被动消息推送" :aria-checked="!!status.passiveMessagePush" :disabled="loading || saving || savingSettings || !snapshot" @click="togglePushSetting('passiveMessagePush')"><span/></button></div>
            <p>收到群消息或群 @ 消息时推送</p>
          </div>
        </aside>
        <div class="update-main">
          <p v-if="error" class="update-error" role="alert">{{error}} <button v-if="!snapshot" @click="load">重新加载</button></p>
          <p v-if="notice" class="update-notice" role="status">{{notice}}</p>
          <div class="update-columns">
            <section class="connect-panel update-editor">
              <header class="connect-heading"><div class="connect-title"><span class="connect-title-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="m16 3 5 5-12 12-6 1 1-6zM13 6l5 5"/></svg></span><h3>编辑通知</h3></div><span class="connect-badge">{{dirty || layoutsDirty || buttonsDirty ? '未保存' : '已同步'}}</span></header>
              <fieldset :disabled="loading || saving || savingLayouts || savingButtons || !snapshot" class="update-fields">
                <div class="update-format"><span>消息格式</span><div class="update-segment"><button v-for="f in [{key:'MARKDOWN',label:'Markdown'},{key:'TEXT',label:'纯文本'}]" :key="f.key" type="button" :class="{active:format===f.key}" :aria-pressed="format===f.key" @click="format=f.key;tab='body'">{{f.label}}</button></div></div>
                <div class="debug-tabs update-tabs" role="tablist"><button class="debug-tab" :class="{active:tab==='body'}" role="tab" :aria-selected="tab==='body'" @click="tab='body'">正文</button><button class="debug-tab" :class="{active:tab==='layout'}" role="tab" :aria-selected="tab==='layout'" @click="tab='layout'">标题与落款</button><button class="debug-tab" :class="{active:tab==='buttons'}" role="tab" :aria-selected="tab==='buttons'" :disabled="format==='TEXT'" @click="tab='buttons'">按钮 <span class="update-count">{{buttonCount}}</span></button></div>
                <div v-show="tab==='body'" class="update-body">
                  <div v-if="format==='MARKDOWN'" class="update-tools"><button v-for="preset in markdownPresets" :key="preset.label" type="button" :title="preset.title" @click="insert(preset.text)">{{preset.label}}</button></div>
                  <textarea v-model="text" aria-label="通知正文" maxlength="16000" spellcheck="false" placeholder="填写本次更新内容…"/>
                  <div class="update-editor-meta"><span>{{format==='TEXT' ? '纯文本消息不携带按钮' : '支持 Markdown、图片与内联键盘'}}</span><span>{{text.length}} / 16000</span></div>
                </div>
                <div v-show="tab==='layout'" class="update-layout-editor">
                  <p class="update-layout-hint">当前编辑{{format==='MARKDOWN' ? ' Markdown' : '纯文本'}}模式，标题或落款留空则不显示</p>
                  <div v-for="field in [{key:'header',label:'标题',position:'之前'},{key:'footer',label:'落款',position:'之后'}]" :key="field.key" class="update-layout-field">
                    <label :for="`update-${field.key}`">{{field.label}}</label>
                    <div v-if="format==='MARKDOWN'" class="update-tools" role="group" :aria-label="`${field.label}插入工具`">
                      <button v-for="preset in markdownPresets" :key="preset.label" type="button" :title="preset.title" @click="insert(preset.text,field.key)">{{preset.label}}</button>
                    </div>
                    <textarea :id="`update-${field.key}`" v-model="currentLayout[field.key]" :aria-label="`通知${field.label}`" maxlength="4000" spellcheck="false" :placeholder="`消息正文${field.position}的内容`"/>
                  </div>
                  <p class="update-layout-hint"><code>{botName}</code> 机器人名称　<code>{time}</code> 通知发布时间</p>
                </div>
                <div v-show="tab==='buttons'" class="update-buttons">
                  <div class="update-button-heading"><span>{{rows.length}} / 5 行</span><button type="button" class="debug-ghost-btn" :disabled="rows.length>=5" @click="rows.push([newButton()])">＋ 添加一行</button></div>
                  <p v-if="!rows.length" class="update-empty">添加按钮，引导用户使用功能或访问页面</p>
                  <section v-for="(row,ri) in rows" :key="ri" class="update-button-row">
                    <div class="update-row-head"><strong>第 {{ri+1}} 行</strong><div><button type="button" :disabled="ri===0" :aria-label="`上移第${ri+1}行`" @click="moveRow(ri,-1)">↑</button><button type="button" :disabled="ri===rows.length-1" :aria-label="`下移第${ri+1}行`" @click="moveRow(ri,1)">↓</button><button type="button" :disabled="row.length>=10" @click="row.push(newButton())">＋ 按钮</button></div></div>
                    <div v-for="(b,bi) in row" :key="bi" class="update-button-form">
                      <div class="update-button-controls"><label>按钮文字<input v-model="b.label" maxlength="40" :aria-label="`第${ri+1}行第${bi+1}个按钮文字`" placeholder="例如：今日问题"/></label><label>类型<select v-model="b.type" :aria-label="`第${ri+1}行第${bi+1}个按钮类型`"><option value="COMMAND">指令</option><option value="LINK">链接</option></select></label><label>样式<select v-model="b.style" :aria-label="`第${ri+1}行第${bi+1}个按钮样式`"><option value="BLUE">蓝色</option><option value="GRAY">灰色</option><option value="RED">红色</option><option value="BLUE_WITH_BACKGROUND">蓝色填充</option></select></label><button class="update-remove" type="button" :aria-label="`删除第${ri+1}行第${bi+1}个按钮`" @click="removeButton(ri,bi)"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7M14 10v7"/></svg></button></div>
                      <input v-model="b.data" maxlength="2048" :aria-label="`第${ri+1}行第${bi+1}个按钮内容`" :placeholder="b.type==='LINK' ? 'https://…' : '/question'"/>
                      <label v-if="b.type==='COMMAND'" class="update-check"><input v-model="b.enter" type="checkbox"/>点击后直接发送指令</label>
                    </div>
                  </section>
                </div>
              </fieldset>
              <footer v-if="tab==='layout'" class="update-actions"><span>单独保存，不影响已通知群记录</span><button class="debug-send-btn" type="button" :disabled="loading || saving || savingLayouts || !layoutsValid || !layoutsDirty || !snapshot" @click="saveLayouts">{{savingLayouts ? '保存中…' : '保存标题与落款'}}</button></footer>
              <footer v-else-if="tab==='buttons'" class="update-actions"><span>默认按钮单独保存</span><button class="debug-send-btn" type="button" :disabled="loading || saving || savingButtons || !buttonsValid || !buttonsDirty || !snapshot" @click="saveButtons">{{savingButtons ? '保存中…' : '保存默认按钮'}}</button></footer>
              <footer v-else class="update-actions"><span>保存会覆盖当前通知并重新开始群通知记录</span><button class="debug-send-btn" type="button" :disabled="loading || saving || savingLayouts || savingButtons || !valid || !dirty || !snapshot" @click="confirm=true">{{saving ? '保存中…' : '保存通知'}}</button></footer>
            </section>
            <section class="connect-panel update-preview-panel"><header class="connect-heading"><div class="connect-title"><h3>消息预览</h3></div><span class="connect-badge">{{format==='TEXT' ? '纯文本' : 'Markdown'}}</span></header>
              <div class="update-preview-stage">
                <div class="update-preview-chat">
                  <span class="update-bot-avatar">
                    <img v-if="botAvatar && !avatarFailed" :src="botAvatar" alt="发送者头像" referrerpolicy="no-referrer" @error="avatarFailed=true"/>
                    <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><rect x="3" y="7" width="18" height="14" rx="5"/><path d="M12 3v4M8 12v3M16 12v3"/></svg>
                  </span>
                  <div class="update-preview-content">
                    <div class="update-preview-author"><span>{{status.botName}}</span><img :src="botBlue" alt="机器人"/></div>
                    <div class="update-preview-message">
                      <div v-if="format==='MARKDOWN'" class="update-markdown" v-html="preview"/>
                      <pre v-else class="update-plain">{{fullText}}</pre>
                      <div v-if="format==='MARKDOWN' && rows.length" class="update-preview-keyboard" aria-label="通知按钮预览">
                        <div v-for="(row,i) in rows" :key="i">
                          <button v-for="(b,j) in row" :key="j" type="button" :class="[`button-${b.style.toLowerCase()}`, {'button-link':b.type==='LINK'}]" tabindex="-1" :title="b.data">
                            <InlineKeyboardButton :button="{...b, display_text:b.label || '按钮文字'}"/>
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <p class="update-preview-note">预览仅展示样式，按钮不会触发操作</p>
              </div>
            </section>
          </div>
        </div>
      </section>
      <div v-if="confirm" class="update-dialog-backdrop" @click.self="!saving && (confirm=false)"><section class="update-dialog" role="dialog" aria-modal="true" aria-labelledby="update-confirm-title"><h3 id="update-confirm-title">保存更新通知</h3><p>覆盖当前通知及已通知群记录，群内下次触发指令时发送新内容。</p><div><button class="debug-ghost-btn" :disabled="saving" @click="confirm=false">取消</button><button class="debug-send-btn" :disabled="saving" @click="save">{{saving ? '保存中…' : '确认保存'}}</button></div></section></div>
    </main>
  </AppShell>
</template>

<style scoped src="../../shared/styles/api-workbench.css"></style>
<style scoped src="../../shared/styles/api-workbench-layout.css"></style>
<style scoped src="./update-notice.css"></style>
