<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import Icon from './Icon.vue'
import botBlue from '../assets/bot-blue.svg'
import { renderMarkdown } from '../../../webui/src/lib/markdown.js'
import 'katex/dist/katex.min.css'
import type { PrivateRequest } from '../activity'
import type { GroupWelcome } from '../groups'
import type { Profile } from '../profile'

const props = defineProps<{ bot: Profile['bot']; groupId: string; request: PrivateRequest; revision: number }>()
const avatarFailed = ref(false)
watch(() => props.bot.avatarUrl, () => { avatarFailed.value = false })
const welcome = shallowRef<GroupWelcome | null>(null)
const loading = ref(false), error = ref(false)
let version = 0, disposed = false
const preview = computed(() => {
  const text = welcome.value?.text || ''
  return renderMarkdown(`@新成员${text.trim() ? ` ${text}` : ''}`)
    .replace('@新成员', '<span class="welcome-mention">@新成员</span>')
    .replace(/<a\b[^>]*>/g, '<span class="welcome-link">').replace(/<\/a>/g, '</span>')
})
const style = (value: string) => ['GRAY', 'BLUE', 'RED', 'BLUE_WITH_BACKGROUND', 'ICON_BUTTON'].includes(value) ? value : 'BLUE'
async function load() {
  const current = ++version
  loading.value = true
  try {
    const result = await props.request<GroupWelcome>(`groups/${encodeURIComponent(props.groupId)}/join-welcome`)
    if (!disposed && current === version && result) { welcome.value = result; error.value = false }
  } catch { if (!disposed && current === version) error.value = true }
  finally { if (current === version) loading.value = false }
}
watch(() => [props.groupId, props.revision], () => { welcome.value = null; error.value = false; void load() }, { immediate: true })
onBeforeUnmount(() => { disposed = true; version++ })
</script>

<template>
  <section class="group-welcome glass" aria-label="加群欢迎" :aria-busy="loading">
    <header class="welcome-heading"><div><Icon name="message" /><h3>加群欢迎</h3></div><button class="text-button" :disabled="loading" aria-label="刷新加群欢迎" @click="load"><Icon name="refresh" :class="{ 'refresh-spinning': loading }" /></button></header>
    <div v-if="error" class="welcome-empty" role="alert">欢迎内容暂时无法读取<button class="text-button" :disabled="loading" @click="load">重试 <Icon name="refresh" :class="{ 'refresh-spinning': loading }" /></button></div>
    <p v-else-if="!welcome" class="welcome-empty" role="status">正在读取欢迎内容…</p>
    <template v-if="welcome">
      <div class="welcome-badges"><span :class="{ enabled: welcome.enabled }">{{ welcome.enabled ? '已开启' : '已关闭' }}</span><span>{{ welcome.custom ? '自定义欢迎' : '系统默认 · 普通新成员' }}</span><span class="welcome-readonly"><Icon name="lock" />只读</span></div>
      <div class="welcome-message">
        <span class="welcome-bot">
          <img v-if="bot.avatarUrl && !avatarFailed" :src="bot.avatarUrl" alt="发送者头像" referrerpolicy="no-referrer" @error="avatarFailed = true" />
          <Icon v-else name="bot" />
        </span>
        <div class="welcome-message-content">
          <div class="welcome-sender"><span>{{ bot.name || '机器人' }}</span><img :src="botBlue" alt="机器人" /></div>
          <div class="welcome-bubble">
            <div class="welcome-markdown" v-html="preview" />
            <div v-if="welcome.keyboard.length" class="welcome-keyboard" :class="{ small: welcome.buttonSize === 'SMALL' }" aria-label="欢迎按钮预览">
              <div v-for="(row, index) in welcome.keyboard" :key="index" class="welcome-button-row"><span v-for="(button, column) in row" :key="column" class="welcome-qq-button" :class="style(button.style)">{{ button.label }}</span></div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.group-welcome { margin-top: 16px; padding: 22px 25px; border-radius: 23px; min-width: 0; }
