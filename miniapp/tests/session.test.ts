import { test } from 'node:test'
import assert from 'node:assert/strict'
import { PageSession, takeEntry, type State } from '../src/session.ts'

const ticket = 'a'.repeat(43)
const token = 'b'.repeat(43)
const grant = () => ({ token, user: { userId: 'owner', displayName: '名字' }, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000 })

test('private mutations use the session token, preserve structured errors and discard late results', async () => {
  let mode = 'error', phase = '', finish!: (value: Response) => void
  const session = new PageSession({
    request: async (url, init) => {
      if (String(url).endsWith('/auth/exchange')) return Response.json(grant())
      assert.equal((init?.headers as Record<string, string>).Authorization, `Bearer ${token}`)
      assert.equal(init?.method, 'POST')
      assert.deepEqual(JSON.parse(String(init?.body)), { groupId: 'group-A' })
      if (mode === 'error') return Response.json({ error: 'GROUP_NOT_FOUND' }, { status: 404 })
      return new Promise<Response>(resolve => { finish = resolve })
    }, changed: state => { phase = state.phase }, release: () => {}
  })
  await session.open('owner', ticket)
  await assert.rejects(session.post('groups/binding', { groupId: 'group-A' }), { message: 'GROUP_NOT_FOUND' })
  assert.equal(phase, 'ready')
  mode = 'pending'
  const pending = session.post('groups/binding', { groupId: 'group-A' })
  session.end()
  finish(Response.json({ code: 'proof' }))
  assert.equal(await pending, null)
  assert.equal(await session.post('groups/binding', { groupId: 'group-A' }), null)
})

test('private image bytes are cached, concurrent requests coalesce and refresh/exit revoke URLs', async () => {
  let imageCalls = 0
  const session = new PageSession({
    request: async (url, init) => {
      if (String(url).endsWith('/auth/exchange')) return Response.json(grant())
      assert.equal((init?.headers as Record<string, string>).Authorization, `Bearer ${token}`)
      imageCalls++
      return new Response(new Blob(['image bytes'], { type: 'image/png' }))
    }, changed: () => {}, release: () => {}
  })
  await session.open('owner', ticket)
  const first = session.image('inventory/image')
  assert.equal(first, session.image('inventory/image'))
  const url = await first
  assert.ok(url?.startsWith('blob:'))
  assert.equal(await session.image('inventory/image'), url)
  assert.equal(imageCalls, 1)
  assert.equal(await (await fetch(url!)).text(), 'image bytes')
  const updated = await session.image('inventory/image', true)
  assert.notEqual(updated, url)
  assert.equal(imageCalls, 2)
  await assert.rejects(fetch(url!))
  session.end()
  await assert.rejects(fetch(updated!))
  assert.equal(await session.image('inventory/image'), null)
})

test('exit aborts image download and a late body cannot recreate its cache', async () => {
  let finish!: (blob: Blob) => void
  let started!: () => void
  let signal: AbortSignal | undefined | null
  const reading = new Promise<void>(resolve => { started = resolve })
  const body = new Promise<Blob>(resolve => { finish = resolve })
  const session = new PageSession({
    request: async (url, init) => {
      if (String(url).endsWith('/auth/exchange')) return Response.json(grant())
      signal = init?.signal
      const response = new Response()
      response.blob = () => { started(); return body }
      return response
    }, changed: () => {}, release: () => {}
  })
  await session.open('owner', ticket)
  const image = session.image('inventory/image')
  await reading
  session.end()
  assert.equal(signal?.aborted, true)
  finish(new Blob(['late'], { type: 'image/png' }))
  assert.equal(await image, null)
  assert.equal(await session.image('inventory/image'), null)
})

test('expired sessions revoke previously cached images even without a prior exit callback', async () => {
  let now = Date.now()
  const session = new PageSession({
    now: () => now,
    request: async url => String(url).endsWith('/auth/exchange') ? Response.json(grant()) : new Response(new Blob(['image'], { type: 'image/png' })),
    changed: () => {}, release: () => {}
  })
  await session.open('owner', ticket)
  const url = await session.image('inventory/image')
  now += 100_000
  assert.equal(await session.image('inventory/image'), null)
  await assert.rejects(fetch(url!))
})


