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
      <button class="side-nav-item" :class="{ active: route.path === '/' }" title="聊天" @click="go('/')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 3c5 0 9 3.4 9 7.6 0 4.2-4 7.6-9 7.6-.9 0-1.8-.1-2.6-.3L5 20.5l.9-3.2C4.1 15.9 3 13.9 3 10.6 3 6.4 7 3 12 3Z" />
          <circle cx="8.5" cy="10.6" r="0.9" fill="currentColor" stroke="none" />
          <circle cx="12" cy="10.6" r="0.9" fill="currentColor" stroke="none" />
          <circle cx="15.5" cy="10.6" r="0.9" fill="currentColor" stroke="none" />
        </svg>
        <span class="side-nav-label">聊天</span>
      </button>
      <button class="side-nav-item" :class="{ active: route.path === '/groups' }" @click="go('/groups')">
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
      <button class="side-nav-item" :class="{ active: route.path === '/gallery' }" @click="go('/gallery')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
          <circle cx="8.5" cy="8.5" r="1.5" />
          <polyline points="21 15 16 10 5 21" />
        </svg>
        图源管理
        <span v-if="galleryBadge > 0" class="feedback-nav-badge">{{ galleryBadge }}</span>
      </button>
      <button class="side-nav-item" :class="{ active: route.path === '/loot' }" @click="go('/loot')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="8" width="18" height="4" rx="1" />
          <path d="M12 8v13" />
          <path d="M19 12v7a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-7" />
          <path d="M7.5 8a2.5 2.5 0 0 1 0-5C11 3 12 8 12 8" />
          <path d="M16.5 8a2.5 2.5 0 0 0 0-5C13 3 12 8 12 8" />
        </svg>
        抽卡管理
      </button>
      <button class="side-nav-item" :class="{ active: route.path === '/stats' }" @click="go('/stats')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="18" y1="20" x2="18" y2="10" />
          <line x1="12" y1="20" x2="12" y2="4" />
          <line x1="6" y1="20" x2="6" y2="14" />
        </svg>
        统计数据
      </button>
      <button class="side-nav-item" :class="{ active: route.path === '/function-settings' }" @click="go('/function-settings')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M4 7h10" />
          <path d="M4 17h16" />
          <circle cx="17" cy="7" r="2.5" />
          <circle cx="8" cy="17" r="2.5" />
        </svg>
        功能设置
      </button>
      <button class="side-nav-item" :class="{ active: route.path === '/errors' }" @click="go('/errors')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
          <line x1="12" y1="9" x2="12" y2="13" />
          <line x1="12" y1="17" x2="12.01" y2="17" />
        </svg>
        错误报告
      </button>
      <button class="side-nav-item" :class="{ active: route.path === '/send-logs' }" @click="go('/send-logs')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M4 5.5h9" />
          <path d="M4 12h7" />
          <path d="M4 18.5h9" />
          <path d="m15 8 5 4-5 4" />
          <path d="M11 12h9" />
        </svg>
        发送日志
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

    <!-- 桌面端：收窄/展开导航栏（手机端隐藏，手机端用抽屉模式） -->
    <button class="nav-collapse-btn" :title="collapsed ? '展开导航栏' : '收窄导航栏'"
            :aria-label="collapsed ? '展开导航栏' : '收窄导航栏'" @click="toggleCollapsed">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <polyline v-if="collapsed" points="9 6 15 12 9 18" />
        <polyline v-else points="15 6 9 12 15 18" />
      </svg>
      <span class="side-nav-label">{{ collapsed ? '' : '收起' }}</span>
    </button>
  </aside>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppBrand from './AppBrand.vue'

const NAV_COLLAPSED_KEY = 'atri.webui.nav_collapsed'
const collapsed = ref(false)

onMounted(() => {
  try {
    collapsed.value = localStorage.getItem(NAV_COLLAPSED_KEY) === '1'
  } catch { /* ignore */ }
  applyCollapsed()
})

function applyCollapsed() {
  document.documentElement.classList.toggle('nav-collapsed', collapsed.value)
}

function toggleCollapsed() {
  collapsed.value = !collapsed.value
  try {
    localStorage.setItem(NAV_COLLAPSED_KEY, collapsed.value ? '1' : '0')
  } catch { /* ignore */ }
  applyCollapsed()
}

/** 供外部「重置布局」调用：导航栏收窄状态归位，键名不外泄 */
function resetCollapsed() {
  collapsed.value = false
  try {
    localStorage.removeItem(NAV_COLLAPSED_KEY)
  } catch { /* ignore */ }
  applyCollapsed()
}

defineExpose({ resetCollapsed })

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
  },
  galleryBadge: {
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
