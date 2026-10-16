import {test, expect} from '@playwright/test'
import {profile} from './profile-fixture'
import {activityResponse} from './activity-fixture'

const ticket = 'a'.repeat(43)
const token = 'b'.repeat(43)

test('binding entry removes secrets, retains navigation and starts QQ authorization only after confirmation', async ({page}, testInfo) => {
  let starts = 0
  await page.route('**/dashboard/api/auth/**', async route => {
    expect(route.request().url()).toContain('/start')
    starts++
    expect(route.request().postDataJSON()).toMatchObject({userOpenId: 'owner', ticket, nav: '0', navAlpha: '0'})
    return route.fulfill({json: {url: `https://graph.qq.com/oauth2.0/authorize?state=${ticket}`}})
  })
  await page.route('https://graph.qq.com/**', route => route.fulfill({contentType: 'text/html', body: '<h1>QQ Authorization</h1>'}))
  await page.goto(`/dashboard/?userOpenId=owner&ticket=${ticket}&_nav=0&_nav_alpha=0`)
  await expect(page.getByRole('heading', {name: '绑定机器人账号'})).toBeVisible()
  expect(starts).toBe(0)
  await expect(page).toHaveURL(/\/dashboard\/\?_nav=0&_nav_alpha=0$/)
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({path: testInfo.outputPath('dashboard-binding.png'), fullPage: true})
  await page.getByRole('button', {name: 'QQ 登录并绑定'}).click()
  await expect(page.getByRole('heading', {name: 'QQ Authorization'})).toBeVisible()
  expect(starts).toBe(1)
})

test('QQ login reuses the profile page and can log out without using the old ticket exchange', async ({page}) => {
  let loggedOut = false
  await page.route('**/dashboard/api/auth/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/logout')) {
      loggedOut = true
      return route.fulfill({status: 204})
    }
    expect(path).toContain('/session')
    return route.fulfill({json: {status: 'ready', access: {token, user: {userId: 'owner', displayName: '亚托利'},
      expiresAt: Date.now() + 7_200_000, idleTimeoutMillis: 90_000}}})
  })
  await page.route('**/atrimeow/profile/api/**', route => {
    const path = new URL(route.request().url()).pathname
    expect(path).not.toContain('/exchange')
    if (path.endsWith('/profile')) return route.fulfill({json: {...profile, avatarUrl: null, bot: {...profile.bot, avatarUrl: null}}})
    const activity = activityResponse(route.request().url())
    if (activity) return route.fulfill({json: activity})
    return route.fulfill({status: 204})
  })
  await page.goto('/dashboard/')
  await expect(page.getByRole('heading', {name: '亚托利', exact: true})).toBeVisible()
  await page.getByRole('button', {name: '退出登录', exact: true}).click()
  await expect(page.getByRole('heading', {name: '使用 QQ 登录'})).toBeVisible()
  expect(loggedOut).toBe(true)
  await expect(page.getByRole('heading', {name: '亚托利', exact: true})).toHaveCount(0)
})

test('unbound QQ accounts receive binding instructions and malformed links cannot start login', async ({page}) => {
  await page.route('**/dashboard/api/auth/session', route => route.fulfill({json: {status: 'unbound'}}))
  await page.goto('/dashboard/')
  await expect(page.getByRole('heading', {name: '尚未绑定机器人账号'})).toBeVisible()
  await expect(page.locator('.entry-description')).toContainText('/dash')
  await page.goto(`/dashboard/?userOpenId=owner&ticket=${ticket}&ticket=${ticket}`)
  await expect(page.getByRole('button', {name: 'QQ 登录并绑定'})).toBeDisabled()
  await expect(page.getByRole('alert')).toContainText('绑定链接已失效')
})
