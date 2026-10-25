<script setup lang="ts">
import {onBeforeUnmount, ref, shallowRef, watch} from 'vue'
import Icon from './Icon.vue'
import ToggleSwitch from './ToggleSwitch.vue'
import {ApiError} from '../session'
import type {PrivateRequest} from '../activity'
import type {GroupFunctions, PrivatePost} from '../groups'

const props = defineProps<{groupId: string; request: PrivateRequest; post: PrivatePost; revision: number}>()
const settings = shallowRef<GroupFunctions | null>(null)
const loading = ref(false), saving = ref(''), error = ref(''), notice = ref('')
let version = 0, disposed = false

async function load() {
  if (saving.value) return
  const current = ++version
  loading.value = true
  error.value = ''
  try {
    const result = await props.request<GroupFunctions>(`groups/${encodeURIComponent(props.groupId)}/functions`)
    if (!disposed && current === version && result) settings.value = result
  } catch {
    if (!disposed && current === version) error.value = '群推送设置暂时无法读取'
  } finally { if (current === version) loading.value = false }
}
async function toggle(key: string, enabled: boolean) {
  if (!settings.value?.canEdit || loading.value || saving.value) return
  const current = version
  saving.value = key
  error.value = ''
  notice.value = ''
  try {
    const result = await props.post<{enabled: boolean}>(`groups/${encodeURIComponent(props.groupId)}/functions/${encodeURIComponent(key)}`, {enabled})
    if (!disposed && current === version && result && settings.value) {
      settings.value = {...settings.value, items: settings.value.items.map(item => item.key === key ? {...item, enabled: result.enabled} : item)}
      notice.value = '设置已保存'
    }
  } catch (reason) {
    if (!disposed && current === version) {
      const messages: Record<string, string> = {
        GROUP_ACTIVE_REQUIRED: '请先在 QQ 中允许机器人发送主动消息', GROUP_NOT_BOUND: '群绑定已失效，请刷新群资料',
        GROUP_SETTINGS_FORBIDDEN: '当前群无法修改推送设置', GROUP_UNAVAILABLE: '群资料暂不可用，请刷新群资料',
        INVALID_GROUP_FUNCTION: '该推送任务已不可用，请刷新后重试'
      }
      error.value = reason instanceof ApiError ? messages[reason.code] || '保存失败，请稍后重试' : '保存失败，请稍后重试'
    }
  } finally { if (current === version) saving.value = '' }
}
watch(() => [props.groupId, props.revision], () => {
  version++
  settings.value = null
  saving.value = ''
  notice.value = ''
  void load()
}, {immediate: true})
onBeforeUnmount(() => { disposed = true; version++ })
</script>

<template>
  <section class="group-push-settings glass" aria-label="群推送功能设置" :aria-busy="loading || !!saving">
    <header class="push-heading"><div><Icon name="broadcast"/><h3>群推送功能设置</h3></div>
      <button type="button" class="text-button" aria-label="刷新群推送设置" :disabled="loading || !!saving" @click="load">
        <Icon name="refresh" :class="{'refresh-spinning': loading}"/>
      </button>
    </header>
    <p v-if="loading && !settings" class="push-status" role="status">正在读取推送设置…</p>
    <div v-if="settings" class="push-list">
      <div v-for="item in settings.items" :key="item.key" class="push-row">
        <div><span>{{ item.name }}</span><small v-if="item.needActiveMessage && !settings.proactive">需允许主动消息</small></div>
        <ToggleSwitch :model-value="item.enabled" :label="item.name"
                      :disabled="!settings.canEdit || loading || !!saving || (item.needActiveMessage && !settings.proactive && !item.enabled)"
                      @update:model-value="toggle(item.key, $event)"/>
      </div>
      <p v-if="!settings.items.length" class="push-status">暂无可设置的群推送功能</p>
      <p v-if="!settings.canEdit" class="push-status">当前群不可修改推送设置</p>
    </div>
    <p v-if="error" class="push-status error" role="alert">{{ error }}<button v-if="!settings" class="text-button" :disabled="loading" @click="load">重试</button></p>
    <p v-else-if="notice" class="push-status" role="status">{{ notice }}</p>
  </section>
</template>

<style scoped>
.group-push-settings { min-width: 0; background: transparent; border: 0; border-top: 1px solid var(--ui-border); border-radius: 0; padding: 24px 0; margin-top: 0; }
.push-heading, .push-heading > div { display: flex; align-items: center; gap: 9px; }
.push-heading { justify-content: space-between; }
.push-heading h3 { margin: 0; font-size: 14px; font-weight: 550; }
.push-heading svg { width: 16px; height: 16px; color: var(--ui-muted); }
.push-list { margin-top: 8px; }
.push-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 0; }
.push-row + .push-row { border-top: 1px solid var(--ui-border); }
.push-row > div { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.push-row span { font-size: 12px; color: var(--ui-secondary); overflow-wrap: anywhere; }
.push-row small, .push-status { color: var(--ui-muted); font-size: 10px; line-height: 1.8; }
.push-status { margin: 12px 0 0; }
.push-status > button { margin-left: 12px; }
.push-status.error { color: var(--ui-danger); }
@media (max-width: 760px) { .group-push-settings { padding: 24px 0; border-radius: 0; } }
</style>
