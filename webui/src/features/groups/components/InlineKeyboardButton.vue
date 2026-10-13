<script setup>
defineProps({button: {type: Object, required: true}})
</script>

<template>
  <span class="inline-keyboard-button-content">
    <svg v-if="button.style === 'ICON_BUTTON'" class="inline-keyboard-button-sparkle" viewBox="0 0 24 24"
         fill="none" aria-hidden="true">
      <path d="M16 20.2A9 9 0 1 1 21 12c0 3.2-1.7 6-4.3 7.6L16 23l-3.5-2.1"
            stroke="#bd54ff" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="m16 20.2 0 2.8-3.5-2.1" stroke="#ef59dd" stroke-width="1.6"
            stroke-linecap="round" stroke-linejoin="round"/>
      <path d="m12 7 1.3 3.7L17 12l-3.7 1.3L12 17l-1.3-3.7L7 12l3.7-1.3Z" fill="#bd54ff"/>
    </svg>
    <span class="inline-keyboard-button-label">{{ button.display_text || '未命名按钮' }}</span>
    <svg v-if="button.type === 'LINK'" class="inline-keyboard-button-link" viewBox="0 0 24 24" fill="none"
         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
         aria-hidden="true">
      <path d="m10 13 4-4M8 10l-3 3a4.2 4.2 0 0 0 6 6l3-3M10 8l3-3a4.2 4.2 0 0 1 6 6l-3 3"/>
    </svg>
  </span>
</template>

<style scoped>
.inline-keyboard-button-content {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  max-width: 100%;
  vertical-align: middle;
}

.inline-keyboard-button-label {
  min-width: 0;
  overflow-wrap: anywhere;
}

.inline-keyboard-button-sparkle {
  width: 1.1em;
  height: 1.1em;
  flex-shrink: 0;
}

.inline-keyboard-button-link {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 12px;
  height: 12px;
  pointer-events: none;
}
</style>
