import {withQqMode} from './qqEnvironment.js'

export function createWebuiApi({
                                   baseUrl, onSessionExpired, assertActive = () => {
    }
                               }) {
    let sessionExpired = false

    return async function request(path, options = {}) {
        assertActive()
        const headers = new Headers(options.headers)
        const multipart = typeof FormData !== 'undefined' && options.body instanceof FormData
        if (!multipart && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
        const response = await fetch(`${baseUrl}${path}`, {credentials: 'same-origin', ...options, headers})
        assertActive()
        if (response.status === 401 || response.status === 403 || response.status === 503) {
            if (!sessionExpired) {
                sessionExpired = true
                onSessionExpired?.(response.status)
            }
            const error = new Error(response.status === 503 ? 'WebUI 已关闭' : response.status === 403 ? '没有管理权限' : '未授权')
            error.status = response.status
            throw error
        }
        const text = await response.text()
        assertActive()
        let payload
        try {
            payload = JSON.parse(text)
        } catch {
            const error = new Error(text || `HTTP ${response.status}`)
            error.status = response.status
            throw error
        }
        if (!response.ok || !payload || payload.code !== 0) {
            const error = new Error(payload?.message || `请求失败（HTTP ${response.status}）`)
            error.status = response.status
            error.code = payload?.code
            error.requestId = payload?.request_id
            throw error
        }
        return payload.data
    }
}

export async function logoutWebui() {
    const response = await fetch('/dashboard/api/auth/logout', {
        method: 'POST', credentials: 'same-origin', headers: {'Content-Type': 'application/json'}, body: '{}'
    })
    if (!response.ok) throw new Error('退出登录失败')
    window.location.replace(withQqMode('/dashboard/'))
}
