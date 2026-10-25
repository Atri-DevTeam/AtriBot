<script setup lang="ts">
import {onBeforeUnmount, ref, shallowRef, watch} from 'vue'
import Icon from './Icon.vue'
import ToggleSwitch from './ToggleSwitch.vue'
import DropdownSelect from './DropdownSelect.vue'
import {ApiError} from '../session'
import type {PrivateRequest} from '../activity'
import type {GroupCommandItem, GroupCommands, PrivatePost} from '../groups'

const props = defineProps<{groupId: string; request: PrivateRequest; post: PrivatePost; revision: number}>()
type Draft = {enabled: boolean; seconds: string; scope: 'USER' | 'GROUP'}
const settings = shallowRef<GroupCommands | null>(null)
const drafts = ref<Record<string, Draft>>({})
const loading = ref(false), saving = ref(''), error = ref(''), notice = ref('')
const scopes = [{value: 'USER', label: '每位用户独立冷却'}, {value: 'GROUP', label: '全群共用冷却'}]
let version = 0, disposed = false
const date = (value: string | null) => value?.replace('T', ' ') || '不限制'
const editable = (item: GroupCommandItem) => !!settings.value?.canEdit && !item.locked && !loading.value && !saving.value
function valid(item: GroupCommandItem) {
  const value = drafts.value[item.key]?.seconds || ''
  return /^(0|[1-9]\d{0,3})$/.test(value) && Number(value) <= 3600
}
function dirty(item: GroupCommandItem) {
  const draft = drafts.value[item.key]
  return !!draft && (draft.enabled !== item.enabled || draft.seconds !== String(item.cooldownSeconds) || draft.scope !== item.scope)
}
function accept(result: GroupCommands, savedKey?: string) {
  const nextDrafts = Object.fromEntries(result.items.map(item => [item.key,
    savedKey && item.key !== savedKey && !item.locked && drafts.value[item.key] ? drafts.value[item.key]
      : {enabled: item.enabled, seconds: String(item.cooldownSeconds), scope: item.scope}]))
  settings.value = result
  drafts.value = nextDrafts
}
async function load() {
  if (saving.value) return
  const current = ++version
  loading.value = true
  error.value = ''
  try {
    const result = await props.request<GroupCommands>(`groups/${encodeURIComponent(props.groupId)}/commands`)
    if (!disposed && current === version && result) accept(result)
  } catch {
    if (!disposed && current === version) error.value = '群指令设置暂时无法读取'
  } finally { if (current === version) loading.value = false }
}
async function save(item: GroupCommandItem) {
  if (!editable(item) || !valid(item) || !dirty(item)) return
  const current = version, draft = drafts.value[item.key]
  saving.value = item.key
  error.value = ''
  notice.value = ''
  try {
    const result = await props.post<GroupCommands>(`groups/${encodeURIComponent(props.groupId)}/commands/${encodeURIComponent(item.key)}`,
      {enabled: draft.enabled, cooldownSeconds: Number(draft.seconds), scope: draft.scope})
    if (!disposed && current === version && result) { accept(result, item.key); notice.value = '设置已保存' }
  } catch (reason) {
    if (!disposed && current === version) {
      const messages: Record<string, string> = {
        COMMAND_SETTINGS_LOCKED: '管理后台的停用规则已生效，此项设置不可修改',
        GROUP_NOT_BOUND: '群绑定已失效，请刷新群资料', GROUP_SETTINGS_FORBIDDEN: '当前群不可修改指令设置',
        GROUP_UNAVAILABLE: '群资料暂不可用，请刷新群资料', INVALID_GROUP_COMMAND: '该指令未开放群配置',
        INVALID_COMMAND_SETTINGS: '请检查设置，冷却时间应为 0–3600 秒'
      }
      if (reason instanceof ApiError && reason.code === 'COMMAND_SETTINGS_LOCKED') {
        const latest = await props.request<GroupCommands>(`groups/${encodeURIComponent(props.groupId)}/commands`).catch(() => null)
        if (!disposed && current === version && latest) accept(latest, item.key)
      }
      if (!disposed && current === version) error.value = reason instanceof ApiError ? messages[reason.code] || '保存失败，请稍后重试' : '保存失败，请稍后重试'
    }
  } finally { if (current === version) saving.value = '' }
}
function mayLeave() {
  return !settings.value?.items.some(item => !item.locked && dirty(item)) || window.confirm('有未保存的指令设置，确认离开？')
}
defineExpose({mayLeave})
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
  <section class="group-command-settings glass" aria-label="群指令设置" :aria-busy="loading || !!saving">
    <header class="command-heading"><div><Icon name="settings"/><h3>群指令设置</h3></div>
      <button type="button" class="text-button" aria-label="刷新群指令设置" :disabled="loading || !!saving"
              @click="mayLeave() && load()"><Icon name="refresh" :class="{'refresh-spinning': loading}"/></button>
    </header>
    <p v-if="loading && !settings" class="command-note" role="status">正在读取指令设置…</p>
    <template v-if="settings">
      <details v-for="item in settings.items" :key="item.key" class="command-item" :data-command="item.key">
        <summary><span>{{ item.displayName }}<small>/{{ item.key }}</small></span>
          <span class="command-state"><Icon v-if="item.locked" name="lock"/>{{ item.locked ? '后台已停用' : item.enabled ? '已开启' : '已关闭' }}</span><Icon name="chevron"/></summary>
        <div v-if="item.locked" class="command-lock"><p>{{ item.lockReason }}</p><small>开始：{{ date(item.startsAt) }}<br/>结束：{{ date(item.endsAt) }}</small></div>
        <form v-if="drafts[item.key]" @submit.prevent="save(item)">
          <fieldset :disabled="!editable(item)">
            <label v-if="item.switchable" class="command-toggle"><span>群内启用</span><ToggleSwitch v-model="drafts[item.key].enabled"
                   :label="`${item.displayName}群内启用`" :disabled="!editable(item)"/></label>
            <template v-if="item.rateLimitable">
              <label class="command-field"><span>冷却时间（秒）</span><input v-model="drafts[item.key].seconds" type="text" inputmode="numeric"
                     maxlength="4" :aria-label="`${item.displayName}冷却时间`" placeholder="0–3600"/></label>
              <div class="command-field"><span>频控范围</span><DropdownSelect v-model="drafts[item.key].scope" :options="scopes"
                   :label="`${item.displayName}频控范围`" :disabled="!editable(item)"/></div>
              <p class="command-note">0 表示不限制。对局中的操作不占用开局冷却。</p>
            </template>
            <button type="submit" class="command-save" :disabled="!editable(item) || !valid(item) || !dirty(item)">{{ saving === item.key ? '保存中…' : '保存设置' }}</button>
          </fieldset>
        </form>
      </details>
      <p v-if="!settings.canEdit" class="command-note">当前群不可修改指令设置</p>
    </template>
    <p v-if="error" class="command-note error" role="alert">{{ error }}<button v-if="!settings" class="text-button" :disabled="loading" @click="load">重试</button></p>
    <p v-else-if="notice" class="command-note" role="status">{{ notice }}</p>
  </section>
