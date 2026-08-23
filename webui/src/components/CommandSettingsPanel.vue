<template>
  <div class="command-panel">
    <div class="command-toolbar">
      <label class="command-search">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <input v-model.trim="query" placeholder="搜索指令、别名或说明" />
      </label>
      <div class="command-stats"><strong>{{ activeRuleCount }}</strong> 条生效规则 · {{ items.length }} 个指令</div>
    </div>

    <div v-if="loading" class="empty-state">加载中...</div>
    <div v-else-if="error" class="empty-state error">{{ error }}</div>
    <div v-else-if="filtered.length === 0" class="empty-state">没有匹配的指令</div>
    <div v-else class="command-list">
      <article v-for="item in filtered" :key="item.name" class="command-row">
        <div class="command-main">
          <div class="command-name-line">
            <code>/{{ item.name }}</code>
            <span v-if="isTestCommand(item)" class="command-badge test">测试指令</span>
            <span v-if="!item.registered" class="command-badge warn">未绑定执行器</span>
            <span v-if="isActive(item.rules?.global)" class="command-badge off">全局停用中</span>
            <span v-else-if="activeGroups(item).length" class="command-badge group">{{ activeGroups(item).length }} 个群停用中</span>
          </div>
          <p>{{ item.description || '暂无说明' }}</p>
          <small v-if="item.aliases?.length">别名：{{ item.aliases.map(v => '/' + v).join('、') }}</small>
          <small v-else>用法：{{ item.usage }}</small>
        </div>
        <div class="command-actions">
          <button class="ghost-button command-action" @click="openGlobal(item)">
            {{ item.rules?.global ? '编辑全局规则' : '全局规则' }}
          </button>
          <button class="ghost-button command-action" @click="openGroups(item)">
            群规则 <span v-if="groupCount(item)">{{ groupCount(item) }}</span>
          </button>
        </div>
      </article>
    </div>

    <Teleport to="body">
      <div v-if="dialog" class="command-modal-backdrop" @mousedown.self="closeDialog">
        <section class="command-modal" role="dialog" aria-modal="true">
          <header>
            <div><h3>{{ dialog.mode === 'global' ? '全局停用规则' : '单群停用规则' }}</h3><p>/{{ dialog.item.name }}</p></div>
            <button class="command-modal-close" aria-label="关闭" @click="closeDialog">×</button>
          </header>

          <div v-if="dialog.mode === 'group' && groupRules.length" class="command-existing">
            <div class="command-existing-title">已配置群（{{ groupRules.length }}）</div>
            <div v-for="entry in groupRules" :key="entry.groupId" class="command-existing-rule">
              <code>{{ entry.groupId }}</code>
              <small>{{ isActive(entry.rule) ? '停用中' : '未生效' }}</small>
              <button title="清除该群规则" :disabled="saving" @click="removeGroupRule(entry.groupId)">×</button>
            </div>
          </div>

          <div class="command-form">
            <label v-if="dialog.mode === 'group'" class="full">
              <span>目标群OpenId</span>
              <textarea v-model="form.groupIds" rows="6" placeholder="在此处输入群id" />
            </label>
            <label class="full"><span>触发时回复</span><textarea v-model.trim="form.reason" rows="3" placeholder="留空则回复默认停用提示" /></label>
            <label v-if="dialog.mode === 'group'" class="command-permanent full">
              <input v-model="form.permanent" type="checkbox" @change="applyPermanent" />
              <span><strong>永久停用</strong><small>持续生效，直到手动清除该群规则</small></span>
            </label>
            <label><span>开始时间</span><input v-model="form.startsAt" type="datetime-local" :disabled="form.permanent" /></label>
            <label><span>结束时间</span><input v-model="form.endsAt" type="datetime-local" :disabled="form.permanent" /></label>
          </div>
          <p class="command-time-note">时间留空表示不限制。全局规则生效时，会优先回复全局理由。</p>
          <p v-if="dialogError" class="command-dialog-error">{{ dialogError }}</p>
          <footer>
            <button v-if="hasCurrentRule" class="ghost-button danger" :disabled="saving" @click="removeRule">清除规则</button>
            <span></span>
            <button class="ghost-button" :disabled="saving" @click="closeDialog">取消</button>
            <button class="primary-button" :disabled="saving || (dialog.mode === 'group' && !form.groupIds.trim())" @click="saveRule">{{ saving ? '保存中...' : '保存规则' }}</button>
          </footer>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import {computed, reactive, ref} from 'vue'

const props = defineProps({api: {type: Function, required: true}})
const items = ref([]), loading = ref(false), error = ref(''), query = ref('')
const dialog = ref(null), dialogError = ref(''), saving = ref(false)
const form = reactive({groupIds: '', reason: '', startsAt: '', endsAt: '', permanent: false})

