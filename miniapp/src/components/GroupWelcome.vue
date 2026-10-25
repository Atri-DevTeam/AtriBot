<script setup lang="ts">
import {computed, onBeforeUnmount, ref, shallowRef, watch} from 'vue'
import Icon from './Icon.vue'
import ToggleSwitch from './ToggleSwitch.vue'
import {ApiError} from '../session'
import botBlue from '../assets/bot-blue.svg'
import {escapeHtml, renderMarkdown} from '../../../webui/src/shared/lib/markdown.js'
import {renderCommandInputTags} from '../../../webui/src/shared/lib/messageRender.js'
import 'katex/dist/katex.min.css'
import type {PrivateRequest} from '../activity'
import type {GroupWelcome, PrivatePost} from '../groups'
import type {Profile} from '../profile'

const props = defineProps<{ bot: Profile['bot']; groupId: string; request: PrivateRequest; post: PrivatePost; revision: number }>()
const avatarFailed = ref(false)
watch(() => props.bot.avatarUrl, () => {
  avatarFailed.value = false
})
const welcome = shallowRef<GroupWelcome | null>(null)
const loading = ref(false), error = ref(false)
const saving = ref(false), saveError = ref('')
const expanded = ref(false)
let version = 0, disposed = false
const preview = computed(() => {
  const labels: string[] = []
  const text = (welcome.value?.text || '').replace(/<qqbot-cmd-input\b[^>]*\/>/g, tag => {
    const label = renderCommandInputTags(tag)
    if (label === tag) return ''
    return `\uE002${labels.push(label) - 1}\uE003`
  })
  return renderMarkdown(`@新成员${text.trim() ? ` ${text}` : ''}`)
      .replace('@新成员', '<span class="welcome-mention">@新成员</span>')
      .replace(/<a\b[^>]*>/g, '<span class="welcome-link">').replace(/<\/a>/g, '</span>')
      .replace(/\uE002(\d+)\uE003/g, (_, index) => `<span class="welcome-command">${escapeHtml(labels[Number(index)] || '')}</span>`)
})
const style = (value: string) => ['GRAY', 'BLUE', 'RED', 'BLUE_WITH_BACKGROUND', 'ICON_BUTTON'].includes(value) ? value : 'BLUE'

async function load() {
  if (saving.value) return
  const current = ++version
  loading.value = true
  try {
    const result = await props.request<GroupWelcome>(`groups/${encodeURIComponent(props.groupId)}/join-welcome`)
    if (!disposed && current === version && result) {
      welcome.value = result;
      error.value = false
    }
  } catch {
    if (!disposed && current === version) error.value = true
  } finally {
    if (current === version) loading.value = false
  }
}

async function setEnabled(enabled: boolean) {
  if (!welcome.value?.canEdit || loading.value || saving.value) return
  const current = version
  saving.value = true
  saveError.value = ''
  try {
    const result = await props.post<{enabled: boolean}>(`groups/${encodeURIComponent(props.groupId)}/join-welcome`, {enabled})
    if (!disposed && current === version && result && welcome.value) welcome.value = {...welcome.value, enabled: result.enabled}
  } catch (reason) {
    if (!disposed && current === version) {
      saveError.value = reason instanceof ApiError && ['GROUP_NOT_BOUND', 'GROUP_SETTINGS_FORBIDDEN', 'GROUP_UNAVAILABLE'].includes(reason.code)
        ? '当前群无法修改欢迎设置，请刷新群资料' : '保存失败，请稍后重试'
    }
  } finally { if (current === version) saving.value = false }
}

watch(() => [props.groupId, props.revision], () => {
  version++;
  welcome.value = null;
  error.value = false;
  loading.value = false
  saving.value = false;
  saveError.value = ''
  if (expanded.value) void load()
})

function toggle() {
  expanded.value = !expanded.value
  if (expanded.value && !welcome.value && !loading.value) void load()
}

onBeforeUnmount(() => {
  disposed = true;
  version++
})
</script>

