<template>
  <section class="chatnt-info-section" aria-label="统一账号信息">
    <div class="chatnt-info-label account-profile-title">统一账号信息</div>
    <UnifiedAccountDetails :account="profile.account" :loading="loading"/>
    <div class="account-profile-field">
      <div class="account-profile-label">权限组</div>
      <div class="nt-seg">
        <button v-for="opt in roleOptions" :key="opt.key" type="button"
                :class="{ active: profile.role === opt.key }"
                :disabled="locked"
                @click="profile.role = opt.key">{{ opt.label }}
        </button>
      </div>
    </div>

    <div class="account-profile-field">
      <div class="account-profile-label">权限节点</div>
      <div v-if="profile.permissions.length === 0" class="nt-card">
        <div class="nt-empty">暂无权限节点</div>
      </div>
      <div v-else class="nt-chips">
        <button v-for="perm in profile.permissions" :key="perm" type="button" class="nt-chip"
                :disabled="locked"
                title="点击移除" @click="emit('remove-perm', perm)">
          {{ perm }}<span aria-hidden="true">×</span>
        </button>
      </div>
      <form class="nt-add" @submit.prevent="submitPerm">
        <input class="nt-input" v-model="draft" :disabled="locked" placeholder="新增权限节点"/>
        <button class="nt-btn" :disabled="locked || !draft.trim()">添加</button>
      </form>
    </div>

    <div class="nt-card account-profile-field">
      <div class="nt-row">
        <span class="nt-row-label">账号状态<span class="nt-row-note">拉黑限制指令，屏蔽静默忽略交互</span></span>
        <AccountStatusSelect v-model="profile.status" :disabled="locked"/>
      </div>
    </div>
  </section>

  <section v-if="profile.c2cRegistered" class="chatnt-info-section" aria-label="私聊设置">
    <div class="chatnt-info-label">私聊设置</div>
    <div class="nt-card">
      <div class="nt-row">
        <span class="nt-row-label">主动消息</span>
        <button type="button" class="nt-switch" :class="{ on: profile.c2cPush }" role="switch"
                :disabled="locked"
                :aria-checked="profile.c2cPush" @click="profile.c2cPush = !profile.c2cPush">
          <span class="nt-switch-knob"/>
        </button>
      </div>
    </div>
  </section>

  <div v-if="error" class="nt-error" role="alert">{{ error }}</div>

  <div class="chatnt-info-section">
    <button class="nt-btn-primary" :disabled="locked" @click="emit('save')">
      {{ loading ? '加载中…' : saving ? '保存中…' : '保存' }}
    </button>
  </div>
</template>

<script setup>
import {computed, ref} from 'vue'
import AccountStatusSelect from './AccountStatusSelect.vue'
import UnifiedAccountDetails from './UnifiedAccountDetails.vue'

const props = defineProps({
  profile: {type: Object, required: true},
  roleOptions: {type: Array, default: () => []},
  saving: {type: Boolean, default: false},
  loading: {type: Boolean, default: false},
  disabled: {type: Boolean, default: false},
  error: {type: String, default: ''}
})

const emit = defineEmits(['save', 'add-perm', 'remove-perm'])

const draft = ref('')
const locked = computed(() => props.loading || props.saving || props.disabled)

function submitPerm() {
  if (locked.value) return
  const value = draft.value.trim()
  if (!value) return
  emit('add-perm', value)
  draft.value = ''
}
</script>

<style scoped>
.account-profile-title { color: var(--color-text-secondary); font-size: 13px; font-weight: 600; }
.account-profile-field { margin-top: 16px; }
.account-profile-label { margin-bottom: 8px; color: var(--color-text-muted); font-size: 12px; }
</style>
