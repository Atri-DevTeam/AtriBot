<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref, watch} from 'vue'
import Icon from './Icon.vue'

const props = withDefaults(defineProps<{
  item: {id: string; key: string}
  load: (item: {id: string; key: string}) => Promise<string | null>
  size?: number
}>(), {size: 44})
const root = ref<HTMLElement>()
const url = ref<string | null>(null)
const loading = ref(false)
let observer: IntersectionObserver | undefined
let visible = false
let version = 0

async function load() {
  if (!visible) return
  const current = ++version
  url.value = null
  loading.value = true
  try {
    const result = await props.load(props.item)
    if (current === version) url.value = result
  } finally { if (current === version) loading.value = false }
}

watch(() => props.item.key, load)
onMounted(() => {
  if (!('IntersectionObserver' in window)) { visible = true; void load(); return }
  observer = new IntersectionObserver(entries => {
    if (!entries.some(entry => entry.isIntersecting)) return
    visible = true
    observer?.disconnect()
    void load()
  }, {rootMargin: '120px'})
  if (root.value) observer.observe(root.value)
})
onBeforeUnmount(() => { ++version; observer?.disconnect() })
</script>

<template>
  <span ref="root" class="price-item-icon" :class="{loading}" :style="{width: `${size}px`, height: `${size}px`}" aria-hidden="true">
    <img v-if="url" :src="url" alt="" :width="size" :height="size" decoding="async"/>
    <Icon v-else name="box"/>
  </span>
</template>

<style scoped>
.price-item-icon {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 12px;
  background: var(--ui-raised);
  color: var(--price-subtle, var(--ui-muted));
}
.price-item-icon img { display: block; width: 100%; height: 100%; object-fit: contain; }
.price-item-icon svg { width: 21px; height: 21px; opacity: .65; }
.price-item-icon.loading svg { animation: icon-pulse 1.2s ease-in-out infinite alternate; }
@keyframes icon-pulse { to { opacity: .25; } }
@media (prefers-reduced-motion: reduce) { .price-item-icon.loading svg { animation: none; } }
</style>
