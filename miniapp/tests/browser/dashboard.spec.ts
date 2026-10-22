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
  test(`external authorization shows unified login before continuing with QQ adaptation ${qq}`, async ({page, isMobile}) => {
    let privateRequests = 0
    await page.route('**/dashboard/api/auth/**', route => {
      privateRequests++
      return route.fulfill({status: 500, json: {error: 'LOGIN_FAILED'}})
    })
    await page.route('**/auth/continue?**', route => {
      const url = new URL(route.request().url())
      expect(url.searchParams.get('state')).toBe(ticket)
      expect(url.searchParams.get('mobile')).toBe(isMobile ? '1' : '0')
      return route.fulfill({contentType: 'text/html', body: '<h1>Continue authorization</h1>'})
    })
    await page.goto(`/dashboard/?authorization=${ticket}${qq ? '&qq=1' : ''}`)
    await expect(page.getByRole('heading', {name: '统一账号登陆', exact: true})).toBeVisible()
    await expect(page.getByText('完成登录后将返回发起认证的网站', {exact: true})).toBeVisible()
    const proceed = page.getByRole('button', {name: '继续认证', exact: true})
    await expect(proceed).toBeEnabled()
    await expect(page.getByRole('button', {name: '切换账号', exact: true})).toBeVisible()
    expect(privateRequests).toBe(0)
    await proceed.click()
    await expect(page.getByRole('heading', {name: 'Continue authorization'})).toBeVisible()
    expect(privateRequests).toBe(0)
  })
}

for (const qq of [false, true]) {
  test(`external authorization switches accounts after clearing the login with QQ adaptation ${qq}`, async ({page, isMobile}) => {
    let loggedOut = false
    let releaseLogout!: () => void
    const pendingLogout = new Promise<void>(resolve => { releaseLogout = resolve })
    await page.route('**/dashboard/api/auth/**', async route => {
      expect(route.request().url()).toContain('/logout')
      expect(route.request().method()).toBe('POST')
      await pendingLogout
      loggedOut = true
      return route.fulfill({status: 204})
    })
    await page.route('**/auth/continue?**', route => {
      expect(loggedOut).toBe(true)
      const url = new URL(route.request().url())
      expect(url.searchParams.get('state')).toBe(ticket)
      expect(url.searchParams.get('mobile')).toBe(isMobile ? '1' : '0')
      return route.fulfill({contentType: 'text/html', body: '<h1>Choose QQ account</h1>'})
    })
    await page.goto(`/dashboard/?authorization=${ticket}${qq ? '&qq=1' : ''}`)
    const switcher = page.getByRole('button', {name: '切换账号', exact: true})
    await expect(switcher).toHaveCSS('font-size', '12px')
    await switcher.click()
    try {
      await expect(switcher).toBeDisabled()
      await expect(page.getByRole('button', {name: '继续认证', exact: true})).toBeDisabled()
    } finally {
      releaseLogout()
    }
    await expect(page.getByRole('heading', {name: 'Choose QQ account'})).toBeVisible()
  })
}

test('failed account switching stays on the external authorization page and can retry', async ({page}) => {
  let logouts = 0
  let continued = false
  await page.route('**/dashboard/api/auth/logout', route => {
    logouts++
    return route.fulfill({status: 500, json: {error: 'LOGIN_FAILED'}})
  })
  await page.route('**/auth/continue?**', route => {
    continued = true
    return route.abort()
  })
  await page.goto(`/dashboard/?authorization=${ticket}`)
  const switcher = page.getByRole('button', {name: '切换账号', exact: true})
  await switcher.click()
  await expect(page.getByRole('alert')).toContainText('QQ 登录失败')
  await expect(switcher).toBeEnabled()
  expect(continued).toBe(false)
  await switcher.click()
  await expect.poll(() => logouts).toBe(2)
  await expect(switcher).toBeEnabled()
})

