import { test, expect, type Page } from '@playwright/test'
import { profile } from './profile-fixture'
import { activity, inventoryImage } from './activity-fixture'
import { moderation } from './moderation-fixture'
import type { Moderation } from '../../src/moderation'

const entry = `/atrimeow/profile/?userId=owner&ticket=${'a'.repeat(43)}`
const group = { groupId: 'group-A', name: '测试群', groupNumber: '123', boundAt: '2026-10-03', available: true,
  tags: [], botRole: 'admin', receiveMode: 'all', memberCount: 10, proactive: false, restricted: false, canManageModeration: true }

async function setup(page: Page, options: { prompt?: boolean; denied?: boolean; noPermission?: boolean; advanced?: boolean } = {}) {
  let current: Moderation = structuredClone({ ...moderation, canCustomizePrompt: !!options.prompt })
  if (options.advanced) {
    current.keywords[0].readOnly = [{ label: '禁言', value: '120 秒' }, { label: '固定提醒内容', value: '管理员提醒' }]
    current.aiReadOnly = [{ label: '自定义输出', value: '管理员输出要求' }]
    current.aiSchedule = { enabled: true, startDate: '', endDate: '', startTime: '21:00', endTime: '06:00', daysOfWeek: [1, 3] }
    current.otherRules = [{ title: '链接规则', fields: [{ label: '状态', value: '已启用' }] }]
    current.customPrompt = options.prompt ? '已有审查标准' : null
    current.promptConfigured = !!options.prompt
  }
  const writes: Record<string, any>[] = []
  let fail = '', welcomeCalls = 0, moderationCalls = 0
  await page.route('**/atrimeow/profile/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/exchange')) return route.fulfill({ json: { token: 'b'.repeat(43), user: { userId: 'owner', displayName: '' }, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000 } })
    if (path.endsWith('/profile')) return route.fulfill({ json: { ...profile, avatarUrl: null, bot: { ...profile.bot, avatarUrl: null } } })
    if (path.endsWith('/activity')) return route.fulfill({ json: activity })
    if (path.endsWith('/inventory/image')) return route.fulfill({ contentType: 'image/png', body: inventoryImage })
    if (path.endsWith('/groups')) return route.fulfill({ json: { groups: [group], pending: null } })
    if (path.endsWith('/moderation')) {
      moderationCalls++
      expect(route.request().headers().authorization).toBe(`Bearer ${'b'.repeat(43)}`)
      if (options.denied) return route.fulfill({ status: 403, json: { error: 'MODERATION_FORBIDDEN' } })
      if (route.request().method() === 'POST') {
        const body = route.request().postDataJSON()
        writes.push(body)
        if (fail) return route.fulfill({ status: fail === 'MODERATION_CHANGED' ? 409 : 403, json: { error: fail } })
        current = { ...current, revision: `revision-${writes.length + 1}`, keywordEnabled: body.keywordEnabled,
          aiEnabled: body.aiEnabled, aiType: body.aiType, aiRemind: body.aiRemind, allowAllLinks: body.allowAllLinks, aiSchedule: body.aiSchedule,
          customPrompt: body.customPrompt === undefined ? current.customPrompt : body.customPrompt,
          promptConfigured: body.customPrompt === undefined ? current.promptConfigured : !!body.customPrompt,
          keywords: body.keywords.map((item: any, index: number) => ({ id: String(index), matchMode: item.matchMode, remind: item.remind, readOnly: current.keywords.find(previous => previous.id === item.id)?.readOnly || [],
            masked: item.value ? [...item.value][0] + '*'.repeat([...item.value].length - 1) : current.keywords.find(previous => previous.id === item.id)?.masked })),
          domains: body.domains.map((item: any, index: number) => ({ id: String(index), masked: item.value ? [...item.value][0] + '*'.repeat([...item.value].length - 2) + [...item.value].at(-1) : current.domains.find(previous => previous.id === item.id)?.masked })) }
      }
      return route.fulfill({ json: current })
    }
    if (path.endsWith('/join-welcome')) {
      welcomeCalls++
      return route.fulfill({ json: { enabled: true, custom: true, text: '欢迎新成员', keyboard: [], buttonSize: 'SMALL' } })
    }
    if (path.endsWith('/group-A')) return route.fulfill({ json: { ...group, canManageModeration: !options.noPermission } })
    return route.fulfill({ status: 204 })
  })
  await page.goto(entry)
  await page.getByRole('button', { name: '群管理', exact: true }).click()
  await page.getByRole('button', { name: '查看测试群详情' }).click()
  const panel = page.getByRole('region', { name: '群管设置', exact: true })
  return { panel, writes, welcomeCalls: () => welcomeCalls, moderationCalls: () => moderationCalls, fail: (code: string) => { fail = code } }
}

