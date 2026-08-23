// 面板布局持久化键的唯一归属地，供聊天页、侧边栏与机器人设置页共用
export const CHAT_LAYOUT_KEY = 'atri.webui.chat_layout'
export const NAV_COLLAPSED_KEY = 'atri.webui.nav_collapsed'

/** 恢复默认面板布局：清除持久化状态；各组件下次挂载时按默认值渲染 */
export function resetPanelLayout() {
  try {
    localStorage.removeItem(CHAT_LAYOUT_KEY)
    localStorage.removeItem(NAV_COLLAPSED_KEY)
  } catch { /* ignore */ }
}
