import { test, expect } from '@playwright/test'
import { profile } from './profile-fixture'
import { activity, inventoryImage } from './activity-fixture'

const entry = `/atrimeow/profile/?userId=owner&ticket=${'a'.repeat(43)}`
const grant = () => ({ token: 'b'.repeat(43), user: { userId: 'owner', displayName: '' }, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000 })

test('intro waits for initial records, reveals once, and does not wait for the remote image', async ({ page }, testInfo) => {
  let recordsReady!: () => void
  let imageReady!: () => void
  const records = new Promise<void>(resolve => { recordsReady = resolve })
  const image = new Promise<void>(resolve => { imageReady = resolve })
  await page.route('**/atrimeow/profile/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/exchange')) return route.fulfill({ json: grant() })
    if (path.endsWith('/profile')) return route.fulfill({ json: { ...profile, avatarUrl: null, bot: { ...profile.bot, avatarUrl: null } } })
    if (path.endsWith('/activity')) { await records; return route.fulfill({ json: activity }) }
    if (path.endsWith('/gains')) return route.fulfill({ json: { items: [], offset: 0, limit: 8, hasMore: false, available: true } })
    if (path.endsWith('/inventory/image')) { await image; return route.fulfill({ contentType: 'image/png', body: inventoryImage }) }
    return route.fulfill({ status: 204 })
  })
  await page.goto(entry)
  await expect(page.getByLabel('正在加载用户档案')).toBeVisible()
  await expect(page.locator('.intro-caption')).toHaveText('正在读取资料')
  await expect(page.locator('.dashboard')).toHaveAttribute('inert', '')
  await expect(page.locator('.intro-portrait img')).toBeVisible()
  await expect.poll(() => page.locator('.intro-portrait img').evaluate(img => (img as HTMLImageElement).naturalWidth)).toBe(1024)
  await page.screenshot({ path: testInfo.outputPath('intro.png'), animations: 'disabled' })
  recordsReady()
  await expect(page.locator('.intro-screen')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '亚托利', exact: true })).toBeVisible()
  await expect(page.locator('[data-game=sound]')).toContainText('80%')
  await expect(page.locator('.inventory-section')).toContainText('正在生成背包图')
  await page.getByRole('button', { name: '更新记录', exact: true }).click()
  await expect(page.locator('.intro-screen')).toHaveCount(0)
  imageReady()
  await expect(page.getByRole('img', { name: '物品卡背包总览' })).toBeVisible()
})

test('slow requests do not trap the page in intro and reduced motion disables movement', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  let continueProfile!: () => void
  const pending = new Promise<void>(resolve => { continueProfile = resolve })
  await page.route('**/atrimeow/profile/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/exchange')) return route.fulfill({ json: grant() })
    if (path.endsWith('/profile')) { await pending; return route.fulfill({ json: { ...profile, avatarUrl: null, bot: { ...profile.bot, avatarUrl: null } } }) }
    return route.fulfill({ status: 502 })
  })
  await page.goto(entry)
  await expect(page.locator('.intro-screen')).toBeVisible()
  expect(await page.locator('.intro-portrait').evaluate(el => getComputedStyle(el).animationName)).toBe('none')
  await expect(page.locator('.intro-screen')).toHaveCount(0, { timeout: 5000 })
  await expect(page.locator('.topbar')).toBeVisible()
  expect(await page.locator('.profile-card').evaluate(el => getComputedStyle(el).animationName)).toBe('none')
  await page.evaluate(() => {
    window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true }))
    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }))
  })
  continueProfile()
  await expect(page.getByRole('heading', { name: '当前会话已过期' })).toBeVisible()
  await expect(page.locator('.dashboard')).toHaveCount(0)
})
