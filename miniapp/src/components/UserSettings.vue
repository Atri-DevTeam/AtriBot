<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import Icon from './Icon.vue'
import DropdownSelect from './DropdownSelect.vue'
import {ApiError} from '../session'
import {number, recordDate, rewardName, type Activity} from '../activity'

type Settings = NonNullable<Activity['settings']>
const props = defineProps<{
  settings: Activity['settings']; busy: boolean; failed: boolean
  post: <T>(path: string, body: unknown) => Promise<T | null>
}>()
const emit = defineEmits<{retry: []; copy: [value: string]; update: [settings: Settings]}>()
const saving = ref(false)
const notice = ref('')
const error = ref('')
const helpOpen = ref(false)
const helpRoot = ref<HTMLElement>()
function closeHelpOutside(event: PointerEvent) {
  if (!helpRoot.value?.contains(event.target as Node)) helpOpen.value = false
}
onMounted(() => document.addEventListener('pointerdown', closeHelpOutside, true))
onBeforeUnmount(() => document.removeEventListener('pointerdown', closeHelpOutside, true))
const drafts = ref<Record<string, string>>({})
const disabled = computed(() => props.busy || props.failed || saving.value)
const items = computed(() => (props.settings?.reward.items || []).map(item => ({
  item, name: rewardName(item), value: props.settings?.reward.priorities.find(priority => priority.item === item)?.value || 0
})).sort((left, right) => left.name.localeCompare(right.name, 'zh-CN')))
const modes = [{value: 'rarity', label: '稀有度优先'}, {value: 'priority', label: '奖励内容优先'}]
watch(() => props.settings, settings => {
  drafts.value = Object.fromEntries((settings?.reward.priorities || []).map(priority => [priority.item, String(priority.value)]))
}, {immediate: true})

async function save(body: Record<string, unknown>, update: (settings: Settings) => Settings) {
  if (disabled.value || !props.settings) return
  const current = props.settings
  saving.value = true
  notice.value = ''
  error.value = ''
  try {
    const result = await props.post<{saved: boolean}>('settings', body)
    if (!result?.saved) return
    emit('update', update(current))
    notice.value = '设置已保存'
  } catch (reason) {
    error.value = reason instanceof ApiError && reason.code === 'INVALID_USER_SETTINGS'
      ? '请检查设置内容，奖励优先级应为 1–30' : '保存失败，请稍后重试'
  } finally { saving.value = false }
}
function priorityValid(item: string) {
  return /^([1-9]|[12]\d|30)$/.test(drafts.value[item] || '')
}
function savePriority(item: string, clear = false) {
  if (!clear && !priorityValid(item)) return
  const value = clear ? null : Number(drafts.value[item])
  void save({action: 'reward_priority', item, value}, settings => ({...settings,
    reward: {...settings.reward, priorities: [
      ...settings.reward.priorities.filter(priority => priority.item !== item),
      ...(value == null ? [] : [{item, value}])
    ]}
  }))
}
</script>