<template>
  <section class="group-welcome glass" aria-label="加群欢迎" :aria-busy="loading || saving">
    <header class="welcome-heading">
      <button class="welcome-toggle" :aria-expanded="expanded" @click="toggle">
        <Icon name="message"/>
        <h3>加群欢迎</h3>
        <Icon name="chevron" :class="{ expanded }"/>
      </button>
      <button v-if="expanded" class="text-button" :disabled="loading || saving" aria-label="刷新加群欢迎" @click="load">
        <Icon name="refresh" :class="{ 'refresh-spinning': loading }"/>
      </button>
    </header>
    <div v-if="expanded" class="welcome-expanded">
      <div v-if="error" class="welcome-empty" role="alert">欢迎内容暂时无法读取
        <button class="text-button" :disabled="loading" @click="load">重试
          <Icon name="refresh" :class="{ 'refresh-spinning': loading }"/>
        </button>
      </div>
      <p v-else-if="!welcome" class="welcome-empty" role="status">正在读取欢迎内容…</p>
      <template v-if="welcome">
        <div class="welcome-setting"><span>欢迎新成员</span><ToggleSwitch :model-value="welcome.enabled" label="加群欢迎开关"
             :disabled="!welcome.canEdit || loading || saving" @update:model-value="setEnabled"/></div>
        <p v-if="saveError" class="welcome-save-error" role="alert">{{ saveError }}</p>
        <div class="welcome-badges"><span :class="{ enabled: welcome.enabled }">{{
            welcome.enabled ? '已开启' : '已关闭'
          }}</span><span>{{ welcome.custom ? '自定义欢迎' : '系统默认 · 普通新成员' }}</span><span
            class="welcome-readonly"><Icon name="lock"/>内容只读</span></div>
        <div class="welcome-message">
        <span class="welcome-bot">
          <img v-if="bot.avatarUrl && !avatarFailed" :src="bot.avatarUrl" alt="发送者头像" referrerpolicy="no-referrer"
               @error="avatarFailed = true"/>
          <Icon v-else name="bot"/>
        </span>
          <div class="welcome-message-content">
            <div class="welcome-sender"><span>{{ bot.name || '机器人' }}</span><img :src="botBlue" alt="机器人"/></div>
            <div class="welcome-bubble">
              <div class="welcome-markdown" v-html="preview"/>
              <div v-if="welcome.keyboard.length" class="welcome-keyboard"
                   :class="{ small: welcome.buttonSize === 'SMALL' }" aria-label="欢迎按钮预览">
                <div v-for="(row, index) in welcome.keyboard" :key="index" class="welcome-button-row"><span
                    v-for="(button, column) in row" :key="column" class="welcome-qq-button"
                    :class="style(button.style)">{{ button.label }}</span></div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.group-welcome {
  min-width: 0;
  background: transparent;
  border: 0;
  border-top: 1px solid var(--ui-border);
  border-radius: 0;
  padding: 24px 0;
  margin-top: 0;
}

.welcome-setting { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 12px; font-size: 12px; color: var(--ui-secondary); }
.welcome-save-error { font-size: 11px; color: var(--ui-danger); margin: 12px 0 0; }

.welcome-heading, .welcome-heading > div {
  display: flex;
  align-items: center;
  gap: 9px;
}

.welcome-heading {
  justify-content: space-between;
}

.welcome-toggle {
  display: flex;
  align-items: center;
  gap: 9px;
  flex: 1;
  min-height: 32px;
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.welcome-toggle svg {
  width: 16px;
  height: 16px;
  color: var(--ui-muted);
}

.welcome-toggle svg:last-child {
  margin-left: auto;
  transition: transform .2s ease;
}

.welcome-toggle svg.expanded {
  transform: rotate(90deg);
}

.welcome-expanded {
  animation: arrive .2s ease both;
}

@media (prefers-reduced-motion: reduce) {
  .welcome-expanded {
    animation: none;
  }

  .welcome-toggle svg {
    transition: none;
  }
}

.welcome-heading h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 550;
}

.welcome-heading > div > svg {
  width: 16px;
  height: 16px;
  color: var(--ui-muted);
}

.welcome-badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 10px 0 18px;
}

.welcome-badges > span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 7px;
  background: var(--ui-raised);
  border: 1px solid var(--ui-border);
  border-radius: 7px;
  color: var(--ui-muted);
  font-size: 9px;
}

.welcome-badges > span.enabled {
  background: var(--ui-raised);
  color: var(--ui-secondary);
}

