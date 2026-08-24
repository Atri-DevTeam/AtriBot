<template>
  <article class="forward-item" :class="{ 'forward-item--quote': isQuote, 'forward-item--compact': compact }">
    <div v-if="!isContainer && (item.author || item.type) && !(item.author && item.content)" class="forward-item-head">
      <span v-if="item.author" class="forward-item-author">{{ item.author }}</span>
      <span v-if="item.type" class="forward-item-type">{{ item.type }}</span>
    </div>

    <button v-if="isContainer && item.forward?.length" type="button" class="forward-item-preview"
            @click="emit('open-forward', item)">
      <span class="forward-item-preview-main forward-item-line">
        <span v-if="item.author" class="forward-item-author">{{ item.author }}</span>
        <span v-if="item.author" class="forward-item-colon">:</span>
        <span class="forward-item-content">[合并消息]</span>
      </span>
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <polyline points="9 18 15 12 9 6"/>
      </svg>
    </button>
    <div v-else-if="isContainer && !item.forward?.length" class="forward-item-line">
      <span v-if="item.author" class="forward-item-author">{{ item.author }}</span>
      <span v-if="item.author" class="forward-item-colon">:</span>
      <span class="forward-item-content">[合并消息]</span>
    </div>
    <div v-else-if="item.author && item.content" class="forward-item-line">
      <span class="forward-item-author">{{ item.author }}</span>
      <span class="forward-item-colon">:</span>
      <span class="forward-item-content">{{ item.content }}</span>
    </div>
    <pre v-else-if="item.content" class="forward-item-content">{{ item.content }}</pre>

    <div v-if="item.card && !compact" class="forward-ark">
      <div class="forward-ark-head">{{ item.card.name }}</div>
      <dl v-if="Object.keys(item.card.fields || {}).length" class="forward-ark-fields">
        <template v-for="(value, key) in item.card.fields" :key="key">
          <dt>{{ key }}</dt><dd>{{ value }}</dd>
        </template>
      </dl>
    </div>

    <div v-if="item.attachments?.length && !compact" class="forward-attachments">
      <template v-for="(attachment, index) in item.attachments" :key="index">
        <a v-if="isImage(attachment) && attachment.url" class="forward-image-link"
           :href="absoluteUrl(attachment.url)" target="_blank" rel="noreferrer">
          <img :src="absoluteUrl(attachment.url)" :alt="attachment.filename || '图片'" loading="lazy" />
        </a>
        <video v-else-if="attachment.type === '视频' && attachment.url"
               :src="absoluteUrl(attachment.url)" controls playsinline preload="metadata" />
        <audio v-else-if="attachment.type === '语音' && attachment.url"
               :src="absoluteUrl(attachment.url)" controls preload="none" />
        <a v-else-if="attachment.url" class="forward-file" :href="absoluteUrl(attachment.url)"
           target="_blank" rel="noreferrer">
          {{ attachment.filename || attachment.type || '附件' }}
          <small v-if="attachment.size">{{ formatSize(attachment.size) }}</small>
        </a>
        <span v-else class="forward-file forward-file--missing">
          {{ attachment.filename || attachment.type || '附件' }}
        </span>
      </template>
    </div>

  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  item: { type: Object, required: true },
  depth: { type: Number, default: 0 },
  compact: { type: Boolean, default: false }
})

const emit = defineEmits(['open-forward'])

function isImage(attachment) {
  return attachment.type === '图片' || attachment.type === '动图'
}

function absoluteUrl(url) {
  if (!url) return ''
  if (/^(https?:)?\/\//i.test(url)) return url.startsWith('//') ? 'https:' + url : url
  if (url.startsWith('data:')) return url
  return 'https://' + url
}

function formatSize(value) {
  const bytes = Number(value)
  if (!Number.isFinite(bytes) || bytes <= 0) return ''
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let index = 0
  let amount = bytes
  while (amount >= 1024 && index < units.length - 1) {
    amount /= 1024
    index++
  }
  return `${amount >= 10 || index === 0 ? Math.round(amount) : amount.toFixed(1)} ${units[index]}`
}

const isQuote = computed(() => props.item.type === '引用消息')
const isContainer = computed(() => props.item.type === '合并转发消息')
</script>
