import {expect, test} from '@playwright/test'

const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jZ1kAAAAASUVORK5CYII=', 'base64')

test('image materials support paging, upload, copying and deletion without changing review tabs', async ({page}, testInfo) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'clipboard', {value: {writeText: async (text: string) => {
      ;(window as any).copiedMaterial = text
    }}})
  })
  let reads = 0
  let uploaded = false
  let deleted = false
  const image = (id: string, name: string) => ({imageId: id, fileName: name,
    url: `qqbot-media://image/v1/${id}`, previewUrl: 'https://material.example.com/preview.png',
    createdAt: 1792540800, expiresAt: 1800316800})
  await page.route('https://material.example.com/**', route => route.fulfill({contentType: 'image/png', body: png}))
  await page.route('**/webui/meta/**', route => route.fulfill({json: {appId: '', botOpenId: ''}}))
  await page.route('**/webui/api/**', route => {
    const url = new URL(route.request().url())
    const path = url.pathname
    let data: unknown = {}
    if (path.endsWith('/auth/verify')) data = {userId: 'admin', displayName: '管理员', role: 'ADMIN', avatarUrl: null}
    else if (path.endsWith('/config')) data = {botName: 'Test Bot'}
    else if (path.endsWith('/count')) data = {pending: 0, reviewed: 0, denied: 0, all: 0}
    else if (path.endsWith('/list')) data = {items: [], total: 0}
    else if (path.endsWith('/material-images') && route.request().method() === 'GET') {
      reads++
      data = {images: deleted ? [] : [uploaded ? image('uploaded', 'uploaded.png')
          : url.searchParams.get('cursor') ? image('second', '第二张.png') : image('first', '第一张.png')],
        nextCursor: !uploaded && !deleted && !url.searchParams.get('cursor') ? 'next&cursor' : '',
        total: deleted ? 0 : uploaded ? 1 : 2}
    } else if (path.endsWith('/material-images') && route.request().method() === 'POST') {
      expect(route.request().headers()['content-type']).toContain('multipart/form-data')
      expect(route.request().postDataBuffer()?.includes(Buffer.from('uploaded.png'))).toBe(true)
      uploaded = true
      data = {imageId: 'uploaded', url: 'qqbot-media://image/v1/uploaded', ttl: 7776000}
    } else if (path.endsWith('/material-images/uploaded') && route.request().method() === 'DELETE') {
      deleted = true
      data = true
    }
    return route.fulfill({json: {code: 0, message: '请求成功', data}})
  })

  await page.goto('http://127.0.0.1:5175/webui/gallery')
  await expect(page.getByRole('button', {name: '图片素材', exact: true})).toBeVisible()
  expect(reads).toBe(0)
  await page.getByRole('button', {name: '图片素材', exact: true}).click()
  await expect(page.getByText('第一张.png', {exact: true})).toBeVisible()
  await expect(page.getByText('2 / 100 张')).toBeVisible()
  await expect(page.getByRole('button', {name: '待审核 0'})).toHaveCount(0)
  await page.getByRole('button', {name: '复制 Markdown', exact: true}).click()
  expect(await page.evaluate(() => (window as any).copiedMaterial)).toBe('![图片](qqbot-media://image/v1/first)')
  await page.getByRole('button', {name: '下一页', exact: true}).click()
  await expect(page.getByText('第二张.png', {exact: true})).toBeVisible()
  await page.getByRole('button', {name: '上一页', exact: true}).click()
  await expect(page.getByText('第一张.png', {exact: true})).toBeVisible()
  await expect(page.getByRole('img', {name: '第一张.png'})).toBeVisible()
  await expect(page.getByRole('button', {name: '上传图片', exact: true})).toBeInViewport()
  await page.screenshot({path: testInfo.outputPath('materials.png'), fullPage: true})
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)

  await page.getByLabel('选择素材图片').setInputFiles({name: 'uploaded.png', mimeType: 'image/png', buffer: png})
  await expect(page.getByText('uploaded.png', {exact: true})).toBeVisible()
  await expect(page.getByRole('status')).toHaveText('图片已上传')
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', {name: '删除', exact: true}).click()
  await expect(page.getByText('暂无图片素材', {exact: true})).toBeVisible()
  expect(deleted).toBe(true)
  await page.getByRole('button', {name: '背景图审核', exact: true}).click()
  await expect(page.getByRole('button', {name: '待审核 0'})).toBeVisible()
  await expect(page.getByText('暂无待审核图片', {exact: true})).toBeVisible()
  expect(errors).toEqual([])
})
