import { createRouter, createWebHistory } from 'vue-router'

const TOKEN_KEY = 'officialWebuiToken'
const API_BASE = import.meta.env.VITE_API_BASE

async function verifyToken(token) {
  try {
    const res = await fetch(`${API_BASE}/auth/verify`, {
      headers: { Authorization: `Bearer ${token}` }
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
      path: '/login',
      name: 'login',
      component: () => import('./views/LoginView.vue'),
      meta: { guest: true }
    }
  ]
})

router.beforeEach(async (to) => {
  const token = localStorage.getItem(TOKEN_KEY)

  if (to.meta.guest && token) {
    const valid = await verifyToken(token)
    if (valid) return '/'
    localStorage.removeItem(TOKEN_KEY)
    return true
  }

  if (to.meta.requiresAuth) {
    if (!token) return '/login'
    const valid = await verifyToken(token)
    if (!valid) {
      localStorage.removeItem(TOKEN_KEY)
      return '/login'
    }
  }
})

export default router
export { TOKEN_KEY, API_BASE }
