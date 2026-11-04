import {expect, test, type Page} from '@playwright/test'

async function mockChat(page: Page, recall: (body: {groupOpenId: string, messageId: string}) => Promise<boolean>) {
  await page.addInitScript(() => {
    window.EventSource = class { close() {} } as any
  })
  await page.route('**/webui/meta/**', route => route.fulfill({json:{}}))
  await page.route('**/webui/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    let data: unknown = {}
    if (path.endsWith('/auth/verify')) data = {role:'ADMIN',displayName:'管理员'}
    else if (path.endsWith('/config')) data = {botName:'AtriBot',botOpenId:'self'}
    else if (path.endsWith('/chat/conversations')) data = {items:[
      {type:'group',openId:'group-a',name:'测试群甲'}, {type:'group',openId:'group-b',name:'测试群乙'},
      {type:'c2c',openId:'user-a',name:'测试用户'}],hasMore:false,cursor:'1'}
    else if (path.endsWith('/chat/pinned') || path.endsWith('/avatar-members')) data = []
    else if (path.endsWith('/groups/recall')) {
      const ok = await recall(route.request().postDataJSON())
      return route.fulfill({status:ok ? 200 : 502,json:{code:ok ? 0 : 19999,message:ok ? '请求成功' : '消息撤回失败',data:null}})
    } else if (path.endsWith('/messages')) data = {total:4,cursor:'1',records:[4,3,2,1].map(id => ({
      id, messageOpenId:id === 4 ? null : `msg-${id}`, username:id === 1 ? 'AtriBot' : '群成员',
      unionOpenId:id === 1 ? 'self' : 'member',eventType:id === 1 ? 'BOT_SEND' : 'GROUP_MESSAGE_CREATE',
      content:`测试消息${id}`,messageType:0,eventTimestamp:1793448000+id
    }))}
    return route.fulfill({json:{code:0,data}})
  })
  await page.goto('http://127.0.0.1:5175/webui/chat?group=group-a')
  await expect(page.locator('.qm')).toHaveCount(4)
}

test('group message selection retains failures and excludes unavailable messages', async ({page}, testInfo) => {
  const requests: {groupOpenId:string,messageId:string}[] = []
  let rejectSecond = true
  await mockChat(page, async body => {
    requests.push(body)
    return !(rejectSecond && body.messageId === 'msg-2')
  })
  await page.locator('[data-message-id="1"] .qm-bubble').click({button:'right'})
  await page.locator('.ctx-menu').getByRole('button',{name:'多选',exact:true}).click()
  await expect(page.getByRole('checkbox',{name:'选择消息 1',exact:true})).toBeChecked()
  await expect(page.getByRole('checkbox',{name:'选择消息 4',exact:true})).toBeDisabled()
  await page.getByRole('button',{name:'全选已加载',exact:true}).click()
  await expect(page.locator('.chatnt-selection-count')).toHaveText('已选择 3 条消息')
  await expect(page.locator('.chatnt-composer')).toBeHidden()
  await page.screenshot({path:testInfo.outputPath('group-selection.png'),fullPage:true})
  expect(await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  page.once('dialog',dialog=>dialog.dismiss())
  await page.getByRole('button',{name:'撤回',exact:true}).click()
  expect(requests).toEqual([])
  page.once('dialog',dialog=>dialog.accept())
  await page.getByRole('button',{name:'撤回',exact:true}).click()
  await expect(page.locator('.chatnt-notice')).toContainText('已撤回 2 条，失败 1 条')
  expect(requests).toHaveLength(3)
  expect(new Set(requests.map(value=>value.messageId)).size).toBe(3)
  expect(requests.every(value=>value.groupOpenId === 'group-a')).toBe(true)
  await expect(page.getByRole('checkbox',{name:'选择消息 2',exact:true})).toBeChecked()
  await expect(page.getByRole('checkbox',{name:'选择消息 1',exact:true})).toBeDisabled()
  await expect(page.locator('[data-message-id="2"] .qm-bubble')).toHaveText('测试消息2')
  rejectSecond = false
  page.once('dialog',dialog=>dialog.accept())
  await page.getByRole('button',{name:'撤回',exact:true}).click()
  await expect(page.locator('.chatnt-selection-bar')).toHaveCount(0)
  await expect(page.locator('.chatnt-composer')).toBeVisible()
  expect(requests.at(-1)?.messageId).toBe('msg-2')
  expect(requests).toHaveLength(4)
})

test('recalls run concurrently and switching conversation isolates results from the new chat', async ({page}, testInfo) => {
  let release: (()=>void) | undefined
  const pending = new Promise<void>(resolve=>{release=resolve})
  const requests: {groupOpenId:string,messageId:string}[] = []
  await mockChat(page, async body=>{ requests.push(body); await pending; return true })
  await page.getByRole('button',{name:'多选消息',exact:true}).click()
  await page.getByRole('button',{name:'全选已加载',exact:true}).click()
  page.once('dialog',dialog=>dialog.accept())
  await page.getByRole('button',{name:'撤回',exact:true}).click()
  // 三条请求均在任何响应返回前发出，防止退回逐条等待的实现
  await expect.poll(()=>requests.length).toBe(3)
  await expect(page.getByRole('button',{name:'撤回中…',exact:true})).toBeDisabled()
  if (testInfo.project.name === 'mobile') await page.getByRole('button',{name:'返回会话列表',exact:true}).click()
  await page.locator('.cnv').filter({hasText:'测试群乙'}).click()
  await expect(page.locator('.chatnt-head-name')).toContainText('测试群乙')
  await expect(page.locator('.chatnt-selection-bar')).toHaveCount(0)
  const completed = Promise.all(requests.map(({messageId}) => page.waitForResponse(response =>
    response.url().endsWith('/groups/recall') && response.request().postDataJSON().messageId === messageId
  ).then(response => response.finished())))
  release!()
  await completed
  await expect(page.locator('.qm-bubble.recalled')).toHaveCount(0)
  expect(requests).toHaveLength(3)
  expect(requests.every(request=>request.groupOpenId === 'group-a')).toBe(true)
  if (testInfo.project.name === 'mobile') await page.getByRole('button',{name:'返回会话列表',exact:true}).click()
  await page.locator('.cnv').filter({hasText:'测试用户'}).click()
  await expect(page.locator('.chatnt-head-name')).toContainText('测试用户')
  await expect(page.getByRole('button',{name:'多选消息',exact:true})).toHaveCount(0)
  expect(requests).toHaveLength(3)
})
