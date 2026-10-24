import {test, expect} from '@playwright/test'
import {profile} from './profile-fixture'
import {activity} from './activity-fixture'

test('personal settings save independently and keep their values after navigating', async ({page}, testInfo) => {
  const changes: Record<string, unknown>[] = []
  let fail = false
  await page.route('**/atrimeow/profile/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/exchange')) return route.fulfill({json: {
      token: 'b'.repeat(43), user: {userId: 'owner', displayName: ''}, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000
    }})
    if (path.endsWith('/profile')) return route.fulfill({json: {...profile, avatarUrl: null, bot: {...profile.bot, avatarUrl: null}}})
    if (path.endsWith('/activity')) return route.fulfill({json: activity})
    if (path.endsWith('/settings')) {
      expect(route.request().headers().authorization).toBe(`Bearer ${'b'.repeat(43)}`)
      changes.push(route.request().postDataJSON())
      return fail ? route.fulfill({status: 502, json: {error: 'USER_SETTINGS_UNAVAILABLE'}}) : route.fulfill({json: {saved: true}})
    }
    return route.fulfill({status: 204})
  })
  await page.goto(`/atrimeow/profile/?userId=owner&ticket=${'a'.repeat(43)}`)
  const settings = page.getByRole('region', {name: '用户设置'})
  const automatic = settings.getByRole('switch', {name: 'Hypixel 自动领取'})
  await expect(automatic).toHaveAttribute('aria-checked', 'true')
  await automatic.click()
  await expect(automatic).toHaveAttribute('aria-checked', 'false')
  await settings.getByRole('combobox', {name: '奖励选择方式'}).click()
  await settings.getByRole('option', {name: '稀有度优先'}).click()
  await expect(settings.getByRole('combobox', {name: '奖励选择方式'})).toContainText('稀有度优先')
  const priorities = settings.locator('.reward-priorities')
  const bedwars = settings.locator('.reward-option').filter({hasText: '起床战争'})
  const skywars = settings.locator('.reward-option').filter({hasText: '空岛战争'})
  await expect(priorities.locator('.reward-options')).toBeHidden()
  await priorities.locator('summary').click()
  await expect(bedwars.locator('form')).toBeVisible()
  await expect(skywars.locator('form')).toBeVisible()
  await bedwars.getByRole('textbox').fill('31')
  await expect(bedwars.getByRole('button', {name: '保存', exact: true})).toBeDisabled()
  await bedwars.getByRole('textbox').fill('12')
  await bedwars.getByRole('button', {name: '保存', exact: true}).click()
  await expect(bedwars.locator('.reward-option-heading small')).toHaveText('12')
  await bedwars.getByRole('button', {name: '清除', exact: true}).click()
  await expect(bedwars.locator('.reward-option-heading small')).toHaveText('未设置')
  await priorities.locator('summary').click()
  await expect(priorities.locator('.reward-options')).toBeHidden()
  const keyboard = settings.getByRole('switch', {name: '按钮兼容模式', exact: true})
  await settings.getByRole('button', {name: '按钮兼容模式说明'}).click()
  await expect(settings.getByRole('tooltip')).toHaveText('部分设备可能不支持内联键盘按钮，可以启用此功能以适配一些操作')
  await keyboard.click()
  await expect(settings.getByRole('tooltip')).toHaveCount(0)
  await expect(keyboard).toHaveAttribute('aria-checked', 'true')
  fail = true
  await keyboard.click()
  await expect(settings.getByRole('alert')).toHaveText('保存失败，请稍后重试')
  await expect(keyboard).toHaveAttribute('aria-checked', 'true')
  expect(changes).toEqual([
    {action: 'reward_enabled', enabled: false}, {action: 'reward_mode', mode: 'rarity'},
    {action: 'reward_priority', item: 'BEDWARS', value: 12}, {action: 'reward_priority', item: 'BEDWARS', value: null},
    {action: 'keyboard', enabled: true}, {action: 'keyboard', enabled: false}
  ])
  await page.getByRole('button', {name: '我的数据', exact: true}).click()
  await page.getByRole('button', {name: '我的档案', exact: true}).click()
  await expect(keyboard).toHaveAttribute('aria-checked', 'true')
  await expect(automatic).toHaveAttribute('aria-checked', 'false')
  await page.setViewportSize({width: 320, height: 740})
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await settings.screenshot({path: testInfo.outputPath('user-settings.png'), animations: 'disabled'})
})
