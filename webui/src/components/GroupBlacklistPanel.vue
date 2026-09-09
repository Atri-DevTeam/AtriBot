<template>
  <div class="chatnt-info-section">
    <div class="chatnt-info-label chatnt-info-label-line">
      <span>群黑名单</span>
      <div class="blacklist-tools">
        <button type="button" class="nt-mini-btn blacklist-add-toggle" title="添加群黑名单" aria-label="添加群黑名单"
                :aria-expanded="showAdd" :disabled="!canManage || saving" @click="showAdd = !showAdd">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
        </button>
        <button type="button" class="nt-mini-btn" title="刷新群黑名单" aria-label="刷新群黑名单"
                :disabled="!canManage || loading || saving" @click="queryBlacklist()">
          <svg :class="{ spin: loading }" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12a9 9 0 1 1-2.64-6.36"/><polyline points="21 3 21 9 15 9"/></svg>
          {{ loading ? '查询中' : '查询' }}
        </button>
      </div>
    </div>
    <div v-if="!canManage" class="nt-empty">机器人不是群管理员，无法查询或添加</div>
    <template v-else>
      <form v-if="showAdd" class="blacklist-add-form" @submit.prevent="addMember">
        <label>成员 OpenID
          <input v-model="memberOpenId" type="text" placeholder="输入成员 OpenID" :disabled="saving" autocomplete="off" />
        </label>
        <p v-if="addError" class="blacklist-error" role="alert">{{ addError }}</p>
        <div class="blacklist-form-actions">
          <button type="button" class="nt-mini-btn" :disabled="saving" @click="showAdd = false">取消</button>
          <button type="submit" class="nt-mini-btn blacklist-submit" :disabled="saving || !memberOpenId.trim()">{{ saving ? '添加中…' : '确认添加' }}</button>
        </div>
      </form>
      <p v-if="queryError" class="blacklist-error" role="alert">{{ queryError }}</p>
      <div v-if="queried" class="nt-card">
        <div v-if="!users.length" class="nt-empty">群黑名单为空</div>
        <template v-else>
          <div class="blacklist-count">已加载 {{ users.length }} 人</div>
          <div v-for="user in users" :key="user.memberOpenId" class="blacklist-member">
            <div class="blacklist-member-head">
              <strong>{{ user.username || '未命名成员' }}</strong>
              <span v-if="user.bot" class="blacklist-tip">机器人</span>
            </div>
            <span class="blacklist-id">{{ user.memberOpenId }}</span>
            <span class="blacklist-tip">拉黑时间 {{ formatTime(user.bannedAt) }}</span>
          </div>
        </template>
        <button v-if="nextCursor" type="button" class="nt-mini-btn blacklist-more"
                :disabled="loading || saving" @click="queryBlacklist(true)">{{ loading ? '加载中…' : '加载更多' }}</button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  groupOpenId: { type: String, required: true },
  canManage: { type: Boolean, default: false },
  request: { type: Function, required: true },
  formatTime: { type: Function, required: true }
})
const emit = defineEmits(['notice'])
const users = ref([])
const nextCursor = ref('')
const queried = ref(false)
const loading = ref(false)
const queryError = ref('')
const showAdd = ref(false)
const memberOpenId = ref('')
const saving = ref(false)
const addError = ref('')
let generation = 0
let queryVersion = 0

async function queryBlacklist(append = false) {
  if (!props.canManage || (append && (loading.value || !nextCursor.value))) return
  const version = ++queryVersion
  const currentGeneration = generation
  const cursor = append ? nextCursor.value : ''
  loading.value = true
  queryError.value = ''
  try {
    const data = await props.request(`/groups/${encodeURIComponent(props.groupOpenId)}/member-blacklist?limit=20&cursor=${encodeURIComponent(cursor)}`)
    if (currentGeneration !== generation || version !== queryVersion) return
    if (!Array.isArray(data?.users)) throw new Error('未获取到群黑名单列表')
    const merged = append ? [...users.value, ...data.users] : data.users
    users.value = [...new Map(merged.map(user => [user.memberOpenId, user])).values()]
    nextCursor.value = data.nextCursor || ''
    queried.value = true
  } catch (error) {
    if (currentGeneration === generation && version === queryVersion) queryError.value = error.message || '查询群黑名单失败'
  } finally {
    if (currentGeneration === generation && version === queryVersion) loading.value = false
  }
}

async function addMember() {
  const id = memberOpenId.value.trim()
  if (!props.canManage || saving.value || !id) return
  const currentGeneration = generation
  saving.value = true
  addError.value = ''
  try {
    await props.request(`/groups/${encodeURIComponent(props.groupOpenId)}/member-blacklist`, {
      method: 'POST', body: JSON.stringify({ memberOpenId: id })
    })
    if (currentGeneration !== generation) return
    showAdd.value = false
    memberOpenId.value = ''
    emit('notice', '已加入群黑名单')
    await queryBlacklist()
  } catch (error) {
    if (currentGeneration === generation) addError.value = error.message || '添加群黑名单失败'
  } finally {
    if (currentGeneration === generation) saving.value = false
  }
}

watch(() => [props.groupOpenId, props.canManage], () => {
  generation++
  queryVersion++
  users.value = []
  nextCursor.value = ''
  queried.value = false
  loading.value = false
  queryError.value = ''
  showAdd.value = false
  memberOpenId.value = ''
  saving.value = false
  addError.value = ''
})

onBeforeUnmount(() => { generation++; queryVersion++ })
</script>

<style scoped>
.blacklist-tools { display: flex; align-items: center; gap: 4px; }
.blacklist-add-toggle { justify-content: center; width: 25px; padding: 0; }
.blacklist-add-form { display: grid; gap: 8px; margin-bottom: 10px; padding: 12px; border: 1px solid #e4e6ea; border-radius: 8px; background: #f7f8fa; }
.blacklist-add-form label { display: grid; gap: 6px; color: #6b7686; font-size: 12px; }
.blacklist-add-form input { width: 100%; min-width: 0; height: 32px; padding: 0 9px; border: 1px solid #d5dbe3; border-radius: 6px; background: #fff; color: #1f2329; font-size: 12px; }
.blacklist-add-form input:focus { border-color: #0099ff; outline: none; box-shadow: 0 0 0 2px rgba(0,153,255,.12); }
.blacklist-form-actions { display: flex; justify-content: flex-end; gap: 8px; }
.blacklist-submit { color: #0099ff; background: #e0f0ff; }
.blacklist-error { margin: 8px 0; padding: 8px 10px; border-left: 3px solid #d14343; border-radius: 5px; background: #fff1f0; color: #d14343; font-size: 12px; overflow-wrap: anywhere; }
.blacklist-count { color: #8f98a6; font-size: 11px; padding: 4px 0 8px; }
.blacklist-member { display: flex; flex-direction: column; gap: 4px; padding: 10px 0; border-top: 1px solid #eceef1; }
.blacklist-member-head { display: flex; align-items: center; gap: 6px; min-width: 0; }
.blacklist-member-head strong { min-width: 0; color: #4d5968; font-size: 12px; font-weight: 500; overflow-wrap: anywhere; }
.blacklist-id { color: #6b7686; font-family: ui-monospace, monospace; font-size: 11px; overflow-wrap: anywhere; }
.blacklist-tip { color: #8f98a6; font-size: 11px; }
.blacklist-more { justify-content: center; width: 100%; margin-top: 8px; }
.blacklist-tools button:focus-visible, .blacklist-form-actions button:focus-visible, .blacklist-more:focus-visible { outline: 2px solid #0099ff; outline-offset: 1px; }
</style>
