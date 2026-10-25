import {test, expect} from '@playwright/test'
import {profile} from './profile-fixture'
import {activityResponse} from './activity-fixture'

test('keeps search responsive while icons load and manages reusable alerts', async ({page}, testInfo) => {
  const token = 'b'.repeat(43)
  const hash = 'a'.repeat(64)
  let iconRequests = 0
  let imageRequests = 0
  let saves = 0
  let releaseIcon!: () => void
  const iconPending = new Promise<void>(resolve => { releaseIcon = resolve })
  let settings = {available: true, enabled: false, canPush: true, limit: 20, rules: [] as any[]}
  const item = {id: 'ENCHANTED_DIAMOND', key: 'ENCHANTED_DIAMOND', name: 'Enchanted Diamond', tier: 'UNCOMMON', markets: ['bazaar'],
    bazaar: {instant_buy: 1400, instant_sell: 1300, buy_order: 1300, sell_offer: 1400}, variant: null}
  await page.route('**/dashboard/api/auth/**', route => route.fulfill({json: {status: 'ready', access: {
    token, user: {userId: 'owner', displayName: '亚托利'}, expiresAt: Date.now() + 7_200_000, idleTimeoutMillis: 90_000
  }}}))
  await page.route('**/atrimeow/profile/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.includes('/price-alerts')) {
      expect(route.request().headers().authorization).toBe(`Bearer ${token}`)
      if (path.endsWith('/search')) return route.fulfill({json: {items: [item], page: 1, total_pages: 1}})
      if (path.endsWith('/item')) return route.fulfill({json: {item, sources: {bazaar: {available: true, stale: false, last_updated: Date.now()}}}})
      if (path.endsWith('/icon')) { iconRequests++; await iconPending; return route.fulfill({json: {hash, tier: item.tier, width: 128, height: 128}}) }
      if (path.includes('/image/')) {
        imageRequests++
        return route.fulfill({contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+nmN8AAAAASUVORK5CYII=', 'base64')})
      }
      if (path.endsWith('/switch')) settings.enabled = route.request().postDataJSON().enabled
      if (path.endsWith('/save')) {
        saves++
        const body = route.request().postDataJSON()
        expect(body).toMatchObject({itemId: item.id, key: item.key, metric: 'instant_buy', threshold: '1200', direction: 'below', enabled: true})
        expect(body).not.toHaveProperty('userId')
        settings.rules = [{...body, threshold: Number(body.threshold), id: 'rule', revision: 'revision', name: item.name, variant: '', completedAt: 0,
          sending: false, lastPrice: 1400, lastSnapshot: Date.now()}]
      }
      if (path.endsWith('/action')) {
        const body = route.request().postDataJSON()
        expect(body.id).toBe('rule')
        if (body.action === 'delete') settings.rules = []
        else settings.rules[0].enabled = body.enabled
      }
      return route.fulfill({json: settings})
    }
    if (path.endsWith('/profile')) return route.fulfill({json: {...profile, skyblockPrice: true, avatarUrl: null, bot: {...profile.bot, avatarUrl: null}}})
    const activity = activityResponse(route.request().url())
    if (activity) return route.fulfill({json: activity})
    return route.fulfill({status: 204})
  })
  await page.goto('/dashboard/')
  await page.getByRole('button', {name: '价格提醒', exact: true}).click()
  await expect(page.getByText('暂无价格提醒')).toBeVisible()
  await page.getByRole('switch', {name: '私聊价格提醒'}).click()
  await expect(page.getByRole('switch')).toBeChecked()
  await page.getByRole('searchbox', {name: '搜索物品'}).fill('diamond')
  await page.getByRole('button', {name: '搜索', exact: true}).click()
  await expect(page.getByRole('button', {name: /Enchanted Diamond/})).toBeVisible()
  await expect.poll(() => iconRequests).toBe(1)
  expect(imageRequests).toBe(0)
  await expect(page.locator('.search-item-text > span')).toHaveText('Bazaar')
  await expect(page.locator('.search-item-text > strong')).toHaveCSS('color', 'rgb(59, 139, 55)')
  await page.getByRole('searchbox', {name: '搜索物品'}).focus()
  await page.screenshot({path: testInfo.outputPath('price-alert-search.png'), fullPage: true})
  await page.getByRole('button', {name: /Enchanted Diamond/}).click()
  await expect(page.getByRole('combobox', {name: '报价类型'})).toBeVisible()
  releaseIcon()
  await expect(page.locator('.selected-item .price-item-icon img')).toBeVisible()
  expect(iconRequests).toBe(1)
  expect(imageRequests).toBe(1)
  await page.getByRole('combobox', {name: '报价类型'}).click()
  await expect(page.getByRole('option', {name: /^Lowest Bin/})).toBeVisible()
  await page.screenshot({path: testInfo.outputPath('price-alert-dropdown.png'), fullPage: true})
  await page.getByRole('option', {name: /^Instant Buy/}).click()
  await page.getByPlaceholder('输入目标价格').fill('1200')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({path: testInfo.outputPath('price-alert-editor.png'), fullPage: true})
  await page.getByRole('button', {name: '创建提醒'}).click()
  await expect(page.getByText('提醒已保存', {exact: true})).toBeVisible()
  expect(saves).toBe(1)
  await expect(page.locator('.rule-item .price-item-icon img')).toBeVisible()
  await expect(page.locator('.rule-item h4')).toHaveCSS('color', 'rgb(59, 139, 55)')
  await expect(page.locator('.rule-source')).toHaveText('Bazaar')
  await expect(page.locator('.rule-threshold strong')).toHaveText('≤ 1,200')
  await page.screenshot({path: testInfo.outputPath('price-alert-rules.png'), fullPage: true})
  expect(iconRequests).toBe(1)
  await page.getByRole('button', {name: '暂停', exact: true}).click()
  await expect(page.getByText('已暂停', {exact: true})).toBeVisible()
  await page.getByRole('button', {name: '启用', exact: true}).click()
  await expect(page.getByText('监测中', {exact: true})).toHaveCount(0)
  await expect(page.getByRole('button', {name: '暂停', exact: true})).toBeVisible()
  await page.getByRole('button', {name: '删除', exact: true}).click()
  await page.getByRole('button', {name: '确认删除'}).click()
  await expect(page.getByText('暂无价格提醒')).toBeVisible()
})

