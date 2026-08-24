<template>
  <section class="forward-card">
    <header class="forward-card-title">
      <span class="forward-card-icon" aria-hidden="true">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M8 8h12v12H8z"/><path d="M4 16V4h12"/>
        </svg>
      </span>
      <span class="forward-card-title-text">{{ displayTitle(record.title) }}</span>
    </header>
    <div class="forward-card-items">
      <ForwardMessageItem v-for="(item, index) in record.items.slice(0, 4)" :key="index"
                          :item="item" :depth="0" :compact="true" @open-forward="openViewer" />
    </div>
    <button v-if="record.items.length" type="button" class="forward-card-toggle" @click="openViewer()">
      <span>点击查看</span>
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <polyline points="6 9 12 15 18 9"/>
      </svg>
    </button>
  </section>

  <Teleport v-if="viewerStack.length" to="body">
    <div class="forward-viewer-backdrop" @click.self="closeViewer">
      <section class="forward-viewer" role="dialog" aria-modal="true" :aria-label="viewerTitle">
        <header class="forward-viewer-head">
          <button v-if="viewerStack.length > 1" type="button" class="forward-viewer-back"
                  aria-label="返回上一层" title="返回上一层" @click="goBack">
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <span class="forward-viewer-title">{{ viewerTitle }}</span>
          <button type="button" class="forward-viewer-close" aria-label="关闭" title="关闭" @click="closeViewer">
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </header>
        <div class="forward-viewer-items">
          <ForwardMessageItem v-for="(item, index) in currentRecord.items" :key="index"
                              :item="item" :depth="viewerStack.length - 1" @open-forward="openViewer" />
        </div>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, ref } from 'vue'
import ForwardMessageItem from './ForwardMessageItem.vue'

const props = defineProps({
  record: { type: Object, required: true }
})

const viewerStack = ref([])
const currentRecord = computed(() => viewerStack.value[viewerStack.value.length - 1])
const viewerTitle = computed(() => displayTitle(currentRecord.value?.title))

function openViewer(item = props.record) {
  if (!viewerStack.value.length) viewerStack.value = [props.record]
  if (item !== props.record && item?.forward?.length) {
    viewerStack.value.push({ title: nestedTitle(item), items: item.forward })
  }
}

function nestedTitle(item) {
  const title = String(item?.content || '').trim()
  return /^\[[^\]\n]+的聊天记录\]$/.test(title) ? '聊天记录' : (title || '聊天记录')
}

function displayTitle(title) {
  const value = String(title || '').trim()
  return value || '聊天记录'
}

function goBack() {
  viewerStack.value.pop()
}

function closeViewer() {
  viewerStack.value = []
}
</script>