.welcome-badges > span.welcome-readonly {
  margin-left: auto;
  background: transparent;
  border-color: transparent;
}

.welcome-readonly svg {
  width: 10px;
  height: 10px;
}

.welcome-message {
  display: flex;
  align-items: flex-start;
  gap: 11px;
}

.welcome-bot {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--ui-border);
  border-radius: 50%;
  background: var(--ui-raised);
  flex-shrink: 0;
  overflow: hidden;
}

.welcome-bot img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.welcome-bot svg {
  width: 20px;
  height: 20px;
}

.welcome-message-content {
  flex: 1;
  min-width: 0;
}

.welcome-sender {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 0 0 6px 2px;
  min-height: 16px;
  font-size: 11px;
  line-height: 16px;
  color: var(--ui-muted);
}

.welcome-sender span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.welcome-sender img {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.welcome-bubble {
  min-width: 0;
  background: var(--ui-raised);
  border: 1px solid var(--ui-border);
  border-radius: 4px 16px 16px;
  padding: 16px;
}

.welcome-markdown {
  font-size: 12px;
  line-height: 1.85;
  overflow-wrap: anywhere;
  color: var(--ui-secondary);
}

.welcome-markdown :deep(> :first-child) {
  margin-top: 0;
}

.welcome-markdown :deep(> :last-child) {
  margin-bottom: 0;
}

.welcome-markdown :deep(p) {
  margin: 10px 0;
}

.welcome-markdown :deep(h1), .welcome-markdown :deep(h2), .welcome-markdown :deep(h3), .welcome-markdown :deep(h4), .welcome-markdown :deep(h5), .welcome-markdown :deep(h6) {
  font-size: 14px;
  font-weight: 600;
  margin: 14px 0 8px;
}

.welcome-markdown :deep(img) {
  display: block;
  max-width: min(100%, 480px);
  max-height: 300px;
  height: auto;
  object-fit: contain;
  border-radius: 9px;
}

.welcome-markdown :deep(img.md-inline-icon) {
  display: inline-block;
  vertical-align: middle;
  border-radius: 0;
}

.welcome-markdown :deep(ul), .welcome-markdown :deep(ol) {
  padding-left: 20px;
}

.welcome-markdown :deep(pre) {
  white-space: pre-wrap;
  background: var(--ui-raised);
  padding: 10px;
  border-radius: 8px;
}

.welcome-markdown :deep(blockquote) {
  border-left: 2px solid var(--ui-border);
  margin: 10px 0;
  padding-left: 10px;
  color: var(--ui-muted);
}

.welcome-markdown :deep(hr) {
  border: 0;
  border-top: 1px solid var(--ui-border);
}

.welcome-markdown :deep(.welcome-mention), .welcome-markdown :deep(.welcome-link), .welcome-markdown :deep(.welcome-command) {
  color: var(--ui-accent);
}

.welcome-markdown :deep(.katex) {
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
}

.welcome-keyboard {
  margin-top: 12px;
}

.welcome-button-row {
  display: flex;
  gap: 6px;
  margin-top: 7px;
}

.welcome-qq-button {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
  text-align: center;
  border: 1px solid #b5cce9;
  color: #2473cd;
  background: #ffffffb0;
  border-radius: 6px;
  padding: 7px 5px;
  font-size: 12px;
  line-height: 1.5;
}

.welcome-qq-button.GRAY {
  border-color: #d7dce3;
  color: #596273;
}

.welcome-qq-button.RED {
  color: #c1362f;
  border-color: #e7b5b2;
}

.welcome-qq-button.BLUE_WITH_BACKGROUND {
  color: #fff;
  background: #337cd9;
  border-color: #337cd9;
}

.welcome-qq-button.ICON_BUTTON {
  background: #edf4fe;
}

.welcome-keyboard.small .welcome-qq-button {
  font-size: 11px;
}

.welcome-empty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 15px 0 0;
  font-size: 11px;
  color: var(--ui-muted);
}

@media (max-width: 760px) {
  .group-welcome {
    padding: 24px 0;
    border-radius: 0;
  }

  .welcome-message {
    gap: 8px;
  }

  .welcome-bubble {
    padding: 12px;
  }
}
</style>
