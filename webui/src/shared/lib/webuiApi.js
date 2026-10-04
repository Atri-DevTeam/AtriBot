export function createWebuiApi({ baseUrl, onSessionExpired, assertActive = () => {} }) {
  let sessionExpired = false

  return async function request(path, options = {}) {
    assertActive()
    const headers = new Headers(options.headers)
    const multipart = typeof FormData !== 'undefined' && options.body instanceof FormData
    if (!multipart && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    const response = await fetch(`${baseUrl}${path}`, { credentials: 'same-origin', ...options, headers })
    assertActive()
    if (response.status === 401 || response.status === 503) {
      if (!sessionExpired) {
        sessionExpired = true
        onSessionExpired?.(response.status)
      }
      const error = new Error(response.status === 401 ? '未授权' : 'WebUI 已关闭')
      error.status = response.status
      throw error
    }
    const text = await response.text()
    assertActive()
    let payload
    try { payload = JSON.parse(text) }
    catch {
      const error = new Error(text || `HTTP ${response.status}`)
      error.status = response.status
      throw error
    }
    if (!response.ok || !payload || payload.status !== 200) {
      const error = new Error(payload?.message || `请求失败（HTTP ${response.status}）`)
      error.status = response.status
      throw error
    }
    return payload.data
  }
}

export function logoutWebui(baseUrl, router) {
  return fetch(`${baseUrl}/auth/logout`, { method: 'POST', credentials: 'same-origin' })
    .catch(() => {})
    .finally(() => router.replace('/login'))
}
