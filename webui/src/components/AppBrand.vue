<template>
  <div class="brand">
    <img
      v-if="botOpenId && appId"
      class="brand-avatar"
      :src="`https://thirdqq.qlogo.cn/qqapp/${appId}/${botOpenId}/100`"
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
defineProps({
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
</script>