<template>
  <section class="settings-card glass" aria-label="用户设置" :aria-busy="busy || saving">
    <header class="module-heading">
      <div>
        <Icon name="settings"/>
        <h2>用户设置</h2></div>
    </header>
    <div v-if="failed" class="section-empty">设置暂时无法读取
      <button class="text-button" :disabled="busy" @click="emit('retry')">重试
        <Icon name="refresh" :class="{ 'refresh-spinning': busy }"/>
      </button>
    </div>
    <div v-else-if="!settings" class="section-empty">读取中…</div>
    <template v-else>
      <dl class="settings-fields">
        <div>
          <dt>B站 UID</dt>
          <dd>{{ settings.bilibiliUid || '未绑定' }}</dd>
        </div>
      </dl>
      <div class="settings-groups">
        <div class="settings-group">
          <h3 class="settings-subtitle">Hypixel 自动领奖</h3>
          <dl class="settings-fields">
            <div>
              <dt>自动领取</dt>
              <dd><button type="button" class="settings-switch" role="switch" aria-label="Hypixel 自动领取"
                          :aria-checked="settings.reward.enabled" :disabled="disabled"
                          @click="save({action: 'reward_enabled', enabled: !settings.reward.enabled}, current => ({...current, reward: {...current.reward, enabled: !current.reward.enabled}}))"><span/></button></dd>
            </div>
          </dl>
          <div class="reward-mode">
            <span>选择方式</span>
            <DropdownSelect :model-value="settings.reward.mode" label="奖励选择方式" :options="modes" :disabled="disabled"
                            @update:model-value="mode => save({action: 'reward_mode', mode}, current => ({...current, reward: {...current.reward, mode}}))"/>
          </div>
          <details class="reward-priorities">
            <summary><span>奖励优先级设置</span><Icon name="chevron"/></summary>
            <p class="settings-hint">优先级为 1–30，数值越大越优先；未设置时为 0</p>
            <div class="reward-options" aria-label="奖励优先级">
              <div v-for="item in items" :key="item.item" class="reward-option">
                <div class="reward-option-heading"><span>{{ item.name }}</span><small>{{ item.value || '未设置' }}</small></div>
                <form class="reward-editor" @submit.prevent="savePriority(item.item)">
                  <label><span>优先级</span><input v-model="drafts[item.item]" type="text" inputmode="numeric" maxlength="2"
                         :aria-label="`${item.name}优先级`" placeholder="1–30" :disabled="disabled"/></label>
                  <button type="submit" class="settings-action" :disabled="disabled || !priorityValid(item.item)">保存</button>
                  <button type="button" class="text-button" :disabled="disabled || !item.value" @click="savePriority(item.item, true)">清除</button>
                </form>
              </div>
            </div>
          </details>
        </div>
        <div class="settings-group">
          <dl class="settings-fields">
            <div>
              <dt ref="helpRoot" class="settings-help-label"
                  @pointerenter="event => { if (event.pointerType === 'mouse') helpOpen = true }"
                  @pointerleave="event => { if (event.pointerType === 'mouse') helpOpen = false }"
                  @keydown.esc.stop="helpOpen = false">按钮兼容模式<button type="button" class="settings-help-button" aria-label="按钮兼容模式说明"
                  :aria-describedby="helpOpen ? 'keyboard-help' : undefined" @click="helpOpen = true"
                  @focus="event => { if ((event.target as HTMLElement).matches(':focus-visible')) helpOpen = true }"
                  @blur="helpOpen = false">?</button>
                <span v-if="helpOpen" id="keyboard-help" role="tooltip" class="settings-tooltip">部分设备可能不支持内联键盘按钮，可以启用此功能以适配一些操作</span>
              </dt>
              <dd><button type="button" class="settings-switch" role="switch" aria-label="按钮兼容模式"
                          :aria-checked="settings.unsupportedKeyboard" :disabled="disabled"
                          @click="save({action: 'keyboard', enabled: !settings.unsupportedKeyboard}, current => ({...current, unsupportedKeyboard: !current.unsupportedKeyboard}))"><span/></button></dd>
            </div>
          </dl>
        </div>
        <div class="settings-group">
          <h3 class="settings-subtitle">分享与邀请</h3>
          <dl class="settings-fields">
            <div>
              <dt>已邀请用户</dt>
              <dd>{{ number(settings.share.invitedCount) }} 人</dd>
            </div>
            <div>
              <dt>通过邀请添加</dt>
              <dd>{{ settings.share.invitedBySomeone ? '是' : '否' }}</dd>
            </div>
            <div v-if="settings.share.createdAt">
              <dt>链接创建日期</dt>
              <dd>{{ recordDate(settings.share.createdAt) }}</dd>
            </div>
            <div class="share-url">
              <dt>分享链接</dt>
              <dd>
                <template v-if="settings.share.url"><span
                    class="share-url-text">{{ settings.share.url }}</span>
                  <button class="copy-button" aria-label="复制分享链接"
                          @click="emit('copy', settings.share.url)">
                    <Icon name="copy"/>
                  </button>
                </template>
                <span v-else>未生成</span></dd>
            </div>
          </dl>
        </div>
      </div>
      <p v-if="notice || error" class="settings-feedback" :class="{error}" :role="error ? 'alert' : 'status'">{{ error || notice }}</p>
    </template>
  </section>
