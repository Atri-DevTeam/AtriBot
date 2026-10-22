<script setup>
import {onBeforeUnmount, onMounted, reactive, ref} from 'vue'
import {useLatestRequest} from '../../../shared/lib/latestRequest.js'
import {formatTime} from '../../../shared/lib/time.js'

const props = defineProps({api: {type: Function, required: true}, density: {type: String, default: 'comfy'}})
const emit = defineEmits(['busy'])
const requests = useLatestRequest()
const items = ref([])
const total = ref(0)
const loading = ref(false)
const busy = ref('')
const error = ref('')
const notice = ref('')
const nextCursor = ref('')
const cursors = ref([''])
const page = ref(0)
const fileInput = ref(null)
const failed = reactive(new Set())
const preview = ref(null)
let disposed = false

function previewUrl(value) {
  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : ''
  } catch { return '' }
}

function displayTime(seconds) {
  return seconds > 0 ? formatTime(seconds * 1000) : '—'
}

async function load(cursor = '', index = 0) {
  const request = requests.begin()
  loading.value = true
  error.value = ''
  try {
    const data = await props.api(`/material-images${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`, {signal: request.signal})
    if (!request.isCurrent()) return
    items.value = data.images
    total.value = data.total
    nextCursor.value = data.nextCursor || ''
    cursors.value = [...cursors.value.slice(0, index), cursor]
    page.value = index
    failed.clear()
  } catch (failure) {
    if (request.isCurrent()) error.value = failure.message
  } finally {
    if (request.isCurrent()) loading.value = false
  }
}

function setBusy(value) {
  busy.value = value
  emit('busy', !!value)
}

async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || busy.value) return
  error.value = ''
  notice.value = ''
  if (file.size < 1 || file.size > 10 * 1024 * 1024) {
    error.value = '图片不能为空，且不能超过 10MB'
    return
  }
  if (!/\.(png|jpe?g|gif|webp)$/i.test(file.name)) {
    error.value = '仅支持 PNG、JPEG、GIF、WebP 图片'
    return
  }
  const data = new FormData()
  data.append('image', file)
  setBusy('upload')
  try {
    await props.api('/material-images', {method: 'POST', body: data})
    if (disposed) return
    notice.value = '图片已上传'
    await load()
  } catch (failure) {
    if (!disposed) error.value = failure.message
  } finally {
    if (!disposed) setBusy('')
  }
}

async function remove(item) {
  if (busy.value || !confirm(`删除素材「${item.fileName || item.imageId}」？删除后不能再被新消息引用，已发送消息不受影响。`)) return
  setBusy(item.imageId)
  error.value = ''
  notice.value = ''
  try {
    await props.api(`/material-images/${encodeURIComponent(item.imageId)}`, {method: 'DELETE'})
    if (disposed) return
    if (preview.value?.imageId === item.imageId) preview.value = null
    notice.value = '素材已删除'
    await load()
  } catch (failure) {
    if (!disposed) error.value = failure.message
  } finally {
    if (!disposed) setBusy('')
  }
}

async function copy(item, markdown = false) {
  const text = markdown ? `![图片](${item.url})` : item.url
  try {
    try { await navigator.clipboard.writeText(text) }
    catch {
      const input = document.createElement('textarea')
      input.value = text
      input.readOnly = true
      input.style.cssText = 'position:fixed;left:-9999px;opacity:0'
      document.body.append(input)
      try {
        input.select()
        if (!document.execCommand('copy')) throw new Error('复制失败')
      } finally { input.remove() }
    }
    if (!disposed) notice.value = markdown ? '已复制 Markdown' : '已复制素材引用'
  } catch {
    if (!disposed) error.value = '复制失败，请从下方输入框手动复制'
  }
}

function onKey(event) { if (event.key === 'Escape') preview.value = null }
onMounted(() => {
  void load()
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  disposed = true
  window.removeEventListener('keydown', onKey)
})
</script>

