import { test, expect } from '@playwright/test'
import { profile } from './profile-fixture'
import { activity, inventoryImage } from './activity-fixture'

test('dock switches sections without reopening the session or losing cached profile data', async ({ page }, testInfo) => {
  let exchanges = 0, imageCalls = 0, profileCalls = 0, aboutCalls = 0
  await page.route('https://thirdqq.qlogo.cn/**', route => route.fulfill({ contentType: 'image/png', body: inventoryImage }))
  await page.route('**/atrimeow/profile/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/exchange')) {
      exchanges++
      return route.fulfill({ json: { token: 'b'.repeat(43), user: { userId: 'owner', displayName: '' }, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000 } })
    }
    if (path.endsWith('/profile')) {
      profileCalls++
      return route.fulfill({ json: { ...profile, avatarUrl: null, bot: { ...profile.bot, avatarUrl: null } } })
    }
    if (path.endsWith('/about')) {
      aboutCalls++
      return route.fulfill({ json: { version: '3.2.3-Release', buildTime: '2026-09-26 12:00:00 CST', branch: 'main', commitId: 'abc1234', developerAvatarUrl: 'https://thirdqq.qlogo.cn/qqapp/test/admin/100' } })
    }
    if (path.endsWith('/activity')) return route.fulfill({ json: activity })
    if (path.endsWith('/groups')) return route.fulfill({ json: { groups: [], pending: null } })
    if (path.endsWith('/inventory/image')) { imageCalls++; return route.fulfill({ contentType: 'image/png', body: inventoryImage }) }
    return route.fulfill({ status: 204 })
  })
  await page.goto(`/atrimeow/profile/?_nav_alpha=0&userId=owner&ticket=${'a'.repeat(43)}`)
  const dock = page.getByRole('navigation', { name: '栏目导航' })
  const profileTab = dock.getByRole('button', { name: '我的档案' })
  const groupsTab = dock.getByRole('button', { name: '群管理' })
  const aboutTab = dock.getByRole('button', { name: '关于' })
  await expect(dock).toBeVisible()
  await expect(profileTab).toHaveAttribute('aria-current', 'page')
  expect((await profileTab.boundingBox())!.x).toBeLessThan((await groupsTab.boundingBox())!.x)
  expect((await groupsTab.boundingBox())!.x).toBeLessThan((await aboutTab.boundingBox())!.x)
  const image = page.getByRole('img', { name: '物品卡背包总览' })
  await expect(image).toHaveAttribute('src', /^blob:/)
  const cachedImage = await image.getAttribute('src')
  await page.screenshot({ path: testInfo.outputPath('dock-profile.png'), animations: 'disabled' })
  await page.evaluate(() => window.scrollTo(0, 600))
  const profileScroll = await page.evaluate(() => scrollY)
  const originalUrl = page.url()
  await groupsTab.click()
  await expect(groupsTab).toHaveAttribute('aria-current', 'page')
  await expect(page.locator('#profile-page')).toBeHidden()
  await expect(page.getByRole('heading', { name: '群管理', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '绑定你的群', exact: true })).toBeVisible()
  expect(await page.evaluate(() => scrollY)).toBe(0)
  await page.screenshot({ path: testInfo.outputPath('dock-groups.png'), animations: 'disabled' })
  expect(aboutCalls).toBe(0)
  await aboutTab.click()
  await expect(aboutTab).toHaveAttribute('aria-current', 'page')
  await expect(page.locator('#groups-page')).toBeHidden()
  await expect(page.locator('#about-page').getByRole('heading', { name: '亚托利喵' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '版本信息' })).toBeVisible()
  await expect(page.locator('#about-page')).toContainText('3.2.3-Release')
  await expect(page.getByRole('img', { name: 'YZ_Ljc_ 的头像' })).toHaveAttribute('src', 'https://thirdqq.qlogo.cn/qqapp/test/admin/100')
  await expect(page.getByRole('heading', { name: '反馈与联系' })).toBeVisible()
  await expect(page.getByRole('button', { name: /社区交流群.*818804507/ })).toBeVisible()
  await expect(page.getByRole('button', { name: /开发者 QQ.*3199590352/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /联系邮箱.*contact@yzljc.top/ })).toHaveAttribute('href', 'mailto:contact@yzljc.top')
  await expect(page.getByRole('button', { name: /反馈指令.*\/feedback/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /帮助文档/ })).toHaveAttribute('href', 'https://docs.qq.com/doc/DUHJQVG9VVE5yQU1S')
  await expect(page.getByRole('link', { name: /GitHub 开源仓库/ })).toHaveAttribute('href', 'https://github.com/Atri-DevTeam/AtriBot')
  await page.screenshot({ path: testInfo.outputPath('dock-about.png'), animations: 'disabled', fullPage: true })
  await profileTab.click()
  await expect(image).toHaveAttribute('src', cachedImage!)
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(profileScroll)
  expect(page.url()).toBe(originalUrl)
  expect({ exchanges, imageCalls, profileCalls, aboutCalls }).toEqual({ exchanges: 1, imageCalls: 1, profileCalls: 1, aboutCalls: 1 })
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
  const refresh = await page.getByRole('button', { name: '更新记录', exact: true }).boundingBox()
  expect(refresh!.y + refresh!.height).toBeLessThan((await dock.boundingBox())!.y)
  await page.setViewportSize({ width: 320, height: 740 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await aboutTab.click()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await groupsTab.click()
  await page.evaluate(() => {
    window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true }))
    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }))
  })
  await expect(dock).toHaveCount(0)
  await expect(page.locator('#groups-page')).toHaveCount(0)
  await expect(page.locator('#about-page')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '当前会话已过期' })).toBeVisible()
})