</template>

<style scoped>
.settings-switch { display: inline-flex; align-items: center; width: 34px; height: 20px; padding: 3px; border: 0; border-radius: 12px; background: #bdc5b9; cursor: pointer; transition: background .18s; }
.settings-switch > span { width: 14px; height: 14px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px #35433920; transition: transform .18s; }
.settings-switch[aria-checked=true] { background: #849978; }
.settings-switch[aria-checked=true] > span { transform: translateX(14px); }
.settings-switch:disabled { cursor: default; opacity: .5; }
.reward-mode { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 14px; margin: 10px 0; font-size: 11px; color: #86917d; }
.settings-hint { margin: 10px 0; font-size: 10px; line-height: 1.8; color: #86917d; }
.reward-priorities > summary { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 11px 0; font-size: 11px; color: #53634d; list-style: none; cursor: pointer; }
.reward-priorities > summary::-webkit-details-marker { display: none; }
.reward-priorities > summary > svg { width: 12px; height: 12px; color: #86917d; transition: transform .18s; }
.reward-priorities[open] > summary > svg { transform: rotate(90deg); }
.reward-options { display: flex; flex-direction: column; }
.reward-option { border-bottom: 1px solid #b3c1a726; }
.reward-option:last-child { border-bottom: 0; }
.reward-option-heading { display: flex; align-items: center; gap: 8px; padding: 11px 0; font-size: 11px; color: #53634d; }
.reward-option-heading > span { min-width: 0; overflow-wrap: anywhere; flex: 1; }
.reward-option-heading > small { color: #86917d; font-size: 10px; }
.reward-editor { display: flex; align-items: center; gap: 10px; padding: 0 0 12px; }
.reward-editor label { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; color: #86917d; font-size: 10px; }
.reward-editor input { width: 64px; min-width: 0; padding: 7px 8px; border: 1px solid #b5c3a740; border-radius: 8px; background: #ffffff70; color: #53634d; font: inherit; font-size: 12px; }
.settings-action { border: 1px solid #b5c3a740; padding: 7px 12px; border-radius: 8px; background: #e4eadc; color: #53634d; font: inherit; font-size: 11px; cursor: pointer; }
.settings-action:disabled, .reward-editor .text-button:disabled { opacity: .45; cursor: default; }
.settings-help-label { position: relative; display: inline-flex; align-items: center; gap: 6px; }
.settings-tooltip { position: absolute; z-index: 20; bottom: calc(100% + 10px); left: 0; width: 240px; max-width: calc(100vw - 80px); box-sizing: border-box; padding: 10px 12px; border: 1px solid #ffffff18; border-radius: 10px; background: #354339; color: #f5f7f2; box-shadow: 0 6px 20px #25302626; font-size: 11px; font-weight: 400; line-height: 1.7; white-space: normal; text-align: left; }
.settings-help-label::after { content: ''; position: absolute; bottom: 100%; left: 0; width: 100%; height: 10px; }
.settings-help-button { display: inline-flex; align-items: center; justify-content: center; width: 15px; height: 15px; padding: 0; border: 1px solid #aebaa4; border-radius: 50%; background: transparent; color: #86917d; font: inherit; font-size: 10px; cursor: pointer; }
.settings-feedback { margin: 14px 0 0; color: #637857; font-size: 11px; }
.settings-feedback.error { color: #ad5656; }
@media (prefers-reduced-motion: reduce) { .settings-switch, .settings-switch > span, .reward-priorities > summary > svg { transition: none; } }
</style>
