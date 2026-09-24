<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import Icon from './Icon.vue'
import groupsIcon from '../assets/dock-groups.png'
import { ApiError } from '../session'
import type { PrivateRequest } from '../activity'
import type { Profile } from '../profile'
import type { BindingChallenge, BoundGroup, GroupOverview, PrivatePost } from '../groups'
const GroupWelcome = defineAsyncComponent(() => import('./GroupWelcome.vue'))
const welcomeRevision = ref(0)

const props = defineProps<{ bot: Profile['bot']; request: PrivateRequest; post: PrivatePost; active: boolean }>()
const emit = defineEmits<{ copy: [value: string] }>()
const overview = shallowRef<GroupOverview | null>(null)
const pending = shallowRef<BindingChallenge | null>(null)
const input = ref(''), error = ref(''), bindingError = ref(''), notice = ref('')
const loading = ref(false), submitting = ref(false), formOpen = ref(false), now = ref(Date.now())
let disposed = false, timer: ReturnType<typeof setInterval> | undefined, ticks = 0
const groups = computed(() => overview.value?.groups || [])
const selectedGroupId = ref<string | null>(null)
const selectedBinding = computed(() => groups.value.find(item => item.groupId === selectedGroupId.value))
const group = shallowRef<BoundGroup | null>(null)
const detailLoading = ref(false), detailError = ref(''), unbinding = ref(false), confirmUnbind = ref(false), unbindError = ref('')
const listElement = ref<HTMLElement | null>(null), detailTitle = ref<HTMLElement | null>(null)
let listScroll = 0, detailVersion = 0, listVersion = 0
async function openGroup(groupId: string) {
  listScroll = window.scrollY
  selectedGroupId.value = groupId
  group.value = null; confirmUnbind.value = false; unbindError.value = ''
  void loadDetail()
  await nextTick()
  window.scrollTo({ top: 0, behavior: 'instant' })
  detailTitle.value?.focus({ preventScroll: true })
}
async function backToList() {
  const previousId = selectedGroupId.value
  selectedGroupId.value = null
  detailVersion++; group.value = null; detailLoading.value = false; detailError.value = ''; confirmUnbind.value = false; unbindError.value = ''
  await nextTick()
  window.scrollTo({ top: listScroll, behavior: 'instant' })
  const row = Array.from(listElement.value?.querySelectorAll<HTMLButtonElement>('button') || []).find(item => item.dataset.groupId === previousId)
  row?.focus({ preventScroll: true })
}
async function loadDetail() {
  const id = selectedGroupId.value
  if (!id || unbinding.value) return
  const version = ++detailVersion
  detailLoading.value = true; detailError.value = ''
  try {
    const result = await props.request<BoundGroup>(`groups/${encodeURIComponent(id)}`)
    if (!disposed && version === detailVersion && result) group.value = result
  } catch { if (!disposed && version === detailVersion) detailError.value = '群详情暂时无法读取' }
  finally { if (version === detailVersion) detailLoading.value = false }
}
async function refresh() { welcomeRevision.value++; await load(); if (selectedGroupId.value) await loadDetail() }
async function unbind() {
  const id = selectedGroupId.value
  if (!id || unbinding.value) return
  unbinding.value = true; unbindError.value = ''; listVersion++
  try {
    const result = await props.post<{ success: boolean }>('groups/unbinding', { groupId: id })
    if (!result?.success || disposed) return
    if (overview.value) overview.value = { ...overview.value, groups: groups.value.filter(item => item.groupId !== id) }
    if (pending.value?.groupId === id) pending.value = null
    notice.value = '已解除群绑定'
    await backToList()
  } catch { if (!disposed) unbindError.value = '解绑失败，请重试' }
  finally { unbinding.value = false }
}
const showForm = computed(() => formOpen.value || !groups.value.length || !!pending.value)
const remaining = computed(() => {
  const seconds = Math.max(0, Math.ceil(((pending.value?.expiresAt || 0) - now.value) / 1000))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
})
const date = (value: string | null) => value ? value.slice(0, 10).replace(/-/g, '.') : '暂无记录'
const role = (value: string | null) => ({ owner: '群主', admin: '管理员', member: '成员' }[value?.toLowerCase() || ''] || '暂无记录')
const receiveMode = (value: string | null) => ({ only_mention: '仅接收 @ 消息', mention_and_context: '@ 消息与上下文', all: '全部消息' }[value || ''] || '暂无记录')
async function load() {
  if (loading.value || submitting.value || unbinding.value || disposed) return
  const version = ++listVersion
  loading.value = true
  try {
    const result = await props.request<GroupOverview>('groups')
    if (!result || disposed || version !== listVersion) return
    const previous = pending.value
    overview.value = result; pending.value = result.pending
    if (selectedGroupId.value && !result.groups.some(item => item.groupId === selectedGroupId.value)) void backToList()
    if (previous && result.groups.some(group => group.groupId === previous.groupId) && !result.pending) {
      notice.value = '群聊绑定成功'; formOpen.value = false; input.value = ''
    } else if (previous && !result.pending && previous.expiresAt <= Date.now()) notice.value = '验证码已过期，请重新生成'
    error.value = ''
  } catch { if (!disposed && version === listVersion) error.value = '群资料暂时无法读取' }
  finally { loading.value = false }
}
async function bind() {
  if (!input.value.trim() || submitting.value || loading.value) return
  submitting.value = true; bindingError.value = ''; notice.value = ''
  try {
    const result = await props.post<BindingChallenge>('groups/binding', { groupId: input.value.trim() })
    if (result && !disposed) { pending.value = result; now.value = Date.now() }
  } catch (failure) {
    if (disposed) return
    const messages: Record<string, string> = { INVALID_GROUP: '请输入有效的群号或群 ID', GROUP_NOT_FOUND: '未找到群记录，请确认机器人已入群；也可使用 /whoami 中的群开放平台 ID', TRY_LATER: '操作较频繁，请稍后再试' }
    bindingError.value = failure instanceof ApiError ? messages[failure.code] || '暂时无法生成验证指令，请重试' : '请求失败，请检查网络后重试'
  } finally { submitting.value = false }
}
function visible() { if (props.active && !document.hidden) void load() }
watch(() => props.active, active => { if (active) void load() }, { immediate: true })
onMounted(() => {
  document.addEventListener('visibilitychange', visible)
  timer = setInterval(() => {
    now.value = Date.now()
    if (!props.active || document.hidden || !pending.value || submitting.value) return
    if (++ticks % 4 === 0) void load()
  }, 1000)
})
onBeforeUnmount(() => { disposed = true; if (timer) clearInterval(timer); document.removeEventListener('visibilitychange', visible) })
</script>

