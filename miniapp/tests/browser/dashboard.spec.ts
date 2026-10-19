import {test, expect} from '@playwright/test'
import {profile} from './profile-fixture'
import {activityResponse} from './activity-fixture'

const ticket = 'a'.repeat(43)
const token = 'b'.repeat(43)

test.beforeEach(async ({page}) => {
  await page.route('**/dashboard/api/bot', route => route.fulfill({json: {...profile.bot, name: '亚托莉喵'}}))
  await page.route(profile.bot.avatarUrl, route => route.fulfill({contentType: 'image/svg+xml', body:
    '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100"><rect width="100" height="100" rx="50" fill="#c9eaff"/><path d="M27 76 45 25h10l18 51H61l-4-13H42l-4 13Zm18-23h9l-4-15Z" fill="#168cdb"/></svg>'}))
})

for (const qq of [false, true]) {
test(`QQ adaptation ${qq} starts authorization only when requested and recovers after failure`, async ({page}, testInfo) => {
  let starts = 0
  let releaseStart!: () => void
  const startPending = new Promise<void>(resolve => { releaseStart = resolve })
  await page.route('**/dashboard/api/auth/session', route => route.fulfill({json: {status: 'anonymous'}}))
  await page.route('**/dashboard/api/auth/start', async route => {
    starts++
    await startPending
    return route.fulfill({status: 500, json: {error: 'LOGIN_FAILED'}})
  })
  await page.goto(qq ? '/dashboard/?qq=1' : '/dashboard/')
  await expect(page).toHaveTitle(qq ? '\u200b' : '个人中心')
  const login = page.getByRole('button', {name: 'QQ 登录', exact: true})
  const card = page.locator('.entry-card')
  if (!qq) await expect(login).toBeEnabled()
  else await expect(login).toBeDisabled()
  await page.evaluate(() => Promise.allSettled(document.getAnimations()
    .filter(animation => animation.effect?.getComputedTiming().iterations !== Infinity)
    .map(animation => animation.finished)))
  await login.hover()
  const bounds = await card.boundingBox()
  if (!qq) await login.click()
  try {
    await expect(page.locator('.qq-login')).toBeDisabled()
    await expect(page.locator('.qq-login')).toHaveCSS('opacity', '1')
    await expect(card).toHaveCSS('transform', 'none')
    expect(await card.boundingBox()).toEqual(bounds)
    await page.screenshot({path: testInfo.outputPath('dashboard-login-pending.png'), fullPage: true})
  } finally {
    releaseStart()
  }
  await expect(page.getByRole('alert')).toContainText('QQ 登录失败')
  await expect(login).toBeEnabled()
  expect(starts).toBe(1)
  await login.click()
  await expect.poll(() => starts).toBe(2)
  await expect(login).toBeEnabled()
})

test(`binding entry retains navigation with QQ adaptation ${qq}`, async ({page, isMobile}, testInfo) => {
  let starts = 0
  await page.route('**/dashboard/api/auth/**', async route => {
    expect(route.request().url()).toContain('/start')
    starts++
    expect(route.request().postDataJSON()).toMatchObject({userOpenId: 'owner', ticket, nav: '0', wwv: '9983', mobile: isMobile, qq})
    return route.fulfill({json: {url: `https://graph.qq.com/oauth2.0/authorize?state=${ticket}`}})
  })
  await page.route('https://graph.qq.com/**', route => route.fulfill({contentType: 'text/html', body: '<h1>QQ Authorization</h1>'}))
  await page.goto(`/dashboard/?userOpenId=owner&ticket=${ticket}&_nav=0&_wwv=9983${qq ? '&qq=1' : ''}`)
  if (qq) {
    await expect(page.getByRole('heading', {name: 'QQ Authorization'})).toBeVisible()
    expect(starts).toBe(1)
    return
  }
  await expect(page.getByRole('heading', {name: '绑定机器人账号'})).toBeVisible()
  expect(starts).toBe(0)
  await expect(page).toHaveURL(/\/dashboard\/\?_nav=0&_wwv=9983$/)
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await expect(page.locator('.entry-illustration img')).toHaveJSProperty('naturalWidth', 1024)
  await expect(page.locator('.entry-illustration img')).toBeInViewport({ratio: .99})
  await expect(page.locator('.entry-illustration')).toHaveCSS('mask-image', 'none')
  await expect(page.getByRole('img', {name: '机器人头像'})).toHaveAttribute('src', profile.bot.avatarUrl)
  await page.screenshot({path: testInfo.outputPath('dashboard-binding.png'), fullPage: true, animations: 'disabled'})
  await page.getByRole('button', {name: 'QQ 登录并绑定'}).click()
  await expect(page.getByRole('heading', {name: 'QQ Authorization'})).toBeVisible()
  expect(starts).toBe(1)
})

}

