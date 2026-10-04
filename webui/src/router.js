import { createRouter, createWebHistory } from 'vue-router'

const LEGACY_TOKEN_KEY = 'officialWebuiToken'
const API_BASE = import.meta.env.VITE_API_BASE

async function verifySession() {
  try {
    const res = await fetch(`${API_BASE}/auth/verify`, {
      credentials: 'same-origin',
      cache: 'no-store'
    })
    return res.status === 200
  } catch {
    return false
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_BASE),
  routes: [
    {
      path: '/channels',
      name: 'channels',
      component: () => import('./features/channels/ChannelView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/',
      name: 'chat',
      component: () => import('./features/chat/ChatView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('./features/users/UserGroupListView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/minecraft-name-review',
      name: 'minecraftNameReview',
      component: () => import('./features/minecraft/MinecraftReviewView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/feedback',
      name: 'feedback',
      component: () => import('./features/feedback/FeedbackView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/gallery',
      name: 'gallery',
      component: () => import('./features/gallery/GalleryView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/loot',
      name: 'loot',
      component: () => import('./features/loot/LootView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/stats',
      name: 'stats',
      component: () => import('./features/stats/StatsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/bot-settings',
      name: 'botSettings',
      component: () => import('./features/settings/BotSettingsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/group-strategy',
      name: 'groupStrategy',
      component: () => import('./features/groups/GroupStrategyView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/group-moderation',
      name: 'groupModeration',
      component: () => import('./features/groups/GroupModerationView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/menu-panel',
      name: 'menuPanel',
      component: () => import('./features/menu/MenuPanelView.vue'),
      meta: { requiresAuth: true }
    },
    // ErrorReport 暂时停用：保留页面源码，旧地址由兜底路由重定向到首页。
    // 恢复时同步取消 AppSidebar.vue 中入口的注释。
    // {
    //   path: '/errors',
    //   name: 'errors',
    //   component: () => import('./features/logs/ErrorsView.vue'),
    //   meta: { requiresAuth: true }
    // },
    {
      path: '/send-logs',
      name: 'sendLogs',
      component: () => import('./features/logs/SendLogsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/event-logs',
      name: 'eventLogs',
      component: () => import('./features/logs/EventLogsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/napcat',
      name: 'napcat',
      component: () => import('./features/napcat/NapcatView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/debug',
      name: 'debug',
      component: () => import('./features/debug/ApiDebugView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('./features/auth/LoginView.vue'),
      meta: { guest: true }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

router.beforeEach(async (to) => {
  localStorage.removeItem(LEGACY_TOKEN_KEY)

  if (to.meta.guest) {
    const valid = await verifySession()
    if (valid) return '/'
    return true
  }

  if (to.meta.requiresAuth) {
    const valid = await verifySession()
    if (!valid) {
      return '/login'
    }
  }
})

export default router
export { LEGACY_TOKEN_KEY, API_BASE, verifySession }