<template>
  <div class="group-management">
    <header class="group-page-heading">
      <button v-if="selectedBinding" class="group-back text-button" :disabled="unbinding" @click="backToList"><Icon name="left" />返回群列表</button>
      <div v-else class="group-page-title"><span class="group-heading-icon"><img :src="groupsIcon" alt="" width="24" height="24" /></span><div><h2>群管理</h2><p>{{ overview ? `已绑定 ${groups.length} 个群` : '读取群资料' }}</p></div></div>
      <div class="group-actions"><button class="text-button" :disabled="loading || detailLoading || unbinding" aria-label="刷新群资料" @click="refresh"><Icon name="refresh" :class="{ 'refresh-spinning': loading || detailLoading }" /></button><button v-if="groups.length && !selectedBinding" class="group-button" :aria-expanded="showForm" @click="formOpen = !formOpen"><Icon name="plus" />绑定群聊</button></div>
    </header>
    <div v-if="error" class="notice glass" role="alert"><span>{{ error }}</span><button class="text-button" :disabled="loading" @click="load">重试 <Icon name="refresh" :class="{ 'refresh-spinning': loading }" /></button></div>
    <p v-if="notice && !selectedBinding" class="group-notice" role="status">{{ notice }}</p>
    <section v-if="!selectedBinding && showForm && (overview || error)" class="group-binding glass" aria-label="绑定群聊">
      <header class="group-section-heading"><h3>{{ pending ? '验证群主身份' : '绑定你的群' }}</h3><span class="group-step">{{ pending ? '02 / 验证' : '01 / 选择群' }}</span></header>
      <form v-if="!pending || pending.expiresAt <= now" class="group-bind-form" @submit.prevent="bind">
        <label for="group-input">群号或群开放平台 ID</label>
        <div class="group-input-row"><input id="group-input" v-model="input" name="groupId" type="text" maxlength="256" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="输入要绑定的群" required :disabled="submitting" /><button class="group-button primary" type="submit" :disabled="submitting || loading || !input.trim()">{{ submitting ? '生成中…' : '生成验证指令' }}</button></div>
        <p class="group-help">机器人需已加入该群。群 ID 可在群内发送 <code>/whoami</code> 获取。</p>
      </form>
      <div v-else class="group-verification">
        <p class="group-help">使用当前 QQ，在目标群中 @ 机器人并发送：</p>
        <div class="group-command"><code>{{ pending.command }}</code><button class="copy-button" aria-label="复制群绑定指令" @click="emit('copy', pending.command)"><Icon name="copy" /></button></div>
        <div class="group-verify-footer"><span class="verification-status"><i></i>等待群主验证 <time>{{ remaining }}</time></span><button class="text-button" :disabled="loading" @click="load">我已发送 <Icon name="refresh" :class="{ 'refresh-spinning': loading }" /></button></div>
        <p class="group-help">离开页面发送后，可重新私聊发送 <code>/profile</code> 查看结果。</p>
      </div>
      <p v-if="bindingError" class="group-form-error" role="alert">{{ bindingError }}</p>
    </section>
    <div v-if="!overview && loading" class="group-loading glass" role="status"><span class="skeleton">正在读取群资料…</span></div>
    <ul v-if="overview && !selectedBinding && groups.length" ref="listElement" class="bound-groups glass" aria-label="已绑定群列表">
      <li v-for="item in groups" :key="item.groupId">
        <button class="group-row" :data-group-id="item.groupId" :aria-label="`查看${item.name || '已绑定群聊'}详情`" @click="openGroup(item.groupId)">
          <span class="bound-group-avatar"><Icon name="users" /></span>
          <span class="group-row-identity"><strong>{{ item.name || '未记录群名' }}</strong><span class="group-row-id" :title="item.groupId">GROUP ID <code>{{ item.groupId }}</code></span></span>
          <span class="group-row-meta"><span>添加时间</span><time :datetime="item.boundAt || undefined">{{ date(item.boundAt) }}</time></span>
          <Icon class="group-row-arrow" name="chevron" />
        </button>
      </li>
    </ul>
      <article v-if="selectedBinding" :key="selectedBinding.groupId" class="bound-group glass" :aria-label="selectedBinding.name || '已绑定群聊'">
        <header class="bound-group-header">
          <span class="bound-group-avatar"><Icon name="users" /></span>
          <div class="bound-group-identity"><h3 ref="detailTitle" tabindex="-1">{{ selectedBinding.name || '未记录群名' }}</h3><div class="group-id"><span>GROUP ID</span><code>{{ selectedBinding.groupId }}</code></div></div>
        </header>
        <div class="group-badges">
          <span class="group-badge owner"><Icon name="shield-check" />群主已验证</span>
          <template v-if="group?.available">
            <span class="group-badge bot-role"><Icon name="bot" />机器人 · {{ role(group.botRole) }}</span>
            <span v-if="group.memberCount != null" class="group-badge members"><Icon name="users" />{{ group.memberCount.toLocaleString('zh-CN') }} 人</span>
          </template>
        </div>
        <div v-if="detailError" class="group-detail-error" role="alert"><span>{{ detailError }}</span><button class="text-button" :disabled="detailLoading || unbinding" @click="loadDetail">重试 <Icon name="refresh" :class="{ 'refresh-spinning': detailLoading }" /></button></div>
        <p v-if="detailLoading && !group" class="group-help" role="status">正在读取群详情…</p>
        <p v-if="group && !group.available" class="group-unavailable">群资料暂不可用，机器人可能已离群</p>
        <div v-if="group?.available" class="group-content" :class="{ 'without-intro': !group.description && !group.category && !group.tags.length }">
          <div v-if="group.description || group.category || group.tags.length" class="group-intro">
            <p v-if="group.description" class="group-description">{{ group.description }}</p>
            <div v-if="group.category || group.tags.length" class="group-tags"><span v-if="group.category" class="category"><Icon name="grid" />{{ group.category }}</span><span v-for="tag in group.tags" :key="tag"><Icon name="tag" />{{ tag }}</span></div>
          </div>
          <dl class="group-details">
            <div><dt><span class="group-field-icon"><Icon name="message" /></span>消息接收</dt><dd>{{ receiveMode(group.receiveMode) }}</dd></div>
            <div><dt><span class="group-field-icon"><Icon name="broadcast" /></span>主动消息</dt><dd :class="{ muted: !group.proactive }">{{ group.proactive ? '已允许' : '未允许' }}</dd></div>
          </dl>
        </div>
        <dl class="group-dates">
          <div v-if="group?.available"><dt><Icon name="bot" />机器人入群</dt><dd>{{ date(group.joinedAt) }}</dd></div>
          <div><dt><Icon name="calendar" />绑定日期</dt><dd>{{ date(selectedBinding.boundAt) }}</dd></div>
        </dl>
        <div class="group-unbind">
          <template v-if="confirmUnbind"><span>确认解除此群的绑定？</span><div class="group-actions"><button class="text-button" :disabled="unbinding" @click="confirmUnbind = false">取消</button><button class="group-button danger" :disabled="unbinding" @click="unbind">{{ unbinding ? '解绑中…' : '确认解绑' }}</button></div></template>
          <button v-else class="text-button danger" @click="confirmUnbind = true">解除绑定</button>
        </div>
        <p v-if="unbindError" class="group-form-error" role="alert">{{ unbindError }}</p>
      </article>
    <GroupWelcome v-if="selectedBinding" :key="selectedBinding.groupId" :bot="bot" :group-id="selectedBinding.groupId" :request="request" :revision="welcomeRevision" />
  </div>