for (const nativeClose of [true, false]) {
test(`profile exit preserves QQ login with native close support ${nativeClose}`, async ({page}) => {
  let starts = 0
  let loggedOut = false
  let released = 0
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'sendBeacon', {value: () => false})
    Object.defineProperty(window, 'close', {value: () => {}})
  })
  if (nativeClose) {
    await page.addInitScript(() => Object.defineProperty(navigator, 'userAgent', {value: 'QQ/9.2.0'}))
    await page.route('https://open.mobile.qq.com/sdk/qqapi.js', route => route.fulfill({
      contentType: 'text/javascript', body:
        'window.nativeCloseCalls = 0; window.mqq = {ui: {popBack() { window.nativeCloseCalls++ }}}'
    }))
  }
  await page.route('**/dashboard/api/auth/**', route => {
    if (route.request().url().endsWith('/start')) starts++
    if (route.request().url().endsWith('/logout')) loggedOut = true
    return route.fulfill({json: {status: 'ready', access: {token, user: {userId: 'owner', displayName: '亚托利'},
      expiresAt: Date.now() + 7_200_000, idleTimeoutMillis: 90_000}}})
  })
  await page.route('**/atrimeow/profile/api/**', route => {
    if (route.request().url().endsWith('/session/close')) {
      expect(route.request().postData()).toBe(token)
      released++
      return route.fulfill({status: 204})
    }
    if (route.request().url().endsWith('/profile')) return route.fulfill({json: {...profile, avatarUrl: null, bot: {...profile.bot, avatarUrl: null}}})
    const activity = activityResponse(route.request().url())
    return activity ? route.fulfill({json: activity}) : route.fulfill({status: 204})
  })
  await page.goto(nativeClose ? '/dashboard/?qq=1' : '/dashboard/')
  await expect(page.getByRole('heading', {name: '亚托利', exact: true})).toBeVisible()
  await expect(page).toHaveTitle(nativeClose ? '\u200b' : '个人中心')
  expect(starts).toBe(0)
  await page.getByRole('button', {name: '退出', exact: true}).click()
  if (nativeClose) {
    await expect.poll(() => page.evaluate(() => (window as Window & {nativeCloseCalls?: number}).nativeCloseCalls)).toBe(1)
  } else {
    await expect(page.getByRole('heading', {name: '已退出当前页面'})).toBeVisible()
    await expect(page.getByText('当前浏览器不允许自动关闭，请手动关闭此页面')).toBeVisible()
  }
  await expect.poll(() => released).toBe(1)
  expect(loggedOut).toBe(false)
  await expect(page.getByRole('heading', {name: '亚托利', exact: true})).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('heading', {name: '亚托利', exact: true})).toBeVisible()
  expect(starts).toBe(0)
})
}

for (const error of ['LOGIN_CANCELLED', 'LOGIN_FAILED']) {
  test(`authorization error ${error} waits for a manual retry`, async ({page}) => {
    let starts = 0
    await page.route('**/dashboard/api/auth/**', route => {
      starts++
      return route.fulfill({status: 500, json: {error: 'LOGIN_FAILED'}})
    })
    await page.goto(`/dashboard/?qq=1&auth_error=${error}`)
    await expect(page.getByRole('alert')).toBeVisible()
    await expect(page.getByRole('button', {name: 'QQ 登录', exact: true})).toBeEnabled()
    expect(starts).toBe(0)
    await expect(page).toHaveTitle('\u200b')
  })
}

