<template>
  <ForwardRecordPreview :record="record" @open="openViewer()" />

  <Teleport v-if="viewerStack.length" to="body">
    <div class="forward-viewer-backdrop" @click.self="closeViewer">
      <section ref="viewerEl" class="forward-viewer" role="dialog" aria-modal="true"
               :aria-label="viewerTitle" tabindex="-1" @keydown="onViewerKeydown">
        <header class="forward-viewer-head">
          <div class="forward-viewer-nav">
            <button v-if="viewerStack.length > 1" type="button" class="forward-viewer-back"
                    aria-label="返回上一层" title="返回上一层" @click="goBack">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="m14 6-6 6 6 6" />
              </svg>
            </button>
          </div>
          <div class="forward-viewer-heading">
            <span class="forward-viewer-title">{{ viewerTitle }}</span>
            <span class="forward-viewer-count">{{ currentRecord.items.length }} 条消息</span>
          </div>
          <button type="button" class="forward-viewer-close" aria-label="关闭" title="关闭" @click="closeViewer">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
              <path d="m6 6 12 12M18 6 6 18" />
            </svg>
          </button>
        </header>
        <div ref="itemsEl" class="forward-viewer-items">
          <ForwardMessageItem v-for="(item, index) in currentRecord.items" :key="index"
                              :item="item" @open-forward="openViewer" />
          <p v-if="!currentRecord.items.length" class="forward-viewer-empty">暂无消息内容</p>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { forwardTitle } from '../lib/forward.js'
import ForwardMessageItem from './ForwardMessageItem.vue'
import ForwardRecordPreview from './ForwardRecordPreview.vue'

const props = defineProps({
  record: { type: Object, required: true }
})

const viewerStack = ref([])
const viewerEl = ref(null)
const itemsEl = ref(null)
let opener = null
const currentRecord = computed(() => viewerStack.value.at(-1)?.record)
const viewerTitle = computed(() => forwardTitle(currentRecord.value?.title))

async function openViewer(item) {
  if (!viewerStack.value.length) {
    opener = document.activeElement
    viewerStack.value = [{ record: props.record, scrollTop: 0 }]
  }
  if (item?.forward?.length) {
    const parent = viewerStack.value.at(-1)
    parent.scrollTop = itemsEl.value?.scrollTop || 0
    parent.trigger = document.activeElement
    viewerStack.value.push({ record: { title: item.content, items: item.forward }, scrollTop: 0 })
  }
  await nextTick()
  if (itemsEl.value) itemsEl.value.scrollTop = 0
  viewerEl.value?.focus()
}

async function goBack() {
  if (viewerStack.value.length < 2) return
  viewerStack.value.pop()
  await nextTick()
  const parent = viewerStack.value.at(-1)
  if (!parent) return
  if (itemsEl.value) itemsEl.value.scrollTop = parent.scrollTop
  if (parent.trigger?.isConnected) parent.trigger.focus()
  else viewerEl.value?.focus()
}

function closeViewer() {
  viewerStack.value = []
  if (opener?.isConnected) opener.focus()
  opener = null
}

function onViewerKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    closeViewer()
    return
  }
  if (event.key !== 'Tab') return
  const controls = [...viewerEl.value.querySelectorAll('button, a[href], video[controls], audio[controls], [tabindex="0"]')]
  const first = controls[0]
  const last = controls.at(-1)
  if (event.shiftKey && (document.activeElement === first || document.activeElement === viewerEl.value)) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === viewerEl.value)) {
    event.preventDefault()
    first?.focus()
  }
}

onBeforeUnmount(() => {
  if (viewerStack.value.length && opener?.isConnected) opener.focus()
})
</script>
