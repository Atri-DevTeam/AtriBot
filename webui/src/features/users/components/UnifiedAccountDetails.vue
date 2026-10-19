<template>
  <div v-if="loading" class="account-details-empty" role="status">加载中…</div>
  <dl v-else-if="account" class="account-details nt-card">
    <div v-for="field in fields" :key="field.key" class="account-details-row nt-row">
      <dt class="nt-row-label">{{ field.label }}</dt>
      <dd class="nt-row-value" :class="{ 'is-identifier': field.identifier }">{{ field.value }}</dd>
    </div>
  </dl>
  <div v-else class="account-details-empty">
    <span>尚未建立统一账号</span>
    <span class="account-details-note">保存权限时自动创建</span>
  </div>
</template>

<script setup>
import {computed} from 'vue'

const props = defineProps({
  account: {type: Object, default: null},
  loading: {type: Boolean, default: false}
})

const fields = computed(() => {
  const account = props.account
  if (!account) return []
  return [
    {key: 'uuid', label: '账号 UUID', value: account.uuid, identifier: true},
    {key: 'username', label: '账号名称', value: account.username || '未设置'},
    {key: 'qqUserOpenId', label: 'QQ OpenID', value: account.qqUserOpenId, identifier: true},
    {key: 'qqUserUin', label: 'QQ 号', value: account.qqUserUin, identifier: true},
    {key: 'qqConnectOpenId', label: 'QQ 互联', value: account.qqConnectOpenId, identifier: true},
    {key: 'minecraftUuid', label: 'Minecraft UUID', value: account.minecraftUuid, identifier: true},
    {key: 'createTime', label: '创建时间', value: account.createTime},
    {key: 'lastUpdateTime', label: '更新时间', value: account.lastUpdateTime}
  ].filter(field => field.value)
})
</script>

<style scoped>
.account-details {
  margin: 0;
}

.account-details-row dd {
  margin: 0;
  min-width: 0;
  user-select: text;
}

.account-details-row .is-identifier { font-family: var(--font-mono); }
.account-details-empty { padding: 12px; border-radius: 9px; background: var(--color-surface-hover); color: var(--color-text-muted); font-size: 12px; }
.account-details-note { display: block; margin-top: 4px; color: var(--color-text-subtle); font-size: 11px; }
</style>
