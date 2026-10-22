import {test, expect} from '@playwright/test'

test.use({baseURL: 'http://127.0.0.1:5175'})

for (const resource of ['src/main.js', 'src/features/chat/ChatView.vue']) {
  test(`webui reports a failed ${resource} instead of leaving a blank page`, async ({page}) => {
    await page.route(`**/${resource}`, route => route.abort())
    await page.route('**/webui/api/auth/verify', route => route.fulfill({json: {
      code: 0, data: {userId: 'owner', displayName: 'YZ_Ljc_', role: 'ADMIN'}
    }}))
    await page.goto('/webui/')
    await expect(page.getByText('页面资源加载失败，请重新加载', {exact: true})).toBeVisible()
    await expect(page.getByRole('link', {name: '重新加载', exact: true})).toBeVisible()
    await expect(page.getByRole('link', {name: '返回工作台', exact: true})).toHaveAttribute('href', '/dashboard/')
  })
}

test('webui displays loading while initial authorization is pending', async ({page}) => {
  let finish!: () => void
  const pending = new Promise<void>(resolve => { finish = resolve })
  await page.route('**/webui/api/auth/verify', async route => {
    await pending
    return route.fulfill({status: 401, json: {code: 10002}})
  })
  await page.route(/\/dashboard\/(?:\?.*)?$/, route => route.fulfill({contentType: 'text/html; charset=utf-8', body: '<h1>QQ 个人中心</h1>'}))
  await page.goto('/webui/')
  try {
    await expect(page.getByText('正在加载管理界面…', {exact: true})).toBeVisible()
    await expect(page.getByRole('button', {name: '当前账号', exact: true})).toHaveCount(0)
  } finally {
    finish()
  }
  await expect(page.getByRole('heading', {name: 'QQ 个人中心'})).toBeVisible()
})

test('webui can enter when browser storage is unavailable', async ({page}) => {
  await page.addInitScript(() => {
    Object.defineProperty(window, 'localStorage', {get() { throw new DOMException('Storage disabled', 'SecurityError') }})
  })
  await page.route('**/webui/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/events')) return route.fulfill({contentType: 'text/event-stream', body: ': ok\n\n'})
    let data: unknown = {}
    if (path.endsWith('/auth/verify')) data = {userId: 'owner', displayName: 'YZ_Ljc_', role: 'ADMIN', avatarUrl: null}
    else if (path.endsWith('/chat/conversations')) data = {items: [], hasMore: false, cursor: '0'}
    else if (path.endsWith('/chat/changes')) data = {changes: [], cursor: '0'}
    else if (path.endsWith('/chat/pinned')) data = []
    return route.fulfill({json: {code: 0, message: '请求成功', data}})
  })
  await page.route('**/webui/meta/**', route => route.fulfill({json: {appId: '', botOpenId: ''}}))
  await page.goto('/webui/')
  await expect(page.getByRole('button', {name: '当前账号', exact: true})).toHaveCount(1)
})

