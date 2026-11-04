import {expect, test} from '@playwright/test'

test('gallery AI settings save separately and review results remain advisory', async ({page}, testInfo) => {
  let settings = {enabled: false, configured: true, model: 'deepseek-flash', prompt: '审核标准', rejectInappropriate: true, rejectOffTopic: true}
  let aiReview: object | null = null
  const writes: string[] = []
  const errors: string[] = []
  page.on('pageerror', e => errors.push(e.message))
  await page.route('**/webui/api/**', route => {
    const request = route.request(), path = new URL(request.url()).pathname
    const ok = (data: unknown) => route.fulfill({json: {code: 0, data}})
    if (request.method() !== 'GET') writes.push(path)
    if (path.endsWith('/auth/verify')) return ok({role: 'ADMIN', displayName: '管理员'})
    if (path.endsWith('/config')) return ok({botName: 'AtriBot'})
    if (path.endsWith('/count')) return ok({pending: 1, reviewed: 0, denied: 0, all: 1})
    if (path.endsWith('/gallery/ai/settings')) {
      if (request.method() === 'PUT') settings = {...settings, ...request.postDataJSON()}
      return ok(settings)
    }
    if (path.endsWith('/gallery/ai/review/image-a')) {
      aiReview = {status: 'UNCERTAIN', reason: '细节不清楚，需要人工确认', model: 'deepseek-flash', reviewedAt: '2026-11-02T12:00:00Z'}
      return ok('已提交初审')
    }
    if (path.endsWith('/gallery/list')) return ok({items: [{id: 'image-a', uploaderName: '投稿用户', reviewStatus: 'PENDING', aiReview}], total: 1})
    return ok({})
  })
  await page.goto('http://127.0.0.1:5175/webui/gallery')
  await expect(page.locator('.gallery-card')).toHaveCount(1)
  await page.getByRole('button', {name: 'AI 初审设置', exact: true}).click()
  const dialog = page.getByRole('dialog', {name: 'AI 初审设置'})
  await dialog.getByRole('checkbox', {name: '启用 AI 初审'}).check()
  await expect(dialog.getByText('拒绝理由：存在不合适的内容')).toBeVisible()
  await expect(dialog.getByText('拒绝理由：图片元素不符合要求')).toBeVisible()
  await dialog.getByRole('checkbox', {name: '自动拒绝非主题图片'}).uncheck()
  await dialog.getByLabel('审核标准', {exact: true}).fill('仅检查明显违规内容，不确定交由人工确认')
  if (testInfo.project.name === 'mobile')
    await expect(dialog.getByLabel('审核标准', {exact: true})).toHaveCSS('font-size', '12px')
  await page.screenshot({path: testInfo.outputPath('ai-settings.png'), fullPage: true})
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await dialog.getByRole('button', {name: '保存设置'}).click()
  await expect(dialog).toHaveCount(0)
  expect(settings.enabled).toBe(true)
  expect(settings.rejectInappropriate).toBe(true)
  expect(settings.rejectOffTopic).toBe(false)
  expect(settings.prompt).toBe('仅检查明显违规内容，不确定交由人工确认')
  await page.getByRole('button', {name: '提交初审', exact: true}).click()
  await expect(page.locator('.gallery-ai-tag')).toHaveText('AI 需人工确认', {timeout: 10000})
  await expect(page.locator('.gallery-status')).toHaveText('未审核')
  await page.locator('.gallery-thumb').click()
  await expect(page.locator('.gallery-ai-detail')).toContainText('细节不清楚，需要人工确认')
  expect(writes).toEqual(['/webui/api/gallery/ai/settings', '/webui/api/gallery/ai/review/image-a'])
  expect(errors).toEqual([])
})

test('missing provider configuration cannot enable AI review', async ({page}) => {
  await page.route('**/webui/api/**', route => {
    const path = new URL(route.request().url()).pathname
    const data = path.endsWith('/auth/verify') ? {role: 'ADMIN'}
      : path.endsWith('/ai/settings') ? {enabled: false, prompt: '审核标准', configured: false, model: 'deepseek-flash'}
      : path.endsWith('/list') ? {items: [], total: 0} : {pending: 0}
    return route.fulfill({json: {code: 0, data}})
  })
  await page.goto('http://127.0.0.1:5175/webui/gallery')
  await page.getByRole('button', {name: 'AI 初审设置', exact: true}).click()
  await expect(page.getByRole('checkbox', {name: '启用 AI 初审'})).toBeDisabled()
  await expect(page.getByRole('dialog')).toContainText('ai.deepseek_image')
})

test('review tags show preliminary approval and explain both rejection states', async ({page}, testInfo) => {
  const reason = '这是一张表情包，图片元素不符合要求'
  await page.route('**/webui/api/**', route => {
    const path = new URL(route.request().url()).pathname
    const data = path.endsWith('/auth/verify') ? {role: 'ADMIN'}
      : path.endsWith('/gallery/list') ? {items: [
        {id: 'pass', reviewStatus: 'PENDING', aiReview: {status: 'PASS', reason: '正常风景'}},
        {id: 'suggest', reviewStatus: 'PENDING', aiReview: {status: 'REJECT', reason}},
        {id: 'denied', reviewStatus: 'DENIED', reviewer: 'AI 初审', aiReview: {status: 'REJECT', reason}}
      ], total: 3} : {}
    return route.fulfill({json: {code: 0, data}})
  })
  await page.goto('http://127.0.0.1:5175/webui/gallery')
  await expect(page.locator('.gallery-ai-tag')).toHaveText(['初步审查通过', 'AI 建议拒绝', 'AI 已拒绝'])
  const buttons = page.getByRole('button', {name: '查看初审拒绝原因'})
  await expect(buttons).toHaveCount(2)
  for (let i = 0; i < 2; i++) {
    if (testInfo.project.name === 'mobile') await buttons.nth(i).click()
    else await buttons.nth(i).hover()
    await expect(page.getByRole('tooltip')).toHaveText(reason)
    const rect = await page.getByRole('tooltip').boundingBox()
    expect(rect!.x).toBeGreaterThanOrEqual(0)
    expect(rect!.x + rect!.width).toBeLessThanOrEqual(page.viewportSize()!.width)
    await buttons.nth(i).press('Escape')
    await expect(page.getByRole('tooltip')).toHaveCount(0)
  }
})
