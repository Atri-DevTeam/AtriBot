<template>
  <img v-if="settings.enabled && imageUrl && !failed" class="chatnt-wallpaper"
       :src="imageUrl" :style="{ opacity: (100 - settings.transparency) / 100 }"
       alt="" aria-hidden="true" draggable="false" referrerpolicy="no-referrer"
       @error="failed = true" />
</template>

<script setup>
import { ref, watch } from 'vue'
import { CHAT_BACKGROUND_URL, useChatBackground } from '../lib/chatBackground.js'

const { settings } = useChatBackground()
const imageUrl = ref('')
const failed = ref(false)

// 只在进入聊天页或重新启用时换图，调透明度和切换会话沿用当前图片。
watch(() => settings.enabled, enabled => {
  failed.value = false
  imageUrl.value = enabled ? `${CHAT_BACKGROUND_URL}?t=${Date.now()}` : ''
}, { immediate: true })
</script>
