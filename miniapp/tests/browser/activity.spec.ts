import { test, expect } from '@playwright/test'

import { inventoryImage, activity, activityResponse } from './activity-fixture'
import { profile } from './profile-fixture'

const token = 'b'.repeat(43)
const entry = `/atrimeow/profile/?userId=owner&ticket=${'a'.repeat(43)}`

test('game records and settings use private requests without loading inventory images', async ({ page }) => {
  let imageCalls = 0
  let gainCalls = 0
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('https://thirdqq.qlogo.cn/**', route => route.abort())
  await page.route('**/atrimeow/profile/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/gains')) gainCalls++
    if (path.endsWith('/exchange')) return route.fulfill({ json: { token, user: { userId: 'owner', displayName: '' }, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000 } })
    if (path.endsWith('/close')) return route.fulfill({ status: 204 })
    expect(route.request().headers().authorization).toBe(`Bearer ${token}`)
    if (path.endsWith('/profile')) return route.fulfill({ json: profile })
    if (path.endsWith('/inventory/image')) { imageCalls++; return route.fulfill({ contentType: 'image/png', body: inventoryImage }) }
    return route.fulfill({ json: activityResponse(route.request().url()) })
  })
  await page.goto(entry)
  await expect(page.locator('[data-game=reaction]')).toContainText('18.24')
  await expect(page.locator('[data-game=sound] .game-score')).toContainText('80%')
  await expect(page.locator('.game-rank')).toContainText('排名：#12')
  await expect(page.locator('.game-card')).toHaveCount(6)
  await expect(page.getByLabel('用户设置')).toContainText('起床战争30')
  await expect(page).toHaveTitle('\u200b')
  await page.evaluate(() => {
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: {
      writeText: async (value: string) => { document.documentElement.dataset.copied = value }
    } })
  })
  await page.getByRole('button', { name: '复制分享链接' }).click()
  await expect(page.locator('.toast')).toHaveText('分享链接已复制')
  expect(await page.evaluate(() => document.documentElement.dataset.copied)).toBe(activity.settings!.share.url)
  await expect(page.locator('.share-url a')).toHaveCount(0)
  await expect(page.locator('.inventory-section')).toHaveCount(0)
  await page.getByRole('button', { name: '更新记录', exact: true }).click()
  expect(imageCalls).toBe(0)
  await expect(page.getByText('金粒来源', { exact: true })).toHaveCount(0)
  expect(gainCalls).toBe(0)
  await expect(page.locator('.stats-grid')).toHaveCount(0)
  await expect(page.locator('.activity-card')).toContainText('金粒余额2,680粒')
  expect(await page.locator('.activity-panel').evaluate(el => el.previousElementSibling?.classList.contains('profile-details'))).toBe(true)
  await page.setViewportSize({ width: 320, height: 740 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pagehide')))
  await expect(page.locator('.activity-panel')).toHaveCount(0)
  expect(errors).toEqual([])
})

test('missing game records differ from failures without an inventory region', async ({ page }) => {
  let inventoryCalls = 0
  await page.route('**/atrimeow/profile/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/exchange')) return route.fulfill({ json: { token, user: { userId: 'owner', displayName: '' }, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000 } })
    if (path.endsWith('/profile')) return route.fulfill({ json: { ...profile, avatarUrl: null, bot: { ...profile.bot, avatarUrl: null } } })
    if (path.endsWith('/activity')) return route.fulfill({ json: { ...activity, reaction: null, freeDraw: null, games: [], settings: null, unavailable: ['freeDraw', 'settings'] } })
    if (path.endsWith('/inventory/image')) {
      inventoryCalls++
      return route.fulfill(inventoryCalls > 1 ? { contentType: 'image/png', body: inventoryImage } : { status: 502 })
    }
    return route.fulfill({ status: 204 })
  })
  await page.goto(entry)
  await expect(page.locator('[data-game=reaction]')).toContainText('最好用时—')
  await expect(page.locator('[data-game=sound] .game-score')).toContainText('正确率—')
  await expect(page.locator('.settings-card')).toContainText('设置暂时无法读取')
  await expect(page.locator('.inventory-section')).toHaveCount(0)
  expect(inventoryCalls).toBe(0)
})
