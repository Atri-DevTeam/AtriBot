import {test, expect} from '@playwright/test'

test('connect API fills ticket, submits selected identity and displays authentication failures', async ({page}) => {
  let issued = 0
  const requests: URL[] = []
  await page.addInitScript(() => Object.defineProperty(navigator, 'clipboard', {value: {writeText: async (value: string) => { (window as any).__copiedConnect = value }}}))
  await page.route('**/webui/api/**', route => {
    const path = new URL(route.request().url()).pathname
    const ok = (data: unknown) => route.fulfill({json: {code: 0, data}})
    if (path.endsWith('/auth/verify')) return ok({role:'ADMIN',username:'管理员'})
    if (path.endsWith('/connect/ticket')) {
      expect(route.request().method()).toBe('POST')
      return ok({enabled:true,ticket:`test-ticket-${++issued}`,expiresAt:Date.now()+300000})
    }
    return ok({})
  })
  await page.route('**/connect/user/background?**', route => {
    const url = new URL(route.request().url())
    requests.push(url)
    if (url.searchParams.get('ticket') === 'invalid') return route.fulfill({status:401,json:{code:10002,message:'身份验证失败',data:null}})
    return route.fulfill({json:{code:0,data:{u_id:'account-uuid',b_id:'bot-id',background:{owner:'owner-hash',id:'approved-background'},submission:null}}})
  })
  await page.goto('http://127.0.0.1:5175/webui/connect')
  await expect(page.getByRole('heading',{name:'API接口'})).toBeVisible()
  await expect(page.getByLabel('ticket',{exact:true})).toHaveValue('test-ticket-1')
  await page.getByLabel('用户标识',{exact:true}).fill('bot-test')
  await page.getByRole('button',{name:'发送请求',exact:true}).click()
  await expect(page.locator('.connect-output')).toContainText('approved-background')
  expect(requests[0].searchParams.get('b_id')).toBe('bot-test')
  await page.getByRole('radio',{name:'c_id QQ 互联 OpenID'}).check()
  await page.getByLabel('用户标识',{exact:true}).fill('connect-test')
  await page.getByRole('button',{name:'发送请求',exact:true}).click()
  await expect.poll(() => requests.length).toBe(2)
  expect(requests[1].searchParams.get('c_id')).toBe('connect-test')
  expect(requests[1].searchParams.has('b_id')).toBe(false)
  await page.getByRole('button',{name:'复制请求地址',exact:true}).click()
  const copied = new URL(await page.evaluate(() => (window as any).__copiedConnect))
  expect(copied.searchParams.get('ticket')).toBe('test-ticket-1')
  await page.getByLabel('ticket',{exact:true}).fill('invalid')
  await page.getByRole('button',{name:'发送请求',exact:true}).click()
  await expect(page.locator('.connect-output')).toContainText('10002')
  await expect(page).toHaveURL(/\/webui\/connect$/)
  await page.getByRole('button',{name:'刷新凭证',exact:true}).click()
  await expect(page.getByLabel('ticket',{exact:true})).toHaveValue('test-ticket-2')
  await page.getByRole('tab',{name:'响应字段',exact:true}).click()
  await expect(page.getByText('background.owner',{exact:true})).toBeVisible()
  const widths = await page.evaluate(() => ({scroll:document.documentElement.scrollWidth,viewport:window.innerWidth}))
  expect(widths.scroll).toBeLessThanOrEqual(widths.viewport)
})
