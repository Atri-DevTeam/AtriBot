<script setup>
import { createWebuiApi } from '../../shared/lib/webuiApi.js'
import {onMounted, ref} from 'vue'
import {onBeforeRouteLeave, useRouter} from 'vue-router'
import {API_BASE} from '../../router.js'
import AppShell from '../../shared/components/AppShell.vue'
import { useBotConfig } from '../../shared/lib/botConfig.js'
import GroupModerationPanel from './components/GroupModerationPanel.vue'

const { loadBotConfig } = useBotConfig()

const router = useRouter()
const sidebarOpen = ref(false)
const groups = ref([]), groupOpenId = ref(''), panelRef = ref(null), loading = ref(true), error = ref('')
let sessionExpired = false
function mayLeave() { return sessionExpired || !panelRef.value || panelRef.value.mayLeave() }
onBeforeRouteLeave(mayLeave)
const api = createWebuiApi({ baseUrl: API_BASE, onSessionExpired: () => {
    sessionExpired = true
    panelRef.value?.releaseLeaveGuard()
    router.replace('/login')
  } })
function selectGroup(event) {
  if (event.target.value === groupOpenId.value) return
  if (mayLeave()) groupOpenId.value = event.target.value
  else event.target.value = groupOpenId.value
}
async function logout() {
  if (!mayLeave()) return
  panelRef.value?.releaseLeaveGuard()
  try { await api('/auth/logout', {method: 'POST'}) } finally { router.replace('/login') }
}
async function loadGroups() {
  loading.value = true; error.value = ''
  try { groups.value = await api('/groups') || [] }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
}
onMounted(() => {
  loadBotConfig(api).catch(() => {})
  loadGroups()
})
</script>

<template>
  <AppShell v-model:open="sidebarOpen">
      <template #toolbar><button class="ghost-button" @click="logout">退出</button></template>

    <main class="moderation-page">
      <header class="moderation-page-head">
        <button type="button" class="mod-button" aria-label="打开导航" @click="sidebarOpen = true">☰</button>
        <div><h1>群管设置</h1><p>也可从聊天侧栏的群聊信息进入。</p></div>
        <select :value="groupOpenId" aria-label="选择群聊" :disabled="loading" @change="selectGroup">
          <option value="">{{ loading ? '正在加载群聊…' : '选择群聊' }}</option>
          <option v-for="group in groups" :key="group.groupOpenId" :value="group.groupOpenId">{{ group.groupName || group.realGroupId || group.groupOpenId }}</option>
        </select>
      </header>
      <p v-if="error" class="mod-alert">{{ error }} <button class="mod-button" @click="loadGroups">重新加载</button></p>
      <GroupModerationPanel v-if="groupOpenId" :key="groupOpenId" ref="panelRef" :group-open-id="groupOpenId" :request="api"/>
      <div v-else class="moderation-page-empty">选择群聊以设置消息审查、入群审核或查看操作日志。</div>
    </main>
  </AppShell>
</template>

<style scoped>
.moderation-page { display: flex; flex-direction: column; flex: 1; min-width: 0; height: 100dvh; background: #f7f8fa; overflow: hidden; }
.moderation-page-head { display: flex; align-items: center; gap: 16px; padding: 22px 28px; border-bottom: 1px solid #e7eaf0; background: #fff; }
.moderation-page-head h1 { margin: 0; font-size: 19px; font-weight: 600; color: #303641; }
.moderation-page-head p { margin: 6px 0 0; font-size: 12px; color: #9199a5; }
.moderation-page-head select { margin-left: auto; max-width: 240px; min-width: 0; padding: 9px 12px; border: 1px solid #e1e4e9; border-radius: 6px; background: #fff; color: #596575; }
.moderation-page-head > button { display: none; }
.moderation-page :deep(.mod-sections), .moderation-page :deep(.mod-editor) { width: 100%; max-width: 760px; margin: 0 auto; }
.moderation-page :deep(.mod-tabs) { justify-content: center; }
.moderation-page :deep(.mod-tabs button) { max-width: 180px; }
.moderation-page-empty { margin: auto; padding: 24px; color: #929aa6; font-size: 13px; text-align: center; }
@media (max-width: 768px) { .moderation-page-head { padding: 14px; flex-wrap: wrap; gap: 10px; } .moderation-page-head > button { display: inline-flex; } .moderation-page-head select { width: 100%; max-width: none; } }
</style>
