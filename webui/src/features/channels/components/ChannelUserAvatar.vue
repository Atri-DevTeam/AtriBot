<script setup>
import { computed } from 'vue'
import { channelUserId, channelUserName } from '../lib/channelComments.js'

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelUserAvatar
 * @Created_at 2026/09/11
 * @Project AtriMeow
 * @Package webui.src.components
 */
const props = defineProps({ user: { type: Object, required: true }, small: Boolean })
const palette = [
  ['#e6efff', '#3966ad'], ['#efe7fc', '#7850a9'], ['#fbe5ee', '#ac5077'],
  ['#fff0db', '#a16c28'], ['#def3ed', '#347f6b'], ['#e1f0f7', '#397b9a'],
  ['#fce8df', '#ac6349'], ['#eaeddc', '#728243']
]
const name = computed(() => channelUserName(props.user))
const initial = computed(() => Array.from(name.value.trim())[0] || '?')
const color = computed(() => {
  const key = channelUserId(props.user) || name.value
  let hash = 0
  for (const character of key) hash = (Math.imul(hash, 31) + character.codePointAt(0)) >>> 0
  const [backgroundColor, color] = palette[hash % palette.length]
  return { backgroundColor, color }
})
</script>

<template>
  <span class="channel-user-avatar" :class="{ small }" :style="color" :title="name" aria-hidden="true">{{ initial }}</span>
</template>

<style scoped>
.channel-user-avatar { display: inline-flex; align-items: center; justify-content: center; width: 38px; height: 38px; flex-shrink: 0; border-radius: 50%; font-size: 15px; font-weight: 600; user-select: none; }
.channel-user-avatar.small { width: 28px; height: 28px; font-size: 12px; }
</style>