for (const qq of [false, true]) {
for (const role of ['ADMIN', 'OWNER']) {
  test(`webui shows the current ${role} account with QQ adaptation ${qq} and logs out`, async ({page, isMobile}, testInfo) => {
    const errors: string[] = []
    let loggedOut = false
    page.on('pageerror', error => errors.push(error.message))
    await page.route('**/webui/api/**', route => {
      const path = new URL(route.request().url()).pathname
      if (path.endsWith('/events')) return route.fulfill({contentType: 'text/event-stream', body: ': ok\n\n'})
      let data: unknown = {}
      if (path.endsWith('/auth/verify')) data = {userId: 'owner', displayName: 'YZ_Ljc_', role, avatarUrl: null}
      else if (path.endsWith('/config')) data = {appId: 'test-app', botOpenId: 'test-bot', botName: 'Test Bot'}
      else if (path.endsWith('/chat/conversations')) data = {items: [], hasMore: false, cursor: '0'}
      else if (path.endsWith('/chat/changes')) data = {changes: [], cursor: '0'}
      else if (path.endsWith('/chat/pinned')) data = []
      else if (path.endsWith('/feedback/count') || path.endsWith('/gallery/count')) data = {pending: 0}
      return route.fulfill({json: {code: 0, message: '请求成功', data}})
    })
    await page.route('**/webui/meta/**', route => route.fulfill({json: {appId: '', botOpenId: ''}}))
    await page.route('https://thirdqq.qlogo.cn/**', route => route.fulfill({contentType: 'image/svg+xml', body:
      '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" rx="50" fill="#c9eaff"/><text x="50" y="68" text-anchor="middle" fill="#168cdb" font-size="56">A</text></svg>'}))
    await page.route('**/dashboard/api/auth/logout', route => {
      loggedOut = true
      expect(route.request().method()).toBe('POST')
      return route.fulfill({status: 204})
    })
    await page.route(/\/dashboard\/(?:\?.*)?$/, route => route.fulfill({contentType: 'text/html; charset=utf-8', body: '<h1>QQ 个人中心</h1>'}))
    await page.goto(qq ? '/webui/?qq=1' : '/webui/')
    if (isMobile) await page.getByRole('button', {name: '打开侧边栏'}).click()
    const account = page.getByRole('button', {name: '当前账号', exact: true})
    await expect(account).toBeVisible()
    await expect(page).toHaveTitle(qq ? '\u200b' : 'AtriBot WebUI')
    const shell = await page.locator('.shell').boundingBox()
    expect(shell?.y).toBe(qq ? 44 : 0)
    expect(Math.round((shell?.y || 0) + (shell?.height || 0))).toBe(await page.evaluate(() => innerHeight))
    if (qq) {
      await expect(page.locator('.qq-topbar')).toBeVisible()
      await expect(page.locator('.qq-topbar')).toHaveCSS('height', '44px')
      expect((await page.locator('.sidebar').boundingBox())?.y).toBe(44)
      await expect(page.locator('.qq-topbar-name')).toHaveText('Test Bot')
      await expect(page.locator('.qq-topbar-caption')).toHaveText('聊天')
      await expect(page.locator('.qq-topbar img')).toHaveAttribute('src', 'https://thirdqq.qlogo.cn/qqapp/test-app/test-bot/100')
      await expect(page.locator('.qq-topbar img')).toHaveJSProperty('naturalWidth', 100)
      await page.locator('.side-nav-item').filter({hasText: '发送日志'}).click()
      await expect(page).toHaveURL(/\/webui\/send-logs\?qq=1$/)
      await expect(page.locator('.qq-topbar-caption')).toHaveText('发送日志')
      if (isMobile) await page.getByRole('button', {name: '打开侧边栏'}).click()
      await page.locator('.side-nav-item').filter({hasText: /^聊天$/}).click()
      await expect(page.locator('.qq-topbar-caption')).toHaveText('聊天')
      if (isMobile) await page.getByRole('button', {name: '打开侧边栏'}).click()
    } else await expect(page.locator('.qq-topbar')).toHaveCount(0)
    await expect(account).toContainText('YZ_Ljc_')
    await expect(account).toContainText(role === 'OWNER' ? '所有者' : '管理员')
    await account.click()
    await expect(page.getByRole('menuitem', {name: /个人档案/})).toHaveAttribute('href', '/dashboard/?panel=personal' + (qq ? '&qq=1' : ''))
    await expect(page.getByRole('menuitem', {name: /返回工作台/})).toHaveAttribute('href', qq ? '/dashboard/?qq=1' : '/dashboard/')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({path: testInfo.outputPath('webui-account-menu.png'), fullPage: true})
    if (!isMobile) {
      await account.click()
      await page.getByRole('button', {name: '收窄导航栏'}).click()
      await expect(page.locator('.sidebar')).toHaveCSS('width', '64px')
      await account.click()
      await expect(page.getByRole('menuitem', {name: /返回工作台/})).toBeInViewport()
    }
    await page.getByRole('menuitem', {name: /退出登录/}).click()
    await expect(page.getByRole('heading', {name: 'QQ 个人中心'})).toBeVisible()
    await expect(page).toHaveURL(qq ? /\/dashboard\/\?qq=1$/ : /\/dashboard\/$/)
    expect(loggedOut).toBe(true)
    expect(errors).toEqual([])
  })
}

}

for (const status of [401, 403, 503]) {
  test(`webui returns to the unified entry after authentication status ${status}`, async ({page}) => {
    let privateRequests = 0
    await page.route('**/webui/api/**', route => {
      if (!route.request().url().endsWith('/auth/verify')) privateRequests++
      return route.fulfill({status, json: {status, message: 'denied'}})
    })
    await page.route(/\/dashboard\/(?:\?.*)?$/, route => route.fulfill({contentType: 'text/html; charset=utf-8', body: '<h1>QQ 个人中心</h1>'}))
    await page.goto('/webui/')
    await expect(page.getByRole('heading', {name: 'QQ 个人中心'})).toBeVisible()
    expect(privateRequests).toBe(0)
    await expect(page.locator('input[type="password"]')).toHaveCount(0)
  })
}
