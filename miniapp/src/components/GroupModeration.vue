<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import Icon from './Icon.vue'
import { ApiError } from '../session'
import type { PrivateRequest } from '../activity'
import type { PrivatePost } from '../groups'
import type { Moderation, ModerationSchedule, SavedKeyword, SavedDomain } from '../moderation'

const props = defineProps<{ groupId: string; request: PrivateRequest; post: PrivatePost; revision: number }>()
const emit = defineEmits<{ accessDenied: [] }>()
type Keyword = Partial<Pick<SavedKeyword, 'id' | 'masked' | 'readOnly'>> & Pick<SavedKeyword, 'matchMode' | 'remind'> & { value?: string; key: number }
type Domain = Partial<SavedDomain> & { value?: string; key: number }
const data = ref<Moderation | null>(null), loading = ref(false), saving = ref(false), error = ref(''), notice = ref('')
const keywordEnabled = ref(false), aiEnabled = ref(false), aiRemind = ref(true), allowAllLinks = ref(true)
const aiType = ref(2)
const defaultSchedule = (): ModerationSchedule => ({ enabled: false, startDate: '', endDate: '', startTime: '00:00', endTime: '23:59', daysOfWeek: [1, 2, 3, 4, 5, 6, 7] })
const schedule = ref<ModerationSchedule>(defaultSchedule())
const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const keywords = ref<Keyword[]>([]), domains = ref<Domain[]>([])
const keywordInput = ref(''), domainInput = ref(''), mode = ref<'CONTAINS' | 'EQUALS'>('CONTAINS')
const prompt = ref(''), baseline = ref('')
let version = 0, nextKey = 0, disposed = false
const canEdit = computed(() => data.value?.canManage === true && !loading.value && !saving.value)
function payload() {
  return {
    revision: data.value?.revision,
    keywordEnabled: keywordEnabled.value,
    keywords: keywords.value.map(item => ({ ...(item.id === undefined ? { value: item.value } : { id: item.id }), matchMode: item.matchMode, remind: item.remind })),
    aiEnabled: aiEnabled.value, aiType: aiType.value, aiRemind: aiRemind.value, allowAllLinks: allowAllLinks.value,
    aiSchedule: { ...schedule.value, daysOfWeek: [...schedule.value.daysOfWeek] },
    domains: domains.value.map(item => item.id === undefined ? { value: item.value } : { id: item.id }),
    ...(data.value?.canCustomizePrompt ? { customPrompt: prompt.value } : {})
  }
}
const dirty = computed(() => !!data.value && (JSON.stringify(payload()) !== baseline.value || !!keywordInput.value || !!domainInput.value))
function mayLeave() {
  if (saving.value) return false
  return !dirty.value || window.confirm('有未保存的群管设置，确认放弃修改？')
}
defineExpose({ mayLeave })
function apply(result: Moderation) {
  if (result.canManage !== true || !Array.isArray(result.keywords) || !Array.isArray(result.domains) || !result.revision)
    throw new Error('Invalid moderation response')
  data.value = result
  keywordEnabled.value = result.keywordEnabled
  aiEnabled.value = result.aiEnabled; aiRemind.value = result.aiRemind; allowAllLinks.value = result.allowAllLinks
  aiType.value = result.aiType ?? 2
  schedule.value = { ...defaultSchedule(), ...result.aiSchedule, daysOfWeek: [...(result.aiSchedule?.daysOfWeek ?? [1, 2, 3, 4, 5, 6, 7])] }
  keywords.value = result.keywords.map(item => ({ ...item, key: ++nextKey }))
  domains.value = result.domains.map(item => ({ ...item, key: ++nextKey }))
  prompt.value = result.customPrompt || ''; keywordInput.value = ''; domainInput.value = ''
  baseline.value = JSON.stringify(payload())
}
function failure(cause: unknown) {
  const code = cause instanceof ApiError ? cause.code : ''
  const messages: Record<string, string> = {
    MODERATION_FORBIDDEN: '仅已绑定的群主可以配置，请确认群绑定状态。',
    CUSTOM_PROMPT_FORBIDDEN: '自定义审查词权限已变更，请重新读取配置。',
    MODERATION_CHANGED: '配置已在其他位置更新，请重新读取后再修改。',
    INVALID_URL_PATTERN: 'URL 白名单的正则表达式无效，请检查后重试。',
    INVALID_MODERATION_SETTINGS: '配置内容无效或超出限制，请检查后重试。'
  }
  if (code === 'MODERATION_FORBIDDEN' || code === 'CUSTOM_PROMPT_FORBIDDEN') clear()
  if (code === 'MODERATION_FORBIDDEN') emit('accessDenied')
  error.value = messages[code] || '群管配置暂时无法保存或读取，请稍后重试。'
}
function clear() {
  data.value = null; keywords.value = []; domains.value = []
  keywordInput.value = ''; domainInput.value = ''; prompt.value = ''
}
async function load(ask = true) {
  if (saving.value || ask && !mayLeave()) return
  const current = ++version
  loading.value = true; error.value = ''; notice.value = ''
  clear()
  try {
    const result = await props.request<Moderation>(`groups/${encodeURIComponent(props.groupId)}/moderation`)
    if (!disposed && current === version && result) apply(result)
  } catch (cause) { if (!disposed && current === version) failure(cause) }
  finally { if (current === version) loading.value = false }
}
function addKeyword() {
  if (!canEdit.value || !keywordInput.value.trim() || keywords.value.length >= 100) return
  keywords.value.push({ key: ++nextKey, value: keywordInput.value, matchMode: mode.value, remind: true })
  keywordInput.value = ''; notice.value = ''
}
function addDomain() {
  if (!canEdit.value || !domainInput.value.trim() || domains.value.length >= 100) return
  if (domainInput.value === '.*') allowAllLinks.value = true
  else domains.value.push({ key: ++nextKey, value: domainInput.value })
  domainInput.value = ''; notice.value = ''
}
function toggleDay(day: number) {
  schedule.value.daysOfWeek = schedule.value.daysOfWeek.includes(day)
    ? schedule.value.daysOfWeek.filter(value => value !== day)
    : [...schedule.value.daysOfWeek, day].sort((a, b) => a - b)
}
async function save() {
  if (!canEdit.value || !dirty.value) return
  if (schedule.value.enabled && schedule.value.startDate && schedule.value.endDate && schedule.value.startDate > schedule.value.endDate) {
    error.value = '结束日期不能早于开始日期。'
    return
  }
  addKeyword(); addDomain()
  saving.value = true; error.value = ''; notice.value = ''
  const current = ++version
  try {
    const result = await props.post<Moderation>(`groups/${encodeURIComponent(props.groupId)}/moderation`, payload())
    if (!disposed && current === version && result) { apply(result); notice.value = '已保存' }
  } catch (cause) { if (!disposed && current === version) failure(cause) }
  finally { if (current === version) saving.value = false }
}
watch(() => props.groupId, () => { clear(); void load(false) }, { immediate: true })
watch(() => props.revision, () => { if (!saving.value) void load(false) })
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value || saving.value) { event.preventDefault(); event.returnValue = '' }
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { disposed = true; version++; clear(); window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <section class="group-moderation glass" aria-label="群管设置" :aria-busy="loading || saving">
    <header class="moderation-heading"><div><Icon name="shield-check" /><h3>群管设置</h3></div>
      <button type="button" class="text-button" :disabled="loading || saving" @click="load()">重新读取<Icon name="refresh" :class="{ 'refresh-spinning': loading }" /></button>
    </header>
    <p v-if="loading" class="moderation-help" role="status">正在验证群绑定并读取配置…</p>
    <p v-if="error" class="moderation-error" role="alert">{{ error }}</p>
    <form v-if="data?.canManage" @submit.prevent="save">
      <fieldset :disabled="!canEdit">
        <section class="moderation-block" aria-label="关键词设置">
          <div class="moderation-section-title"><h4>关键词设置</h4><label class="moderation-toggle"><input v-model="keywordEnabled" type="checkbox" />启用关键词审查</label></div>
          <p class="moderation-help">命中后按规则处理。保存后仅显示关键词的首字，如需修改内容，请删除后重新添加。</p>
          <div class="moderation-add keyword-add">
            <input v-model="keywordInput" aria-label="新关键词" placeholder="输入关键词" maxlength="256" autocomplete="off" @keydown.enter.prevent="addKeyword" />
            <select v-model="mode" aria-label="新关键词匹配模式"><option value="CONTAINS">包含</option><option value="EQUALS">全等</option></select>
            <button type="button" class="moderation-button" :disabled="!keywordInput.trim() || keywords.length >= 100" @click="addKeyword">添加</button>
          </div>
          <ul v-if="keywords.length" class="moderation-rules" aria-label="关键词列表">
            <li v-for="(item, index) in keywords" :key="item.key" class="moderation-keyword">
              <span class="moderation-secret">{{ item.id === undefined ? item.value : item.masked }}<small v-if="item.id === undefined">待保存</small></span>
              <div class="moderation-rule-actions"><select v-model="item.matchMode" :aria-label="`关键词 ${index + 1} 匹配模式`"><option value="CONTAINS">包含</option><option value="EQUALS">全等</option></select>
                <label><input v-model="item.remind" type="checkbox" />提醒</label>
                <button type="button" class="text-button danger" :aria-label="`删除关键词 ${index + 1}`" @click="keywords.splice(index, 1)">删除</button>
              </div>
              <div v-if="item.readOnly?.length" class="moderation-readonly" :aria-label="`关键词 ${index + 1} 高级设置`">
                <span class="moderation-readonly-label"><Icon name="lock" />高级设置 · 只读</span>
                <dl><div v-for="(field, fieldIndex) in item.readOnly" :key="fieldIndex"><dt>{{ field.label }}</dt><dd>{{ field.value }}</dd></div></dl>
              </div>
            </li>
          </ul>
          <p v-else class="moderation-empty">暂未设置关键词</p>
          <div v-for="(rule, index) in data.otherRules" :key="index" class="moderation-readonly" :aria-label="`${rule.title}高级设置`">
            <span class="moderation-readonly-label"><Icon name="lock" />{{ rule.title }} · 只读</span>
            <dl><div v-for="(field, fieldIndex) in rule.fields" :key="fieldIndex"><dt>{{ field.label }}</dt><dd>{{ field.value }}</dd></div></dl>
          </div>
        </section>
        <section class="moderation-block" aria-label="AI 审查设置">
          <div class="moderation-section-title"><h4>AI 审查</h4><label class="moderation-toggle"><input v-model="aiEnabled" type="checkbox" />启用 AI 审查</label></div>
          <label class="moderation-mode">审查模式<select v-model.number="aiType" aria-label="审查模式"><option :value="0">仅接口词库</option><option :value="1">接口词库与 AI 同时审查</option><option :value="2">接口词库优先，未命中再审查 AI</option><option :value="3">仅 AI</option></select></label>
          <label class="moderation-toggle"><input v-model="aiRemind" type="checkbox" />命中后发送提醒</label>
          <section class="moderation-schedule" aria-label="生效时间">
            <div class="moderation-subheading"><h5>生效时间</h5><label><input v-model="schedule.enabled" type="checkbox" />仅在指定时间审查</label></div>
            <template v-if="schedule.enabled">
              <div class="moderation-field-grid">
                <label>开始日期<input v-model="schedule.startDate" type="date" aria-label="开始日期" :max="schedule.endDate || '9999-12-31'" /></label>
                <label>结束日期<input v-model="schedule.endDate" type="date" aria-label="结束日期" :min="schedule.startDate || undefined" max="9999-12-31" /></label>
              </div>
              <div class="moderation-weekdays" role="group" aria-label="生效星期"><button v-for="(day, index) in weekdays" :key="day" type="button" :aria-pressed="schedule.daysOfWeek.includes(index + 1)" @click="toggleDay(index + 1)">{{ day }}</button></div>
              <div class="moderation-field-grid">
                <label>开始时间<input v-model="schedule.startTime" type="time" aria-label="开始时间" required /></label>
                <label>结束时间<input v-model="schedule.endTime" type="time" aria-label="结束时间" required /></label>
              </div>
              <p class="moderation-help">日期留空表示不限；结束时间早于开始时间时跨天生效，星期按开始当天计算；起止时间相同表示全天。</p>
              <p v-if="!schedule.daysOfWeek.length" class="moderation-help">未选择星期，审查将不会生效。</p>
            </template>
            <p v-else class="moderation-help">不限制生效时间。</p>
          </section>
          <div v-if="data.aiReadOnly?.length" class="moderation-readonly" aria-label="AI 高级设置">
            <span class="moderation-readonly-label"><Icon name="lock" />高级设置 · 只读</span>
            <dl><div v-for="(field, index) in data.aiReadOnly" :key="index"><dt>{{ field.label }}</dt><dd>{{ field.value }}</dd></div></dl>
          </div>
          <div class="moderation-subheading"><h5>URL 白名单</h5><label><input v-model="allowAllLinks" type="checkbox" />全部放行</label></div>
          <p class="moderation-help">支持正则表达式。保存后仅显示首尾字符；关闭全部放行时，仅放行白名单匹配的链接。</p>
          <div class="moderation-add"><input v-model="domainInput" type="text" aria-label="新 URL 白名单" placeholder="输入 URL 正则表达式" maxlength="512" autocomplete="off" spellcheck="false" :disabled="allowAllLinks" @keydown.enter.prevent="addDomain" /><button type="button" class="moderation-button" :disabled="allowAllLinks || !domainInput.trim() || domains.length >= 100" @click="addDomain">添加</button></div>
          <ul v-if="domains.length" class="moderation-rules" aria-label="URL 白名单列表">
            <li v-for="(item, index) in domains" :key="item.key"><span class="moderation-secret">{{ item.id === undefined ? item.value : item.masked }}<small v-if="item.id === undefined">待保存</small></span><button type="button" class="text-button danger" :aria-label="`删除白名单 ${index + 1}`" @click="domains.splice(index, 1)">删除</button></li>
          </ul>
          <p v-if="!allowAllLinks && !domains.length" class="moderation-help">白名单为空时不放行任何链接。</p>
          <div v-if="data.canCustomizePrompt" class="moderation-prompt">
            <div class="moderation-subheading"><h5>自定义 AI 审查词</h5><span>{{ prompt.trim() ? '已填写' : '未设置' }}</span></div>
            <p class="moderation-help">填写违规判断标准和放行条件，留空则使用默认审查词。</p>
            <textarea v-model="prompt" aria-label="自定义 AI 审查词" rows="5" maxlength="8000" autocomplete="off" placeholder="填写违规判断标准和放行条件" />
          </div>
        </section>
        <p class="moderation-reminder"><Icon name="message" /><span>默认提醒：喵~这种内容可不行哦，换个话题吧~<br />已配置的高级设置优先生效，仅可在管理后台修改。</span></p>
      </fieldset>
      <footer class="moderation-footer"><span role="status">{{ saving ? '正在保存…' : notice || (dirty ? '有未保存的修改' : '') }}</span><button type="submit" class="moderation-button primary" :disabled="!canEdit || !dirty">{{ saving ? '保存中…' : '保存设置' }}</button></footer>
    </form>
  </section>
</template>

<style scoped>
.group-moderation { margin-top: 16px; padding: 22px 25px; border-radius: 23px; min-width: 0; color: #64735e; }
.moderation-heading, .moderation-heading>div, .moderation-section-title, .moderation-subheading, .moderation-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.moderation-heading>div { justify-content: flex-start; gap: 9px; }
.moderation-heading svg { width: 16px; height: 16px; color: #8a9a80; }
h3, h4, h5 { margin: 0; font-weight: 550; }
h3 { font-size: 14px; } h4 { font-size: 13px; } h5 { font-size: 12px; }
fieldset { border: 0; padding: 0; margin: 0; min-width: 0; }
.moderation-block { margin-top: 20px; padding-top: 20px; border-top: 1px solid #b5c3a730; }
.moderation-help, .moderation-empty { font-size: 11px; color: #849079; line-height: 1.85; margin: 10px 0 13px; }
.moderation-error { color: #a07863; font-size: 12px; line-height: 1.8; }
label { display: inline-flex; align-items: center; gap: 6px; font-size: 11px; cursor: pointer; }
input[type=checkbox] { accent-color: #829970; width: 15px; height: 15px; margin: 0; }
input:not([type=checkbox]), select, textarea { box-sizing: border-box; min-width: 0; border: 1px solid #c7d1bf; border-radius: 10px; padding: 10px 12px; color: #53644b; background: #ffffffbd; font: inherit; font-size: 13px; line-height: 1.6; }
input:focus-visible, select:focus-visible, textarea:focus-visible, button:focus-visible { outline: 2px solid #a3b496; outline-offset: 2px; }
input::placeholder, textarea::placeholder { color: #9aa58f; }
input:disabled, select:disabled, textarea:disabled { opacity: .7; background: #eef1e9; cursor: not-allowed; }
.moderation-add { display: flex; align-items: stretch; gap: 8px; min-width: 0; }
.moderation-add>input { flex: 1; width: 0; }
.moderation-button { min-height: 36px; padding: 8px 14px; background: #ffffff91; border: 1px solid #fff; border-radius: 11px; color: #637857; font-size: 11px; white-space: nowrap; cursor: pointer; }
.moderation-button.primary { background: #dfe9d4; }
button:disabled { cursor: default; opacity: .5; }
.moderation-rules { list-style: none; margin: 12px 0 0; padding: 0 13px; border: 1px solid #ffffffb0; background: #ffffff50; border-radius: 12px; }
.moderation-rules li { display: flex; align-items: center; gap: 10px; padding: 11px 0; }
.moderation-rules li+li { border-top: 1px solid #b5c3a726; }
.moderation-rules li.moderation-keyword { flex-wrap: wrap; }
.moderation-readonly { width: 100%; box-sizing: border-box; margin-top: 12px; padding: 12px; border: 1px solid #ffffffb0; border-radius: 11px; background: #eff1eb70; }
.moderation-readonly-label { display: flex; align-items: center; gap: 5px; font-size: 10px; color: #8a947f; }
.moderation-readonly-label svg { width: 12px; height: 12px; }
.moderation-readonly dl { margin: 8px 0 0; }
.moderation-readonly dl>div { display: grid; grid-template-columns: minmax(65px, 90px) minmax(0, 1fr); gap: 10px; margin-top: 7px; font-size: 11px; line-height: 1.8; }
.moderation-readonly dt { color: #89947e; }
.moderation-readonly dd { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.moderation-secret { flex: 1; min-width: 0; font-size: 12px; overflow-wrap: anywhere; white-space: pre-wrap; }
.moderation-secret small { display: block; color: #9a927e; font-size: 9px; margin-top: 3px; }
.moderation-rule-actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.moderation-rule-actions select { padding: 5px 7px; font-size: 11px; }
.moderation-subheading { margin: 20px 0 12px; }
.moderation-subheading>span { font-size: 10px; color: #8b9683; }
.moderation-section-title { margin-bottom: 12px; }
.moderation-mode { display: flex; flex-direction: column; align-items: stretch; gap: 8px; margin-bottom: 14px; }
.moderation-prompt { margin-top: 20px; padding: 16px; border: 1px solid #d4ddcc; border-radius: 12px; background: #f4f7ef80; }
.moderation-prompt .moderation-subheading { margin: 0; }
.moderation-prompt textarea { display: block; width: 100%; min-height: 150px; resize: vertical; margin-top: 12px; line-height: 1.8; }
.moderation-field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.moderation-field-grid label { min-width: 0; display: flex; flex-direction: column; align-items: stretch; gap: 7px; }
.moderation-field-grid input { width: 100%; }
.moderation-weekdays { display: flex; flex-wrap: wrap; gap: 6px; margin: 14px 0; }
.moderation-weekdays button { flex: 1 0 34px; min-height: 36px; padding: 6px; border: 1px solid #d2dbcb; border-radius: 9px; color: #6c7e60; background: #ffffff90; font-size: 11px; cursor: pointer; }
.moderation-weekdays button[aria-pressed=true] { border-color: #9db28c; background: #dfe9d4; color: #4e6441; }
.moderation-reminder { display: flex; gap: 8px; margin: 20px 0 0; padding: 13px; border-radius: 12px; background: #e9efdf60; font-size: 11px; line-height: 1.9; color: #7e8b74; }
.moderation-reminder svg { width: 14px; height: 14px; flex-shrink: 0; margin-top: 3px; }
.moderation-footer { margin-top: 18px; font-size: 11px; color: #839675; }
.danger { color: #ac7f70; }
@media (max-width: 760px) { .group-moderation { padding: 18px; border-radius: 21px; } .moderation-prompt { padding: 12px; } }
@media (max-width: 360px) { .moderation-field-grid { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 420px) { .moderation-rules li { flex-wrap: wrap; } .moderation-rule-actions { width: 100%; justify-content: flex-end; } .keyword-add { flex-wrap: wrap; } .keyword-add>input { flex-basis: 100%; } }
</style>
