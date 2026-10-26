import {expect, test} from '@playwright/test'

test('only pending replies offer deletion and deleted content disappears', async ({page}) => {
  const ticket = {id: '12345678-1111-2222-3333-123456789abc', platform: 'OFFICIAL_GROUP', userId: 'member-openid',
    username: '测试用户', groupId: 'group-openid', status: 'WAITING_USER', submitContent: '问题',
    replyContent: '待补发内容', replyTime: '2026-10-26 12:20:00', updateTime: '2026-10-26 12:20:00'}
  let entries = [
    {id: '1', type: 'REPLY', content: '已送达内容', actorName: '管理员', canDelete: false},
    {id: '2', type: 'REPLY', content: '待补发内容', actorName: '管理员', canDelete: true}
  ]
  let deleted = false
  await page.route('**/webui/meta/**', route => route.fulfill({json: {}}))
  await page.route('**/webui/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    let data: unknown = {}
    if (path.endsWith('/auth/verify')) data = {userId: 'admin', role: 'ADMIN', displayName: '管理员'}
    else if (path.endsWith('/feedback/list')) data = {items: [ticket], total: 1, page: 1, pageSize: 20}
    else if (path.endsWith('/feedback/count')) data = {pending: 0, processing: 0, waiting: 1, closed: 0, all: 1}
    else if (path.endsWith('/entries')) data = {items: [...entries].reverse(), nextCursor: ''}
    else if (request.method() === 'DELETE' && path.endsWith(`/feedback/${ticket.id}/entries/2`)) {
      deleted = true
      ticket.replyContent = '已送达内容'
      entries = [entries[0], {id: '2', type: 'REPLY_DELETED', content: '', actorName: '管理员', canDelete: false}]
    }
    await route.fulfill({json: {code: 0, message: '请求成功', data}})
  })
  await page.goto('http://127.0.0.1:5175/webui/feedback')
  await page.locator('.ticket-history summary').click()
  await expect(page.getByRole('button', {name: '删除回复', exact: true})).toHaveCount(1)
  await expect(page.locator('.ticket-entry').filter({hasText: '已送达内容'}).getByRole('button')).toHaveCount(0)
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', {name: '删除回复', exact: true}).click()
  await expect(page.getByRole('button', {name: '删除回复', exact: true})).toHaveCount(0)
  await expect(page.getByText('回复已删除', {exact: true})).toHaveCount(1)
  await page.locator('.ticket-history summary').click()
  await expect(page.getByText('回复已删除', {exact: true})).toBeVisible()
  await expect(page.getByText('待补发内容', {exact: true})).toHaveCount(0)
  expect(deleted).toBe(true)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('tickets retain history, use authenticated replies and close explicitly', async ({page}) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  const ticket = {
    id: '12345678-1111-2222-3333-123456789abc', platform: 'OFFICIAL_GROUP', userId: 'member-openid',
    username: '测试用户', groupId: 'group-openid', status: 'PENDING', submitContent: '首次提交的问题',
    latestContent: '追加的复现步骤', latestContentTime: '2026-10-26 12:10:00', replyActorName: '',
    createTime: '2026-10-26 12:00:00', updateTime: '2026-10-26 12:10:00', closedTime: '',
    replyContent: '', replyTime: ''
  }
  const entries = [
    {id: '1', type: 'SUBMIT', actorId: 'member-openid', actorName: '测试用户', content: ticket.submitContent, createTime: ticket.createTime, silent: false, avatarUrl: ''},
    {id: '2', type: 'APPEND', actorId: 'member-openid', actorName: '测试用户', content: ticket.latestContent, createTime: ticket.latestContentTime, silent: false, avatarUrl: ''}
  ]
  const replies: unknown[] = []
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jZ1kAAAAASUVORK5CYII=', 'base64')
  await page.route('https://thirdqq.qlogo.cn/**', route => route.fulfill({contentType: 'image/png', body: png}))
  const adminAvatar = 'https://q.qlogo.cn/qqapp/123/admin-openid/0'
  await page.route('https://q.qlogo.cn/**', route => route.fulfill({contentType: 'image/png', body: png}))
  await page.route('**/webui/meta/**', route => route.fulfill({json: {}}))
  await page.route('**/webui/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    let data: unknown = {}
    if (url.pathname.endsWith('/auth/verify')) data = {userId: 'admin-openid', role: 'ADMIN', displayName: '维护者'}
    else if (url.pathname.endsWith('/config')) data = {appId: '123456', botName: '测试机器人'}
    else if (url.pathname.endsWith('/feedback/count')) data = {
      pending: Number(ticket.status === 'PENDING'), processing: Number(ticket.status === 'PROCESSING'), waiting: Number(ticket.status === 'WAITING_USER'), closed: Number(ticket.status === 'CLOSED'), all: 1
    }
    else if (url.pathname.endsWith('/feedback/list')) {
      const filter = url.searchParams.get('filter')
      const items = filter === 'all' || (filter === 'waiting' ? ticket.status === 'WAITING_USER' : filter?.toUpperCase() === ticket.status) ? [ticket] : []
      data = {items, total: items.length, page: 1, pageSize: 20}
    }
    else if (url.pathname.endsWith('/entries')) data = {items: [...entries].reverse(), nextCursor: ''}
    else if (url.pathname.endsWith('/feedback/reply')) {
      const body = request.postDataJSON()
      replies.push(body)
      Object.assign(ticket, {status: 'WAITING_USER', replyContent: body.replyContent, replyActorName: '维护者', replyTime: '2026-10-26 12:20:00'})
      entries.push({id: '3', type: 'REPLY', actorId: 'account-uuid', actorName: '维护者', content: body.replyContent,
        createTime: ticket.replyTime, silent: body.silent, avatarUrl: adminAvatar})
    }
    else if (url.pathname.endsWith('/feedback/close')) {
      expect(request.postDataJSON()).toEqual({id: ticket.id})
      ticket.status = 'CLOSED'
      ticket.closedTime = '2026-10-26 12:30:00'
      entries.push({id: '4', type: 'ADMIN_CLOSE', actorId: 'account-uuid', actorName: '维护者', content: '',
        createTime: ticket.closedTime, silent: true, avatarUrl: adminAvatar})
    }
    else if (url.pathname.endsWith('/feedback/reopen')) {
      expect(request.postDataJSON()).toEqual({id: ticket.id})
      ticket.status = 'WAITING_USER'
      ticket.closedTime = ''
      entries.push({id: '5', type: 'ADMIN_REOPEN', actorId: 'account-uuid', actorName: '维护者', content: '',
        createTime: '2026-10-26 12:40:00', silent: true, avatarUrl: adminAvatar})
    }
    await route.fulfill({json: {code: 0, message: '请求成功', data}})
  })
  await page.goto('http://127.0.0.1:5175/webui/feedback')
  await expect(page.getByText('追加的复现步骤', {exact: true})).toBeVisible()
  await expect(page.locator('.ticket-user-icon img')).toHaveAttribute('src', 'https://thirdqq.qlogo.cn/qqapp/123456/member-openid/640')
  await expect(page.getByText('OFFICIAL_GROUP', {exact: true})).toHaveCount(0)
  const avatar = await page.locator('.ticket-user-icon').boundingBox()
  const name = await page.locator('.ticket-user-name').boundingBox()
  expect(Math.abs(avatar!.y + avatar!.height / 2 - name!.y - name!.height / 2)).toBeLessThan(1)
  const identifiers = page.locator('.ticket-identifiers > div')
  await expect(identifiers).toHaveCount(2)
  const userId = await identifiers.nth(0).boundingBox()
  const groupId = await identifiers.nth(1).boundingBox()
  expect(userId!.y).toBeGreaterThanOrEqual(name!.y + name!.height)
  expect(groupId!.y).toBeGreaterThan(userId!.y)
  expect(groupId!.x).toEqual(userId!.x)
  await page.locator('.ticket-history summary').click()
  await expect(page.locator('.ticket-entry')).toHaveCount(2)
  await expect(page.locator('.ticket-entry').first().locator('.ticket-entry-avatar img')).toHaveAttribute('src', 'https://thirdqq.qlogo.cn/qqapp/123456/member-openid/640')
  await page.getByRole('button', {name: '回复工单', exact: true}).click()
  await page.getByRole('button', {name: '已修复', exact: true}).click()
  await expect(page.getByLabel('隐藏用户原始内容')).toHaveCount(0)
  await expect(page.getByLabel('私聊回复', {exact: true})).toBeDisabled()
  await page.getByLabel('静默回复').check()
  await page.getByRole('button', {name: '提交回复', exact: true}).click()
  await expect(page.locator('.ticket-dialog')).not.toBeVisible()
  expect(replies).toEqual([{id: ticket.id, replyContent: '问题已修复', silent: true, privateReply: false, closeTicket: false}])
  await page.getByRole('button', {name: '等待用户回复 1', exact: true}).click()
  await expect(page.getByText('维护者 · 回复', {exact: true})).toBeVisible()
  await expect(page.getByRole('button', {name: '关闭工单', exact: true})).toBeVisible()
  await page.locator('.ticket-history summary').click()
  await expect(page.locator('.ticket-entry')).toHaveCount(3)
  await expect(page.locator('.ticket-entry').last().getByText('维护者', {exact: true})).toBeVisible()
  await expect(page.locator('.ticket-entry').last().getByText('静默', {exact: true})).toBeVisible()
  await expect(page.locator('.ticket-entry').last().locator('.ticket-entry-avatar img')).toHaveAttribute('src', adminAvatar)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', {name: '关闭工单', exact: true}).click()
  await page.getByRole('button', {name: '已关闭 1', exact: true}).click()
  await expect(page.getByRole('button', {name: '回复工单', exact: true})).toHaveCount(0)
  await expect(page.getByRole('button', {name: '关闭工单', exact: true})).toHaveCount(0)
  await expect(page.locator('.ticket-closed-time')).toBeVisible()
  await page.getByRole('button', {name: '重新打开', exact: true}).click()
  await page.getByRole('button', {name: '等待用户回复 1', exact: true}).click()
  await expect(page.getByRole('button', {name: '回复工单', exact: true})).toBeVisible()
  await expect(page.locator('.ticket-closed-time')).toHaveCount(0)
  await page.locator('.ticket-history summary').click()
  await expect(page.locator('.ticket-entry')).toHaveCount(5)
  await expect(page.locator('.ticket-entry').last()).toContainText('重新打开')
  await expect(page.locator('.ticket-entry').last()).toContainText('维护者')
  await expect(page.locator('.ticket-entry').last().locator('.ticket-entry-avatar img')).toHaveAttribute('src', adminAvatar)
  expect(errors).toEqual([])
})

