<script setup>
import { ref } from 'vue'
import JoinWelcomePanel from '../../groups/components/JoinWelcomePanel.vue'

defineProps({
  api: { type: Function, required: true },
  botName: { type: String, default: 'AtriBot' },
  botAvatarUrl: { type: String, default: '' },
  debugGroupId: { type: String, default: '' }
})

const roles = [
  { value: 'USER', label: '普通用户' },
  { value: 'ADMIN', label: '机器人管理员' },
  { value: 'OWNER', label: '机器人开发者' }
]
const role = ref('USER')
const editor = ref(null)

function mayLeave() { return editor.value?.mayLeave() ?? true }
function releaseLeaveGuard() { editor.value?.releaseLeaveGuard() }
function load() { return editor.value?.load() }
function selectRole(value) {
  if (value === role.value || !mayLeave()) return
  role.value = value
}

defineExpose({ mayLeave, releaseLeaveGuard, load })
</script>

<template>
  <div class="default-welcome-settings">
    <div class="default-welcome-roles" role="group" aria-label="新成员的机器人权限身份">
      <button v-for="item in roles" :key="item.value" type="button" class="ghost-button"
              :class="{ active: role === item.value }" :aria-pressed="role === item.value"
              @click="selectRole(item.value)">{{ item.label }} <span>{{ item.value }}</span></button>
    </div>
    <p class="bs-card-desc default-welcome-description">按新成员的机器人权限身份选择欢迎内容。已开启入群欢迎且未设置群自定义内容的群使用此配置，发送时自动 @ 新成员。</p>
    <JoinWelcomePanel :key="role" ref="editor" :default-role="role" :request="api"
                      :bot-name="botName" :bot-avatar-url="botAvatarUrl" :debug-group-id="debugGroupId" />
  </div>
</template>

<style scoped>
.default-welcome-roles { display: flex; flex-wrap: wrap; gap: 8px; }
.default-welcome-roles button { display: inline-flex; align-items: center; gap: 6px; }
.default-welcome-roles button span { font-size: 10px; opacity: .65; }
.default-welcome-roles button.active { background: var(--color-surface-alt); border-color: var(--color-text-subtle); color: var(--color-text-strong); }
.default-welcome-description { margin: 12px 0; line-height: 1.7; }
.default-welcome-settings :deep(.welcome-scroll) { flex: none; overflow: visible; padding: 0; }
.default-welcome-settings :deep(.welcome-save-bar) { padding: 14px 0 0; background: transparent; }
</style>
