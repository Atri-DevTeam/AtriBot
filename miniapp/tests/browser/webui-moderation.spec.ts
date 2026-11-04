import {expect, test} from '@playwright/test'

test('AI moderation switches remain inside the chat settings drawer', async ({page}, testInfo) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => { window.EventSource = class { close() {} } as any })
  await page.route('**/webui/meta/**', route => route.fulfill({json: {}}))
  await page.route('**/webui/api/**', route => {
    const path = new URL(route.request().url()).pathname
    let data: unknown = {}
    if (path.endsWith('/auth/verify')) data = {role: 'ADMIN', displayName: '管理员'}
    else if (path.endsWith('/config')) data = {botName: 'AtriBot', botOpenId: 'self'}
    else if (path.endsWith('/chat/conversations')) data = {items: [{type: 'group', openId: 'group-a', name: '测试群'}], hasMore: false, cursor: '1'}
    else if (path.endsWith('/chat/pinned') || path.endsWith('/avatar-members') || path.endsWith('/groups/functions/keys')) data = []
    else if (path.endsWith('/group-moderation/group-a')) data = {keywordRecall: {rules: Array.from({length: 12}, (_, index) => ({ruleId: String(index), type: 'KEYWORD', keyword: `关键词${index}`}))}}
    else if (path.endsWith('/messages')) data = {total: 0, records: []}
    else if (path.endsWith('/groups/group-a')) data = {groupOpenId: 'group-a', groupName: '测试群'}
    return route.fulfill({json: {code: 0, data}})
  })
  await page.goto('http://127.0.0.1:5175/webui/chat?group=group-a')
  await page.getByRole('button', {name: '信息与设置', exact: true}).click()
  await page.getByRole('button', {name: '群管设置'}).click()
  const panel = page.locator('.moderation-panel')
  const toggle = panel.getByRole('checkbox', {name: '启用 AI 审查', exact: true})
  await expect(toggle).toBeAttached()
  const label = toggle.locator('..')
  await expect(page.locator('.chatnt-panel-enter-active')).toHaveCount(0)
  await label.scrollIntoViewIfNeeded()
  const before = await label.boundingBox()
  for (const enabled of [true, false, true]) {
    await label.click()
    await expect(toggle).toBeChecked({checked: enabled})
    await expect(label).toBeInViewport()
    const after = await label.boundingBox()
    expect(after!.y).toBeCloseTo(before!.y, 0)
    expect(after!.x).toBeCloseTo(before!.x, 0)
    await expect(panel.getByRole('button', {name: '保存设置'})).toBeInViewport()
  }
  await expect(panel.getByRole('button', {name: '保存设置'})).toBeEnabled()
  await page.screenshot({path: testInfo.outputPath('ai-toggle.png'), fullPage: true})
  expect(errors).toEqual([])
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