test('eligible tickets can request a private reply and silent mode clears that choice', async ({page}) => {
  const replies: unknown[] = []
  const ticket = {id: '12345678-1111-2222-3333-123456789abc', platform: 'OFFICIAL_GROUP', userId: 'member-openid',
    username: '测试用户', groupId: 'group-openid', status: 'PENDING', submitContent: '问题', canPrivateReply: true}
  await page.route('**/webui/meta/**', route => route.fulfill({json: {}}))
  await page.route('**/webui/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    let data: unknown = {}
    if (path.endsWith('/auth/verify')) data = {userId: 'admin', role: 'ADMIN', displayName: '维护者'}
    else if (path.endsWith('/feedback/list')) {
      const filter = new URL(route.request().url()).searchParams.get('filter')
      const matches = filter === 'all' || (filter === 'waiting' ? ticket.status === 'WAITING_USER' : filter?.toUpperCase() === ticket.status)
      data = {items: matches ? [ticket] : [], total: Number(matches), page: 1, pageSize: 20}
    }
    else if (path.endsWith('/feedback/count')) data = {pending: Number(ticket.status === 'PENDING'), processing: 0, waiting: Number(ticket.status === 'WAITING_USER'), closed: Number(ticket.status === 'CLOSED'), all: 1}
    else if (path.endsWith('/feedback/reply')) {
      const body = route.request().postDataJSON()
      replies.push(body)
      ticket.status = body.closeTicket ? 'CLOSED' : 'WAITING_USER'
    }
    await route.fulfill({json: {code: 0, message: '请求成功', data}})
  })
  await page.goto('http://127.0.0.1:5175/webui/feedback')
  await page.getByRole('button', {name: '回复工单', exact: true}).click()
  const privateReply = page.getByLabel('私聊回复', {exact: true})
  await expect(privateReply).toBeEnabled()
  await privateReply.check()
  await page.getByLabel('静默回复').check()
  await expect(privateReply).not.toBeChecked()
  await expect(privateReply).toBeDisabled()
  await page.getByLabel('静默回复').uncheck()
  await privateReply.check()
  await page.getByLabel('回复内容', {exact: true}).fill('通过私聊答复')
  await expect(page.getByRole('checkbox', {name: '等待用户回复', exact: true})).toHaveCount(0)
  await page.getByRole('button', {name: '提交回复', exact: true}).click()
  await expect(page.locator('.ticket-dialog')).not.toBeVisible()
  expect(replies).toEqual([{id: ticket.id, replyContent: '通过私聊答复', silent: false, privateReply: true, closeTicket: false}])
  await page.getByRole('button', {name: '等待用户回复 1', exact: true}).click()
  await expect(page.locator('.ticket-tag--waiting_user')).toHaveText('等待用户回复')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', {name: '回复工单', exact: true}).click()
  await expect(page.getByRole('checkbox', {name: '等待用户回复', exact: true})).toHaveCount(0)
  const closeTicket = page.getByLabel('同时关闭工单', {exact: true})
  await expect(closeTicket).not.toBeChecked()
  await closeTicket.check()
  await page.getByRole('button', {name: '取消', exact: true}).click()
  await page.getByRole('button', {name: '回复工单', exact: true}).click()
  await expect(closeTicket).not.toBeChecked()
  await closeTicket.check()
  await page.getByLabel('回复内容', {exact: true}).fill('处理完成')
  await page.getByRole('button', {name: '提交回复', exact: true}).click()
  await expect(page.locator('.ticket-dialog')).not.toBeVisible()
  expect(replies[1]).toEqual({id: ticket.id, replyContent: '处理完成', silent: false, privateReply: false, closeTicket: true})
  await page.getByRole('button', {name: '已关闭 1', exact: true}).click()
  await expect(page.locator('.ticket-tag--closed')).toHaveText('已关闭')
})
