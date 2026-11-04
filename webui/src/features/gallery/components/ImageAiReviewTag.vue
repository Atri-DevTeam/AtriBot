<script setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'

const props = defineProps({image: {type: Object, required: true}})
const trigger = ref(null)
const visible = ref(false)
const position = ref({})
const status = computed(() => props.image.aiReview?.status || 'NONE')
const label = computed(() => {
  if (status.value === 'REJECT' && props.image.reviewStatus === 'DENIED' && props.image.reviewer === 'AI 初审') return 'AI 已拒绝'
  return {QUEUED: 'AI 待初审', PASS: '初步审查通过', REJECT: 'AI 建议拒绝', UNCERTAIN: 'AI 需人工确认', FAILED: 'AI 初审失败'}[status.value] || 'AI 未初审'
})
const reason = computed(() => props.image.aiReview?.reason || props.image.reviewRemark || '未提供具体原因')
const tooltipId = `image-ai-reason-${Math.random().toString(36).slice(2)}`

function show() {
  if (!trigger.value) return
  const rect = trigger.value.getBoundingClientRect()
  const width = Math.min(280, window.innerWidth - 24)
  const above = rect.top > window.innerHeight / 2
  position.value = {
    width: `${width}px`, left: `${Math.max(12, Math.min(rect.left, window.innerWidth - width - 12))}px`,
    ...(above ? {bottom: `${window.innerHeight - rect.top + 8}px`, maxHeight: `${rect.top - 20}px`}
      : {top: `${rect.bottom + 8}px`, maxHeight: `${window.innerHeight - rect.bottom - 20}px`})
  }
  visible.value = true
}
function hide() { visible.value = false }
function leave() { if (document.activeElement !== trigger.value) hide() }
function reposition() {
  if (!visible.value || !trigger.value) return
  const rect = trigger.value.getBoundingClientRect()
  if (rect.bottom < 0 || rect.top > window.innerHeight) hide()
  else show()
}
onMounted(() => { window.addEventListener('resize', reposition); window.addEventListener('scroll', reposition, true) })
onBeforeUnmount(() => { window.removeEventListener('resize', reposition); window.removeEventListener('scroll', reposition, true) })
</script>

<template>
  <span class="gallery-ai-label">
    <span class="gallery-ai-tag" :data-status="status">{{ label }}</span>
    <button v-if="status === 'REJECT'" ref="trigger" type="button" class="gallery-ai-reason-button"
            aria-label="查看初审拒绝原因" :aria-describedby="visible ? tooltipId : undefined"
            @mouseenter="show" @mouseleave="leave" @focus="show" @blur="hide" @click.stop="show" @keydown.esc.stop="hide">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" aria-hidden="true">
        <circle cx="12" cy="12" r="9"/><path d="M9.5 9a2.5 2.5 0 0 1 5 .5c0 1.5-2.5 2-2.5 3.5"/><path d="M12 16h.01"/>
      </svg>
    </button>
    <Teleport to="body">
      <div v-if="visible" :id="tooltipId" class="gallery-ai-reason-tooltip" role="tooltip" :style="position" @mouseenter="show" @mouseleave="leave">{{ reason }}</div>
    </Teleport>
  </span>
</template>

<style scoped>
.gallery-ai-label { display: inline-flex; align-items: center; gap: 4px; min-width: 0; }
.gallery-ai-reason-button { display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; width: 24px; height: 24px; padding: 0; border: 0; border-radius: 5px; background: transparent; color: #a87579; cursor: help; }
.gallery-ai-reason-button:hover, .gallery-ai-reason-button:focus-visible { color: #c34b55; background: #fff0f0; outline: none; }
.gallery-ai-reason-tooltip { position: fixed; z-index: 1600; box-sizing: border-box; padding: 10px 12px; border: 1px solid #e6e9ee; border-radius: 8px; background: #fff; color: #51545a; box-shadow: 0 4px 18px rgb(25 40 60 / 12%); font-size: 12px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; overflow-y: auto; }
</style>