test('malformed external authorization cannot start an ordinary login', async ({page}) => {
  let requests = 0
  await page.route('**/dashboard/api/auth/**', route => {
    requests++
    return route.fulfill({status: 500, json: {error: 'LOGIN_FAILED'}})
  })
  await page.goto('/dashboard/?authorization=invalid&qq=1')
  await expect(page.getByRole('alert')).toContainText('授权已过期')
  await expect(page.getByRole('button', {name: '继续认证', exact: true})).toBeDisabled()
  await expect(page.getByRole('button', {name: '切换账号', exact: true})).toBeDisabled()
  expect(requests).toBe(0)
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
  await expect(page.getByRole('button', {name: '切换账号', exact: true})).toBeVisible()
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

for (const qq of [false, true]) {
test(`profile logout revokes the login and stays logged out after reload with QQ adaptation ${qq}`, async ({page}) => {
  let starts = 0
  let loggedOut = false
  let released = 0
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'sendBeacon', {value: () => false})
  })
  await page.route('**/dashboard/api/auth/**', route => {
    if (route.request().url().endsWith('/start')) starts++
    if (route.request().url().endsWith('/logout')) {
      expect(route.request().method()).toBe('POST')
      loggedOut = true
      return route.fulfill({status: 204})
    }
    if (loggedOut) return route.fulfill({json: {status: 'anonymous'}})
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
  await page.goto(qq ? '/dashboard/?qq=1' : '/dashboard/')
  await expect(page.getByRole('heading', {name: '亚托利', exact: true})).toBeVisible()
  await expect(page).toHaveTitle(qq ? '\u200b' : '个人中心')
  expect(starts).toBe(0)
  await page.getByRole('button', {name: '退出登录', exact: true}).click()
  await expect(page.getByRole('heading', {name: '统一账号登陆'})).toBeVisible()
  await expect.poll(() => released).toBe(1)
  expect(loggedOut).toBe(true)
  await expect(page.getByRole('heading', {name: '亚托利', exact: true})).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('heading', {name: '统一账号登陆'})).toBeVisible()
  await expect(page.getByRole('button', {name: 'QQ 登录', exact: true})).toBeEnabled()
  expect(new URL(page.url()).searchParams.get('qq')).toBe(qq ? '1' : null)
  expect(starts).toBe(0)
})
}

test('failed profile logout preserves the profile and allows retry', async ({page}) => {
  let attempts = 0
  let released = 0
  await page.addInitScript(() => Object.defineProperty(navigator, 'sendBeacon', {value: () => false}))
  await page.route('**/dashboard/api/auth/session', route => route.fulfill({json: {
    status: 'ready', access: {token, user: {userId: 'owner', displayName: '亚托利'},
      expiresAt: Date.now() + 7_200_000, idleTimeoutMillis: 90_000}
  }}))
  await page.route('**/dashboard/api/auth/logout', route => {
    attempts++
    return route.fulfill({status: attempts === 1 ? 500 : 204})
  })
  await page.route('**/atrimeow/profile/api/**', route => {
    if (route.request().url().endsWith('/session/close')) released++
    if (route.request().url().endsWith('/profile')) return route.fulfill({json: {...profile, avatarUrl: null, bot: {...profile.bot, avatarUrl: null}}})
    const activity = activityResponse(route.request().url())
    return activity ? route.fulfill({json: activity}) : route.fulfill({status: 204})
  })
  await page.goto('/dashboard/')
  await expect(page.getByRole('heading', {name: '亚托利', exact: true})).toBeVisible()
  const logout = page.getByRole('button', {name: '退出登录', exact: true})
  await logout.click()
  await expect(page.getByRole('alert')).toHaveText('退出登录失败，请稍后重试')
  await expect(logout).toBeEnabled()
  await expect(page.getByRole('heading', {name: '亚托利', exact: true})).toBeVisible()
  expect(released).toBe(0)
  await logout.click()
  await expect(page.getByRole('heading', {name: '统一账号登陆'})).toBeVisible()
  await expect.poll(() => released).toBe(1)
})

for (const workspace of [false, true]) {
test(`dashboard switches accounts from ${workspace ? 'workspace selection' : 'login page'}`, async ({page, isMobile}) => {
  let loggedOut = false
  await page.route('**/dashboard/api/auth/session', route => route.fulfill({json: workspace ? {
    status: 'ready', canManage: true, webuiEnabled: true, access: {token,
      user: {userId: 'owner', displayName: '亚托利'}, expiresAt: Date.now() + 7_200_000, idleTimeoutMillis: 90_000}
  } : {status: 'anonymous'}}))
  await page.route('**/dashboard/api/auth/logout', route => {
    expect(route.request().method()).toBe('POST')
    loggedOut = true
    return route.fulfill({status: 204})
  })
  await page.route('**/dashboard/api/auth/start', route => {
    expect(loggedOut).toBe(true)
    expect(route.request().postDataJSON()).toMatchObject({mobile: isMobile, qq: workspace})
    return route.fulfill({json: {url: `https://graph.qq.com/oauth2.0/authorize?state=${ticket}`}})
  })
  await page.route('**/atrimeow/profile/api/session/close', route => route.fulfill({status: 204}))
  await page.route('https://graph.qq.com/**', route => route.fulfill({contentType: 'text/html', body: '<h1>Choose QQ account</h1>'}))
  await page.goto(workspace ? '/dashboard/?qq=1' : '/dashboard/')
  await expect(page.getByRole('heading', {name: workspace ? '选择工作台' : '统一账号登陆'})).toBeVisible()
  await page.getByRole('button', {name: '切换账号', exact: true}).click()
  await expect(page.getByRole('heading', {name: 'Choose QQ account'})).toBeVisible()
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
