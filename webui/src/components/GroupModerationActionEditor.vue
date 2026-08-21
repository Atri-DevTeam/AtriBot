<template>
  <div class="gm-action-grid">
    <label class="checkbox-label">
      <input type="checkbox" :checked="modelValue.recall" @change="patch('recall', $event.target.checked)"/> 撤回消息
    </label>
    <label class="checkbox-label">
      <input type="checkbox" :checked="modelValue.remind" @change="patch('remind', $event.target.checked)"/> 发送提醒
    </label>
    <input v-if="modelValue.remind" class="gs-input gm-remind-input" type="text"
           :value="modelValue.remindMessage" @input="patch('remindMessage', $event.target.value)"
           placeholder="撤回提醒，例如：你的消息违规了哦"/>
    <label class="checkbox-label">
      <input type="checkbox" :checked="modelValue.mute" @change="patch('mute', $event.target.checked)"/> 禁言
    </label>
    <label v-if="modelValue.mute" class="gs-form-row gm-mute-seconds">
      <span class="gs-form-label">禁言时长（秒）</span>
      <input class="gs-input" type="number" min="1" :value="modelValue.muteSeconds"
             @input="patch('muteSeconds', Number($event.target.value) || 0)"/>
    </label>
    <label class="checkbox-label">
      <input type="checkbox" :checked="modelValue.notifyDebugGroup" @change="patch('notifyDebugGroup', $event.target.checked)"/> 通知到开发组
    </label>
  </div>
</template>

<script setup>
const props = defineProps({modelValue: {type: Object, required: true}})
const emit = defineEmits(['update:modelValue'])

function patch(key, value) {
  emit('update:modelValue', {...props.modelValue, [key]: value})
}
</script>