.welcome-heading, .welcome-heading>div { display: flex; align-items: center; gap: 9px; }
.welcome-heading { justify-content: space-between; }
.welcome-heading h3 { margin: 0; font-size: 14px; font-weight: 550; }
.welcome-heading>div>svg { width: 16px; height: 16px; color: #8a9a80; }
.welcome-badges { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin: 10px 0 18px; }
.welcome-badges>span { display: inline-flex; align-items: center; gap: 4px; padding: 4px 7px; background: #ffffff70; border: 1px solid #fff; border-radius: 7px; color: #91988a; font-size: 9px; }
.welcome-badges>span.enabled { background: #e9efdf88; color: #7c906c; }
.welcome-badges>span.welcome-readonly { margin-left: auto; background: transparent; border-color: transparent; }
.welcome-readonly svg { width: 10px; height: 10px; }
.welcome-message { display: flex; align-items: flex-start; gap: 11px; }
.welcome-bot { display: grid; place-items: center; width: 36px; height: 36px; border: 1px solid #fff; border-radius: 50%; background: #ffffff80; flex-shrink: 0; overflow: hidden; }
.welcome-bot img { width: 100%; height: 100%; object-fit: cover; }
.welcome-bot svg { width: 20px; height: 20px; }
.welcome-message-content { flex: 1; min-width: 0; }
.welcome-sender { display: flex; align-items: center; gap: 5px; margin: 0 0 6px 2px; min-height: 16px; font-size: 11px; line-height: 16px; color: #8a9288; }
.welcome-sender span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.welcome-sender img { width: 14px; height: 14px; flex-shrink: 0; }
.welcome-bubble { min-width: 0; background: #ffffff91; border: 1px solid #fff; border-radius: 4px 16px 16px; padding: 16px; }
.welcome-markdown { font-size: 12px; line-height: 1.85; overflow-wrap: anywhere; color: #64735e; }
.welcome-markdown :deep(> :first-child) { margin-top: 0; }
.welcome-markdown :deep(> :last-child) { margin-bottom: 0; }
.welcome-markdown :deep(p) { margin: 10px 0; }
.welcome-markdown :deep(h1), .welcome-markdown :deep(h2), .welcome-markdown :deep(h3), .welcome-markdown :deep(h4), .welcome-markdown :deep(h5), .welcome-markdown :deep(h6) { font-size: 14px; font-weight: 600; margin: 14px 0 8px; }
.welcome-markdown :deep(img) { display: block; max-width: 100%; height: auto; border-radius: 9px; }
.welcome-markdown :deep(img.md-inline-icon) { display: inline-block; vertical-align: middle; border-radius: 0; }
.welcome-markdown :deep(ul), .welcome-markdown :deep(ol) { padding-left: 20px; }
.welcome-markdown :deep(pre) { white-space: pre-wrap; background: #eef1e880; padding: 10px; border-radius: 8px; }
.welcome-markdown :deep(blockquote) { border-left: 2px solid #c9d5bf; margin: 10px 0; padding-left: 10px; color: #89917f; }
.welcome-markdown :deep(hr) { border: 0; border-top: 1px solid #b5c3a740; }
.welcome-markdown :deep(.welcome-mention), .welcome-markdown :deep(.welcome-link) { color: #6e93b4; }
.welcome-markdown :deep(.katex) { max-width: 100%; overflow-x: auto; overflow-y: hidden; }
.welcome-keyboard { margin-top: 12px; }
.welcome-button-row { display: flex; gap: 6px; margin-top: 7px; }
.welcome-qq-button { flex: 1; min-width: 0; overflow-wrap: anywhere; text-align: center; border: 1px solid #b5cce9; color: #2473cd; background: #ffffffb0; border-radius: 6px; padding: 7px 5px; font-size: 12px; line-height: 1.5; }
.welcome-qq-button.GRAY { border-color: #d7dce3; color: #596273; }
.welcome-qq-button.RED { color: #c1362f; border-color: #e7b5b2; }
.welcome-qq-button.BLUE_WITH_BACKGROUND { color: #fff; background: #337cd9; border-color: #337cd9; }
.welcome-qq-button.ICON_BUTTON { background: #edf4fe; }
.welcome-keyboard.small .welcome-qq-button { font-size: 11px; }
.welcome-empty { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin: 15px 0 0; font-size: 11px; color: #929b88; }
@media (max-width: 760px) {
  .group-welcome { padding: 18px; border-radius: 21px; }
  .welcome-message { gap: 8px; }
  .welcome-bubble { padding: 12px; }
}
</style>
