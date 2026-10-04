<script setup>
import { onMounted, ref } from 'vue'

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelReplyComposer
 * @Created_at 2026/09/11
 * @Project AtriMeow
 * @Package webui.src.components
 */
defineProps({ modelValue: { type: String, default: '' }, name: String, busy: Boolean, error: String })
defineEmits(['update:modelValue', 'submit', 'cancel'])
const input = ref(null)
onMounted(() => input.value?.focus({ preventScroll: true }))
</script>

<template>
  <form class="channel-comment-form channel-reply-form" @submit.prevent="$emit('submit')" @keydown.esc.stop="$emit('cancel')">
    <span class="channel-reply-recipient">回复 {{ name }}</span>
    <textarea ref="input" :value="modelValue" @input="$emit('update:modelValue', $event.target.value)"
              maxlength="10000" rows="2" :placeholder="`回复 ${name}…`" :aria-label="`回复 ${name}`" :disabled="busy" required />
    <p v-if="error" class="channel-error" role="alert">{{ error }}</p>
    <div class="channel-comment-compose-actions">
      <button type="button" class="channel-comment-link" :disabled="busy" @click="$emit('cancel')">取消</button>
      <button class="channel-primary" :disabled="busy || !modelValue.trim()">{{ busy ? '处理中…' : '发送回复' }}</button>
    </div>
  </form>
</template>
