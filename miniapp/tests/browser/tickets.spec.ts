import {test, expect} from '@playwright/test'
import {profile} from './profile-fixture'
import {activity} from './activity-fixture'

test('tickets share the existing thread, close without reopen, and fit the five-tab dock', async ({page}, testInfo) => {
  const id = '00000000-0000-0000-0000-000000000001'
  const ticket = {id, status: 'WAITING_USER', content: '初始问题', createdAt: '2026-10-28 10:00:00', updatedAt: '2026-10-28 10:10:00'}
  const entries = [{id: 2, type: 'REPLY', content: '管理员答复', createdAt: '2026-10-28 10:10:00'},
    {id: 1, type: 'SUBMIT', content: '初始问题', createdAt: '2026-10-28 10:00:00'}]
  await page.route('**/atrimeow/profile/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/exchange')) return route.fulfill({json: {token: 'b'.repeat(43), user: {userId: 'owner', displayName: '用户'}, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000}})
    if (path.endsWith('/profile')) return route.fulfill({json: {...profile, avatarUrl: null, bot: {...profile.bot, avatarUrl: null}}})
    if (path.endsWith('/activity')) return route.fulfill({json: {...activity, loot: {ownedCards: 125, collectedTypes: 30, collectedNormalTypes: 25, normalCatalogTypes: 100, normalCollectionCompletion: 25}, rankings: {loot: {ownedCards: 123}}}})
    if (path.endsWith('/tickets')) {
      if (route.request().method() === 'POST') {
        expect(route.request().postDataJSON()).toEqual({content: '网页补充'})
        ticket.status = 'PENDING'
        entries.unshift({id: 3, type: 'APPEND', content: '网页补充', createdAt: '2026-10-28 10:20:00'})
        return route.fulfill({json: {id, appended: true}})
      }
      return route.fulfill({json: {items: [ticket], hasMore: false}})
    }
    if (path.endsWith('/close')) {
      ticket.status = 'CLOSED'
      entries.unshift({id: 4, type: 'USER_CLOSE', content: '', createdAt: '2026-10-28 10:21:00'})
      return route.fulfill({json: {closed: true}})
    }
    if (path.endsWith('/tickets/' + id)) return route.fulfill({json: {ticket, entries: {items: entries, hasMore: false}}})
    return route.fulfill({status: 204})
  })
  await page.goto(`/atrimeow/profile/?userId=owner&ticket=${'a'.repeat(43)}`)
  const dock = page.getByRole('navigation', {name: '栏目导航'})
  await expect(dock).toBeVisible()
  await expect(dock.getByRole('button')).toHaveText(['我的档案', '我的数据', '群管理', '工单', '关于'])
  await dock.getByRole('button', {name: '工单', exact: true}).click()
  await expect(page.getByText('初始问题', {exact: true})).toBeVisible()
  await page.screenshot({path: testInfo.outputPath('tickets-list.png'), fullPage: true})
  await page.getByRole('button', {name: '提交工单', exact: true}).click()
  await page.getByLabel('提交内容', {exact: true}).fill('网页补充')
  await page.getByRole('button', {name: '提交工单', exact: true}).click()
  await expect(page.getByText('已补充到原工单', {exact: true})).toBeVisible()
  await expect(page.getByText('管理员答复', {exact: true})).toBeVisible()
  await page.getByRole('button', {name: '关闭工单', exact: true}).click()
  await page.getByRole('button', {name: '确认关闭', exact: true}).click()
  await expect(page.getByText('已关闭', {exact: true})).toBeVisible()
  await expect(page.getByRole('button', {name: /重新打开/})).toHaveCount(0)
  await expect(page.getByLabel('补充内容', {exact: true})).toHaveCount(0)
  await page.screenshot({path: testInfo.outputPath('tickets-detail.png'), fullPage: true})
  await dock.getByRole('button', {name: '我的数据'}).click()
  const loot = page.getByRole('region', {name: '抽卡统计'})
  await expect(loot).toContainText('125')
  await expect(loot).toContainText('25.00%')
  await expect(loot).toContainText('#123')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})
