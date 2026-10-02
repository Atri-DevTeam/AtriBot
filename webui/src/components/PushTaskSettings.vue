<template>
  <div class="chatnt-info-section">
    <div class="chatnt-info-label">功能开关</div>
    <div class="nt-card">
      <div v-if="loading" class="nt-empty">加载中</div>
      <div v-else-if="error" class="nt-error">{{ error }}</div>
      <template v-else>
        <div v-if="entries.length === 0" class="nt-empty">暂无功能配置</div>
        <div v-for="[key, cfg] in entries" :key="key" class="nt-row">
          <span class="nt-row-label">{{ cfg.displayName || key }}<span v-if="cfg.displayName" class="nt-row-note">{{ key }}</span></span>
          <button type="button" class="nt-switch" :class="{ on: cfg.enabled }" role="switch"
                  :aria-label="cfg.displayName || key" :aria-checked="!!cfg.enabled" :disabled="!!savingKey"
                  @click="emit('toggle', key, !cfg.enabled)"><span class="nt-switch-knob" /></button>
        </div>
      </template>
    </div>
    <div v-if="!loading && !error && addableKeys.length" class="nt-add">
      <select class="nt-select" v-model="newFunctionKey" :disabled="!!savingKey">
        <option value="">选择功能</option>
        <option v-for="key in addableKeys" :key="key" :value="key">{{ key }}</option>
      </select>
      <button type="button" class="nt-btn" :disabled="!newFunctionKey || !!savingKey"
              @click="emit('toggle', newFunctionKey, true)">添加</button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  entries: { type: Array, default: () => [] },
  functionKeys: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  savingKey: { type: String, default: '' }
})
const emit = defineEmits(['toggle'])
const newFunctionKey = ref('')
const addableKeys = computed(() => {
  const owned = new Set(props.entries.map(([key]) => key))
  return props.functionKeys.filter(key => !owned.has(key))
})
watch(addableKeys, keys => {
  if (!keys.includes(newFunctionKey.value)) newFunctionKey.value = ''
})
</script>