test('restricts configuration without friendship', async ({page}) => {
  await page.route('**/dashboard/api/auth/**', route => route.fulfill({json: {status: 'ready', access: {
    token: 'b'.repeat(43), user: {userId: 'owner', displayName: '亚托利'}, expiresAt: Date.now() + 7_200_000, idleTimeoutMillis: 90_000
  }}}))
  await page.route('**/atrimeow/profile/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/price-alerts')) return route.fulfill({json: {available: false, enabled: false, canPush: false, limit: 20, rules: []}})
    if (path.endsWith('/profile')) return route.fulfill({json: {...profile, skyblockPrice: true, avatarUrl: null, bot: {...profile.bot, avatarUrl: null}}})
    const activity = activityResponse(route.request().url())
    if (activity) return route.fulfill({json: activity})
    return route.fulfill({status: 204})
  })
  await page.goto('/dashboard/')
  await page.getByRole('button', {name: '价格提醒', exact: true}).click()
  await expect(page.getByText('请先添加机器人为好友，再设置私聊提醒')).toBeVisible()
  await expect(page.getByRole('switch')).toBeDisabled()
  await expect(page.getByRole('searchbox', {name: '搜索物品'})).toBeDisabled()
})

for (const priceState of ['loading', 'stale', 'failed'] as const) {
  test(`saves valid configuration while price preview is ${priceState}`, async ({page}) => {
    let releaseQuote!: () => void
    const pending = new Promise<void>(resolve => { releaseQuote = resolve })
    let saves = 0
    const item = {id: 'PET', key: 'PET:variant-from-search', name: 'Pet',
      variant: {pet_type: 'SHEEP', pet_tier: 'LEGENDARY', pet_skin: 'SHEEP_WHITE'}}
    const settings = {available: true, enabled: true, canPush: true, limit: 20, rules: [] as any[]}
    await page.route('**/dashboard/api/auth/**', route => route.fulfill({json: {status: 'ready', access: {
      token: 'b'.repeat(43), user: {userId: 'owner', displayName: '亚托利'}, expiresAt: Date.now() + 7_200_000, idleTimeoutMillis: 90_000
    }}}))
    await page.route('**/atrimeow/profile/api/**', async route => {
      const path = new URL(route.request().url()).pathname
      if (path.includes('/price-alerts')) {
        if (path.endsWith('/search')) return route.fulfill({json: {items: [item], page: 1, total_pages: 1}})
        if (path.endsWith('/item')) {
          if (priceState === 'loading') await pending
          if (priceState === 'failed') return route.fulfill({status: 502, json: {error: 'PRICE_ALERT_UNAVAILABLE'}})
          return route.fulfill({json: {item: {...item, auction: {lowest_bin: 200}},
            sources: {auctions: {available: true, stale: true, last_updated: Date.now() - 3_600_000}}}})
        }
        if (path.endsWith('/icon')) return route.fulfill({status: 502, json: {error: 'PRICE_ICON_UNAVAILABLE'}})
        if (path.endsWith('/save')) {
          saves++
          const body = route.request().postDataJSON()
          expect(body).toMatchObject({key: item.key, name: item.name, metric: 'lowest_bin', threshold: '150'})
          expect(JSON.parse(body.variant)).toEqual(item.variant)
          settings.rules = [{...body, id: 'rule', revision: 'revision', sending: false, completedAt: 0, lastPrice: null, lastSnapshot: 0}]
        }
        return route.fulfill({json: settings})
      }
      if (path.endsWith('/profile')) return route.fulfill({json: {...profile, skyblockPrice: true, avatarUrl: null, bot: {...profile.bot, avatarUrl: null}}})
      const activity = activityResponse(route.request().url())
      if (activity) return route.fulfill({json: activity})
      return route.fulfill({status: 204})
    })
    try {
      await page.goto('/dashboard/')
      await page.getByRole('button', {name: '价格提醒', exact: true}).click()
      await page.getByRole('searchbox', {name: '搜索物品'}).fill('sheep')
      await page.getByRole('button', {name: '搜索', exact: true}).click()
      await page.getByRole('button', {name: /Pet.*SHEEP/}).click()
      await page.getByRole('combobox', {name: '报价类型'}).click()
      await page.getByRole('option', {name: /^Lowest Bin/}).click()
      await page.getByPlaceholder('输入目标价格').fill('150')
      await expect(page.getByRole('button', {name: '创建提醒'})).toBeEnabled()
      await page.getByRole('button', {name: '创建提醒'}).click()
      await expect(page.getByText('提醒已保存', {exact: true})).toBeVisible()
      expect(saves).toBe(1)
      releaseQuote()
      await expect(page.getByRole('button', {name: '创建提醒'})).toHaveCount(0)
    } finally { releaseQuote() }
  })
}

