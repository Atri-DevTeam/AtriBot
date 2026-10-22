import {shallowRef} from 'vue'

export const WEBUI_API_BASE = import.meta.env.VITE_API_BASE || '/webui/api'
export const currentWebuiUser = shallowRef(null)
let loading = null

export async function verifyWebuiSession() {
  if (loading) return loading
  loading = (async () => {
    try {
      const response = await fetch(`${WEBUI_API_BASE}/auth/verify`, {credentials: 'same-origin', cache: 'no-store'})
      const payload = response.ok ? await response.json() : null
      const user = payload?.code === 0 ? payload.data : null
      currentWebuiUser.value = user && ['ADMIN', 'OWNER'].includes(user.role) ? user : null
    } catch {
      currentWebuiUser.value = null
    }
    return !!currentWebuiUser.value
  })()
  try { return await loading } finally { loading = null }
}
