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
      path: '/',
      name: 'chat',
      component: () => import('./views/ChatView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('./views/UserGroupListView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/minecraft-name-review',
      name: 'minecraftNameReview',
      component: () => import('./views/MinecraftReviewView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/feedback',
      name: 'feedback',
      component: () => import('./views/FeedbackView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/gallery',
      name: 'gallery',
      component: () => import('./views/GalleryView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/loot',
      name: 'loot',
      component: () => import('./views/LootView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/stats',
      name: 'stats',
      component: () => import('./views/StatsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/bot-settings',
      name: 'botSettings',
      component: () => import('./views/BotSettingsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/group-strategy',
      name: 'groupStrategy',
      component: () => import('./views/GroupStrategyView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/group-moderation',
      name: 'groupModeration',
      component: () => import('./views/GroupModerationView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/menu-panel',
      name: 'menuPanel',
      component: () => import('./views/MenuPanelView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/errors',
      name: 'errors',
      component: () => import('./views/ErrorsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/send-logs',
      name: 'sendLogs',
      component: () => import('./views/SendLogsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/event-logs',
      name: 'eventLogs',
      component: () => import('./views/EventLogsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/napcat',
      name: 'napcat',
      component: () => import('./views/NapcatView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/debug',
      name: 'debug',
      component: () => import('./views/ApiDebugView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('./views/LoginView.vue'),
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