test('unbound QQ accounts can copy the binding command and malformed links cannot start login', async ({page, isMobile}, testInfo) => {
  await page.addInitScript(() => Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {
    writeText: async (value: string) => { document.documentElement.dataset.copiedCommand = value }
  }}))
  if (isMobile) await page.setViewportSize({width: 320, height: 568})
  await page.route('**/dashboard/api/auth/session', route => route.fulfill({json: {status: 'unbound'}}))
  await page.goto('/dashboard/')
  await expect(page.getByRole('heading', {name: '尚未绑定机器人账号'})).toBeVisible()
  const copy = page.getByRole('button', {name: '复制 /dash 指令', exact: true})
  await expect(copy).toContainText('/dash')
  await copy.click()
  await expect(copy).toContainText('已复制')
  expect(await page.evaluate(() => document.documentElement.dataset.copiedCommand)).toBe('/dash')
  expect(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight)).toBe(true)
  await page.screenshot({path: testInfo.outputPath('unbound-copy-command.png'), fullPage: true})
  await page.goto(`/dashboard/?userOpenId=owner&ticket=${ticket}&ticket=${ticket}`)
  await expect(page.getByRole('button', {name: 'QQ 登录并绑定'})).toBeDisabled()
  await expect(page.getByRole('alert')).toContainText('绑定链接已失效')
})

for (const webuiEnabled of [true, false]) {
  test(`administrators choose a workspace with webui enabled ${webuiEnabled}`, async ({page}, testInfo) => {
    await page.route('**/dashboard/api/auth/session', route => route.fulfill({json: {
      status: 'ready', canManage: true, webuiEnabled, access: {token,
        user: {userId: 'owner', displayName: '亚托利'}, expiresAt: Date.now() + 7_200_000, idleTimeoutMillis: 90_000}
    }}))
    await page.route('**/atrimeow/profile/api/**', route => {
      if (route.request().url().endsWith('/profile')) return route.fulfill({json: {...profile, avatarUrl: null, bot: {...profile.bot, avatarUrl: null}}})
      const activity = activityResponse(route.request().url())
      return activity ? route.fulfill({json: activity}) : route.fulfill({status: 204})
    })
    await page.goto('/dashboard/')
    await expect(page.getByRole('heading', {name: '选择工作台'})).toBeVisible()
    const management = page.getByRole('button', {name: /WebUI 管理/})
    if (webuiEnabled) await expect(management).toBeEnabled()
    else await expect(management).toBeDisabled()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({path: testInfo.outputPath('workspace-choice.png'), fullPage: true, animations: 'disabled'})
    await page.getByRole('button', {name: /个人面板/}).click()
    await expect(page.getByRole('heading', {name: '亚托利', exact: true})).toBeVisible()
    await expect(page.getByRole('heading', {name: '选择工作台'})).toHaveCount(0)
  })
}

for (const qq of [false, true]) {
test(`an administrator can enter webui and the profile with QQ adaptation ${qq}`, async ({page}) => {
  await page.route('**/dashboard/api/auth/session', route => route.fulfill({json: {
    status: 'ready', canManage: true, webuiEnabled: true, access: {token,
      user: {userId: 'owner', displayName: '亚托利'}, expiresAt: Date.now() + 7_200_000, idleTimeoutMillis: 90_000}
  }}))
  await page.route(/\/webui\/(?:\?.*)?$/, route => route.fulfill({contentType: 'text/html', body: '<h1>Management workspace</h1>'}))
  await page.goto(qq ? '/dashboard/?qq=1' : '/dashboard/')
  await page.getByRole('button', {name: /WebUI 管理/}).click()
  await expect(page.getByRole('heading', {name: 'Management workspace'})).toBeVisible()
  await expect(page).toHaveURL(qq ? /\/webui\/\?qq=1$/ : /\/webui\/$/)
  await page.route('**/atrimeow/profile/api/**', route => {
    if (route.request().url().endsWith('/profile')) return route.fulfill({json: {...profile, avatarUrl: null, bot: {...profile.bot, avatarUrl: null}}})
    const activity = activityResponse(route.request().url())
    return activity ? route.fulfill({json: activity}) : route.fulfill({status: 204})
  })
  await page.goto('/dashboard/?panel=personal' + (qq ? '&qq=1' : ''))
  await expect(page.getByRole('heading', {name: '亚托利', exact: true})).toBeVisible()
})

}
