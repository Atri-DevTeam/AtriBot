<script setup lang="ts">
import profileIcon from '../assets/dock-profile.png'
import groupsIcon from '../assets/dock-groups.png'

defineProps<{ active: 'profile' | 'groups' }>()
const emit = defineEmits<{ select: [page: 'profile' | 'groups'] }>()
// Pixel icons reused from atriwebsite/src/lib/mcMobileDockIcons.js.
const items = [
  { key: 'groups', label: '群管理', icon: groupsIcon },
  { key: 'profile', label: '我的档案', icon: profileIcon }
] as const
</script>

<template>
  <nav class="navigation-dock glass" aria-label="栏目导航" :class="{ 'profile-active': active === 'profile' }">
    <span class="dock-selection" aria-hidden="true"></span>
    <button v-for="item in items" :key="item.key" type="button" class="dock-item"
      :class="{ active: active === item.key }" :aria-current="active === item.key ? 'page' : undefined"
      :aria-controls="item.key === 'profile' ? 'profile-page' : 'groups-page'" @click="emit('select', item.key)">
      <img :src="item.icon" alt="" width="16" height="16" draggable="false" />
      <span>{{ item.label }}</span>
    </button>
  </nav>
</template>

<style scoped>
.navigation-dock { position: fixed; z-index: 25; left: 50%; bottom: max(10px,env(safe-area-inset-bottom)); transform: translateX(-50%); display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); width: min(224px,calc(100% - 40px)); padding: 3px; border-radius: 22px; background: #f4f5f0c9; box-shadow: 0 6px 20px #35433912, 0 2px 5px #35433905, inset 0 1px 0 #ffffff; -webkit-backdrop-filter: blur(28px) saturate(1.25); backdrop-filter: blur(28px) saturate(1.25); animation: dock-arrive .55s cubic-bezier(.22,1,.36,1) both; }
.dock-selection { position: absolute; left: 3px; top: 3px; bottom: 3px; width: calc(50% - 3px); border-radius: 18px; background: #ffffffa8; border: 1px solid #fff; box-shadow: 0 3px 10px #44573c07; transition: transform .36s cubic-bezier(.22,1,.36,1); pointer-events: none; }
.profile-active .dock-selection { transform: translateX(100%); }
.dock-item { position: relative; display: flex; flex-direction: row; align-items: center; justify-content: center; gap: 6px; min-height: 36px; padding: 4px 10px; border: 0; border-radius: 18px; background: transparent; color: #8b9683; font-size: 11px; line-height: 1.4; letter-spacing: .5px; transition: color .2s; }
.dock-item img { display: block; width: 16px; height: 16px; image-rendering: pixelated; opacity: .62; transform: translateY(0) scale(.92); transition: transform .28s cubic-bezier(.22,1,.36,1), opacity .2s; }
.dock-item.active { color: #53634d; font-weight: 600; }
.dock-item.active img { opacity: 1; transform: translateY(-1px) scale(1); }
.dock-item:active img { transform: scale(.88); }
.dock-item:focus-visible { outline-offset: -2px; }
@media (hover: hover) { .dock-item:hover img { opacity: 1; transform: translateY(-3px) scale(1.08); } }
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) { .navigation-dock { background: #f4f5f0f5; } }
@keyframes dock-arrive { from { opacity: 0; transform: translate(-50%,16px); } to { opacity: 1; transform: translate(-50%,0); } }
@media (prefers-reduced-motion: reduce) { .navigation-dock, .dock-selection, .dock-item, .dock-item img { animation: none; transition: none; } }
</style>
