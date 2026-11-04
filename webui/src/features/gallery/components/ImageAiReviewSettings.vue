<script setup>
import {onBeforeUnmount, onMounted, reactive, ref} from 'vue'

const props = defineProps({api: {type: Function, required: true}})
const emit = defineEmits(['close'])
const settings = reactive({
  enabled: false,
  prompt: '',
  configured: false,
  rejectInappropriate: true,
  rejectOffTopic: true
})
const loading = ref(true), saving = ref(false), error = ref('')
const controller = new AbortController()
let disposed = false

async function load() {
  error.value = ''
  loading.value = true
  try {
    Object.assign(settings, await props.api('/gallery/ai/settings', {signal: controller.signal}))
  } catch (e) {
    if (!disposed) error.value = e.message
  } finally {
    if (!disposed) loading.value = false
  }
}

async function save() {
  if (loading.value || saving.value || !settings.prompt.trim()) return
  saving.value = true
  error.value = ''
  try {
    await props.api('/gallery/ai/settings', {
      method: 'PUT', body: JSON.stringify({
        enabled: settings.enabled, prompt: settings.prompt,
        rejectInappropriate: settings.rejectInappropriate, rejectOffTopic: settings.rejectOffTopic
      })
    })
    if (!disposed) emit('close')
  } catch (e) {
    if (!disposed) error.value = e.message
  } finally {
    saving.value = false
  }
}

function close() {
  if (!saving.value) emit('close')
}

function keydown(e) {
  if (e.key === 'Escape') close()
}

onMounted(() => {
  load();
  window.addEventListener('keydown', keydown)
})
onBeforeUnmount(() => {
  disposed = true;
  controller.abort();
  window.removeEventListener('keydown', keydown)
})
</script>

<template>
  <div class="modal-backdrop gallery-ai-backdrop" @click.self="close">
    <section class="modal gallery-ai-modal" role="dialog" aria-modal="true" aria-labelledby="gallery-ai-title">
      <header class="modal-head">
        <h2 id="gallery-ai-title">AI 初审设置</h2>
        <button class="icon-button" aria-label="关闭初审设置" :disabled="saving" @click="close">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="m6 6 12 12M18 6 6 18"/>
          </svg>
        </button>
      </header>
      <div class="modal-body gallery-ai-form">
        <p v-if="loading" class="gallery-ai-note">正在加载…</p>
        <template v-else>
          <label class="gallery-ai-enable"><span>启用 AI 初审<small>新投稿完成转存后自动初审</small></span>
            <input v-model="settings.enabled" type="checkbox"
                   :disabled="saving || (!settings.configured && !settings.enabled)" aria-label="启用 AI 初审"/>
          </label>
          <p v-if="!settings.configured" class="gallery-ai-error">请在 config.yml 中填写 ai.deepseek_image
            的接口地址、密钥和模型</p>
          <div class="gallery-ai-rules">
            <label class="gallery-ai-enable">
              <span>自动拒绝不适宜内容<small>明显色情、暴力、鉴政内容</small><small>拒绝理由：存在不合适的内容</small></span>
              <input v-model="settings.rejectInappropriate" type="checkbox" :disabled="saving"
                     aria-label="自动拒绝不适宜内容"/>
            </label>
            <label class="gallery-ai-enable">
              <span>自动拒绝非主题图片<small>表情包、玩梗图等非正常主题投稿</small><small>拒绝理由：图片元素不符合要求</small></span>
              <input v-model="settings.rejectOffTopic" type="checkbox" :disabled="saving"
                     aria-label="自动拒绝非主题图片"/>
            </label>
          </div>
          <label class="gallery-ai-prompt">审核标准
            <textarea v-model="settings.prompt" rows="7" maxlength="8000" :disabled="saving"
                      placeholder="填写图片审核标准"/>
          </label>
          <p class="gallery-ai-note">自动拒绝沿用审核通知流程；未命中规则或无法确定的投稿交由人工审核，不自动通过</p>
        </template>
        <p v-if="error" class="gallery-ai-error" role="alert">{{ error }}</p>
      </div>
      <footer class="modal-foot">
        <button class="ghost-button" :disabled="saving" @click="close">取消</button>
        <button class="primary-button" :disabled="loading || saving || !settings.prompt.trim()" @click="save">
          {{ saving ? '保存中…' : '保存设置' }}
        </button>
      </footer>
    </section>
  </div>
</template>
