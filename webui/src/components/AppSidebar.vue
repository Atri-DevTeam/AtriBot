<template>
  <div v-if="open" class="sidebar-backdrop show" @click="close" />

  <aside class="sidebar" :class="{ 'sidebar--open': open }">
    <div class="sidebar-head">
      <button class="sidebar-close" aria-label="关闭侧边栏" @click="close">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </button>
    </div>

    <AppBrand :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName" />

    <nav class="side-nav">
      <button class="side-nav-item" :class="{ active: route.path === '/' }" @click="go('/')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>
        群聊
      </button>
      <button class="side-nav-item" :class="{ active: route.path === '/c2c' }" @click="go('/c2c')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
          <circle cx="12" cy="7" r="4" />
        </svg>
        私聊
      </button>
      <button class="side-nav-item" :class="{ active: route.path === '/users' }" @click="go('/users')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </svg>
        用户列表
      </button>
      <button class="side-nav-item" :class="{ active: route.path === '/feedback' }" @click="go('/feedback')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
        </svg>
        反馈管理
        <span v-if="feedbackBadge > 0" class="feedback-nav-badge">{{ feedbackBadge }}</span>
      </button>
      <button class="side-nav-item" :class="{ active: route.path === '/napcat' }" @click="go('/napcat')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="4" width="18" height="14" rx="3" />
          <path d="M8 20h8" />
          <path d="M12 18v2" />
        </svg>
        Napcat功能
      </button>
      <button class="side-nav-item" :class="{ active: route.path === '/debug' }" @click="go('/debug')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="4 17 10 11 4 5" />
          <line x1="12" y1="19" x2="20" y2="19" />
        </svg>
        调试
      </button>
    </nav>

    <div class="side-toolbar">
      <slot name="toolbar" />
    </div>
  </aside>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import AppBrand from './AppBrand.vue'

defineProps({
  open: {
    type: Boolean,
    default: false
  },
  appId: {
    type: String,
    default: ''
  },
  botOpenId: {
    type: String,
    default: ''
  },
  botName: {
    type: String,
    default: 'AtriBot'
  },
  feedbackBadge: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['update:open'])
const route = useRoute()
const router = useRouter()

function close() {
  emit('update:open', false)
}

function go(path) {
  router.push(path)
  close()
}
</script>