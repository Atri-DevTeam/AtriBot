<script setup>
import { ref } from 'vue'
import AppSidebar from './AppSidebar.vue'
import { useBotConfig } from '../lib/botConfig.js'

defineProps({
  open: { type: Boolean, default: false },
  appId: { type: String, default: undefined },
  botOpenId: { type: String, default: undefined },
  botName: { type: String, default: undefined },
  feedbackBadge: { type: Number, default: 0 },
  galleryBadge: { type: Number, default: 0 }
})
const emit = defineEmits(['update:open'])
const identity = useBotConfig()
const sidebar = ref(null)
defineExpose({ resetCollapsed: () => sidebar.value?.resetCollapsed() })
</script>

<template>
  <div class="shell">
    <AppSidebar ref="sidebar" :open="open" @update:open="emit('update:open', $event)"
                :app-id="appId ?? identity.appId.value" :bot-open-id="botOpenId ?? identity.botOpenId.value"
                :bot-name="botName ?? identity.botName.value" :feedback-badge="feedbackBadge" :gallery-badge="galleryBadge">
      <template #toolbar><slot name="toolbar" /></template>
    </AppSidebar>
    <div class="sidebar-spacer" />
    <slot />
  </div>
</template>
