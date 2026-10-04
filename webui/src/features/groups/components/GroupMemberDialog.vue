<template>
  <Teleport to="body">
    <div class="member-dialog-backdrop" @click.self="close">
      <section ref="dialogEl" class="member-dialog" role="dialog" aria-modal="true"
               aria-labelledby="group-member-dialog-title" :aria-busy="loading || removing"
               tabindex="-1" @keydown.esc.stop.prevent="close" @keydown.tab="trapFocus">
        <header class="member-dialog-head">
          <h3 id="group-member-dialog-title">成员详情</h3>
          <button type="button" class="member-dialog-close" aria-label="关闭" :disabled="removing" @click="close">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>
          </button>
        </header>
        <div class="member-dialog-body">
          <div class="member-dialog-profile">
            <div class="member-dialog-avatar">
              <span>{{ displayName.slice(0, 1).toUpperCase() }}</span>
              <img v-if="avatarUrl && !avatarFailed" :src="avatarUrl" alt="" referrerpolicy="no-referrer" @error="avatarFailed = true" />
            </div>
            <div class="member-dialog-identity">
              <strong>{{ displayName }}</strong>
              <div v-if="info" class="member-dialog-badges">
                <span>{{ memberRole }}</span>
                <span>{{ info.bot ? '机器人' : '用户' }}</span>
              </div>
              <span v-else>群成员</span>
            </div>
          </div>
          <p v-if="loading" class="member-dialog-status" role="status">正在获取成员信息…</p>
          <p v-else-if="loadError" class="member-dialog-alert" role="alert">{{ loadError }}</p>
          <dl v-else-if="info" class="member-dialog-details">
            <div><dt>入群时间</dt><dd>{{ joinedAt }}</dd></div>
            <div><dt>成员 OpenID</dt><dd class="member-dialog-id">{{ info.memberOpenId }}</dd></div>
            <div v-if="info.unionOpenId && info.unionOpenId !== info.memberOpenId">
              <dt>Union OpenID</dt><dd class="member-dialog-id">{{ info.unionOpenId }}</dd>
            </div>
          </dl>
          <p v-if="actionError" class="member-dialog-error" role="alert">{{ actionError }}</p>
        </div>
        <footer v-if="canManage" class="member-dialog-foot">
          <template v-if="confirmingRemove">
            <p>确认将 {{ displayName }} 踢出本群？</p>
            <label class="member-dialog-blacklist">
              <input v-model="addToMemberBlacklist" type="checkbox" :disabled="removing" />
              同时加入群黑名单
            </label>
            <div class="member-dialog-actions">
              <button type="button" class="member-dialog-btn" :disabled="removing" @click="confirmingRemove = false">取消</button>
              <button type="button" class="member-dialog-btn member-dialog-btn--danger" :disabled="removing" @click="removeMember">
                {{ removing ? '正在踢出…' : addToMemberBlacklist ? '踢出并拉黑' : '确认踢出' }}
              </button>
            </div>
          </template>
          <div v-else class="member-dialog-actions">
            <button type="button" class="member-dialog-btn member-dialog-btn--danger" @click="addToMemberBlacklist = false; confirmingRemove = true">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 5H4v14h5m5-14 7 7-7 7M8 12h13"/></svg>
              踢出
            </button>
            <button type="button" class="member-dialog-btn" @click="muteMember">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M8 3h11a2 2 0 0 1 2 2v10M3 4a2 2 0 0 0-1 1.7V21l4-4h11M2 2l20 20"/></svg>
              禁言
            </button>
          </div>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  groupOpenId: { type: String, required: true },
  member: { type: Object, required: true },
  request: { type: Function, required: true },
  avatarUrl: { type: String, default: '' }
})
const emit = defineEmits(['close', 'mute', 'removed'])
const dialogEl = ref(null)
const info = ref(null)
const canManage = ref(false)
const loading = ref(false)
const removing = ref(false)
const confirmingRemove = ref(false)
const addToMemberBlacklist = ref(false)
const loadError = ref('')
const actionError = ref('')
const avatarFailed = ref(false)
let requestVersion = 0
const previousFocus = document.activeElement

const displayName = computed(() => info.value?.username || props.member.username || '该成员')
const memberRole = computed(() => ({ owner: '群主', admin: '管理员', member: '普通成员' })[info.value?.memberRole?.toLowerCase()] || info.value?.memberRole || '未知')
const joinedAt = computed(() => {
  const value = info.value?.joinedAt
  if (!value) return '未知'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
})
const memberPath = computed(() => `/groups/${encodeURIComponent(props.groupOpenId)}/members/${encodeURIComponent(props.member.unionOpenId)}`)

