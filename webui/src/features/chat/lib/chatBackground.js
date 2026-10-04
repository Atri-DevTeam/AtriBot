import { onBeforeUnmount, onMounted, reactive, readonly } from 'vue'

const STORAGE_KEY = 'atri.webui.chat_background'
export const CHAT_BACKGROUND_URL = 'https://www.loliapi.com/acg/pc/'
const defaults = { enabled: false, transparency: 70 }
const settings = reactive({ ...defaults })

function normalize(value) {
  return {
    enabled: value?.enabled === true,
    transparency: typeof value?.transparency === 'number' && Number.isFinite(value.transparency)
      ? Math.round(Math.min(100, Math.max(0, value.transparency)))
      : defaults.transparency
  }
}

function loadSettings() {
  try {
    Object.assign(settings, normalize(JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')))
  } catch { /* 存储不可用时保留当前设置 */ }
}

function updateSettings(changes) {
  Object.assign(settings, normalize({ ...settings, ...changes }))
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
  } catch { /* 存储不可用时仍在当前页面生效 */ }
}

export function useChatBackground() {
  function onStorage(event) {
    if (event.key === STORAGE_KEY || event.key === null) loadSettings()
  }

  onMounted(() => {
    loadSettings()
    window.addEventListener('storage', onStorage)
  })
  onBeforeUnmount(() => window.removeEventListener('storage', onStorage))

  return { settings: readonly(settings), updateSettings }
}