<template>
  <section class="content material-images-panel">
    <section class="chat-panel material-panel">
      <div class="chat-head material-head">
        <strong>Markdown 图片素材</strong>
        <span class="material-quota">{{ total }} / 100 张</span>
        <div class="material-toolbar">
          <button class="ghost-button" :disabled="loading || !!busy" @click="load()">刷新</button>
          <button class="primary-button" :disabled="loading || !!busy || total >= 100" @click="fileInput?.click()">
            {{ busy === 'upload' ? '上传中…' : '上传图片' }}
          </button>
          <input ref="fileInput" class="material-file-input" type="file" accept=".png,.jpg,.jpeg,.gif,.webp" aria-label="选择素材图片" @change="upload"/>
        </div>
      </div>
      <div class="material-body">
        <p class="material-help">支持 PNG、JPEG、GIF、WebP，单张不超过 10MB。每次成功引用后有效期顺延至 90 天。</p>
        <p v-if="error" class="material-message material-error" role="alert">{{ error }}</p>
        <p v-if="notice" class="material-message" role="status">{{ notice }}</p>
        <div v-if="loading && !items.length" class="empty-state">正在读取素材…</div>
        <div v-else-if="!items.length && !error" class="empty-state">暂无图片素材</div>
        <div v-else class="gallery-grid" :class="`gallery-grid--${density}`" :aria-busy="loading">
          <figure v-for="item in items" :key="item.imageId" class="gallery-card material-card">
            <button class="gallery-thumb material-thumb" :disabled="!previewUrl(item.previewUrl)" :aria-label="`预览 ${item.fileName || item.imageId}`" @click="preview = item">
              <img v-if="previewUrl(item.previewUrl) && !failed.has(item.imageId)" :src="previewUrl(item.previewUrl)"
                   :alt="item.fileName" loading="lazy" decoding="async" referrerpolicy="no-referrer" @error="failed.add(item.imageId)"/>
              <span v-else class="gallery-broken">预览不可用，请刷新列表</span>
            </button>
            <figcaption class="material-meta">
              <strong class="material-name" :title="item.fileName">{{ item.fileName || '未命名图片' }}</strong>
              <span class="material-id">{{ item.imageId }}</span>
              <dl class="material-times">
                <div><dt>上传时间</dt><dd>{{ displayTime(item.createdAt) }}</dd></div>
                <div><dt>当前有效期</dt><dd>{{ displayTime(item.expiresAt) }}</dd></div>
              </dl>
              <input class="material-reference" aria-label="素材引用" :value="item.url" readonly @focus="$event.target.select()"/>
              <div class="material-actions">
                <button class="ghost-button" @click="copy(item)">复制引用</button>
                <button class="ghost-button" @click="copy(item, true)">复制 Markdown</button>
                <button class="ghost-button danger" :disabled="!!busy || loading" @click="remove(item)">{{ busy === item.imageId ? '删除中…' : '删除' }}</button>
              </div>
            </figcaption>
          </figure>
        </div>
        <div v-if="page > 0 || nextCursor" class="feedback-pagination">
          <button class="ghost-button" :disabled="page === 0 || loading || !!busy" @click="load(cursors[page - 1], page - 1)">上一页</button>
          <span>第 {{ page + 1 }} 页</span>
          <button class="ghost-button" :disabled="!nextCursor || loading || !!busy" @click="load(nextCursor, page + 1)">下一页</button>
        </div>
      </div>
    </section>
    <div v-if="preview" class="material-viewer" role="dialog" aria-modal="true" aria-label="素材预览" @click.self="preview = null">
      <button class="material-viewer-close" aria-label="关闭素材预览" @click="preview = null">×</button>
      <img :src="previewUrl(preview.previewUrl)" :alt="preview.fileName" referrerpolicy="no-referrer"/>
    </div>
  </section>
</template>

<style scoped>
.material-images-panel { min-height: 0; grid-template-columns: minmax(0, 1fr); padding-top: 0; }
.material-panel { width: 100%; min-width: 0; grid-template-rows: auto minmax(0, 1fr); }
.material-head { gap: 12px; flex-wrap: wrap; }
.material-quota { color: var(--color-text-muted); font-size: 12px; }
.material-toolbar { display: flex; gap: 8px; margin-left: auto; }
.material-file-input { display: none; }
.material-body { padding: 16px; overflow: auto; min-height: 0; }
.material-body .gallery-grid { padding: 0; }
.material-help { color: var(--color-text-muted); font-size: 12px; line-height: 1.7; margin: 0 0 16px; }
.material-message { margin: 0 0 14px; padding: 10px 12px; border-radius: 8px; background: var(--color-surface-muted, #f3f5f8); font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
.material-error { color: #c43f4c; background: #fff2f3; }
.material-card { margin: 0 0 12px; }
.material-thumb { width: 100%; aspect-ratio: 16 / 9; min-height: 0; border: 0; padding: 0; cursor: zoom-in; display: block; text-align: center; }
.material-thumb img { height: 100%; object-fit: contain; }
.material-thumb:disabled { cursor: default; }
.material-meta { display: grid; gap: 8px; padding: 14px; min-width: 0; }
.material-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.material-id { color: var(--color-text-muted); font: 11px ui-monospace, monospace; overflow-wrap: anywhere; }
.material-times { margin: 2px 0; display: grid; gap: 5px; font-size: 12px; }
.material-times div { display: flex; flex-wrap: wrap; gap: 6px 12px; }
.material-times dt { color: var(--color-text-muted); }
.material-times dd { margin: 0; }
.material-reference { width: 100%; min-width: 0; box-sizing: border-box; border: 1px solid var(--color-border); border-radius: 7px; background: var(--color-surface-muted, #f5f7fa); color: var(--color-text-muted); font: 11px ui-monospace, monospace; padding: 8px; }
.material-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.material-actions button { font-size: 11px; padding: 6px 8px; }
.material-actions .danger { margin-left: auto; }
.material-viewer { position: fixed; inset: 0; z-index: 1200; background: rgba(10, 15, 25, .88); display: flex; align-items: center; justify-content: center; padding: 48px 20px 24px; }
.material-viewer img { max-width: 100%; max-height: 100%; object-fit: contain; }
.material-viewer-close { position: absolute; right: 20px; top: 12px; background: transparent; border: 0; color: #fff; font-size: 32px; cursor: pointer; }
@media (max-width: 600px) {
  .material-body { padding: 12px; }
  .material-body .gallery-grid { column-count: 1; column-width: auto; }
  .material-head { gap: 8px; }
  .material-head strong { font-size: 13px; }
  .material-toolbar { gap: 5px; }
  .material-toolbar button { padding: 7px 9px; font-size: 12px; }
  .material-meta { padding: 10px; }
}
</style>
