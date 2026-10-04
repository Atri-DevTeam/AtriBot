<template>
  <ArkMessageCard v-if="displayCard" :ark="displayCard" />
</template>

<script setup>
import { computed } from 'vue'
import { parseCardMessage } from '../lib/card.js'
import ArkMessageCard from './ArkMessageCard.vue'

const props = defineProps({ card: { type: [String, Object], default: null } })
const displayCard = computed(() => {
  const card = parseCardMessage(props.card)
  return card ? { ark_name: '卡片', ark_type: 'tuwen', fields: {
    title: card.title, desc: card.description, preview: card.picUrl, jump_url: card.jumpUrl
  } } : null
})
</script>