for (const administrator of [false, true]) {
  test(`exposes the test action only when administrator is ${administrator}`, async ({page}, testInfo) => {
    let tests = 0
    const rule = {id: 'rule', revision: 'revision', itemId: 'TEST', key: 'TEST', name: 'Test Item', variant: '',
      metric: 'lowest_bin', direction: 'below', threshold: 100, enabled: true, completedAt: 0, lastPrice: null, lastSnapshot: 0, sending: false}
    await page.route('**/dashboard/api/auth/**', route => route.fulfill({json: {status: 'ready', access: {
      token: 'b'.repeat(43), user: {userId: 'owner', displayName: '亚托利'}, expiresAt: Date.now() + 7_200_000, idleTimeoutMillis: 90_000
    }}}))
    await page.route('**/atrimeow/profile/api/**', route => {
      const path = new URL(route.request().url()).pathname
      if (path.endsWith('/price-alerts')) return route.fulfill({json: {available: true, enabled: true,
        canPush: true, canTest: administrator, limit: 20, rules: [rule]}})
      if (path.endsWith('/test')) {
        tests++
        expect(route.request().postDataJSON()).toEqual({id: 'rule', revision: 'revision'})
        return route.fulfill({json: {success: true}})
      }
      if (path.endsWith('/icon')) return route.fulfill({json: {hash: 'a'.repeat(64), tier: 'EPIC'}})
      if (path.includes('/image/')) return route.fulfill({status: 502})
      if (path.endsWith('/profile')) return route.fulfill({json: {...profile, skyblockPrice: true, avatarUrl: null, bot: {...profile.bot, avatarUrl: null}}})
      const activity = activityResponse(route.request().url())
      if (activity) return route.fulfill({json: activity})
      return route.fulfill({status: 204})
    })
    await page.goto('/dashboard/')
    await page.getByRole('button', {name: '价格提醒', exact: true}).click()
    await expect(page.getByText('监测中', {exact: true})).toHaveCount(0)
    await expect(page.locator('.rule-item h4')).toHaveCSS('color', 'rgb(170, 0, 170)')
    await expect(page.locator('.rule-source')).toHaveText('Auction House')
    await page.screenshot({path: testInfo.outputPath('price-alert-existing-rule.png'), fullPage: true})
    if (administrator) {
      const button = page.getByRole('button', {name: '测试', exact: true})
      await expect(button).toHaveCSS('color', 'rgb(238, 160, 166)')
      await button.click()
      await expect(page.getByText('测试消息已发送')).toBeVisible()
      expect(tests).toBe(1)
      expect(rule.enabled).toBe(true)
      expect(rule.completedAt).toBe(0)
    } else await expect(page.getByRole('button', {name: '测试', exact: true})).toHaveCount(0)
  })
}
