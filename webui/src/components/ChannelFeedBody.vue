<script setup>
import { computed, ref } from 'vue'
import { renderMarkdown } from '../lib/markdown.js'
import ChannelImagePreview from './ChannelImagePreview.vue'

/**
 * @Author YZ_Ljc_
 * @ClassName ChannelFeedBody
 * @Created_at 2026/09/11
 * @Project AtriMeow
 * @Package webui.src.components
 */
const props = defineProps({ feed: { type: Object, required: true }, detail: Boolean })
const previewUrl = ref('')
function previewImage(event) {
  const img = event.target.closest?.('img')
  if (!img) return
  const url = img.currentSrc || img.src
  if (!/^https?:\/\//i.test(url)) return
  event.preventDefault()
  event.stopPropagation()
  previewUrl.value = url
}
function bodyText(value) {
  if (typeof value === 'string') return value
  if (!value || typeof value !== 'object') return ''
  if (typeof value.source_markdown === 'string' && value.source_markdown) return value.source_markdown
  return typeof value.text === 'string' ? value.text : ''
}

const images = computed(() => {
  const candidates = [props.feed.images, props.feed.content_richtext?.images, props.feed.content?.images]
  const items = candidates.find(value => Array.isArray(value) && value.length) || []
  return items.map(item => {
    const url = typeof item === 'string' ? item : item?.picUrl || item?.url || item?.pic_url || ''
    return typeof url === 'string' && /^https?:\/\//i.test(url) ? url : ''
  })
})
const source = computed(() => {
  const candidates = props.detail
    ? [props.feed.content_richtext, props.feed.content]
    : [props.feed.content_snippet, props.feed.content_richtext, props.feed.content]
  return candidates.map(bodyText).find(value => value !== '') || ''
})
const markdown = computed(() => props.detail && (
  props.feed.is_markdown === true || props.feed.content_richtext?.is_markdown === true || props.feed.content?.is_markdown === true
))
const usedImages = computed(() => new Set(markdown.value
  ? [...source.value.matchAll(/\[\(0,(\d+)\)\]\(@img\)/g)].map(match => Number(match[1])) : []))
const html = computed(() => renderMarkdown(source.value.replace(/\[\(0,(\d+)\)\]\(@img\)/g, (_, index) => {
  const url = images.value[Number(index)]
  return url ? `![帖子配图](${url.replace(/\(/g, '%28').replace(/\)/g, '%29')})` : '[图片暂不可用]'
})))
const gallery = computed(() => images.value.filter((url, index) => url && !usedImages.value.has(index)))
</script>

<template>
  <div class="channel-body" @click="previewImage">
    <div v-if="markdown" class="channel-markdown" v-html="html" />
    <p v-else class="channel-plaintext">{{ source }}</p>
    <div v-if="gallery.length" class="channel-images" :class="{ 'channel-images-single': gallery.length === 1 }">
      <a v-for="(url, index) in gallery" :key="`${url}-${index}`" :href="url" target="_blank" rel="noopener noreferrer"
         aria-label="放大查看图片" @click.prevent.stop="previewUrl = url">
        <img :src="url" alt="帖子配图" loading="lazy" referrerpolicy="no-referrer">
      </a>
    </div>
  </div>
  <ChannelImagePreview v-if="previewUrl" :src="previewUrl" @close="previewUrl = ''" />
</template>

<style scoped>
.channel-plaintext { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; margin: 10px 0; }
.channel-images { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); align-items: start; gap: 8px; margin-top: 12px; max-width: 480px; }
.channel-images a { display: flex; justify-content: flex-start; cursor: zoom-in; }
.channel-images img { display: block; width: auto; height: auto; max-width: 100%; max-height: 160px; object-fit: contain; border-radius: 7px; background: #f1f4f8; }
.channel-images-single { grid-template-columns: minmax(0, 280px); }
.channel-images-single img { max-height: 280px; }
.channel-markdown { line-height: 1.8; overflow-wrap: anywhere; }
.channel-markdown :deep(img) { width: auto; height: auto; max-width: min(100%, 280px); max-height: 280px; object-fit: contain; border-radius: 7px; cursor: zoom-in; }
.channel-markdown :deep(pre) { overflow: auto; padding: 12px; background: #f3f5f9; border-radius: 6px; }
.channel-markdown :deep(a) { color: #2471db; }
.channel-markdown :deep(blockquote) { border-left: 3px solid #b9cee9; padding-left: 14px; margin-left: 0; color: #667085; }
</style>
