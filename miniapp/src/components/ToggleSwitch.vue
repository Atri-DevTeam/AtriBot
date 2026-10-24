<script setup lang="ts">
defineProps<{modelValue: boolean; label: string; disabled?: boolean}>()
const emit = defineEmits<{'update:modelValue': [value: boolean]}>()
</script>

<template>
  <button type="button" class="toggle-switch" role="switch" :aria-label="label" :aria-checked="modelValue"
          :disabled="disabled" @click="emit('update:modelValue', !modelValue)"><span/></button>
</template>

<style scoped>
.toggle-switch { display: inline-flex; flex-shrink: 0; align-items: center; width: 34px; height: 20px; padding: 3px; border: 0; border-radius: 12px; background: #bdc5b9; cursor: pointer; transition: background .18s; }
.toggle-switch > span { width: 14px; height: 14px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px #35433920; transition: transform .18s; }
.toggle-switch[aria-checked=true] { background: #849978; }
.toggle-switch[aria-checked=true] > span { transform: translateX(14px); }
.toggle-switch:disabled { cursor: default; opacity: .5; }
@media (prefers-reduced-motion: reduce) { .toggle-switch, .toggle-switch > span { transition: none; } }
</style>