test('owner edits masked rules, welcome stays collapsed, saves contain only permitted fields', async ({ page }, testInfo) => {
  const { panel, writes, welcomeCalls } = await setup(page)
  await expect(panel.getByLabel('启用关键词审查')).toBeVisible()
  expect(welcomeCalls()).toBe(0)
  await expect(page.getByRole('button', { name: '加群欢迎', exact: true })).toHaveAttribute('aria-expanded', 'false')
  await expect(panel.getByText('自定义 AI 审查词', { exact: true })).toHaveCount(0)
  await expect(panel.getByLabel('全部放行')).toBeChecked()
  await panel.getByLabel('启用关键词审查').check()
  await panel.getByLabel('新关键词', { exact: true }).fill('测试秘密词')
  await panel.getByLabel('新关键词匹配模式').selectOption('EQUALS')
  await panel.getByRole('region', { name: '关键词设置' }).getByRole('button', { name: '添加', exact: true }).click()
  await panel.getByLabel('启用 AI 审查', { exact: true }).check()
  await panel.getByLabel('全部放行').uncheck()
  await panel.getByLabel('新 URL 白名单').fill('^https://example\\.com/$')
  await panel.getByRole('button', { name: '保存设置' }).click()
  await expect(panel.getByRole('status')).toHaveText('已保存')
  expect(writes).toHaveLength(1)
  expect(writes[0].keywords).toEqual([{ id: '0', matchMode: 'CONTAINS', remind: true }, { value: '测试秘密词', matchMode: 'EQUALS', remind: true }])
  expect(Object.keys(writes[0]).sort()).toEqual(['revision', 'keywordEnabled', 'keywords', 'aiEnabled', 'aiType', 'aiRemind', 'allowAllLinks', 'domains', 'aiSchedule'].sort())
  await expect(panel.getByRole('list', { name: '关键词列表' })).toContainText('测****')
  await expect(panel).not.toContainText('测试秘密词')
  await expect(panel).not.toContainText('example')
  await expect(panel.getByLabel('新关键词', { exact: true })).toHaveValue('')
  await expect(panel.getByLabel('新 URL 白名单')).toHaveValue('')
  await panel.getByLabel('命中后发送提醒').uncheck()
  await panel.getByRole('button', { name: '保存设置' }).click()
  await expect(panel.getByRole('status')).toHaveText('已保存')
  expect(writes[1].keywords.every((item: any) => 'id' in item && !('value' in item))).toBe(true)
  expect(writes[1].domains.every((item: any) => 'id' in item && !('value' in item))).toBe(true)
  await panel.screenshot({ path: testInfo.outputPath('moderation.png'), animations: 'disabled' })
  await page.setViewportSize({ width: 320, height: 740 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: '加群欢迎', exact: true }).click()
  await expect(page.getByRole('region', { name: '加群欢迎', exact: true })).toContainText('欢迎新成员')
  expect(welcomeCalls()).toBe(1)
})

test('prompt editor requires capability and disappears if permission is revoked on save', async ({ page }) => {
  const { panel, writes, fail } = await setup(page, { prompt: true })
  await expect(panel.getByLabel('自定义 AI 审查词', { exact: true })).toHaveValue('')
  await panel.getByLabel('自定义 AI 审查词', { exact: true }).fill('自定义审查范围')
  await panel.getByRole('button', { name: '保存设置' }).click()
  await expect(panel.getByRole('status')).toHaveText('已保存')
  expect(writes[0].customPrompt).toBe('自定义审查范围')
  await expect(panel.getByLabel('自定义 AI 审查词', { exact: true })).toHaveValue('自定义审查范围')
  await panel.getByLabel('自定义 AI 审查词', { exact: true }).fill('')
  fail('CUSTOM_PROMPT_FORBIDDEN')
  await panel.getByRole('button', { name: '保存设置' }).click()
  await expect(panel.getByRole('alert')).toContainText('权限已变更')
  await expect(panel.locator('form')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '返回群列表' })).toBeVisible()
})

test('non-owner cannot edit and conflict leaves drafts for explicit reload', async ({ page }) => {
  const { panel, writes, fail } = await setup(page)
  await panel.getByLabel('新关键词', { exact: true }).fill('尚未保存')
  page.once('dialog', dialog => dialog.dismiss())
  await page.getByRole('button', { name: '返回群列表' }).click()
  await expect(panel.getByLabel('新关键词', { exact: true })).toHaveValue('尚未保存')
  fail('MODERATION_CHANGED')
  await panel.getByRole('button', { name: '保存设置' }).click()
  await expect(panel.getByRole('alert')).toContainText('其他位置更新')
  await expect(panel).toContainText('尚未保存')
  page.once('dialog', dialog => dialog.accept())
  await panel.getByRole('button', { name: '重新读取' }).click()
  await expect(panel).not.toContainText('尚未保存')
  fail('MODERATION_FORBIDDEN')
  await panel.getByLabel('启用 AI 审查', { exact: true }).check()
  await panel.getByRole('button', { name: '保存设置' }).click()
  await expect(panel).toHaveCount(0)
  expect(writes).toHaveLength(2)
})

test('initial ownership denial shows no configuration controls', async ({ page }) => {
  const { panel, writes, moderationCalls } = await setup(page, { denied: true })
  await expect.poll(moderationCalls).toBe(1)
  await expect(panel).toHaveCount(0)
  expect(writes).toHaveLength(0)
})

