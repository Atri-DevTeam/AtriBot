<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import Icon from './Icon.vue'

defineProps<{ src: string }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement | null>(null)
const stage = ref<HTMLElement | null>(null)
const picture = ref<HTMLImageElement | null>(null)
const zoom = ref(1)
const x = ref(0), y = ref(0)
const dragging = ref(false)
const transform = computed(() => `translate3d(${x.value}px,${y.value}px,0) scale(${zoom.value})`)
type Point = { x: number; y: number }
const pointers = new Map<number, Point>()
let observer: ResizeObserver | undefined
let previousOverflow = ''
let previousFocus: HTMLElement | null = null

function clampPan() {
  if (!stage.value || !picture.value) return
  const limitX = Math.max(0, (picture.value.clientWidth * zoom.value - stage.value.clientWidth) / 2)
  const limitY = Math.max(0, (picture.value.clientHeight * zoom.value - stage.value.clientHeight) / 2)
  x.value = Math.max(-limitX, Math.min(limitX, x.value))
  y.value = Math.max(-limitY, Math.min(limitY, y.value))
}
function reset() { zoom.value = 1; x.value = 0; y.value = 0 }
function changeZoom(value: number, point?: Point) {
  const next = Math.max(1, Math.min(6, value))
  const box = stage.value?.getBoundingClientRect()
  const anchorX = point && box ? point.x - box.left - box.width / 2 : 0
  const anchorY = point && box ? point.y - box.top - box.height / 2 : 0
  const ratio = next / zoom.value
  x.value = anchorX - (anchorX - x.value) * ratio
  y.value = anchorY - (anchorY - y.value) * ratio
  zoom.value = next
  clampPan()
}
function wheel(event: WheelEvent) {
  const delta = event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? 200 : 1)
  changeZoom(zoom.value * Math.exp(-Math.max(-300, Math.min(300, delta)) * .003), { x: event.clientX, y: event.clientY })
}
function down(event: PointerEvent) {
  if ((event.pointerType === 'mouse' && event.button !== 0) || pointers.size >= 2) return
  pointers.set(event.pointerId, { x: event.clientX, y: event.clientY })
  stage.value?.setPointerCapture(event.pointerId)
  dragging.value = true
}
const center = (points: Point[]): Point => ({ x: (points[0].x + points[1].x) / 2, y: (points[0].y + points[1].y) / 2 })
const distance = (points: Point[]) => Math.hypot(points[0].x - points[1].x, points[0].y - points[1].y)
function move(event: PointerEvent) {
  const previous = pointers.get(event.pointerId)
  if (!previous) return
  const before = [...pointers.values()]
  pointers.set(event.pointerId, { x: event.clientX, y: event.clientY })
  const after = [...pointers.values()]
  if (before.length === 2) {
    const from = center(before), to = center(after)
    if (distance(before) > 0) changeZoom(zoom.value * distance(after) / distance(before), from)
    x.value += to.x - from.x; y.value += to.y - from.y
  } else {
    x.value += event.clientX - previous.x; y.value += event.clientY - previous.y
  }
  clampPan()
}
function up(event: PointerEvent) {
  pointers.delete(event.pointerId)
  dragging.value = pointers.size > 0
}
function key(event: KeyboardEvent) {
  if (event.key === '+' || event.key === '=') changeZoom(zoom.value * 1.25)
  else if (event.key === '-') changeZoom(zoom.value / 1.25)
  else if (event.key === '0') reset()
  else if (event.key.startsWith('Arrow')) {
    if (event.key === 'ArrowLeft') x.value += 60
    if (event.key === 'ArrowRight') x.value -= 60
    if (event.key === 'ArrowUp') y.value += 60
    if (event.key === 'ArrowDown') y.value -= 60
    clampPan()
  } else return
  event.preventDefault()
}
onMounted(() => {
  previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  dialog.value?.showModal()
  observer = new ResizeObserver(clampPan)
  if (stage.value) observer.observe(stage.value)
})
onBeforeUnmount(() => {
  observer?.disconnect()
  pointers.clear()
  dialog.value?.close()
  document.body.style.overflow = previousOverflow
  if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true })
})
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="image-viewer" aria-label="物品卡背包大图" @cancel.prevent="emit('close')" @keydown="key">
      <header class="viewer-header"><span>物品卡背包</span><button class="viewer-button" aria-label="关闭大图" autofocus @click="emit('close')"><Icon name="close" /></button></header>
      <div ref="stage" class="viewer-stage" :class="{ dragging, zoomed: zoom > 1 }" @wheel.prevent="wheel"
        @pointerdown.prevent="down" @pointermove.prevent="move" @pointerup="up" @pointercancel="up" @lostpointercapture="up"
        @dblclick.prevent="changeZoom(zoom > 1 ? 1 : 2.5, { x: $event.clientX, y: $event.clientY })">
        <img ref="picture" :src="src" alt="物品卡背包完整图片" draggable="false" :style="{ transform }" @load="clampPan" />
      </div>
      <div class="viewer-controls glass" aria-label="图片缩放工具">
        <button class="viewer-button" aria-label="缩小图片" :disabled="zoom <= 1" @click="changeZoom(zoom / 1.25)"><Icon name="minus" /></button>
        <output aria-label="缩放比例">{{ Math.round(zoom * 100) }}%</output>
        <button class="viewer-button" aria-label="放大图片" :disabled="zoom >= 6" @click="changeZoom(zoom * 1.25)"><Icon name="plus" /></button>
        <button class="viewer-fit" @click="reset">适应屏幕</button>
      </div>
    </dialog>
  </Teleport>
