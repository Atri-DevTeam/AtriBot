<script setup lang="ts">
defineProps<{ name: string }>()
const paths: Record<string, string> = {
  'shield-check': 'M12 3 4 6v6c0 5 8 9 8 9s8-4 8-9V6ZM8.5 12l2.5 2.5 4.5-5',
  tag: 'M3 3h8l10 10-8 8L3 11ZM7 7h.01',
  broadcast: 'M4 10h4l11-5v14L8 14H4ZM8 14l2 6h3l-2-5M22 9v6',
  expand: 'M8 3H3v5M16 3h5v5M21 16v5h-5M8 21H3v-5',
  close: 'M6 6l12 12M18 6 6 18',
  plus: 'M12 5v14M5 12h14',
  minus: 'M5 12h14',
  settings: 'M4 7h16M4 17h16M8 4v6M16 14v6',
  external: 'M14 3h7v7M21 3l-11 11M10 3H3v18h18v-7',
  gamepad: 'M7 7h10a4 4 0 0 1 4 3l1 7a2 2 0 0 1-3 2l-4-3H9l-4 3a2 2 0 0 1-3-2l1-7a4 4 0 0 1 4-3ZM7 10v5M4.5 12.5h5M16 11h.01M19 14h.01',
  timer: 'M9 2h6M12 5v2M18 5l2 2M12 10v5l3 2M20 15a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
  trophy: 'M7 3h10v6a5 5 0 0 1-10 0ZM7 5H3v3a4 4 0 0 0 4 4M17 5h4v3a4 4 0 0 1-4 4M12 14v6M7 21h10',
  mine: 'M17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0ZM12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2',
  grid: 'M3 3h18v18H3ZM9 3v18M15 3v18M3 9h18M3 15h18',
  wheel: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 3v6M12 15v6M3 12h6M15 12h6M6 6l4 4M14 14l4 4M6 18l4-4M14 10l4-4',
  scissors: 'M8 8a3 3 0 1 1-6 0 3 3 0 0 1 6 0ZM8 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0ZM8 9l13 12M8 17 21 3',
  sound: 'M4 10v4M8 6v12M12 3v18M16 6v12M20 10v4',
  ticket: 'M3 5h18v5a2 2 0 0 0 0 4v5H3v-5a2 2 0 0 0 0-4ZM15 5v3M15 11v2M15 16v3',
  box: 'm12 3 9 5v9l-9 5-9-5V8ZM3 8l9 5 9-5M12 13v9M7.5 5.5l9 5',
  chevron: 'm9 5 7 7-7 7',
  left: 'm15 5-7 7 7 7',
  'arrow-down': 'M12 4v16M6 14l6 6 6-6',
  bot: 'M12 7a8 8 0 0 1 8 8v2.4a2.6 2.6 0 0 1-2.6 2.6H6.6A2.6 2.6 0 0 1 4 17.4V15a8 8 0 0 1 8-8ZM12 4.4V7M9.4 12.2v2.4M14.6 12.2v2.4',
  lock: 'M7 10V7a5 5 0 0 1 10 0v3M5 10h14v11H5zM12 14v3',
  user: 'M19 21v-2a7 7 0 0 0-14 0v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8',
  users: 'M16 21v-2a6 6 0 0 0-12 0v2M10 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M17 4a4 4 0 0 1 0 7M20 21v-2a6 6 0 0 0-3-5',
  message: 'M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8v.5ZM8 11h8M8 15h4',
  calendar: 'M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2ZM8 16l3 3 5-5',
  coin: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 6v12M15 8h-4a2 2 0 0 0 0 4h2a2 2 0 0 1 0 4H9',
  collection: 'M7 7h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2ZM3 16V5a2 2 0 0 1 2-2h11M13 11l1 2.5 2.5 1-2.5 1-1 2.5-1-2.5-2.5-1 2.5-1Z',
  copy: 'M9 9h11v12H9zM15 5V3H3v12h2',
  exit: 'M9 21H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5M15 7l5 5-5 5M8 12h12',
  refresh: 'M20 7v5h-5M4 17v-5h5M6.1 6.1a8 8 0 0 1 12.3 1L20 12M4 12l1.6 4.9a8 8 0 0 0 12.3 1',
  spark: 'm12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z',
  orbit: 'M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0ZM21 3C16-2-2 16 3 21S26 8 21 3ZM5 4l-1-1M21 20l-1-1'
}
</script>
<template>
  <svg viewBox="0 0 24 24" fill="none" :stroke="name === 'bot' ? '#90969D' : 'currentColor'" :stroke-width="name === 'bot' ? 2 : 1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path :d="paths[name] || paths.spark" />
    <circle v-if="name === 'bot'" cx="12" cy="3.3" r="1.5" fill="#90969D" stroke="none" />
  </svg>
</template>
