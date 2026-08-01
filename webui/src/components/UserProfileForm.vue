<template>
  <!-- 用户档案编辑：私聊对端和群成员共用同一套 /c2c/{id}/profile 接口 -->
  <div class="chatnt-info-section">
    <div class="chatnt-info-label">权限组</div>
    <div class="nt-seg">
      <button v-for="opt in roleOptions" :key="opt.key" type="button"
              :class="{ active: profile.role === opt.key }"
              @click="profile.role = opt.key">{{ opt.label }}</button>
    </div>
  </div>

  <div class="chatnt-info-section">
    <div class="chatnt-info-label">权限节点</div>
    <div v-if="profile.permissions.length === 0" class="nt-card"><div class="nt-empty">暂无权限节点</div></div>
    <div v-else class="nt-chips">
      <button v-for="perm in profile.permissions" :key="perm" type="button" class="nt-chip"
              title="点击移除" @click="emit('remove-perm', perm)">
        {{ perm }}<span aria-hidden="true">×</span>
      </button>
    </div>
    <form class="nt-add" @submit.prevent="submitPerm">
      <input class="nt-input" v-model="draft" placeholder="新增权限节点" />
      <button class="nt-btn" :disabled="!draft.trim()">添加</button>
    </form>
  </div>

  <div class="chatnt-info-section">
    <div class="chatnt-info-label">状态</div>
    <div class="nt-card">
      <div class="nt-row">
        <span class="nt-row-label">拉黑<span class="nt-row-note">禁止使用指令</span></span>
        <button type="button" class="nt-switch" :class="{ on: profile.blocked }" role="switch"
                :aria-checked="profile.blocked" @click="profile.blocked = !profile.blocked">
          <span class="nt-switch-knob" />
        </button>
      </div>
      <div class="nt-row">
        <span class="nt-row-label">屏蔽<span class="nt-row-note">静默忽略所有交互</span></span>
        <button type="button" class="nt-switch" :class="{ on: profile.ignored }" role="switch"
                :aria-checked="profile.ignored" @click="profile.ignored = !profile.ignored">
          <span class="nt-switch-knob" />
        </button>
      </div>
      <div class="nt-row">
        <span class="nt-row-label">主动消息</span>
        <button type="button" class="nt-switch" :class="{ on: profile.c2cPush }" role="switch"
                :aria-checked="profile.c2cPush" @click="profile.c2cPush = !profile.c2cPush">
          <span class="nt-switch-knob" />
        </button>
      </div>
    </div>
    <div v-if="error" class="nt-error">{{ error }}</div>
  </div>

  <div class="chatnt-info-section">
    <button class="nt-btn-primary" :disabled="saving" @click="emit('save')">
      {{ saving ? '保存中…' : '保存' }}
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  // 直接收下父组件的 reactive 对象，权限组和开关就地改，保存时统一提交
  profile: { type: Object, required: true },
  roleOptions: { type: Array, default: () => [] },
  saving: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const emit = defineEmits(['save', 'add-perm', 'remove-perm'])

const draft = ref('')

function submitPerm() {
  const value = draft.value.trim()
  if (!value) return
  emit('add-perm', value)
  draft.value = ''
}
</script>
