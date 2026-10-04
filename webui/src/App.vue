<script setup>
import { watch } from 'vue'
import { useRoute } from 'vue-router'
import { resetBotConfig } from './shared/lib/botConfig.js'

const route = useRoute()
watch(() => route.meta.guest, guest => { if (guest) resetBotConfig() }, { immediate: true })
</script>

<template>
  <router-view />
</template>
