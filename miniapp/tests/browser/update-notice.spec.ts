import {expect, test} from '@playwright/test'

test('edit update content, preview buttons and save on desktop and mobile', async ({page}, testInfo) => {
  const requests: any[] = []
  let joinEventPush = false
  let passiveMessagePush = false
  let enabled = true
  let settingsWrites = 0
  let layoutWrites = 0
  let defaultButtons: any[] = []
  let buttonWrites = 0
  let layouts = {
    textLayout: {header: '{botName}近期更新日志:\n> {time}', footer: '遇到了一些问题？使用/feedback向开发者反馈！'},
    markdownLayout: {header: '{botName}近期更新日志:\n> {time}', footer: '遇到了一些问题？使用/feedback向开发者反馈！'}
  }
  let content = {text: '**近期更新**\n\n每日问题已上线，试试 /question', format: 'MARKDOWN', buttons: []}
  await page.route('**/webui/api/**', route => {
    const path = new URL(route.request().url()).pathname
    const ok = (data: unknown) => route.fulfill({json: {code: 0, data}})
    if (path.endsWith('/auth/verify')) return ok({role: 'ADMIN', displayName: '管理员'})
    if (path.endsWith('/update-notice/settings')) {
      const settings = route.request().postDataJSON()
      if ('joinEventPush' in settings) joinEventPush = settings.joinEventPush
      if ('passiveMessagePush' in settings) passiveMessagePush = settings.passiveMessagePush
      if ('enabled' in settings) enabled = settings.enabled
      settingsWrites++
      return ok({joinEventPush, passiveMessagePush, enabled})
    }
    if (path.endsWith('/update-notice/layouts')) {
      layouts = route.request().postDataJSON()
      layoutWrites++
      return ok(layouts)
    }
    if (path.endsWith('/update-notice/buttons')) {
      defaultButtons = route.request().postDataJSON().buttons
      buttonWrites++
      return ok({buttons: defaultButtons})
    }
    if (path.endsWith('/update-notice')) {
      if (route.request().method() === 'PUT') { content = route.request().postDataJSON(); requests.push(content) }
      return ok({content: {...content, ...layouts, buttons: defaultButtons}, createdAt: 1793707200000, notifiedGroups: requests.length ? 0 : 128, botName: '亚托莉喵', joinEventPush, passiveMessagePush, enabled})
    }
    return ok({})
  })
  const errors: string[] = []
  page.on('pageerror', e => errors.push(e.message))
  await page.goto('http://127.0.0.1:5175/webui/update-notice')
  await expect(page.getByLabel('通知正文')).toHaveValue(content.text)
  await page.getByRole('switch', {name: '入群事件推送'}).click()
  await expect(page.getByRole('switch', {name: '入群事件推送'})).toBeChecked()
  await expect(page.locator('.update-stats')).toContainText('128')
  expect(settingsWrites).toBe(1)
  await expect(page.getByRole('switch', {name: '被动消息推送'})).not.toBeChecked()
  await page.getByRole('switch', {name: '被动消息推送'}).click()
  await expect(page.getByRole('switch', {name: '被动消息推送'})).toBeChecked()
  await expect(page.getByRole('switch', {name: '入群事件推送'})).toBeChecked()
  await expect(page.locator('.update-stats')).toContainText('128')
  expect(settingsWrites).toBe(2)
  await page.getByRole('switch', {name: '更新通知', exact: true}).click()
  await expect(page.getByRole('switch', {name: '更新通知', exact: true})).not.toBeChecked()
  await expect(page.getByRole('switch', {name: '入群事件推送'})).toBeChecked()
  await expect(page.locator('.update-stats')).toContainText('128')
  await page.getByRole('switch', {name: '更新通知', exact: true}).click()
  await expect(page.getByRole('switch', {name: '更新通知', exact: true})).toBeChecked()
  expect(requests).toHaveLength(0)
  await page.getByRole('tab', {name: '标题与落款', exact: true}).click()
  await page.getByLabel('通知落款').fill('独立保存的落款')
  await page.getByRole('button', {name: '保存标题与落款', exact: true}).click()
  await expect(page.getByRole('status')).toHaveText('标题与落款已保存')
  await expect(page.locator('.update-stats')).toContainText('128')
  expect(requests).toHaveLength(0)
  expect(layoutWrites).toBe(1)
  await page.getByRole('tab', {name: /按钮/}).click()
  await page.getByRole('button', {name: '＋ 添加一行'}).click()
  await page.getByLabel('第1行第1个按钮文字').fill('今日问题')
  await page.getByLabel('第1行第1个按钮内容').fill('/question')
  await expect(page.locator('.update-preview-keyboard')).toContainText('今日问题')
  await page.getByRole('button', {name: '保存默认按钮', exact: true}).click()
  await expect(page.getByRole('status')).toHaveText('默认按钮已保存')
  expect(buttonWrites).toBe(1)
  expect(requests).toHaveLength(0)
  await expect(page.locator('.update-stats')).toContainText('128')
  await page.getByRole('tab', {name: '正文', exact: true}).click()
  await expect(page.getByRole('button', {name: '保存通知', exact: true})).toBeDisabled()
  await page.getByLabel('通知正文').fill('更新后的正文')
  await page.getByRole('button', {name: '保存通知', exact: true}).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  expect(requests).toHaveLength(0)
  await page.getByRole('button', {name: '确认保存'}).click()
  await expect(page.getByRole('status')).toContainText('已保存')
  expect(defaultButtons[0][0]).toEqual({label: '今日问题', data: '/question', type: 'COMMAND', style: 'BLUE', enter: true})
  expect(requests[0].buttons).toBeUndefined()
  await page.getByRole('tab', {name: '正文', exact: true}).click()
  await page.getByLabel('通知正文').fill('**近期更新**\n\n每日问题已上线\n\n> 每天一道题，选择你的答案')
  await page.locator('.update-preview-panel').scrollIntoViewIfNeeded()
  await page.locator('.update-preview-panel').screenshot({path: testInfo.outputPath('update-message-preview.png')})
  await page.screenshot({path: testInfo.outputPath('update-notice.png'), fullPage: true})
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', {name: '纯文本', exact: true}).click()
  await expect(page.getByRole('tab', {name: /按钮/})).toBeDisabled()
  await page.getByRole('tab', {name: '标题与落款', exact: true}).click()
  await page.getByLabel('通知标题').fill('{botName} · 更新通知')
  await page.getByLabel('通知落款').fill('')
  await page.getByRole('button', {name: 'Markdown', exact: true}).click()
  await page.getByRole('tab', {name: '标题与落款', exact: true}).click()
  await expect(page.getByLabel('通知标题')).toHaveValue('{botName}近期更新日志:\n> {time}')
  await page.getByLabel('通知标题').fill('**{botName} 更新**')
  await page.getByLabel('通知落款').fill('Markdown 尾部')
  await expect(page.locator('.update-markdown strong').first()).toHaveText('亚托莉喵 更新')
  await page.getByRole('button', {name: '保存标题与落款', exact: true}).click()
  await expect(page.getByRole('status')).toHaveText('标题与落款已保存')
  expect(requests).toHaveLength(1)
  await page.getByRole('button', {name: '纯文本', exact: true}).click()
  await expect(page.locator('.update-plain')).toContainText('亚托莉喵 · 更新通知')
  await expect(page.locator('.update-plain')).not.toContainText('Markdown 尾部')
  await page.getByLabel('通知正文').fill('<img src=x onerror=alert(1)>')
  await expect(page.locator('.update-plain')).toContainText('<img src=x onerror=alert(1)>')
  await page.getByRole('button', {name: '保存通知', exact: true}).click()
  await page.getByRole('button', {name: '确认保存'}).click()
  await expect(page.getByRole('status')).toContainText('已保存')
  expect(requests[1].format).toBe('TEXT')
  expect(requests[1].buttons).toBeUndefined()
  expect(requests[1].textLayout).toBeUndefined()
  expect(layouts.textLayout).toEqual({header: '{botName} · 更新通知', footer: ''})
  expect(layouts.markdownLayout).toEqual({header: '**{botName} 更新**', footer: 'Markdown 尾部'})
  await page.reload()
  await expect(page.getByRole('switch', {name: '被动消息推送'})).toBeChecked()
  await expect(page.getByRole('switch', {name: '入群事件推送'})).toBeChecked()
  await page.getByRole('tab', {name: '标题与落款', exact: true}).click()
  await expect(page.getByLabel('通知落款')).toHaveValue('')
  await page.getByRole('button', {name: 'Markdown', exact: true}).click()
  await expect(page.locator('.update-markdown')).toContainText('Markdown 尾部')
  await expect(page.locator('.update-preview-keyboard')).toContainText('今日问题')
  await expect(page.getByRole('button', {name: '保存通知', exact: true})).toBeEnabled()
  expect(errors).toEqual([])
})
