import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { inventoryImage, activity, activityResponse } from './activity-fixture'
import { profile } from './profile-fixture'

const token = 'b'.repeat(43)
const entry = `/atrimeow/profile/?userId=owner&ticket=${'a'.repeat(43)}`

test('game records, settings and cached inventory image use private requests and clear on exit', async ({ page }) => {
  let imageCalls = 0
  let gainCalls = 0
  let finishImageRefresh!: () => void
  const imageRefresh = new Promise<void>(resolve => { finishImageRefresh = resolve })
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
    if (path.endsWith('/inventory/image')) { imageCalls++; if (imageCalls > 1) await imageRefresh; return route.fulfill({ contentType: 'image/png', body: inventoryImage }) }
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
  await expect(page.locator('.free-draw')).toHaveText('今日免费抽卡可用')
  const image = page.getByRole('img', { name: '物品卡背包总览' })
  await expect(image).toBeVisible()
  await expect(image).toHaveAttribute('src', /^blob:/)
  const firstUrl = await image.getAttribute('src')
  expect(imageCalls).toBe(1)
  await page.getByRole('button', { name: '更新记录', exact: true }).click()
  await expect(image).toHaveAttribute('src', firstUrl!)
  expect(imageCalls).toBe(1)
  const refreshImage = page.getByLabel('物品卡背包', { exact: true }).getByRole('button', { name: '更新', exact: true })
  await refreshImage.click()
  await expect.poll(() => imageCalls).toBe(2)
  await expect(refreshImage).toBeDisabled()
  await expect(refreshImage.locator('svg')).toHaveCSS('animation-name', 'refresh-spin')
  await expect(refreshImage).toHaveCSS('cursor', 'default')
  finishImageRefresh()
  await expect(refreshImage).toBeEnabled()
  await expect(refreshImage.locator('svg')).not.toHaveClass(/refresh-spinning/)
  await expect(image).not.toHaveAttribute('src', firstUrl!)
  const finalUrl = await image.getAttribute('src')
  await expect(page.getByText('金粒来源', { exact: true })).toHaveCount(0)
  expect(gainCalls).toBe(0)
  await expect(page.locator('.stats-grid')).toHaveCount(0)
  await expect(page.locator('.activity-card')).toContainText('金粒余额2,680粒')
  expect(await page.locator('.activity-panel').evaluate(el => el.previousElementSibling?.classList.contains('profile-details'))).toBe(true)
  expect(await page.locator('.inventory-section').evaluate(el => !el.nextElementSibling)).toBe(true)
  await page.setViewportSize({ width: 320, height: 740 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pagehide')))
  await expect(page.locator('.activity-panel')).toHaveCount(0)
  expect(await page.evaluate(async url => { try { await fetch(url!); return false } catch { return true } }, finalUrl)).toBe(true)
  expect(errors).toEqual([])
})

test('missing game records differ from failures and inventory image retries rendering', async ({ page }) => {
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
  await expect(page.locator('.free-draw')).toContainText('暂时无法读取')
  await page.locator('.inventory-section').getByRole('button', { name: '重试' }).click()
  await expect(page.getByRole('img', { name: '物品卡背包总览' })).toBeVisible()
  expect(inventoryCalls).toBe(2)
})

test('inventory viewer supports zoom, pan, fit, touch gestures and cleanup', async ({ page, context }, testInfo) => {
  let imageCalls = 0
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  const largeImage = readFileSync('../src/main/resources/official-webui/img/atri-main.png')
  await page.route('**/atrimeow/profile/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/exchange')) return route.fulfill({ json: { token, user: { userId: 'owner', displayName: '' }, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000 } })
    if (path.endsWith('/profile')) return route.fulfill({ json: { ...profile, avatarUrl: null, bot: { ...profile.bot, avatarUrl: null } } })
    if (path.endsWith('/activity')) return route.fulfill({ json: activity })
    if (path.endsWith('/inventory/image')) { imageCalls++; return route.fulfill({ contentType: 'image/png', body: largeImage }) }
    return route.fulfill({ status: 204 })
  })
  await page.goto(entry)
  await expect(page.locator('.intro-screen')).toHaveCount(0)
  await expect(page.locator('.gold-icon')).toBeVisible()
  await expect.poll(() => page.locator('.gold-icon').evaluate(el => (el as HTMLImageElement).naturalWidth)).toBeGreaterThan(0)
  await page.screenshot({ path: testInfo.outputPath('profile.png'), animations: 'disabled' })
  const preview = page.getByRole('button', { name: '放大查看物品卡背包' })
  const thumbnail = preview.locator('img')
  await expect(thumbnail).toHaveCSS('max-height', 'none')
  await expect.poll(() => thumbnail.evaluate(el => {
    const image = el as HTMLImageElement
    return Math.abs(image.clientHeight - image.clientWidth * image.naturalHeight / image.naturalWidth)
  })).toBeLessThan(2)
  await preview.click()
  const dialog = page.getByRole('dialog', { name: '物品卡背包大图' })
  const image = dialog.getByRole('img')
  await expect(dialog).toBeVisible()
  await expect.poll(() => image.evaluate(el => (el as HTMLImageElement).naturalWidth)).toBe(1024)
  const stage = dialog.locator('.viewer-stage')
  const box = (await stage.boundingBox())!
  const fitted = (await image.boundingBox())!
  expect(fitted.height).toBeLessThanOrEqual(box.height)
  expect(fitted.width).toBeLessThanOrEqual(box.width)
  await dialog.getByRole('button', { name: '放大图片', exact: true }).click()
  await expect(dialog.getByLabel('缩放比例')).toHaveText('125%')
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.wheel(0, -300)
  await expect(dialog.getByLabel('缩放比例')).not.toHaveText('125%')
  const before = await image.getAttribute('style')
  await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2 + 70, { steps: 6 })
  await page.mouse.up()
  await expect(image).not.toHaveAttribute('style', before!)
  await dialog.getByRole('button', { name: '适应屏幕' }).click()
  await expect(dialog.getByLabel('缩放比例')).toHaveText('100%')
  if (testInfo.project.name === 'mobile') {
    const cdp = await context.newCDPSession(page)
    const cx = box.x + box.width / 2, cy = box.y + box.height / 2
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: cx - 30, y: cy, id: 1 }, { x: cx + 30, y: cy, id: 2 }] })
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: cx - 75, y: cy, id: 1 }, { x: cx + 75, y: cy, id: 2 }] })
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
    await expect.poll(async () => Number((await dialog.getByLabel('缩放比例').innerText()).replace('%', ''))).toBeGreaterThan(150)
    await cdp.detach()
  }
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await expect(preview).toBeFocused()
  expect(await page.evaluate(() => document.body.style.overflow)).not.toBe('hidden')
  await preview.click()
  await expect(dialog.getByLabel('缩放比例')).toHaveText('100%')
  expect(imageCalls).toBe(1)
  const close = dialog.getByRole('button', { name: '关闭大图' })
  if (testInfo.project.name === 'mobile') expect((await close.boundingBox())!.y).toBeGreaterThanOrEqual(74)
  await close.click()
  await expect(dialog).toHaveCount(0)
  await preview.click()
  await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pagehide')))
  await expect(dialog).toHaveCount(0)
  expect(await page.evaluate(() => document.body.style.overflow)).not.toBe('hidden')
  expect(errors).toEqual([])
})
