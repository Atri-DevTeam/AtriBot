import {test, expect} from '@playwright/test'
import {profile} from './profile-fixture'
import {activity} from './activity-fixture'
import type {GroupCommands} from '../../src/groups'

test('group command settings save scoped rules and respect changing administrator locks', async ({page}, testInfo) => {
  const group = {groupId: 'group-A', name: '测试群', available: true, restricted: false,
    proactive: true, tags: [], canManageModeration: false}
  const initial = {key: 'tictactoe', displayName: '井字棋', switchable: true, rateLimitable: true,
    enabled: true, cooldownSeconds: 0, scope: 'USER' as const, locked: false,
    lockReason: '', startsAt: null, endsAt: null}
  let current: GroupCommands = {items: [initial, {...initial, key: 'ec', displayName: '随机附魔',
    enabled: false, locked: true, lockReason: '功能维护', startsAt: '2026-10-24T00:00:00', endsAt: null}], canEdit: true}
  let failure = '', activateLock = false
  const writes: unknown[] = [], errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/atrimeow/profile/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/exchange')) return route.fulfill({json: {token: 'b'.repeat(43), user: {userId: 'owner', displayName: ''}, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000}})
    expect(route.request().headers().authorization).toBe(`Bearer ${'b'.repeat(43)}`)
    if (path.endsWith('/profile')) return route.fulfill({json: {...profile, avatarUrl: null, bot: {...profile.bot, avatarUrl: null}}})
    if (path.endsWith('/activity')) return route.fulfill({json: activity})
    if (path.endsWith('/groups')) return route.fulfill({json: {groups: [group], pending: null}})
    if (path.endsWith('/commands')) return route.fulfill({json: current})
    if (path.endsWith('/commands/tictactoe')) {
      const body = route.request().postDataJSON()
      writes.push(body)
      if (activateLock) {
        current = {...current, items: current.items.map(item => item.key === 'tictactoe' ? {...item, enabled: false, locked: true, lockReason: '临时停用'} : item)}
        return route.fulfill({status: 409, json: {error: 'COMMAND_SETTINGS_LOCKED'}})
      }
      if (failure) return route.fulfill({status: 502, json: {error: failure}})
      current = {...current, items: current.items.map(item => item.key === 'tictactoe' ? {...item, ...body} : item)}
      return route.fulfill({json: current})
    }
    if (path.endsWith('/functions')) return route.fulfill({json: {items: [], canEdit: true, proactive: true}})
    if (path.endsWith('/group-A')) return route.fulfill({json: group})
    return route.fulfill({status: 204})
  })
  await page.goto(`/atrimeow/profile/?userId=owner&ticket=${'a'.repeat(43)}`)
  await page.getByRole('button', {name: '群管理', exact: true}).click()
  await page.getByRole('button', {name: '查看测试群详情'}).click()
  const panel = page.getByRole('region', {name: '群指令设置', exact: true})
  const game = panel.locator('[data-command=tictactoe]'), ec = panel.locator('[data-command=ec]')
  await expect(game).not.toHaveAttribute('open', '')
  await game.locator('summary').click()
  const enabled = game.getByRole('switch', {name: '井字棋群内启用'})
  const seconds = game.getByLabel('井字棋冷却时间')
  const scope = game.getByRole('combobox', {name: '井字棋频控范围'})
  const save = game.getByRole('button', {name: '保存设置', exact: true})
  await seconds.fill('10')
  await scope.click()
  await game.getByRole('option', {name: '全群共用冷却'}).click()
  await enabled.click()
  await save.click()
  await expect(panel.getByRole('status')).toHaveText('设置已保存')
  expect(writes).toEqual([{enabled: false, cooldownSeconds: 10, scope: 'GROUP'}])
  failure = 'COMMAND_SETTINGS_UNAVAILABLE'
  await seconds.fill('20')
  await save.click()
  await expect(panel.getByRole('alert')).toHaveText('保存失败，请稍后重试')
  await expect(seconds).toHaveValue('20')
  await ec.locator('summary').click()
  await expect(ec).toContainText('功能维护')
  await expect(ec.getByRole('switch')).toBeDisabled()
  await expect(ec.getByRole('textbox')).toBeDisabled()
  await expect(ec.getByRole('combobox')).toBeDisabled()
  await expect(ec.getByRole('button', {name: '保存设置'})).toBeDisabled()
  failure = ''
  activateLock = true
  await save.click()
  await expect(panel.getByRole('alert')).toContainText('管理后台的停用规则已生效')
  await expect(game).toContainText('临时停用')
  await expect(enabled).toBeDisabled()
  await expect(seconds).toHaveValue('10')
  expect(writes).toHaveLength(3)
  current = {...current, items: current.items.map(item => item.key === 'tictactoe' ? {...item, locked: false, enabled: true} : item)}
  await panel.getByRole('button', {name: '刷新群指令设置'}).click()
  await expect(enabled).toBeEnabled()
  await expect(enabled).toHaveAttribute('aria-checked', 'true')
  await expect(seconds).toHaveValue('10')
  current = {...current, canEdit: false}
  await panel.getByRole('button', {name: '刷新群指令设置'}).click()
  await expect(enabled).toBeDisabled()
  await page.setViewportSize({width: 320, height: 740})
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await panel.screenshot({path: testInfo.outputPath('command-settings.png'), animations: 'disabled'})
  expect(errors).toEqual([])
})