test('without group.moderation the entire section is absent and configuration is never requested', async ({ page }) => {
  const { panel, writes, moderationCalls } = await setup(page, { noPermission: true, prompt: true })
  await expect(page.locator('.group-details')).toBeVisible()
  await expect(panel).toHaveCount(0)
  expect(moderationCalls()).toBe(0)
  expect(writes).toHaveLength(0)
})

test('advanced WebUI settings are display-only while mode and existing prompt remain editable', async ({ page }) => {
  const { panel, writes } = await setup(page, { advanced: true, prompt: true })
  await expect(panel.getByLabel('AI 高级设置')).toContainText('管理员输出要求')
  await expect(panel.getByLabel('关键词 1 高级设置')).toContainText('120 秒')
  await expect(panel.getByLabel('链接规则高级设置')).toContainText('已启用')
  await expect(panel.locator('.moderation-readonly input, .moderation-readonly textarea, .moderation-readonly select, .moderation-readonly button')).toHaveCount(0)
  await expect(panel.getByText('更新审查词', { exact: true })).toHaveCount(0)
  await expect(panel.getByLabel('自定义 AI 审查词', { exact: true })).toHaveValue('已有审查标准')
  await panel.getByLabel('审查模式', { exact: true }).selectOption('3')
  await panel.getByLabel('自定义 AI 审查词', { exact: true }).fill('')
  await panel.getByRole('button', { name: '保存设置' }).click()
  await expect(panel.getByRole('status')).toHaveText('已保存')
  expect(writes[0].aiType).toBe(3)
  expect(writes[0].customPrompt).toBe('')
  expect(writes[0]).not.toHaveProperty('aiReadOnly')
  expect(writes[0]).not.toHaveProperty('customOutput')
  expect(writes[0].keywords[0]).not.toHaveProperty('readOnly')
  await expect(panel.getByLabel('AI 高级设置')).toContainText('管理员输出要求')
  await expect(panel.getByLabel('自定义 AI 审查词', { exact: true })).toHaveValue('')
  await panel.getByRole('button', { name: '重新读取' }).click()
  await expect(panel.getByLabel('审查模式', { exact: true })).toHaveValue('3')
  await expect(panel.getByLabel('自定义 AI 审查词', { exact: true })).toHaveValue('')
})

test('unconfigured advanced sections are absent', async ({ page }) => {
  const { panel } = await setup(page)
  await expect(panel.getByLabel('审查模式', { exact: true })).toBeVisible()
  await expect(panel.locator('.moderation-readonly')).toHaveCount(0)
})

test('schedule edits round trip and URL and prompt controls fit narrow screens', async ({ page }, testInfo) => {
  const { panel, writes } = await setup(page, { advanced: true, prompt: true })
  await expect(panel.getByLabel('仅在指定时间审查')).toBeChecked()
  await expect(panel.getByLabel('开始时间', { exact: true })).toHaveValue('21:00')
  await panel.getByLabel('开始日期', { exact: true }).fill('2026-10-05')
  await panel.getByLabel('结束日期', { exact: true }).fill('2026-10-31')
  await panel.getByLabel('开始时间', { exact: true }).fill('22:30')
  await panel.getByRole('button', { name: '周二', exact: true }).click()
  await panel.getByRole('button', { name: '保存设置' }).click()
  await expect(panel.getByRole('status')).toHaveText('已保存')
  expect(writes[0].aiSchedule).toEqual({ enabled: true, startDate: '2026-10-05', endDate: '2026-10-31', startTime: '22:30', endTime: '06:00', daysOfWeek: [1, 2, 3] })
  await panel.getByRole('button', { name: '重新读取' }).click()
  await expect(panel.getByLabel('开始日期', { exact: true })).toHaveValue('2026-10-05')
  await expect(panel.getByRole('button', { name: '周二', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await panel.getByLabel('全部放行').uncheck()
  for (const label of ['新 URL 白名单', '自定义 AI 审查词']) {
    const style = await panel.getByLabel(label, { exact: true }).evaluate(element => {
      const css = getComputedStyle(element)
      return { fontSize: css.fontSize, border: css.borderTopWidth, padding: parseFloat(css.paddingLeft) }
    })
    expect(style).toEqual({ fontSize: '13px', border: '1px', padding: 12 })
  }
  await panel.screenshot({ path: testInfo.outputPath('moderation-schedule.png'), animations: 'disabled' })
  await page.setViewportSize({ width: 320, height: 740 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const url = await panel.getByLabel('新 URL 白名单').boundingBox()
  const prompt = await panel.getByLabel('自定义 AI 审查词', { exact: true }).boundingBox()
  expect(url!.width).toBeGreaterThan(150)
  expect(prompt!.width).toBeGreaterThan(200)
  await panel.getByLabel('仅在指定时间审查').uncheck()
  await expect(panel.getByLabel('开始时间', { exact: true })).toHaveCount(0)
  await panel.getByRole('button', { name: '保存设置' }).click()
  await expect(panel.getByRole('status')).toHaveText('已保存')
  expect(writes[1].aiSchedule).toEqual({ ...writes[0].aiSchedule, enabled: false })
})
