import { test, expect } from '@playwright/test'
import { profile } from './profile-fixture'

test('missing names, failed avatars and partial data are explicit; long IDs fit narrow screens', async ({ page }) => {
  const userId = '0123456789ABCDEF'.repeat(4)
  await page.route('https://thirdqq.qlogo.cn/**', route => route.abort())
  await page.route('**/atrimeow/profile/api/**', route => {
    if (route.request().url().endsWith('/exchange')) return route.fulfill({ json: {
      token: 'b'.repeat(43), user: { userId, displayName: 'Do not treat session hints as recorded usernames' },
      expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000
    } })
    if (route.request().url().endsWith('/profile')) return route.fulfill({ json: {
      ...profile, userId, username: null, account: null, coins: null, signIn: null, unavailable: ['coins']
    } })
    return route.fulfill({ status: 204 })
  })
  await page.goto(`/atrimeow/profile/?userId=${userId}&ticket=${'a'.repeat(43)}`)
  await expect(page.locator('.dashboard')).toHaveAttribute('aria-busy', 'false')
  await expect(page.getByRole('img', { name: '默认头像' })).toBeVisible()
  await expect(page.locator('.profile-fields > div').first()).toContainText('暂无记录')
  await expect(page.locator('.profile-fields > div').filter({ hasText: '绑定 QQ 号' })).toContainText('未绑定')
  await expect(page.locator('.activity-row').filter({ hasText: '金粒余额' })).toContainText('暂时无法读取')
  await expect(page.locator('.activity-row').filter({ hasText: '最近签到' })).toContainText('暂无记录')
  await expect(page.getByText('Do not treat session hints as recorded usernames')).toHaveCount(0)
  await page.setViewportSize({ width: 320, height: 740 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.evaluate(() => {
    window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true }))
    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }))
  })
  await expect(page.getByRole('heading', { level: 2, name: '当前会话已过期' })).toBeVisible()
  await expect(page.getByText(userId, { exact: true })).toHaveCount(0)
})

test('profile request can be retried without reusing the entry ticket', async ({ page }) => {
  let calls = 0
  await page.route('**/atrimeow/profile/api/**', route => {
    if (route.request().url().endsWith('/exchange')) return route.fulfill({ json: {
      token: 'b'.repeat(43), user: { userId: 'owner', displayName: '' }, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000
    } })
    if (route.request().url().endsWith('/profile')) {
      calls++
      return calls === 1 ? route.fulfill({ status: 500 }) : route.fulfill({ json: { ...profile, avatarUrl: null, bot: { ...profile.bot, avatarUrl: null } } })
    }
    return route.fulfill({ status: 204 })
  })
  await page.goto(`/atrimeow/profile/?userId=owner&ticket=${'a'.repeat(43)}`)
  await page.locator('.notice').getByRole('button', { name: '重试' }).click()
  await expect(page.getByRole('heading', { name: '亚托利', exact: true })).toBeVisible()
  await expect(page.locator('.profile-fields > div').filter({ hasText: '绑定 QQ 号' })).toContainText('123456789')
  expect(calls).toBe(2)
})