const filtered = computed(() => {
  const q = query.value.toLowerCase()
  if (!q) return items.value
  return items.value.filter(v => [v.name, v.description, v.usage, ...(v.aliases || [])].join(' ').toLowerCase().includes(q))
})
const activeRuleCount = computed(() => items.value.reduce((sum, item) => sum + (isActive(item.rules?.global) ? 1 : 0) + activeGroups(item).length, 0))
const groupRules = computed(() => Object.entries(dialog.value?.item.rules?.groups || {}).map(([groupId, rule]) => ({groupId, rule})))
const hasCurrentRule = computed(() => dialog.value?.mode === 'global' && !!dialog.value.item.rules?.global)

function isActive(rule) {
  if (!rule) return false
  const now = Date.now(), start = rule.startsAt ? new Date(rule.startsAt).getTime() : null, end = rule.endsAt ? new Date(rule.endsAt).getTime() : null
  return (start == null || now >= start) && (end == null || now < end)
}
function activeGroups(item) { return Object.values(item.rules?.groups || {}).filter(isActive) }
function groupCount(item) { return Object.keys(item.rules?.groups || {}).length }
function isTestCommand(item) { return [item.name, ...(item.aliases || [])].some(name => String(name).toLowerCase().startsWith('test')) }
function assignRule(rule) { Object.assign(form, {groupIds: '', reason: rule?.reason || '', startsAt: rule?.startsAt || '', endsAt: rule?.endsAt || '', permanent: !!rule && !rule.startsAt && !rule.endsAt}) }
function openGlobal(item) { dialog.value = {mode: 'global', item}; assignRule(item.rules?.global); dialogError.value = '' }
function openGroups(item) {
  dialog.value = {mode: 'group', item}
  dialogError.value = ''
  const configured = Object.values(item.rules?.groups || {})
  assignRule(configured.length ? configured[configured.length - 1] : null)
  if (!configured.length) form.permanent = true
}
function applyPermanent() { if (form.permanent) { form.startsAt = ''; form.endsAt = '' } }
function closeDialog() { if (!saving.value) dialog.value = null }

