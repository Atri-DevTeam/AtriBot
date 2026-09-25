<script setup>
import { computed, onMounted, ref } from 'vue'
import { isBlockedCosUrl } from '../lib/mediaUrl.js'

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelImagePreview
 * @Created_at 2026/09/11
 * @Project AtriMeow
 * @Package webui.src.components
 */
const props = defineProps({ src: { type: String, required: true } })
const visibleSrc = computed(() => isBlockedCosUrl(props.src) ? '' : props.src)
const emit = defineEmits(['close'])
const dialog = ref(null)
const original = ref(false)
const failed = ref(false)
onMounted(() => dialog.value.showModal())
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="channel-image-preview" aria-label="图片预览"
            @cancel.prevent="emit('close')" @click="($event.target === $event.currentTarget) && emit('close')">
      <header>
        <span>图片预览</span>
        <button type="button" :aria-pressed="original" @click="original = !original">{{ original ? '适应窗口' : '原始尺寸' }}</button>
        <a v-if="visibleSrc" :href="visibleSrc" target="_blank" rel="noopener noreferrer">打开原图 ↗</a>
        <button type="button" autofocus aria-label="关闭图片预览" @click="emit('close')">关闭 ×</button>
      </header>
      <div class="channel-image-preview-body" :class="{ original }">
        <p v-if="failed" role="alert">图片加载失败，可尝试打开原图。</p>
        <img v-else-if="visibleSrc" :src="visibleSrc" alt="放大预览" referrerpolicy="no-referrer" @error="failed = true">
        <p v-else>该图片已禁止在 WebUI 中加载。</p>
      </div>
    </dialog>
  </Teleport>
</template>

<style scoped>
.channel-image-preview { width: min(1100px, calc(100vw - 32px)); height: calc(100dvh - 48px); max-width: none; max-height: none; padding: 0; border: 1px solid #ffffff24; border-radius: 12px; color: #dde5f0; background: #18202c; box-shadow: 0 24px 80px #0005; }
.channel-image-preview[open] { display: flex; flex-direction: column; }
.channel-image-preview::backdrop { background: #0b1223b8; }
header { display: flex; align-items: center; gap: 14px; flex-shrink: 0; padding: 14px 18px; border-bottom: 1px solid #ffffff16; font: 12px var(--font-sans, sans-serif); }
header > span { margin-right: auto; }
header button, header a { border: 0; border-radius: 5px; background: transparent; color: #c4d3e8; padding: 6px; text-decoration: none; font: inherit; cursor: pointer; white-space: nowrap; }
header button:hover, header a:hover { color: white; background: #ffffff12; }
header :focus-visible { outline: 2px solid #82b3ff; outline-offset: 2px; }
.channel-image-preview-body { display: flex; align-items: center; justify-content: center; flex: 1; min-height: 0; padding: 16px; overflow: auto; }
.channel-image-preview-body img { display: block; width: auto; height: auto; max-width: 100%; max-height: 100%; object-fit: contain; }
.channel-image-preview-body.original { align-items: flex-start; justify-content: flex-start; }
.channel-image-preview-body.original img { flex-shrink: 0; max-width: none; max-height: none; }
@media (max-width: 600px) { header { gap: 5px; padding: 10px; } header > span { display: none; } .channel-image-preview { height: calc(100dvh - 24px); } }
</style>