</template>

<style scoped>
.image-viewer { position: fixed; inset: 0; margin: 0; padding: 0; width: 100%; height: 100vh; height: 100dvh; max-width: none; max-height: none; border: 0; background: #f4f5f0fa; color: #53634d; overflow: hidden; }
.image-viewer[open] { display: flex; flex-direction: column; }
.image-viewer::backdrop { background: #35433960; }
.viewer-header { display: flex; align-items: center; justify-content: space-between; padding: calc(10px + env(safe-area-inset-top)) max(20px,env(safe-area-inset-right)) 10px max(20px,env(safe-area-inset-left)); border-bottom: 1px solid #ffffffc0; flex-shrink: 0; font-size: 14px; }
.viewer-button { display: grid; place-items: center; width: 42px; height: 42px; border: 1px solid #fff; border-radius: 13px; background: #ffffff80; color: #73856a; }
.viewer-button:disabled { cursor: default; opacity: .35; }
.viewer-stage { flex: 1; min-height: 0; display: flex; align-items: center; justify-content: center; overflow: hidden; touch-action: none; user-select: none; overscroll-behavior: contain; }
.viewer-stage.zoomed { cursor: grab; }
.viewer-stage.dragging { cursor: grabbing; }
.viewer-stage img { flex-shrink: 0; width: auto; height: auto; max-width: calc(100% - 32px); max-height: calc(100% - 32px); object-fit: contain; transform-origin: center; user-select: none; -webkit-user-drag: none; pointer-events: none; }
.viewer-controls { display: flex; align-items: center; justify-content: center; gap: 9px; padding: 8px; margin: 10px auto calc(16px + env(safe-area-inset-bottom)); border-radius: 20px; flex-shrink: 0; }
.viewer-controls output { min-width: 49px; text-align: center; font-size: 12px; font-variant-numeric: tabular-nums; }
.viewer-fit { padding: 10px 12px; border: 0; border-left: 1px solid #c9d3c34d; background: transparent; color: #73856a; font-size: 12px; }
@media (max-width: 760px), (pointer: coarse) {
  /* Reserve space for QQ's overlaid navigation as well as the device safe area. */
  .viewer-header { padding-top: calc(74px + env(safe-area-inset-top)); }
}
</style>