</template>

<style scoped>
.group-management { animation: arrive .3s ease both; }
.group-page-heading, .group-page-title, .group-actions, .group-section-heading, .bound-group-header { display: flex; align-items: center; }
.group-page-heading { justify-content: space-between; gap: 12px; margin: 4px 0 23px; }
.group-page-title { gap: 12px; }
.group-heading-icon { display: grid; place-items: center; width: 45px; height: 45px; border: 1px solid #fff; border-radius: 15px; background: #ffffff80; }
.group-heading-icon img { width: 24px; height: 24px; image-rendering: pixelated; }
.group-page-title h2 { margin: 0; font-size: 18px; font-weight: 550; }
.group-page-title p { margin: 5px 0 0; font-size: 10px; color: #8b9683; }
.group-actions { gap: 14px; }
.group-actions>.text-button { padding: 10px 0; }
.group-actions>.text-button svg { width: 16px; height: 16px; }
.group-button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 38px; padding: 9px 14px; border: 1px solid #fff; border-radius: 12px; background: #ffffff91; color: #637857; font-size: 11px; white-space: nowrap; }
.group-button svg { width: 13px; height: 13px; }
.group-button.primary { background: #dfe9d49c; border-color: #ffffffc9; }
.group-binding { padding: 25px 27px; border-radius: 23px; margin-bottom: 22px; }
.group-section-heading { justify-content: space-between; gap: 12px; margin-bottom: 20px; }
.group-section-heading h3 { margin: 0; font-size: 14px; font-weight: 550; }
.group-step { color: #97a28f; font-size: 10px; letter-spacing: .7px; }
.group-bind-form label { display: block; font-size: 11px; color: #7e8b74; margin-bottom: 10px; }
.group-input-row { display: flex; gap: 10px; }
.group-input-row input { flex: 1; min-width: 0; min-height: 42px; border: 1px solid #ffffff; border-radius: 12px; padding: 10px 13px; font: inherit; font-size: 14px; color: #354339; background: #ffffffa0; outline-offset: 2px; }
.group-input-row input:focus { outline: 1px solid #a3b496; }
.group-input-row input::placeholder { color: #a0aa97; }
.group-help { color: #89957e; font-size: 11px; line-height: 1.9; margin: 12px 0 0; }
.group-help code { color: #677e5c; }
.group-verification>.group-help:first-child { margin-top: 0; }
.group-command { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-top: 13px; padding: 14px 16px; background: #ffffff91; border: 1px solid #fff; border-radius: 14px; }
.group-command code { font-size: 16px; color: #596d4e; overflow-wrap: anywhere; }
.group-command .copy-button { flex-shrink: 0; }
.group-verify-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 10px; }
.verification-status { display: inline-flex; align-items: center; gap: 7px; font-size: 10px; color: #879477; }
.verification-status i { width: 5px; height: 5px; border-radius: 50%; background: #9daf86; }
.verification-status time { font-variant-numeric: tabular-nums; color: #a28e68; }
.group-form-error, .group-unavailable { margin: 12px 0 0; font-size: 11px; color: #a07863; line-height: 1.8; }
.group-notice { margin: 0 0 18px; padding-left: 2px; font-size: 12px; color: #6c855d; }
.group-loading { display: grid; place-items: center; min-height: 180px; border-radius: 23px; color: #7e8b74; font-size: 12px; }
.bound-groups { list-style: none; margin: 0; padding: 0 18px; border-radius: 20px; }
.bound-groups li + li { border-top: 1px solid #b5c3a726; }
.group-row { display: flex; align-items: center; gap: 13px; width: 100%; min-height: 78px; padding: 15px 0; border: 0; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; transition: background .18s ease; border-radius: 12px; }
.group-row:hover { background: #ffffff60; }
.group-row:focus-visible { outline: 2px solid #a3b496; outline-offset: 3px; }
.group-row .bound-group-avatar { width: 40px; height: 40px; border-radius: 13px; }
.group-row .bound-group-avatar svg { width: 20px; height: 20px; }
.group-row-identity { flex: 1; min-width: 0; }
.group-row-identity strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; font-weight: 550; }
.group-row-identity>span { display: block; margin-top: 5px; font-size: 10px; color: #8e9a82; }
.group-row-identity>.group-row-id { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 8px; letter-spacing: .6px; color: #9aa48f; }
.group-row-id code { margin-left: 5px; font-size: 9px; letter-spacing: 0; }
.group-row-meta { flex-shrink: 0; text-align: right; font-size: 10px; color: #8b9683; font-variant-numeric: tabular-nums; }
.group-row-meta>span { display: block; margin-bottom: 5px; font-size: 9px; color: #9aa48f; }
.group-detail-error, .group-unbind { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-top: 20px; font-size: 11px; color: #9d8071; }
.group-unbind { padding-top: 18px; border-top: 1px solid #b5c3a726; }
.danger { color: #ac7f70; }
.group-row-arrow { width: 14px; height: 14px; flex-shrink: 0; color: #a3b098; }
.group-back { gap: 6px; min-height: 38px; }
.group-back svg { width: 15px; height: 15px; }
.bound-group { padding: 28px; min-width: 0; border-radius: 24px; animation: arrive .25s ease both; }
.bound-group-header { gap: 15px; }
.bound-group-avatar { display: grid; place-items: center; width: 49px; height: 49px; flex-shrink: 0; border: 1px solid #fff; border-radius: 16px; background: linear-gradient(145deg,#e5ecdc90,#dce7e780); color: #8b9d7f; }
.bound-group-avatar svg { width: 24px; height: 24px; }
.bound-group-identity { flex: 1; min-width: 0; }
.bound-group-identity h3 { margin: 0; font-size: 20px; line-height: 1.5; font-weight: 550; overflow-wrap: anywhere; }
.group-badges { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 19px; }
.group-badge { display: inline-flex; align-items: center; gap: 5px; padding: 5px 9px; border: 1px solid #ffffffd9; border-radius: 9px; font-size: 10px; line-height: 1.5; }
.group-badge svg { width: 13px; height: 13px; flex-shrink: 0; }
.group-badge.owner { color: #7c906c; background: #e9efdf88; }
.group-badge.bot-role { color: #7c8d9b; background: #e6eef18c; }
.group-badge.members { color: #968977; background: #f1ecdf80; font-variant-numeric: tabular-nums; }
.group-content { display: grid; grid-template-columns: minmax(0,1fr) minmax(0,1fr); gap: 30px; margin-top: 23px; padding-top: 23px; border-top: 1px solid #b5c3a726; }
.group-content.without-intro { grid-template-columns: 1fr; }
.group-intro { min-width: 0; }
.group-description { font-size: 12px; line-height: 1.95; color: #7e8977; white-space: pre-wrap; overflow-wrap: anywhere; margin: 0; }
.group-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 13px; }
.group-tags:first-child { margin-top: 0; }
.group-tags span { display: inline-flex; align-items: center; gap: 4px; padding: 4px 7px; font-size: 9px; line-height: 1.5; color: #9a909f; background: #f1edf56b; border: 1px solid #ffffffbf; border-radius: 6px; overflow-wrap: anywhere; max-width: 100%; }
.group-tags span.category { color: #8c967c; background: #ebefdf70; }
.group-tags svg { width: 11px; height: 11px; flex-shrink: 0; }
.group-details { align-self: start; min-width: 0; margin: 0; padding: 1px 13px; background: #ffffff50; border: 1px solid #ffffffa6; border-radius: 14px; }
.group-details>div { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 10px 0; }
.group-details>div+div { border-top: 1px solid #b5c3a71f; }
.group-details dt { display: flex; align-items: center; gap: 7px; flex-shrink: 0; font-size: 10px; color: #8e9a82; }
.group-field-icon { display: grid; place-items: center; width: 25px; height: 25px; border-radius: 8px; background: #e8eddf70; color: #97a48a; }
.group-field-icon svg { width: 14px; height: 14px; }
.group-details dd { margin: 0; font-size: 11px; line-height: 1.6; color: #6f8264; text-align: right; overflow-wrap: anywhere; }
.group-details dd.muted { color: #a1a59b; }
.group-dates { display: flex; flex-wrap: wrap; gap: 22px 38px; margin: 24px 0 19px; }
.group-dates>div+div { padding-left: 25px; border-left: 1px solid #b5c3a72e; }
.group-dates dt { display: flex; align-items: center; gap: 5px; color: #9aa48f; font-size: 9px; }
.group-dates dt svg { width: 12px; height: 12px; }
.group-dates dd { margin: 7px 0 0; font-size: 11px; color: #7e8b74; font-variant-numeric: tabular-nums; letter-spacing: .4px; }
.group-id { display: flex; gap: 12px; align-items: baseline; margin-top: 8px; color: #9aa48f; }
.group-id span { flex-shrink: 0; font-size: 8px; letter-spacing: 1px; }
.group-id code { min-width: 0; font-size: 9px; overflow-wrap: anywhere; }
@media (max-width: 760px) {
  .group-page-heading { margin-bottom: 18px; }
  .group-page-title h2 { font-size: 16px; }
  .group-binding, .bound-group { padding: 21px; border-radius: 21px; }
  .bound-groups { padding: 0 13px; border-radius: 18px; }
  .group-row { gap: 10px; min-height: 72px; padding: 13px 0; }
  .group-row-identity strong { font-size: 13px; }
  .group-input-row { flex-direction: column; }
  .group-input-row input { font-size: 16px; }
  .group-input-row .group-button { align-self: flex-end; }
  .bound-group-header { gap: 12px; }
  .bound-group-identity h3 { font-size: 17px; }
  .group-badges { gap: 6px; margin-top: 17px; }
  .group-badge { font-size: 9px; padding: 5px 7px; }
  .group-content { grid-template-columns: 1fr; gap: 19px; margin-top: 19px; padding-top: 19px; }
  .group-dates { gap: 15px 23px; margin-top: 21px; }
  .group-dates>div+div { padding-left: 23px; }
  .group-command { padding: 12px; gap: 8px; }
  .group-command code { font-size: 13px; }
}
</style>
