export type User = { userId: string; displayName: string }
export type State = { phase: 'entering' | 'ready' | 'expired'; user?: User }
export class ApiError extends Error {
  readonly code: string
  constructor(code: string) { super(code); this.code = code }
}
type Grant = { token: string; user: User; expiresAt: number; idleTimeoutMillis: number }
type Options = {
  request: typeof fetch
  release: (token: string) => void
  changed: (state: State) => void
  now?: () => number
  apiBase?: string
}

/** No cookie/storage/global token. A PageSession belongs to exactly one document. */
export class PageSession {
  #token = ''
  #ended = false
  #opened = false
  #heartbeatRunning = false
  #deadline = 0
  #expiresAt = 0
  #idleMillis = 0
  #options: Options
  #images = new Map<string, string>()
  #imageControllers = new Set<AbortController>()
  #imageRequests = new Map<string, Promise<string | null>>()

  constructor(options: Options) { this.#options = options }
  #now() { return this.#options.now?.() ?? Date.now() }

  async #request(path: string, init: RequestInit, timeoutMillis = 10_000) {
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), timeoutMillis)
    try {
      const base = (this.#options.apiBase || '/atrimeow/profile/api').replace(/\/+$/, '')
      return await this.#options.request(`${base}/${path}`, {
        ...init, signal: controller.signal, credentials: 'omit', cache: 'no-store', referrerPolicy: 'no-referrer'
      })
    } finally { clearTimeout(timeout) }
  }

  async open(userId: string | null, ticket: string | null) {
    if (this.#opened || this.#ended) return
    this.#opened = true
    if (!userId || userId.length > 256 || !ticket || !/^[\w-]{43}$/.test(ticket)) { this.end(); return }
    try {
      const response = await this.#request('auth/exchange', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ userId, ticket })
      })
      if (!response.ok) { this.end(); return }
      const grant = await response.json() as Grant
      // Navigation may have ended this document while the exchange was in flight.
      if (this.#ended) { this.#options.release(grant.token); return }
      this.#token = grant.token
      this.#expiresAt = grant.expiresAt
      this.#idleMillis = grant.idleTimeoutMillis
      this.#deadline = this.#now() + this.#idleMillis
      this.#options.changed({ phase: 'ready', user: grant.user })
    } catch { this.end() }
  }

  async heartbeat() {
    if (!this.#token || this.#ended) return
    if (this.#now() >= this.#deadline || this.#now() >= this.#expiresAt) { this.end(); return }
    if (this.#heartbeatRunning) return
    this.#heartbeatRunning = true
    try {
      const response = await this.#request('session/heartbeat', {
        method: 'POST', headers: { Authorization: `Bearer ${this.#token}` }
      })
      if (response.ok && !this.#ended) this.#deadline = this.#now() + this.#idleMillis
      else if (response.status === 401 || response.status === 403 || response.status === 503) this.end()
    } catch {
      // Brief disconnections can recover within the idle lease, never after it expires.
      if (this.#now() >= this.#deadline) this.end()
    } finally { this.#heartbeatRunning = false }
  }

  /** Private APIs stay tied to this document; late responses after exit are discarded. */
  async get<T>(path: string): Promise<T | null> {
    if (!this.#token || this.#ended) return null
    if (this.#now() >= this.#deadline || this.#now() >= this.#expiresAt) { this.end(); return null }
    const response = await this.#request(path, { method: 'GET', headers: { Authorization: `Bearer ${this.#token}` } })
    if (this.#ended) return null
    if (response.status === 401 || response.status === 403 || response.status === 503) { this.end(); return null }
    if (!response.ok) throw new Error('记录暂时无法读取')
    const result = await response.json() as T
    if (this.#ended) return null
    this.#deadline = this.#now() + this.#idleMillis
    return result
  }

  /** Authenticated mutations use the same in-memory session and discard late results. */
  async post<T>(path: string, body: unknown): Promise<T | null> {
    if (!this.#token || this.#ended) return null
    if (this.#now() >= this.#deadline || this.#now() >= this.#expiresAt) { this.end(); return null }
    const response = await this.#request(path, {
      method: 'POST', headers: { Authorization: `Bearer ${this.#token}`, 'Content-Type': 'application/json' }, body: JSON.stringify(body)
    })
    if (this.#ended) return null
    if (response.status === 401 || response.status === 403 || response.status === 503) { this.end(); return null }
    if (!response.ok) {
      const error = await response.json().catch(() => ({})) as { error?: string }
      if (this.#ended) return null
      throw new ApiError(error.error || 'REQUEST_FAILED')
    }
    const result = await response.json() as T
    if (this.#ended) return null
    this.#deadline = this.#now() + this.#idleMillis
    return result
  }

  /** Keep image bytes in this document, never cache expiring remote URLs or persist private images. */
  image(path: string, refresh = false): Promise<string | null> {
    if (!this.#token || this.#ended) return Promise.resolve(null)
    if (this.#now() >= this.#deadline || this.#now() >= this.#expiresAt) { this.end(); return Promise.resolve(null) }
    const pending = this.#imageRequests.get(path)
    if (pending) return pending
    const cached = this.#images.get(path)
    if (cached && !refresh) return Promise.resolve(cached)
    const request = this.#loadImage(path).finally(() => this.#imageRequests.delete(path))
    this.#imageRequests.set(path, request)
    return request
  }

  async #loadImage(path: string): Promise<string | null> {
    const controller = new AbortController()
    this.#imageControllers.add(controller)
    const timeout = setTimeout(() => controller.abort(), 60_000)
    try {
      const base = (this.#options.apiBase || '/atrimeow/profile/api').replace(/\/+$/, '')
      const response = await this.#options.request(`${base}/${path}`, {
        method: 'GET', headers: { Authorization: `Bearer ${this.#token}` }, signal: controller.signal,
        credentials: 'omit', cache: 'no-store', referrerPolicy: 'no-referrer'
      })
      if (this.#ended) return null
      if (response.status === 401 || response.status === 403 || response.status === 503) { this.end(); return null }
      if (!response.ok) throw new Error('背包图片暂时无法读取')
      const blob = await response.blob()
      if (this.#ended) return null
      if (this.#now() >= this.#deadline || this.#now() >= this.#expiresAt) { this.end(); return null }
      if (!['image/png', 'image/jpeg', 'image/webp', 'image/gif'].includes(blob.type) || !blob.size || blob.size > 25 * 1024 * 1024)
        throw new Error('背包图片格式无效')
      const url = URL.createObjectURL(blob)
      const previous = this.#images.get(path)
      this.#images.set(path, url)
      if (previous) URL.revokeObjectURL(previous)
      this.#deadline = this.#now() + this.#idleMillis
      return url
    } catch (error) {
      if (this.#ended) return null
      throw error
    } finally {
      clearTimeout(timeout)
      this.#imageControllers.delete(controller)
    }
  }

  end() {
    if (this.#ended) return
    this.#ended = true
    const token = this.#token
    this.#token = ''
    for (const url of this.#images.values()) URL.revokeObjectURL(url)
    this.#images.clear()
    for (const controller of this.#imageControllers) controller.abort()
    this.#imageControllers.clear()
    this.#imageRequests.clear()
    this.#options.changed({ phase: 'expired' })
    if (token) this.#options.release(token)
  }
}

export function takeEntry(url: URL) {
  const userId = url.searchParams.getAll('userId')
  const ticket = url.searchParams.getAll('ticket')
  url.searchParams.delete('userId')
  url.searchParams.delete('ticket')
  return { userId: userId.length === 1 ? userId[0] : null, ticket: ticket.length === 1 ? ticket[0] : null }
}