</template>

<style scoped>
.group-command-settings { min-width: 0; background: transparent; border: 0; border-top: 1px solid var(--ui-border); border-radius: 0; padding: 24px 0; margin-top: 0; }
.command-heading, .command-heading > div { display: flex; align-items: center; gap: 9px; }
.command-heading { justify-content: space-between; margin-bottom: 8px; }
.command-heading h3 { margin: 0; font-size: 14px; font-weight: 550; }
.command-heading svg { width: 16px; height: 16px; color: var(--ui-muted); }
.command-item + .command-item { border-top: 1px solid var(--ui-border); }
.command-item > summary { display: flex; align-items: center; gap: 9px; padding: 13px 0; color: var(--ui-secondary); list-style: none; cursor: pointer; font-size: 12px; }
.command-item > summary::-webkit-details-marker { display: none; }
.command-item > summary > span:first-child { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.command-item > summary small { display: block; margin-top: 4px; font-size: 10px; color: var(--ui-muted); }
.command-item > summary > svg { width: 12px; height: 12px; transition: transform .18s; }
.command-item[open] > summary > svg { transform: rotate(90deg); }
.command-state { display: inline-flex; align-items: center; gap: 4px; font-size: 10px; color: var(--ui-muted); }
.command-state svg { width: 12px; height: 12px; }
fieldset { border: 0; padding: 0 0 12px; margin: 0; min-width: 0; }
.command-toggle, .command-field { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin: 10px 0; font-size: 11px; color: var(--ui-muted); }
.command-field { display: grid; grid-template-columns: 100px minmax(0, 1fr); }
.command-field input { min-width: 0; width: 100%; box-sizing: border-box; padding: 11px 12px; border: 1px solid var(--ui-border); border-radius: 11px; background: var(--ui-input); color: var(--ui-text); font: inherit; font-size: 12px; }
.command-save { padding: 8px 12px; border: 1px solid var(--ui-border); border-radius: 9px; background: var(--ui-raised); color: var(--ui-text); font: inherit; font-size: 11px; cursor: pointer; }
.command-save:disabled, fieldset:disabled input { opacity: .5; cursor: default; }
.command-note { margin: 10px 0; font-size: 10px; color: var(--ui-muted); line-height: 1.8; }
.command-note > button { margin-left: 12px; }
.command-note.error { color: var(--ui-danger); }
.command-lock { padding: 10px 12px; margin: 0 0 12px; border-radius: 9px; background: var(--ui-raised); color: var(--ui-muted); }
.command-lock p { margin: 0 0 5px; font-size: 11px; overflow-wrap: anywhere; }
.command-lock small { font-size: 10px; line-height: 1.8; }
@media (max-width: 760px) { .group-command-settings { padding: 24px 0; border-radius: 0; } }
@media (max-width: 360px) { .command-field { grid-template-columns: 85px minmax(0, 1fr); gap: 8px; } }
@media (prefers-reduced-motion: reduce) { .command-item > summary > svg { transition: none; } }
</style>
