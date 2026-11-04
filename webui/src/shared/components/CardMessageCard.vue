<template>
  <component v-if="card" :is="targetUrl ? 'a' : 'div'" class="tuwen-card"
             :href="targetUrl || undefined" :target="targetUrl ? '_blank' : undefined"
             :rel="targetUrl ? 'noopener noreferrer' : undefined">
    <div class="tuwen-content">
      <div v-if="card.title" class="tuwen-title">{{ card.title }}</div>
      <div v-if="card.description || card.picUrl" class="tuwen-details">
        <div v-if="card.description" class="tuwen-description">{{ card.description }}</div>
        <div v-if="card.picUrl" class="tuwen-thumbnail">
          <img v-if="previewUrl && !previewFailed" :src="previewUrl" alt="" loading="lazy"
               referrerpolicy="no-referrer" @error="previewFailed = true"/>
        </div>
      </div>
    </div>
    <div v-if="targetUrl" class="tuwen-footer">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
           stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M10 13a5 5 0 0 0 7 .5l3-3a5 5 0 0 0-7-7l-1.7 1.7M14 11a5 5 0 0 0-7-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/>
      </svg>
      <span>{{ domain }}</span>
    </div>
  </component>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { parseCardMessage } from '../lib/card.js'
import { mediaUrl } from '../lib/mediaUrl.js'

const props = defineProps({ card: { type: [String, Object], default: null } })
const card = computed(() => parseCardMessage(props.card))
const targetUrl = computed(() => {
  try {
    const url = new URL(card.value?.jumpUrl)
    return ['http:', 'https:'].includes(url.protocol) ? url.href : ''
  } catch {
    return ''
  }
})
const domain = computed(() => targetUrl.value ? new URL(targetUrl.value).hostname : '')
const previewUrl = computed(() => mediaUrl(card.value?.picUrl))
const previewFailed = ref(false)
watch(previewUrl, () => { previewFailed.value = false })
</script>

<style scoped>
.tuwen-card {
  display: block;
  box-sizing: border-box;
  width: 248px;
  max-width: 100%;
  overflow: hidden;
  border-radius: 9px;
  background: #fff;
  color: #222;
  text-decoration: none;
}
.tuwen-content { padding: 9px 10px 10px; }
.tuwen-title {
  font-size: 15px;
  font-weight: 400;
  line-height: 1.35;
  overflow-wrap: anywhere;
}
.tuwen-details { display: flex; align-items: center; gap: 10px; margin-top: 4px; }
.tuwen-description {
  flex: 1;
  min-width: 0;
  color: #999;
  font-size: 13px;
  line-height: 1.35;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}
.tuwen-thumbnail {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  margin-left: auto;
  overflow: hidden;
  border-radius: 5px;
  background: #f7f7f7;
}
.tuwen-thumbnail img { display: block; width: 100%; height: 100%; object-fit: cover; }
.tuwen-footer {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-top: 1px solid #f0f0f0;
  color: #999;
  font-size: 12px;
  line-height: 1.3;
}
.tuwen-footer svg { width: 12px; height: 12px; flex: 0 0 12px; color: #333; }
.tuwen-footer span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tuwen-card:focus-visible { outline: 2px solid #0099ff; outline-offset: -2px; }
</style>