test('configured proxy API prefix applies to exchange, private reads and heartbeat', async () => {
  const calls: string[] = []
  const session = new PageSession({
    apiBase: '/custom/profile/api/',
    request: async (url, init) => {
      calls.push(String(url))
      if (String(url).endsWith('/auth/exchange')) return Response.json(grant())
      assert.equal((init?.headers as Record<string, string>).Authorization, `Bearer ${token}`)
      return Response.json({ ok: true })
    },
    changed: () => {}, release: () => {}
  })
  await session.open('owner', ticket)
  await session.get('activity')
  await session.heartbeat()
  assert.deepEqual(calls, ['/custom/profile/api/auth/exchange', '/custom/profile/api/activity', '/custom/profile/api/session/heartbeat'])
  session.end()
})

test('entry secrets are stripped while preserving QQ presentation parameters; duplicates rejected', () => {
  const url = new URL(`https://example.com/atrimeow/profile/?_nav_alpha=0&userId=owner&ticket=${ticket}`)
  assert.deepEqual(takeEntry(url), { userId: 'owner', ticket })
  assert.equal(url.search, '?_nav_alpha=0')
  assert.equal(takeEntry(new URL(`https://example.com/?userId=a&userId=b&ticket=${ticket}`)).userId, null)
})

test('late exchange result after page exit is revoked and cannot revive the page', async () => {
  let resolve!: (response: Response) => void
  const response = new Promise<Response>((done) => { resolve = done })
  const states: State[] = []
  const released: string[] = []
  const session = new PageSession({ request: async () => response, changed: s => states.push(s), release: t => released.push(t) })
  const opening = session.open('owner', ticket)
  session.end()
  resolve(Response.json(grant()))
  await opening
  assert.deepEqual(states, [{ phase: 'expired' }])
  assert.deepEqual(released, [token])
})

test('a document exchanges only once and termination removes identity and stops access', async () => {
  let requests = 0
  const states: State[] = []
  const released: string[] = []
  const session = new PageSession({ request: async () => { requests++; return Response.json(grant()) }, changed: s => states.push(s), release: t => released.push(t) })
  await session.open('owner', ticket)
  await session.open('owner', ticket)
  assert.equal(requests, 1)
  assert.equal(states[0].phase, 'ready')
  session.end()
  session.end()
  await session.heartbeat()
  assert.equal(requests, 1)
  assert.deepEqual(states.at(-1), { phase: 'expired' })
  assert.deepEqual(released, [token])
})

test('missing credentials never request an exchange; failed tickets are not retried', async () => {
  let requests = 0
  const states: State[] = []
  const create = () => new PageSession({ request: async () => { requests++; return new Response(null, { status: 401 }) }, changed: s => states.push(s), release: () => {} })
  await create().open(null, null)
  assert.equal(requests, 0)
  const session = create()
  await session.open('owner', ticket)
  await session.open('owner', ticket)
  assert.equal(requests, 1)
  assert.ok(states.every(s => s.phase === 'expired'))
})

test('returning after idle expiry cannot renew a page session', async () => {
  let now = Date.now()
  let requests = 0
  const states: State[] = []
  const session = new PageSession({ now: () => now, request: async () => { requests++; return Response.json(grant()) }, changed: s => states.push(s), release: () => {} })
  await session.open('owner', ticket)
  now += 90000
  await session.heartbeat()
  assert.equal(requests, 1)
  assert.deepEqual(states.at(-1), { phase: 'expired' })
})

test('profile requests carry a bearer token and discard late data after exit', async () => {
  let resolve!: (response: Response) => void
  const delayed = new Promise<Response>(done => { resolve = done })
  const session = new PageSession({ request: async (url, init) => {
    if (String(url).endsWith('/exchange')) return Response.json(grant())
    assert.equal((init?.headers as Record<string, string>).Authorization, `Bearer ${token}`)
    return delayed
  }, changed: () => {}, release: () => {} })
  await session.open('owner', ticket)
  const result = session.get('profile')
  session.end()
  resolve(Response.json({ username: 'Private record' }))
  assert.equal(await result, null)
  assert.equal(await session.get('profile'), null)
})
