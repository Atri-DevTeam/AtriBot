<script setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import {currentWebuiUser, verifyWebuiSession} from '../lib/webuiSession.js'
import {withQqMode} from '../lib/qqEnvironment.js'

const router = useRouter()
const open = ref(false)
const element = ref(null)
const menu = ref(null)
const menuStyle = ref({})
const avatarFailed = ref(false)
const user = currentWebuiUser
const name = computed(() => user.value?.displayName || '管理员')
const role = computed(() => user.value?.role === 'OWNER' ? '所有者' : '管理员')

function refreshPage() { window.location.reload() }

function toggleMenu() {
  if (open.value) { open.value = false; return }
  const bounds = element.value.getBoundingClientRect()
  const collapsed = innerWidth >= 769 && document.documentElement.classList.contains('nav-collapsed')
  const width = collapsed ? 230 : bounds.width
  menuStyle.value = {
    width: `${width}px`,
    left: `${Math.max(8, Math.min(collapsed ? bounds.right + 12 : bounds.left, innerWidth - width - 8))}px`,
    bottom: `${innerHeight - (collapsed ? bounds.bottom : bounds.top - 10)}px`
  }
  open.value = true
}

function closeMenu() { open.value = false }

function dismiss(event) {
  if (event.type === 'keydown' ? event.key === 'Escape'
      : !element.value?.contains(event.target) && !menu.value?.contains(event.target)) closeMenu()
}

onMounted(() => {
  if (!user.value) void verifyWebuiSession()
  document.addEventListener('pointerdown', dismiss)
  document.addEventListener('keydown', dismiss)
  window.addEventListener('resize', closeMenu)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', dismiss)
  document.removeEventListener('keydown', dismiss)
  window.removeEventListener('resize', closeMenu)
})
</script>

<template>
  <div ref="element" class="current-user">
    <Teleport to="body">
    <div v-if="open" ref="menu" class="current-user-menu" :style="menuStyle" role="menu" aria-label="账号操作">
      <div class="current-user-heading"><strong>{{ name }}</strong><span>{{ role }}</span></div>
      <a role="menuitem" :href="withQqMode('/dashboard/?panel=personal')">个人档案<span>↗</span></a>
      <a role="menuitem" :href="withQqMode('/dashboard/')">返回工作台<span>↗</span></a>
      <button role="menuitem" @click="refreshPage">刷新页面<span>↻</span></button>
      <slot />
      <button role="menuitem" class="current-user-logout" @click="open = false; router.push('/logout')">退出登录<span>↪</span></button>
    </div>
    </Teleport>
    <button class="current-user-button" aria-label="当前账号" :title="name + ' · ' + role"
            aria-haspopup="menu" :aria-expanded="open" @click="toggleMenu">
      <img v-if="user?.avatarUrl && !avatarFailed" class="current-user-avatar" :src="user.avatarUrl" alt="" referrerpolicy="no-referrer" @error="avatarFailed = true"/>
      <span v-else class="current-user-avatar current-user-fallback">{{ name.slice(0, 1) }}</span>
      <span class="current-user-info"><strong>{{ name }}</strong><span>{{ role }}</span></span>
      <svg class="current-user-more" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m9 5 6 7-6 7"/></svg>
    </button>
  </div>
</template>

<style scoped>
.current-user { position: relative; width: 100%; min-width: 0; color-scheme: dark; }
.current-user-button { width: 100%; display: flex; align-items: center; gap: 10px; padding: 10px; border: 0; border-radius: 8px; background: transparent; color: var(--color-sidebar-active-text); text-align: left; cursor: pointer; transition: background .16s; }
.current-user-button:hover, .current-user-button[aria-expanded="true"] { background: var(--color-sidebar-hover-bg); }
.current-user-avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; flex: 0 0 36px; }
.current-user-fallback { display: grid; place-items: center; background: var(--color-sidebar-active-bg); color: #d5dce6; font-weight: 600; font-size: 15px; }
.current-user-info { min-width: 0; flex: 1; display: grid; gap: 3px; }
.current-user-info strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 600; }
.current-user-info > span { font-size: 11px; color: var(--color-sidebar-text); }
.current-user-more { flex: 0 0 16px; color: var(--color-sidebar-text-muted); transform: rotate(-90deg); }
.current-user-menu { position: fixed; z-index: 150; box-sizing: border-box; padding: 6px; border: 1px solid #ffffff1f; border-radius: 12px; background: var(--color-sidebar-bg-alt); color-scheme: dark; box-shadow: 0 12px 36px #00000052; }
.current-user-heading { padding: 9px 10px 12px; margin-bottom: 4px; border-bottom: 1px solid var(--color-sidebar-border); display: grid; gap: 4px; overflow-wrap: anywhere; }
.current-user-heading strong { font-size: 13px; font-weight: 600; color: var(--color-sidebar-active-text); }
.current-user-heading span { font-size: 11px; color: var(--color-sidebar-text); }
.current-user-menu a, .current-user-menu button { width: 100%; display: flex; align-items: center; justify-content: space-between; padding: 10px; border: 0; border-radius: 7px; background: transparent; text-decoration: none; color: var(--color-sidebar-text); font: inherit; font-size: 12px; cursor: pointer; }
.current-user-menu a:hover, .current-user-menu button:hover { background: var(--color-sidebar-hover-bg); color: var(--color-sidebar-active-text); }
.current-user-menu .current-user-logout { border-top: 1px solid var(--color-sidebar-border); border-radius: 0 0 7px 7px; margin-top: 4px; padding-top: 12px; color: #ef9298; }
.current-user-menu .current-user-logout:hover { background: #ef929814; color: #ffb0b5; }
.current-user-menu button:focus-visible, .current-user-menu a:focus-visible, .current-user-button:focus-visible { outline: 2px solid var(--color-sidebar-accent); outline-offset: 2px; }
.current-user-menu :deep(.ghost-button) { width: 100%; justify-content: flex-start; border: 0; background: transparent; color: var(--color-sidebar-text); font-size: 12px; }
.current-user-menu :deep(.ghost-button:hover) { background: var(--color-sidebar-hover-bg); color: var(--color-sidebar-active-text); }
@media (min-width: 769px) {
  :global(html.nav-collapsed .current-user-button) { justify-content: center; padding: 6px 0; background: transparent; }
  :global(html.nav-collapsed .current-user-info), :global(html.nav-collapsed .current-user-more) { display: none; }
}
</style>