async function loadInfo() {
  const version = ++requestVersion
  info.value = null
  canManage.value = false
  loadError.value = ''
  actionError.value = ''
  confirmingRemove.value = false
  addToMemberBlacklist.value = false
  loading.value = true
  try {
    if (!props.member.unionOpenId) throw new Error('该成员缺少 OpenID，无法查询详情')
    const data = await props.request(memberPath.value)
    if (version !== requestVersion) return
    canManage.value = data?.canManage === true
    info.value = data?.memberInfo || null
    if (!info.value) loadError.value = data?.memberInfoError || '未获取到成员信息'
  } catch (error) {
    if (version === requestVersion) loadError.value = error.message || '获取成员信息失败'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function muteMember() {
  if (!canManage.value || !props.member.unionOpenId) return
  emit('mute', { ...props.member, username: displayName.value })
}

async function removeMember() {
  if (!canManage.value || !props.member.unionOpenId || !confirmingRemove.value || removing.value) return
  const version = requestVersion
  const member = { ...props.member, username: displayName.value }
  const blacklist = addToMemberBlacklist.value
  removing.value = true
  actionError.value = ''
  try {
    const result = await props.request(`${memberPath.value}/remove`, {
      method: 'POST',
      body: JSON.stringify({ addToMemberBlacklist: blacklist })
    })
    if (version === requestVersion) emit('removed', {
      ...member,
      addToMemberBlacklist: blacklist,
      blacklistFailed: (result?.addToMemberBlacklistFailOpenIds || []).length > 0
    })
  } catch (error) {
    if (version === requestVersion) actionError.value = error.message || '踢出成员失败'
  } finally {
    if (version === requestVersion) removing.value = false
  }
}

function close() {
  if (!removing.value) emit('close')
}

function trapFocus(event) {
  const buttons = [...dialogEl.value.querySelectorAll('button:not(:disabled), input:not(:disabled)')]
  const first = buttons[0]
  const last = buttons.at(-1)
  if (!first) { event.preventDefault(); return }
  if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogEl.value)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(() => [props.groupOpenId, props.member.unionOpenId], () => {
  avatarFailed.value = false
  removing.value = false
  loadInfo()
  nextTick(() => dialogEl.value?.focus())
}, { immediate: true })

onBeforeUnmount(() => {
  requestVersion++
  if (previousFocus?.isConnected) previousFocus.focus()
})
</script>

<style scoped>
.member-dialog-backdrop { position: fixed; inset: 0; z-index: 200; display: grid; place-items: center; padding: 12px; background: rgba(16, 20, 28, .36); backdrop-filter: blur(1px); }
.member-dialog { width: min(360px, 100%); max-height: calc(100dvh - 24px); overflow-y: auto; border: 1px solid #dfe3e8; border-radius: 12px; background: #fff; box-shadow: 0 18px 48px rgba(16, 20, 28, .18); color: #1f2329; font-size: 13px; outline: none; }
.member-dialog-head { position: relative; display: flex; align-items: center; justify-content: center; min-height: 44px; padding: 0 48px; }
.member-dialog-head h3 { margin: 0; color: #8f98a6; font-size: 12px; font-weight: 400; }
.member-dialog-close { position: absolute; right: 10px; top: 8px; display: grid; place-items: center; width: 28px; height: 28px; padding: 0; border: 0; border-radius: 7px; background: transparent; color: #8f98a6; cursor: pointer; }
.member-dialog-close:hover { background: #eceef1; color: #1f2329; }
.member-dialog-body { padding: 6px 24px 20px; }
.member-dialog-profile { display: flex; flex-direction: column; align-items: center; gap: 12px; margin-bottom: 20px; text-align: center; }
.member-dialog-avatar { position: relative; display: grid; place-items: center; width: 64px; height: 64px; flex: none; overflow: hidden; border-radius: 50%; background: #c3cad6; color: #fff; font-size: 26px; }
.member-dialog-avatar img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.member-dialog-identity { display: flex; flex-direction: column; align-items: center; gap: 8px; min-width: 0; max-width: 100%; }
.member-dialog-identity strong { max-width: 100%; font-size: 18px; font-weight: 600; line-height: 1.4; overflow-wrap: anywhere; }
.member-dialog-identity > span { color: #6b7686; font-size: 12px; }
.member-dialog-badges { display: flex; justify-content: center; flex-wrap: wrap; gap: 6px; }
.member-dialog-badges span { padding: 2px 8px; border-radius: 4px; background: #f2f4f7; color: #6b7686; font-size: 11px; }
.member-dialog-details { margin: 0; padding-top: 6px; border-top: 1px solid #eceef1; text-align: center; }
.member-dialog-details > div { display: flex; flex-direction: column; gap: 5px; padding: 9px 0; }
.member-dialog-details dt { color: #8f98a6; font-size: 11px; }
.member-dialog-details dd { margin: 0; overflow-wrap: anywhere; }
.member-dialog-id { font-family: ui-monospace, monospace; font-size: 12px; }
.member-dialog-status { color: #6b7686; text-align: center; }
.member-dialog-error { color: #d14343; overflow-wrap: anywhere; }
.member-dialog-alert { margin: 0; padding: 10px 12px; border-left: 3px solid #d14343; border-radius: 5px; background: #fff1f0; color: #d14343; font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.member-dialog-foot { margin: 0 24px; padding: 16px 0 20px; border-top: 1px solid #eceef1; text-align: center; }
.member-dialog-foot p { margin: 0 0 12px; overflow-wrap: anywhere; }
.member-dialog-blacklist { display: flex; align-items: center; justify-content: center; gap: 7px; margin-bottom: 14px; color: #4d5968; cursor: pointer; }
.member-dialog-blacklist input { width: 14px; height: 14px; margin: 0; accent-color: #0099ff; }
.member-dialog-blacklist input:focus-visible { outline: 2px solid #0099ff; outline-offset: 2px; }
.member-dialog-actions { display: flex; justify-content: center; gap: 10px; }
.member-dialog-btn { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-width: 88px; min-height: 34px; padding: 6px 12px; border: 1px solid #d5dbe3; border-radius: 7px; background: #fff; color: #4d5968; font-size: 13px; cursor: pointer; }
.member-dialog-btn:hover:not(:disabled) { background: #f2f3f5; }
.member-dialog-btn--danger { color: #d14343; border-color: #edcaca; }
.member-dialog-btn--danger:hover:not(:disabled) { background: #fff1f0; }
.member-dialog button:disabled { opacity: .5; cursor: not-allowed; }
.member-dialog button:focus-visible { outline: 2px solid #0099ff; outline-offset: 2px; }
</style>
