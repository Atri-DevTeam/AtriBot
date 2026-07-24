<template>
  <component
    :is="targetUrl ? 'a' : 'div'"
    v-if="card"
    class="ark-card"
    :class="{ 'ark-card--clickable': targetUrl }"
    :href="targetUrl || undefined"
    :target="targetUrl ? '_blank' : undefined"
    :rel="targetUrl ? 'noreferrer' : undefined"
    :title="targetUrl ? '打开卡片链接' : undefined"
  >
    <div v-if="card.preview && !previewFailed" class="ark-preview">
      <img :src="card.preview" :alt="card.title" referrerpolicy="no-referrer" loading="lazy" @error="previewFailed = true" />
    </div>
    <div v-else class="ark-preview ark-preview--empty" aria-hidden="true">
      <svg viewBox="0 0 64 64">
        <rect x="10" y="14" width="44" height="36" rx="8" fill="none" stroke="currentColor" stroke-width="4"/>
        <path d="M20 28h24M20 38h16" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
        <path d="M44 14v12h10" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </div>
    <div class="ark-body">
      <div class="ark-meta">
        <img v-if="card.sourceLogo" class="ark-source-logo" :src="card.sourceLogo" alt="" referrerpolicy="no-referrer" loading="lazy" @error="$event.target.style.display = 'none'" />
        <span>{{ card.tag || card.source || card.typeName }}</span>
        <span v-if="metaTail" class="ark-type">{{ metaTail }}</span>
      </div>
      <div class="ark-title">{{ card.title }}</div>
      <div v-if="card.prompt && card.prompt !== card.title" class="ark-prompt">{{ card.prompt }}</div>
    </div>
  </component>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { parseArkMessage } from '../lib/ark.js'

const props = defineProps({
  ark: {
    type: [String, Object, Array],
    default: null
  }
})

const card = computed(() => parseArkMessage(props.ark))
const targetUrl = computed(() => card.value?.targetUrl || '')
const metaTail = computed(() => {
  if (!card.value) return ''
  const head = card.value.tag || card.value.source || card.value.typeName
  if (card.value.source && card.value.source !== head) return card.value.source
  if (card.value.typeName && card.value.typeName !== head) return card.value.typeName
  return ''
})
const previewFailed = ref(false)

watch(card, () => {
  previewFailed.value = false
})
</script>

<style scoped>
.ark-card {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 10px;
  width: min(420px, 100%);
  overflow: hidden;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #ffffff;
  color: inherit;
  text-decoration: none;
}

.ark-card--clickable {
  cursor: pointer;
  transition: border-color 0.12s ease, box-shadow 0.12s ease, transform 0.12s ease;
}

.ark-card--clickable:hover {
  border-color: #94a3b8;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.08);
}

.ark-preview {
  width: 86px;
  height: 86px;
  background: #f1f5f9;
}

.ark-preview img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ark-preview--empty {
  display: grid;
  place-items: center;
  color: #64748b;
}

.ark-preview--empty svg {
  width: 34px;
  height: 34px;
}

.ark-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  padding: 9px 10px 9px 0;
}

.ark-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.2;
}

.ark-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ark-source-logo {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  object-fit: cover;
  flex: 0 0 auto;
}

.ark-type {
  flex: 0 0 auto;
  padding-left: 5px;
  border-left: 1px solid #cbd5e1;
}

.ark-title {
  color: #111827;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.35;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ark-prompt {
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 700px) {
  .ark-card {
    grid-template-columns: 72px minmax(0, 1fr);
  }

  .ark-preview,
  .ark-preview img {
    width: 72px;
    height: 72px;
  }
}
</style>
