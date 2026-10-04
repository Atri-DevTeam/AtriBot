<script setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import MessageModerationSettings from './MessageModerationSettings.vue'
import JoinReviewSettings from './JoinReviewSettings.vue'
import ModerationLogs from './ModerationLogs.vue'
import {readModerationSettings, serializeModerationSettings} from '../lib/moderationSettings.js'
import '../styles/moderation-panel.css'
const props = defineProps({groupOpenId: {type: String, required: true}, request: {type: Function, required: true}})
const tabs = [{key: 'messages', label: '消息审查'}, {key: 'join', label: '入群审核'}, {key: 'logs', label: '操作日志'}]
const tab = ref('messages'), settings = ref(readModerationSettings()), loading = ref(false), loaded = ref(false), saving = ref(false), error = ref(''), notice = ref('')
const baseline = ref(''), dirty = computed(() => loaded.value && JSON.stringify(settings.value) !== baseline.value)
const endpoint = `/group-moderation/${encodeURIComponent(props.groupOpenId)}`
let disposed = false, controller
function accept(value) { settings.value = readModerationSettings(value); baseline.value = JSON.stringify(settings.value); loaded.value = true }
async function load() {
  if (loading.value || saving.value) return
  loading.value = true
  error.value = ''
  controller = new AbortController()
  try { const value = await props.request(endpoint, {signal: controller.signal}); if (!disposed) accept(value) }
  catch (e) { if (!disposed) error.value = e.message }
  finally { if (!disposed) loading.value = false }
}
async function save() {
  if (!loaded.value || saving.value || !dirty.value) return
  saving.value = true
  error.value = ''; notice.value = ''
  const value = serializeModerationSettings(settings.value)
  try {
    const saved = await props.request(endpoint, {method: 'PUT', body: JSON.stringify(value)})
    if (!disposed) { accept(saved || value); notice.value = '已保存当前群的设置。' }
  } catch (e) { if (!disposed) error.value = e.message }
  finally { if (!disposed) saving.value = false }
}
function mayLeave() {
  if (saving.value) { notice.value = '正在保存，请稍候。'; return false }
  return !dirty.value || window.confirm('群管设置尚未保存，确定放弃修改吗？')
}
function releaseLeaveGuard() { loaded.value = false; window.removeEventListener('beforeunload', beforeUnload) }
function beforeUnload(event) { if (dirty.value || saving.value) { event.preventDefault(); event.returnValue = '' } }
function reset() { if (!saving.value && window.confirm('放弃未保存的群管设置？')) { accept(JSON.parse(baseline.value)); notice.value = ''; error.value = '' } }
defineExpose({mayLeave, releaseLeaveGuard})
onMounted(() => { window.addEventListener('beforeunload', beforeUnload); load() })
onBeforeUnmount(() => { disposed = true; controller?.abort(); window.removeEventListener('beforeunload', beforeUnload) })
</script>
<template>
  <div class="moderation-panel">
    <nav class="mod-tabs" aria-label="群管设置分类"><button v-for="item in tabs" :key="item.key" :class="{active: tab === item.key}" :aria-pressed="tab === item.key" @click="tab = item.key">{{ item.label }}</button></nav>
    <div class="mod-scroll">
      <p v-if="error" class="mod-alert" role="alert">{{ error }} <button v-if="!loaded" class="mod-button" @click="load">重新加载</button></p>
      <p v-if="notice" class="mod-notice" role="status">{{ notice }}</p>
      <ModerationLogs v-if="tab === 'logs'" :group-open-id="groupOpenId" :request="request"/>
      <p v-else-if="loading" class="mod-empty">正在加载群管设置…</p>
      <fieldset v-else-if="loaded" class="mod-editor" :disabled="saving">
        <MessageModerationSettings v-if="tab === 'messages'" :settings="settings"/>
        <JoinReviewSettings v-else :config="settings.joinReview"/>
      </fieldset>
    </div>
    <footer v-if="loaded" class="mod-save-bar"><span role="status">{{ dirty ? '有未保存的修改' : '已同步当前群配置' }}</span><div class="mod-tools"><button class="mod-button" :disabled="!dirty || saving" @click="reset">撤销</button><button class="mod-button primary" :disabled="!dirty || saving" @click="save">{{ saving ? '保存中…' : '保存设置' }}</button></div></footer>
  </div>
</template>