async function load() {
  loading.value = true; error.value = ''
  try {
    items.value = await props.api('/command-settings') || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
async function saveRule() {
  saving.value = true; dialogError.value = ''
  try {
    const name = encodeURIComponent(dialog.value.item.name)
    const body = {reason: form.reason || null, startsAt: form.startsAt || null, endsAt: form.endsAt || null}
    if (dialog.value.mode === 'global') await props.api(`/command-settings/${name}/global`, {method: 'PUT', body: JSON.stringify(body)})
    else {
      const groupIds = [...new Set(form.groupIds.split(/\r?\n/).map(v => v.trim()).filter(Boolean))]
      await props.api(`/command-settings/${name}/groups`, {method: 'PUT', body: JSON.stringify({...body, groupIds})})
    }
    await load(); const fresh = items.value.find(v => v.name === dialog.value.item.name)
    if (dialog.value.mode === 'global') { dialog.value.item = fresh; assignRule(fresh.rules?.global) }
    else { dialog.value.item = fresh; form.groupIds = '' }
  } catch (e) { dialogError.value = e.message } finally { saving.value = false }
}
async function removeRule() {
  saving.value = true; dialogError.value = ''
  try {
    const name = encodeURIComponent(dialog.value.item.name)
    if (dialog.value.mode === 'global') await props.api(`/command-settings/${name}/global`, {method: 'DELETE'})
    const mode = dialog.value.mode, commandName = dialog.value.item.name
    await load();
    dialog.value.item = items.value.find(v => v.name === commandName)
    if (mode === 'global') assignRule(null)
  } catch (e) { dialogError.value = e.message } finally { saving.value = false }
}
async function removeGroupRule(groupId) {
  saving.value = true; dialogError.value = ''
  try {
    const commandName = dialog.value.item.name
    await props.api(`/command-settings/${encodeURIComponent(commandName)}/groups/${encodeURIComponent(groupId)}`, {method: 'DELETE'})
    await load(); dialog.value.item = items.value.find(v => v.name === commandName)
  } catch (e) { dialogError.value = e.message } finally { saving.value = false }
}

defineExpose({load})
</script>

<style scoped>
.command-toolbar,.command-row,.command-actions,.command-name-line,.command-modal header,.command-modal footer{display:flex;align-items:center}.command-toolbar{justify-content:space-between;gap:16px;margin-bottom:12px}.command-search{height:34px;flex:1;max-width:360px;display:flex;align-items:center;gap:8px;padding:0 10px;border:1px solid var(--color-border-input);border-radius:var(--radius-md);background:var(--color-surface)}.command-search:focus-within{border-color:var(--color-accent-border);box-shadow:var(--focus-ring)}.command-search svg{width:16px;fill:none;stroke:currentColor;color:var(--color-text-muted);stroke-width:2}.command-search input{width:100%;border:0;outline:0;background:none;color:var(--color-text);font-size:var(--text-sm)}.command-stats{font-size:var(--text-xs);color:var(--color-text-muted);white-space:nowrap}.command-stats strong{color:var(--color-text-strong)}.command-list{border-top:1px solid var(--color-hairline)}.command-row{justify-content:space-between;gap:18px;padding:13px 2px;border-bottom:1px solid var(--color-hairline)}.command-main{min-width:0}.command-name-line{gap:7px}.command-name-line code{font-size:var(--text-md);font-weight:600;color:var(--color-text-strong)}.command-main p{margin:3px 0;color:var(--color-text);font-size:var(--text-sm)}.command-main small{color:var(--color-text-muted);font-size:var(--text-xs)}.command-badge{padding:2px 6px;border-radius:10px;font-size:10px;background:var(--color-surface-sunken);color:var(--color-text-muted)}.command-badge.off{background:var(--color-danger-soft);color:var(--color-danger)}.command-badge.group{background:var(--color-warning-soft);color:var(--color-warning-strong)}.command-actions{gap:7px;flex:none}.command-action{height:30px;min-height:30px;padding:0 9px;font-size:var(--text-xs)}.command-action span{margin-left:3px}.command-modal-backdrop{position:fixed;inset:0;z-index:1000;display:grid;place-items:center;padding:20px;background:rgba(15,23,42,.42);backdrop-filter:blur(2px)}.command-modal{width:min(620px,100%);max-height:min(760px,calc(100vh - 40px));overflow:auto;padding:20px;border:1px solid var(--color-border);border-radius:var(--radius-lg);background:var(--color-surface);box-shadow:var(--shadow-lg)}.command-modal header{justify-content:space-between;padding-bottom:13px;border-bottom:1px solid var(--color-hairline)}.command-modal h3{margin:0;font-size:var(--text-lg)}.command-modal header p{margin:3px 0 0;color:var(--color-text-muted);font-family:var(--font-mono);font-size:var(--text-xs)}.command-modal-close{border:0;background:none;color:var(--color-text-muted);font-size:25px;cursor:pointer}.command-existing{display:flex;flex-wrap:wrap;gap:7px;padding:13px 0;border-bottom:1px solid var(--color-hairline)}.command-existing-title{width:100%;font-size:var(--text-xs);color:var(--color-text-muted)}.command-existing button{display:flex;gap:7px;padding:6px 8px;border:1px solid var(--color-border);border-radius:var(--radius-md);background:var(--color-surface);color:var(--color-text);cursor:pointer}.command-existing button.active{border-color:var(--color-accent-border);background:var(--color-accent-soft)}.command-existing small{color:var(--color-text-muted)}.command-form{display:grid;grid-template-columns:1fr 1fr;gap:13px;padding-top:15px}.command-form label{display:flex;flex-direction:column;gap:6px}.command-form label.full{grid-column:1/-1}.command-form label>span{font-size:var(--text-xs);font-weight:600;color:var(--color-text-muted)}.command-form input,.command-form textarea{box-sizing:border-box;width:100%;padding:8px 10px;border:1px solid var(--color-border-input);border-radius:var(--radius-md);background:var(--color-surface);color:var(--color-text);font:inherit;outline:none}.command-form input{height:34px}.command-form textarea{resize:vertical}.command-form input:focus,.command-form textarea:focus{border-color:var(--color-accent-border);box-shadow:var(--focus-ring)}.command-time-note{margin:10px 0;font-size:var(--text-xs);color:var(--color-text-muted)}.command-dialog-error{color:var(--color-danger);font-size:var(--text-sm)}.command-modal footer{gap:8px;padding-top:14px;border-top:1px solid var(--color-hairline)}.command-modal footer span{flex:1}.command-modal footer button{min-height:32px}.danger{color:var(--color-danger)}
@media(max-width:700px){.command-toolbar,.command-row{align-items:stretch;flex-direction:column}.command-search{max-width:none}.command-actions{justify-content:flex-end}.command-form{grid-template-columns:1fr}.command-form label.full{grid-column:auto}}
.command-badge.test{border:1px solid var(--color-danger);background:var(--color-danger-soft);color:var(--color-danger);font-weight:600}
.command-existing-rule{display:flex;align-items:center;gap:7px;max-width:100%;padding:5px 7px;border:1px solid var(--color-border);border-radius:var(--radius-md);background:var(--color-surface)}
.command-existing-rule code{max-width:240px;overflow:hidden;text-overflow:ellipsis;color:var(--color-text)}
.command-existing-rule button{padding:0 2px;border:0;background:transparent;color:var(--color-text-muted);font-size:17px}
.command-permanent{flex-direction:row!important;align-items:center;padding:10px;border:1px solid var(--color-border);border-radius:var(--radius-md);cursor:pointer}
.command-form .command-permanent>input{width:16px;height:16px;margin:0;flex:none}
.command-permanent>span{display:flex;flex-direction:column;gap:2px}.command-permanent strong{color:var(--color-text-strong);font-size:var(--text-sm)}.command-permanent small{font-weight:400;color:var(--color-text-muted)}
.command-form input:disabled{opacity:.55;background:var(--color-surface-sunken)}
</style>
