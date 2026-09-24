import { test, expect } from '@playwright/test'
import { profile } from './profile-fixture'
import { activity, inventoryImage } from './activity-fixture'
import type { BindingChallenge, BoundGroup } from '../../src/groups'

const entry = `/atrimeow/profile/?userId=owner&ticket=${'a'.repeat(43)}`
const group: BoundGroup = { groupId: 'A'.repeat(32), groupNumber: '123456789', name: '亚托利的小花园',
  description: '一起聊天、搭建，分享 Minecraft 的日常。', category: '游戏', tags: ['Minecraft', '建筑', '生存'],
  memberCount: 328, joinedAt: '2026-06-20T10:00:00+08:00', botRole: 'admin', receiveMode: 'all', proactive: true,
  restricted: false, boundAt: '2026-09-20T10:00:00Z', available: true }

test('owner starts binding, copies proof, switches tabs and sees confirmed group data', async ({ page }, testInfo) => {
  let pending: BindingChallenge | null = null, verified = false, requests = 0
  const secondGroup = { ...group, groupId: 'B'.repeat(32), groupNumber: '987654321', name: '建筑交流小组', memberCount: 42, receiveMode: 'only_mention' }
  const removed = new Set<string>(), detailRequests: string[] = []
  let unbindRequests = 0, unbindOffline = true
  const errors: string[] = []
  const welcome = { enabled: true, custom: true, text: '**入群须知**\n\n- 请先阅读公告\n- [帮助](https://example.com/help)\n\n<script>window.badWelcome = true</script>', buttonSize: 'SMALL', keyboard: [[{ label: '帮助', style: 'BLUE' }, { label: '打卡', style: 'BLUE_WITH_BACKGROUND' }]] }
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/atrimeow/profile/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/exchange')) return route.fulfill({ json: { token: 'b'.repeat(43), user: { userId: 'owner', displayName: '' }, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000 } })
    expect(route.request().headers().authorization).toBe(`Bearer ${'b'.repeat(43)}`)
    if (path.endsWith('/profile')) return route.fulfill({ json: { ...profile, avatarUrl: null, bot: { ...profile.bot, avatarUrl: null } } })
    if (path.endsWith('/activity')) return route.fulfill({ json: activity })
    if (path.endsWith('/inventory/image')) return route.fulfill({ contentType: 'image/png', body: inventoryImage })
    if (path.endsWith('/groups/binding')) {
      requests++
      expect(route.request().postDataJSON()).toEqual({ groupId: '123456789' })
      pending = { groupId: group.groupId, code: 'AABBCCDDEEFF', command: '/群绑定 AABBCCDDEEFF', expiresAt: Date.now() + 600000 }
      return route.fulfill({ json: pending })
    }
    if (path.endsWith('/groups/unbinding')) {
      unbindRequests++
      if (unbindOffline) return route.fulfill({ status: 502, json: { error: 'GROUPS_UNAVAILABLE' } })
      removed.add(route.request().postDataJSON().groupId)
      return route.fulfill({ json: { success: true } })
    }
    if (path.endsWith('/groups')) return route.fulfill({ json: { groups: verified ? [group, secondGroup].filter(item => !removed.has(item.groupId)).map(({ groupId, name, groupNumber, boundAt }) => ({ groupId, name, groupNumber, boundAt })) : [], pending: verified ? null : pending } })
    if (path.endsWith('/join-welcome')) return route.fulfill({ json: path.includes(group.groupId) ? welcome : { ...welcome, text: '欢迎来到建筑交流小组', enabled: false, custom: false } })
    if (path.includes('/groups/')) {
      detailRequests.push(path)
      return route.fulfill({ json: path.endsWith(group.groupId) ? group : secondGroup })
    }
    return route.fulfill({ status: 204 })
  })
  await page.goto(entry)
  const dock = page.getByRole('navigation', { name: '栏目导航' })
  await dock.getByRole('button', { name: '群管理' }).click()
  await page.getByLabel('群号或群开放平台 ID').fill('123456789')
  await page.getByRole('button', { name: '生成验证指令' }).click()
  await expect(page.locator('.group-command')).toContainText('/群绑定 AABBCCDDEEFF')
  await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: async (value: string) => { document.documentElement.dataset.copied = value } } }))
  await page.getByRole('button', { name: '复制群绑定指令' }).click()
  await expect(page.locator('.toast')).toHaveText('验证指令已复制')
  expect(await page.evaluate(() => document.documentElement.dataset.copied)).toBe('/群绑定 AABBCCDDEEFF')
  await page.screenshot({ path: testInfo.outputPath('binding.png'), animations: 'disabled' })
  await dock.getByRole('button', { name: '我的档案' }).click()
  await dock.getByRole('button', { name: '群管理' }).click()
  await expect(page.locator('.group-command')).toContainText('AABBCCDDEEFF')
  expect(requests).toBe(1)
  verified = true
  await page.getByRole('button', { name: '我已发送' }).click()
  const firstRow = page.getByRole('button', { name: `查看${group.name}详情` })
  const secondRow = page.getByRole('button', { name: `查看${secondGroup.name}详情` })
  await expect(firstRow).toBeVisible()
  await expect(secondRow).toBeVisible()
  await expect(page.getByText('群聊绑定成功', { exact: true })).toBeVisible()
  await expect(page.locator('.group-command')).toHaveCount(0)
  await expect(page.locator('.group-details')).toHaveCount(0)
  await expect(firstRow).toContainText('添加时间2026.09.20')
  await expect(firstRow).not.toContainText('328')
  expect(detailRequests).toEqual([])
  const firstBox = (await firstRow.boundingBox())!, secondBox = (await secondRow.boundingBox())!
  expect(secondBox.y).toBeGreaterThanOrEqual(firstBox.y + firstBox.height)
  expect(secondBox.x).toBe(firstBox.x)
  await page.screenshot({ path: testInfo.outputPath('group-list.png'), animations: 'disabled' })
  await firstRow.click()
  await expect(page.getByRole('article', { name: group.name! })).toBeVisible()
  await expect(page.getByRole('list', { name: '已绑定群列表' })).toHaveCount(0)
  await expect(page.locator('.group-badges')).toContainText('328 人')
  await expect(page.locator('.group-badges')).toContainText('机器人 · 管理员')
  await expect(page.locator('.group-details')).toContainText('全部消息')
  await expect(page.locator('.group-dates')).toContainText('2026.06.20')
  await expect(page.locator('.group-tags')).toContainText('Minecraft')
  const welcomePanel = page.getByRole('region', { name: '加群欢迎', exact: true })
  await expect(welcomePanel.locator('strong')).toHaveText('入群须知')
  await expect(welcomePanel.locator('.welcome-mention')).toHaveText('@新成员')
  await expect(welcomePanel.locator('.welcome-qq-button')).toHaveCount(2)
  await expect(welcomePanel.locator('input, textarea, select, a, script')).toHaveCount(0)
  await expect(welcomePanel.getByRole('button')).toHaveCount(1)
  await welcomePanel.locator('.welcome-qq-button').first().click()
  await expect(page.getByRole('article', { name: group.name! })).toBeVisible()
  await welcomePanel.screenshot({ path: testInfo.outputPath('welcome.png'), animations: 'disabled' })
  await page.screenshot({ path: testInfo.outputPath('groups.png'), animations: 'disabled' })
  await page.setViewportSize({ width: 320, height: 740 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: '返回群列表' }).click()
  await expect(firstRow).toBeFocused()
  await expect(page.locator('.group-details')).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await secondRow.click()
  await expect(page.getByRole('article', { name: secondGroup.name })).toBeVisible()
  await expect(page.getByRole('article', { name: group.name! })).toHaveCount(0)
  await expect(page.locator('.group-details')).toContainText('仅接收 @ 消息')
  await expect(welcomePanel).toContainText('欢迎来到建筑交流小组')
  await expect(welcomePanel).toContainText('已关闭')
  await expect(welcomePanel).not.toContainText('入群须知')
  expect(detailRequests).toHaveLength(2)
  await page.getByRole('button', { name: '解除绑定', exact: true }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  expect(unbindRequests).toBe(0)
  await page.getByRole('button', { name: '解除绑定', exact: true }).click()
  await page.getByRole('button', { name: '确认解绑', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('解绑失败')
  await expect(page.getByRole('article', { name: secondGroup.name })).toBeVisible()
  unbindOffline = false
  await page.getByRole('button', { name: '确认解绑', exact: true }).click()
  await expect(firstRow).toBeVisible()
  await expect(secondRow).toHaveCount(0)
  await expect(page.getByText('已解除群绑定', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '刷新群资料' }).click()
  await expect(secondRow).toHaveCount(0)
  await firstRow.click()
  await page.getByRole('button', { name: '解除绑定', exact: true }).click()
  await page.getByRole('button', { name: '确认解绑', exact: true }).click()
  await expect(page.getByRole('heading', { name: '绑定你的群' })).toBeVisible()
  await expect(page.getByRole('list', { name: '已绑定群列表' })).toHaveCount(0)
  expect(errors).toEqual([])
})

test('errors stay retryable and server pending binding resumes on a fresh entry', async ({ page }) => {
  let offline = true, restorePending = false, accepted = false
  await page.route('**/atrimeow/profile/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/exchange')) return route.fulfill({ json: { token: 'b'.repeat(43), user: { userId: 'owner', displayName: '' }, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000 } })
    if (path.endsWith('/profile')) return route.fulfill({ json: { ...profile, avatarUrl: null, bot: { ...profile.bot, avatarUrl: null } } })
    if (path.endsWith('/activity')) return route.fulfill({ json: activity })
    if (path.endsWith('/inventory/image')) return route.fulfill({ contentType: 'image/png', body: inventoryImage })
    if (path.endsWith('/groups/binding')) return route.fulfill({ status: 404, json: { error: 'GROUP_NOT_FOUND' } })
    if (path.endsWith('/groups')) return route.fulfill(offline ? { status: 502 } : { json: { groups: accepted ? [group] : [], pending: restorePending && !accepted ? { groupId: group.groupId, code: 'AABBCCDDEEFF', command: '/群绑定 AABBCCDDEEFF', expiresAt: Date.now() + 600000 } : null } })
    return route.fulfill({ status: 204 })
  })
  await page.goto(entry)
  await page.getByRole('button', { name: '群管理', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('群资料暂时无法读取')
  offline = false
  await page.getByRole('button', { name: '刷新群资料' }).click()
  await page.getByLabel('群号或群开放平台 ID').fill('999999')
  await page.getByRole('button', { name: '生成验证指令' }).click()
  await expect(page.getByRole('alert')).toContainText('未找到群记录')
  await expect(page.getByRole('navigation', { name: '栏目导航' })).toBeVisible()
  restorePending = true
  await page.goto(entry)
  await page.getByRole('button', { name: '群管理', exact: true }).click()
  await expect(page.locator('.group-command')).toContainText('AABBCCDDEEFF')
  accepted = true
  await page.getByRole('button', { name: '我已发送' }).click()
  await expect(page.getByRole('button', { name: `查看${group.name}详情` })).toBeVisible()
  await expect(page.locator('.group-details')).toHaveCount(0)
})

