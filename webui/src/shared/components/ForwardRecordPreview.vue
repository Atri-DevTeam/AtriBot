<template>
  <button type="button" class="forward-card" :aria-label="`查看${title}`" @click="$emit('open')">
    <span class="forward-card-title">{{ title }}</span>
    <span class="forward-card-items">
      <span v-for="(item, index) in record.items.slice(0, 4)" :key="index" class="forward-card-summary">
        {{ item.author ? `${item.author}：` : '' }}{{ forwardSummary(item) }}
      </span>
      <span v-if="!record.items.length" class="forward-card-summary">暂无消息内容</span>
    </span>
    <span class="forward-card-footer">
      <span>聊天记录</span>
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="m9 5 7 7-7 7" />
      </svg>
    </span>
  </button>
</template>

<script setup>
import { computed } from 'vue'
import { forwardTitle, forwardSummary } from '../lib/forward.js'

const props = defineProps({ record: { type: Object, required: true } })
defineEmits(['open'])
const title = computed(() => forwardTitle(props.record.title))
</script>
