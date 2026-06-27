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
      name: 'dashboard',
      component: () => import('./views/DashboardView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/c2c',
      name: 'c2c',
      component: () => import('./views/C2CView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/feedback',
      name: 'feedback',
      component: () => import('./views/FeedbackView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('./views/LoginView.vue'),
      meta: { guest: true }
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
