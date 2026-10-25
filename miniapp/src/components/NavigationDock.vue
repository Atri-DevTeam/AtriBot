<script setup lang="ts">
import {computed} from 'vue'
import profileIcon from '../assets/dock-profile.png'
import dataIcon from '../assets/dock-data.png'
import groupsIcon from '../assets/dock-groups.png'
import pricesIcon from '../assets/dock-prices.png'
import Icon from './Icon.vue'

type Page = 'profile' | 'data' | 'groups' | 'prices' | 'about'
const props = defineProps<{ active: Page; canUsePrices?: boolean }>()
const emit = defineEmits<{ select: [page: Page] }>()
const items = [
  {key: 'profile', label: '我的档案', icon: profileIcon},
  {key: 'data', label: '我的数据', icon: dataIcon},
  {key: 'groups', label: '群管理', icon: groupsIcon},
  {key: 'prices', label: '价格提醒', icon: pricesIcon},
  {key: 'about', label: '关于', icon: null}
] as const
const visibleItems = computed(() => items.filter(item => item.key !== 'prices' || props.canUsePrices))
const selectedIndex = computed(() => Math.max(0, visibleItems.value.findIndex(item => item.key === props.active)))
</script>

<template>
  <nav class="navigation-dock glass" :class="{ 'dock-expanded': visibleItems.length > 4 }" aria-label="栏目导航"
       :style="{ '--dock-count': visibleItems.length, '--dock-index': selectedIndex }">
    <span class="dock-selection" aria-hidden="true"></span>
    <button v-for="item in visibleItems" :key="item.key" type="button" class="dock-item"
            :class="{ active: active === item.key }" :aria-current="active === item.key ? 'page' : undefined"
            :aria-controls="`${item.key}-page`" @click="emit('select', item.key)">
      <img v-if="item.icon" :src="item.icon" alt="" width="16" height="16" draggable="false"/>
      <Icon v-else name="info"/>
      <span>{{ item.label }}</span>
    </button>
  </nav>
</template>

<style scoped>
.navigation-dock {
  position: fixed;
  z-index: 25;
  top: calc(64px + env(safe-area-inset-top));
  left: 50%;
  transform: translateX(-50%);
  display: grid;
  grid-template-columns: repeat(var(--dock-count), minmax(0, 1fr));
  width: min(1060px, calc(100% - 56px));
  padding: 0;
  background: var(--ui-page);
  border: 0;
  border-bottom: 1px solid var(--ui-border);
  box-shadow: 0 -1px var(--ui-page);
}
.dock-selection {
  position: absolute;
  left: 0;
  bottom: -1px;
  width: calc(100% / var(--dock-count));
  height: 2px;
  transform: translateX(calc(var(--dock-index) * 100%));
  background: var(--ui-text);
  transition: transform .22s cubic-bezier(.22, 1, .36, 1);
  pointer-events: none;
}
.dock-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 0;
  min-height: 46px;
  padding: 10px 8px;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
  transition: color .18s, background .18s;
}
.dock-item img, .dock-item svg { display: block; width: 18px; height: 18px; opacity: .65; flex-shrink: 0; }
.dock-item img { image-rendering: pixelated; }
.dock-item.active { color: var(--ui-text); font-weight: 500; }
.dock-item.active img, .dock-item.active svg { opacity: 1; }
.dock-item:focus-visible { outline-offset: -3px; }
@media (hover: hover) { .dock-item:hover { background: var(--ui-panel); color: var(--ui-text); } }
@media (max-width: 760px) {
  .navigation-dock { top: auto; bottom: 0; width: 100%; padding: 6px max(8px, env(safe-area-inset-right)) max(8px, env(safe-area-inset-bottom)) max(8px, env(safe-area-inset-left)); border: 0; border-top: 1px solid var(--ui-border); background: var(--ui-panel); box-shadow: none; }
  .dock-selection { display: none; }
  .dock-item { flex-direction: column; gap: 6px; min-height: 48px; padding: 5px 2px; font-size: 10px; border-radius: 6px; }
  .dock-item.active::before { content: ''; position: absolute; top: -7px; width: 20px; height: 2px; background: var(--ui-text); }
}
@media (prefers-reduced-motion: reduce) { .dock-selection, .dock-item { transition: none; } }
</style>
