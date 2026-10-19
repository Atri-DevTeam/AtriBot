<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { resetBotConfig, useBotConfig } from './shared/lib/botConfig.js'
import { isQqPage } from './shared/lib/qqEnvironment.js'

const route = useRoute()
const sectionTitle = computed(() => route.meta.title || '管理中心')
const {appId, botOpenId, botName} = useBotConfig()
const avatarFailed = ref(false)
const botAvatar = computed(() => appId.value && botOpenId.value
  ? `https://thirdqq.qlogo.cn/qqapp/${encodeURIComponent(appId.value)}/${encodeURIComponent(botOpenId.value)}/100`
  : '')
watch(botAvatar, () => { avatarFailed.value = false })
watch(() => route.meta.guest, guest => { if (guest) resetBotConfig() }, { immediate: true })
</script>

<template>
  <header v-if="isQqPage" class="qq-topbar" aria-label="机器人信息">
    <div class="qq-topbar-brand">
      <img v-if="botAvatar && !avatarFailed" class="qq-topbar-avatar" :src="botAvatar" alt="机器人头像"
           width="32" height="32" referrerpolicy="no-referrer" @error="avatarFailed = true" />
      <span v-else class="qq-topbar-avatar qq-topbar-fallback" aria-hidden="true">{{ botName.slice(0, 1) }}</span>
      <span class="qq-topbar-name">{{ botName }}</span>
      <span class="qq-topbar-divider" aria-hidden="true" />
      <span class="qq-topbar-caption">{{ sectionTitle }}</span>
    </div>
  </header>
  <router-view />
</template>