test('welcome retries independently, renders images and math, and discards responses from a previous group', async ({ page }) => {
  const secondGroup = { ...group, groupId: 'B'.repeat(32), name: '第二个群' }
  let releaseFirst!: () => void
  const firstResponse = new Promise<void>(resolve => { releaseFirst = resolve })
  let firstStarted = false, firstSent = false, secondCalls = 0, mutations = 0
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('https://images.example.test/welcome.png', route => route.fulfill({ contentType: 'image/png', body: inventoryImage }))
  await page.route('**/atrimeow/profile/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/exchange')) return route.fulfill({ json: { token: 'b'.repeat(43), user: { userId: 'owner', displayName: '' }, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000 } })
    if (path.includes('/groups/') && route.request().method() !== 'GET') mutations++
    if (path.endsWith('/profile')) return route.fulfill({ json: { ...profile, avatarUrl: null, bot: { ...profile.bot, avatarUrl: null } } })
    if (path.endsWith('/activity')) return route.fulfill({ json: activity })
    if (path.endsWith('/inventory/image')) return route.fulfill({ contentType: 'image/png', body: inventoryImage })
    if (path.endsWith('/groups')) return route.fulfill({ json: { groups: [group, secondGroup], pending: null } })
    if (path.endsWith('/join-welcome')) {
      expect(route.request().headers().authorization).toBe(`Bearer ${'b'.repeat(43)}`)
      if (path.includes(group.groupId)) {
        firstStarted = true
        await firstResponse
        await route.fulfill({ json: { enabled: true, custom: true, text: '第一个群的旧内容', buttonSize: 'SMALL', keyboard: [] } })
        firstSent = true
        return
      }
      if (++secondCalls === 1) return route.fulfill({ status: 502, json: { error: 'WELCOME_UNAVAILABLE' } })
      return route.fulfill({ json: { enabled: false, custom: true, text: '**第二个群欢迎**\n\n$x^2$\n\n![欢迎图片 #320px #180px](https://images.example.test/welcome.png)', buttonSize: 'SMALL', keyboard: [[{ label: '只读按钮', style: 'RED' }]] } })
    }
    if (path.includes('/groups/')) return route.fulfill({ json: path.endsWith(group.groupId) ? group : secondGroup })
    return route.fulfill({ status: 204 })
  })
  await page.goto(entry)
  await page.getByRole('button', { name: '群管理', exact: true }).click()
  await page.getByRole('button', { name: `查看${group.name}详情` }).click()
  await expect.poll(() => firstStarted).toBe(true)
  await page.getByRole('button', { name: '返回群列表' }).click()
  await page.getByRole('button', { name: `查看${secondGroup.name}详情` }).click()
  const panel = page.getByRole('region', { name: '加群欢迎', exact: true })
  await expect(panel.getByRole('alert')).toContainText('欢迎内容暂时无法读取')
  await expect(page.getByRole('article', { name: secondGroup.name })).toBeVisible()
  await panel.getByRole('button', { name: '重试', exact: true }).click()
  await expect(panel.locator('strong')).toHaveText('第二个群欢迎')
  await expect(panel.locator('.katex')).toHaveCount(1)
  const picture = panel.getByRole('img', { name: '欢迎图片' })
  await expect(picture).toBeVisible()
  await expect(picture).toHaveAttribute('referrerpolicy', 'no-referrer')
  await expect(panel.locator('.welcome-qq-button')).toHaveClass(/RED/)
  await panel.locator('.welcome-qq-button').click()
  releaseFirst()
  await expect.poll(() => firstSent).toBe(true)
  await expect(panel).not.toContainText('第一个群的旧内容')
  await expect(panel).toContainText('第二个群欢迎')
  await page.setViewportSize({ width: 320, height: 740 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(mutations).toBe(0)
  expect(errors).toEqual([])
})
