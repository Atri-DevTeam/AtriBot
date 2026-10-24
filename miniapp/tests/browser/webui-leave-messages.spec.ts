import {expect, test} from '@playwright/test'

test('group leave messages retain the optional member-join trigger and clear it only after saving', async ({page}) => {
  const notes: Record<string, unknown>[] = []
  const writes: unknown[] = []
  let fail = false
  await page.route('**/leave-messages-test', route => route.fulfill({contentType: 'text/html; charset=utf-8', body: `
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <div id="app" style="position:fixed;bottom:20px;right:20px"></div>
    <script type="module">
      import {createApp, h} from '/node_modules/.vite/deps/vue.js';
      import GroupLeaveMessages from '/src/features/groups/components/GroupLeaveMessages.vue';
      const request = async (path, options = {}) => {
        const response = await fetch('/mock' + path, options);
        if (!response.ok) throw new Error('保存失败');
        return response.json();
      };
      createApp({render: () => h(GroupLeaveMessages, {groupId: 'group-A', request})}).mount('#app');
    </script>`}))
  await page.route('**/mock/groups/group-A/leave-messages**', route => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON()
      writes.push(body)
      if (fail) return route.fulfill({status: 500, json: {}})
      const id = String(notes.length + 1)
      notes.push({id, ...body, attempts: 0})
      return route.fulfill({json: {id}})
    }
    return route.fulfill({json: notes})
  })
  await page.goto('http://127.0.0.1:5175/leave-messages-test')
  await page.getByRole('button', {name: '群留言，0 条待发送'}).click()
  const dialog = page.getByRole('dialog', {name: '群留言设置'})
  const trigger = dialog.getByRole('checkbox', {name: '成员入群时触发'})
  await expect(trigger).not.toBeChecked()
  await dialog.getByRole('textbox', {name: '新留言'}).fill('**成员入群留言**')
  await trigger.check()
  fail = true
  await dialog.getByRole('button', {name: '添加留言'}).click()
  await expect(dialog.getByRole('alert')).toHaveText('保存失败')
  await expect(trigger).toBeChecked()
  fail = false
  await dialog.getByRole('button', {name: '添加留言'}).click()
  await expect(dialog.locator('.leave-item')).toContainText('成员入群时也可触发')
  await expect(trigger).not.toBeChecked()
  await dialog.getByRole('textbox', {name: '新留言'}).fill('仅消息触发')
  await dialog.getByRole('button', {name: '添加留言'}).click()
  await expect(dialog.locator('.leave-item')).toHaveCount(2)
  await expect(dialog.locator('.leave-join-tag')).toHaveCount(1)
  expect(writes).toEqual([
    {content: '**成员入群留言**', triggerOnMemberJoin: true},
    {content: '**成员入群留言**', triggerOnMemberJoin: true},
    {content: '仅消息触发', triggerOnMemberJoin: false}
  ])
  await dialog.getByRole('button', {name: '刷新', exact: true}).click()
  await expect(dialog.locator('.leave-join-tag')).toHaveCount(1)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
