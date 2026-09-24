import { test, expect } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { profile } from './profile-fixture'
import { inventoryImage, activityResponse } from './activity-fixture'

const ticket = 'a'.repeat(43)
const token = 'b'.repeat(43)
const url = `/atrimeow/profile/?_nav_alpha=0&userId=owner&ticket=${ticket}`

test('responsive entry, in-memory session, refresh and replay rejection', async ({ page, context }, testInfo) => {
  let exchanges = 0
  await context.route('https://thirdqq.qlogo.cn/**', async route => route.fulfill({ contentType: 'image/png', body: await readFile('../src/main/resources/official-webui/img/atri-main.png') }))
  await context.route('**/atrimeow/profile/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/inventory/image')) return route.fulfill({ contentType: 'image/png', body: inventoryImage })
    const activityData = activityResponse(route.request().url())
    if (activityData) return route.fulfill({ json: activityData })
    if (path.endsWith('/exchange')) {
      exchanges++
      expect(route.request().postDataJSON()).toEqual({ userId: 'owner', ticket })
      return route.fulfill(exchanges === 1
        ? { json: { token, user: { userId: 'owner', displayName: '亚托利' }, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000 } }
        : { status: 401, json: { error: 'ENTRY_EXPIRED' } })
    }
    if (path.endsWith('/close')) expect(route.request().postData()).toBe(token)
    if (path.endsWith('/profile')) {
      expect(route.request().headers().authorization).toBe(`Bearer ${token}`)
      return route.fulfill({ json: profile })
    }
    return route.fulfill({ status: 204 })
  })
  await page.goto(url)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('亚托利喵用户档案')
  await expect(page.getByRole('img', { name: '机器人头像' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '亚托利', exact: true })).toBeVisible()
  await expect(page.getByRole('img', { name: '用户头像' })).toBeVisible()
  await expect(page.locator('.activity-row').filter({ hasText: '私聊消息' })).toContainText('128 条已记录消息')
  await expect(page.locator('[data-game=reaction]')).toContainText('18.24')
  await expect(page.getByRole('img', { name: '物品卡背包总览' })).toBeVisible()
  expect(await page.evaluate(() => getComputedStyle(document.documentElement).backgroundColor)).toBe('rgb(244, 245, 240)')
  await expect(page).toHaveURL(/\/atrimeow\/profile\/\?_nav_alpha=0$/)
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  expect(await context.cookies()).toEqual([])
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('ready.png'), fullPage: true, animations: 'disabled' })
  await page.reload()
  await expect(page.getByRole('heading', { level: 2, name: '当前会话已过期' })).toBeVisible()
  expect(exchanges).toBe(1)
  await page.goto(url)
  await expect(page.getByRole('heading', { level: 2, name: '当前会话已过期' })).toBeVisible()
  expect(exchanges).toBe(2)
  await page.screenshot({ path: testInfo.outputPath('expired.png'), fullPage: true, animations: 'disabled' })
})

test('pagehide and cached pageshow never restore the previous identity', async ({ page }) => {
  await page.route('**/atrimeow/profile/api/**', route => route.request().url().endsWith('/exchange')
    ? route.fulfill({ json: { token, user: { userId: 'owner', displayName: '亚托利' }, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000 } })
    : route.request().url().endsWith('/profile') ? route.fulfill({ json: { ...profile, avatarUrl: null, bot: { ...profile.bot, avatarUrl: null } } }) : route.fulfill({ status: 204 }))
  await page.goto(url)
  await expect(page.getByRole('heading', { name: '亚托利', exact: true })).toBeVisible()
  await page.evaluate(() => {
    window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true }))
    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }))
  })
  await expect(page.getByRole('heading', { level: 2, name: '当前会话已过期' })).toBeVisible()
  await expect(page.getByRole('button', { name: '退出' })).toHaveCount(0)
})

test('direct entry is locked and makes no authorization request', async ({ page }) => {
  let apiRequests = 0
  page.on('request', request => { if (request.url().includes('/atrimeow/profile/api/')) apiRequests++ })
  await page.goto('/atrimeow/profile/')
  await expect(page.getByRole('heading', { level: 2, name: '当前会话已过期' })).toBeVisible()
  expect(apiRequests).toBe(0)
})
