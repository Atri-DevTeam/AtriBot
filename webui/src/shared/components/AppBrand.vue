<template>
  <div class="brand">
    <img
      v-if="botOpenId && appId"
      class="brand-avatar"
      :src="botAvatar"
      referrerpolicy="no-referrer"
    />
    <div v-else class="brand-mark">A</div>
    <div>
      <h1>{{ botName }}</h1>
      <p>官方机器人WebUI</p>
    </div>
  </div>
</template>

<script setup>
import { computed, watch } from 'vue'

const props = defineProps({
  appId: {
    type: String,
    default: ''
  },
  botOpenId: {
    type: String,
    default: ''
  },
  botName: {
    type: String,
    default: 'AtriBot'
  }
})

const botAvatar = computed(() => props.appId && props.botOpenId
  ? `https://thirdqq.qlogo.cn/qqapp/${encodeURIComponent(props.appId)}/${encodeURIComponent(props.botOpenId)}/100`
  : '')

watch(botAvatar, avatar => {
  const icon = document.getElementById('webui-favicon')
  if (!avatar || !icon) return
  icon.removeAttribute('type')
  icon.href = avatar
}, { immediate: true })
</script>
