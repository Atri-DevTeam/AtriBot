import { createRouter, createWebHistory } from 'vue-router'
import {WEBUI_API_BASE, verifyWebuiSession} from './shared/lib/webuiSession.js'
import {logoutWebui} from './shared/lib/webuiApi.js'
import {isQqPage, withQqMode} from './shared/lib/qqEnvironment.js'

const LEGACY_TOKEN_KEY = 'officialWebuiToken'
const API_BASE = WEBUI_API_BASE
const verifySession = verifyWebuiSession

const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_BASE),
  routes: [
    {
      path: '/channels',
      name: 'channels',
      component: () => import('./features/channels/ChannelView.vue'),
      meta: { requiresAuth: true, title: '频道' }
    },
    {
      path: '/',
      name: 'chat',
      component: () => import('./features/chat/ChatView.vue'),
      meta: { requiresAuth: true, title: '聊天' }
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('./features/users/UserGroupListView.vue'),
      meta: { requiresAuth: true, title: '用户数据' }
    },
    {
      path: '/minecraft-name-review',
      name: 'minecraftNameReview',
      component: () => import('./features/minecraft/MinecraftReviewView.vue'),
      meta: { requiresAuth: true, title: '玩家名审核' }
    },
    {
      path: '/feedback',
      name: 'feedback',
      component: () => import('./features/feedback/FeedbackView.vue'),
      meta: { requiresAuth: true, title: '工单管理' }
    },
    {
      path: '/gallery',
      name: 'gallery',
      component: () => import('./features/gallery/GalleryView.vue'),
      meta: { requiresAuth: true, title: '图片审核' }
    },
    {
      path: '/loot',
      name: 'loot',
      component: () => import('./features/loot/LootView.vue'),
      meta: { requiresAuth: true, title: '抽卡管理' }
    },
    {
      path: '/stats',
      name: 'stats',
      component: () => import('./features/stats/StatsView.vue'),
      meta: { requiresAuth: true, title: '统计数据' }
    },
    {
      path: '/bot-settings',
      name: 'botSettings',
      component: () => import('./features/settings/BotSettingsView.vue'),
      meta: { requiresAuth: true, title: '机器人设置' }
    },
    {
      path: '/group-strategy',
      name: 'groupStrategy',
      component: () => import('./features/groups/GroupStrategyView.vue'),
      meta: { requiresAuth: true, title: '加群策略' }
    },
    {
      path: '/group-moderation',
      name: 'groupModeration',
      component: () => import('./features/groups/GroupModerationView.vue'),
      meta: { requiresAuth: true, title: '群管系统' }
    },
    {
      path: '/menu-panel',
      name: 'menuPanel',
      component: () => import('./features/menu/MenuPanelView.vue'),
      meta: { requiresAuth: true, title: '菜单与面板' }
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
      meta: { requiresAuth: true, title: '发送日志' }
    },
    {
      path: '/event-logs',
      name: 'eventLogs',
      component: () => import('./features/logs/EventLogsView.vue'),
      meta: { requiresAuth: true, title: '事件记录' }
    },
    {
      path: '/napcat',
      name: 'napcat',
      component: () => import('./features/napcat/NapcatView.vue'),
      meta: { requiresAuth: true, title: 'Napcat功能' }
    },
    {
      path: '/debug',
      name: 'debug',
      component: () => import('./features/debug/ApiDebugView.vue'),
      meta: { requiresAuth: true, title: '调试' }
    },
    {
      path: '/logout',
      name: 'logout',
      component: () => import('./features/auth/LoginView.vue'),
      meta: { guest: true }
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
  if (isQqPage && to.query.qq !== '1') return {path: to.path, query: {...to.query, qq: '1'}, hash: to.hash, replace: true}
  try { localStorage.removeItem(LEGACY_TOKEN_KEY) } catch {}

  if (to.meta.guest) {
    if (to.name === 'logout') {
      try { await logoutWebui() }
      catch { window.alert('退出登录失败，请稍后重试') }
    } else window.location.replace(withQqMode('/dashboard/'))
    return false
  }

  if (to.meta.requiresAuth) {
    const valid = await verifySession()
    if (!valid) {
      window.location.replace(withQqMode('/dashboard/'))
      return false
    }
  }
})

export default router
export { LEGACY_TOKEN_KEY, API_BASE, verifySession }
