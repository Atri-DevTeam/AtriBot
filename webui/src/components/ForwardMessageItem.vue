<template>
  <article class="forward-item">
    <div class="forward-item-avatar" aria-hidden="true">{{ Array.from(item.author || '?')[0] }}</div>
    <div class="forward-item-main">
      <div class="forward-item-head">
        <span class="forward-item-author">{{ item.author || '未知发送者' }}</span>
        <span v-if="isQuote" class="forward-item-type">引用消息</span>
      </div>
      <div class="forward-item-bubble" :class="{ 'forward-item--quote': isQuote, 'forward-item-bubble--record': isContainer && item.forward?.length }">
        <ForwardRecordPreview v-if="isContainer && item.forward?.length"
                              :record="{ title: item.content, items: item.forward }"
                              @open="emit('open-forward', item)" />
        <p v-else-if="isContainer" class="forward-item-content">[聊天记录]</p>
        <p v-else-if="item.content" class="forward-item-content">{{ item.content }}</p>
        <div v-if="item.card" class="forward-ark">
          <div class="forward-ark-head">{{ item.card.name }}</div>
          <dl v-if="Object.keys(item.card.fields || {}).length" class="forward-ark-fields">
            <template v-for="(value, key) in item.card.fields" :key="key">
              <dt>{{ key }}</dt><dd>{{ value }}</dd>
            </template>
          </dl>
        </div>

        <div v-if="item.attachments?.length" class="forward-attachments">
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

        <ForwardRecordPreview v-if="!isContainer && item.forward?.length"
                              :record="{ title: '引用的聊天记录', items: item.forward }"
                              @open="emit('open-forward', item)" />
        <p v-if="!isContainer && !item.content && !item.card && !item.attachments?.length && !item.forward?.length"
           class="forward-item-content">[{{ item.type || '消息' }}]</p>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import ForwardRecordPreview from './ForwardRecordPreview.vue'

const props = defineProps({
  item: { type: Object, required: true }
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
