import {expect, test} from '@playwright/test'

test('debug catalog and request editor work on desktop and mobile', async ({page}, testInfo) => {
  const requests: any[] = []
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/webui/api/**', route => {
    const path = new URL(route.request().url()).pathname
    const ok = (data: unknown) => route.fulfill({json: {code: 0, data}})
    if (path.endsWith('/auth/verify')) return ok({role: 'ADMIN', displayName: '管理员'})
    if (path.endsWith('/config')) return ok({debugGroupOpenId: 'test-group', superAdminOpenId: 'test-user'})
    if (path.endsWith('/debug/official/request')) {
      const request = route.request().postDataJSON()
      requests.push(request)
      return ok({statusCode: 200, durationMillis: 42, method: request.method, url: request.path, headers: {'content-type': 'application/json'}, body: '{"id":"preview-message"}'})
    }
    return ok({})
  })
  await page.goto('http://127.0.0.1:5175/webui/debug')
  const mobile = testInfo.project.name === 'mobile'
  if (mobile) await page.getByRole('button', {name: '选择接口', exact: true}).click()
  await page.getByRole('textbox', {name: '搜索接口'}).fill('发送群聊消息')
  await page.locator('.debug-catalog-item').filter({hasText: '发送群聊消息'}).first().click()
  await expect(page.getByLabel('请求路径', {exact: true})).toHaveValue('/v2/groups/{group_openid}/messages')
  if (mobile) await expect(page.getByLabel('搜索接口')).not.toBeVisible()
  await page.getByLabel('请求内容', {exact: true}).fill('{"content":"test {group_openid}","msg_type":0}')
  await page.getByRole('tab', {name: 'Headers', exact: true}).click()
  await page.getByLabel('请求头', {exact: true}).fill('X-Test: preserved')
  await page.getByRole('button', {name: '发送请求', exact: true}).click()
  await expect(page.locator('.debug-output:visible')).toContainText('preview-message')
  expect(requests).toEqual([{method: 'POST', path: '/v2/groups/test-group/messages', headers: {'X-Test': 'preserved'}, body: '{"content":"test {group_openid}","msg_type":0}'}])
  await page.locator('form').getByRole('tab', {name: 'Body'}).click()
  await page.getByRole('button', {name: '放大', exact: true}).click()
  await expect(page.getByRole('dialog', {name: 'Body 编辑器'})).toBeVisible()
  await page.getByRole('button', {name: '关闭', exact: true}).click()
  await page.screenshot({path: testInfo.outputPath('debug-workbench.png'), fullPage: true})
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const pathBox = await page.getByLabel('请求路径', {exact: true}).boundingBox()
  expect(pathBox!.height).toBe(32)
  expect(errors).toEqual([])
})

test('catalog scroll is independent from request and response panels', async ({page}, testInfo) => {
  await page.route('**/webui/api/**', route => route.fulfill({json: {code: 0, data:
    route.request().url().includes('/auth/verify') ? {role: 'ADMIN'} : {}}}))
  await page.goto('http://127.0.0.1:5175/webui/debug')
  if (testInfo.project.name === 'mobile') await page.getByRole('button', {name: '选择接口', exact: true}).click()
  await page.getByLabel('搜索接口').fill('/')
  const catalog = page.locator('.debug-catalog-list')
  const main = page.locator('.debug-workbench > .connect-main')
  const initialMain = await main.boundingBox()
  await catalog.hover()
  await page.mouse.wheel(0, 500)
  await expect.poll(() => catalog.evaluate(el => el.scrollTop)).toBeGreaterThan(0)
  expect(await main.evaluate(el => el.scrollTop)).toBe(0)
  expect((await main.boundingBox())!.y).toBe(initialMain!.y)
  expect(await page.locator('.debug-workbench').evaluate(el => el.scrollTop)).toBe(0)

  await catalog.evaluate(el => { el.scrollTop = el.scrollHeight })
  const catalogScroll = await catalog.evaluate(el => el.scrollTop)
  await page.mouse.wheel(0, 500)
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
  expect(await main.evaluate(el => el.scrollTop)).toBe(0)

  const rect = (await main.boundingBox())!
  await page.mouse.move(rect.x + rect.width - 10, rect.y + 40)
  await page.mouse.wheel(0, 400)
  await expect.poll(() => main.evaluate(el => el.scrollTop)).toBeGreaterThan(0)
  expect(await catalog.evaluate(el => el.scrollTop)).toBe(catalogScroll)
  expect(await page.locator('.debug-workbench').evaluate(el => el.scrollTop)).toBe(0)
})
