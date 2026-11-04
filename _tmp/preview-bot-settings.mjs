import {createServer} from '../webui/node_modules/vite/dist/node/index.js'
import {chromium} from '../miniapp/node_modules/@playwright/test/index.mjs'
import {mkdir} from 'node:fs/promises'
const server = await createServer({root: new URL('../webui', import.meta.url).pathname.replace(/^\/(\w:)/, '$1'), server: {host: '127.0.0.1', port: 5186, strictPort: true}})
await server.listen()
const browser = await chromium.launch({channel: 'msedge', headless: true})
await mkdir(new URL('../output/previews/', import.meta.url), {recursive: true})
try {
  for (const width of [1440, 390, 320]) {
    const page = await browser.newPage({viewport: {width, height: 1000}})
    const errors = []
    page.on('pageerror', e => errors.push(e.message))
    let settings = [
      {key: 'qq.enabled', label: '官方机器人', description: '启用 QQ 官方机器人连接', type: 'boolean', value: true, restartRequired: true},
      {key: 'webui.enabled', label: 'WebUI', description: '启用 WebUI 管理面板', type: 'boolean', value: true, restartRequired: true},
      {key: 'qq.debug-group-openId', label: '调试群 OpenID', description: '调试消息使用的目标群', type: 'string', value: 'AB1234567890ABCDEF1234567890ABCDEF'},
      {key: 'test.limit', label: '消息记录保留天数', description: '超过保留时间的记录将自动清理', type: 'int', value: 30}
    ]
    await page.route('**/webui/api/**', route => {
      const path = new URL(route.request().url()).pathname
      const ok = data => route.fulfill({json: {code: 0, data}})
      if (path.endsWith('/auth/verify')) return ok({role: 'ADMIN', displayName: '管理员'})
      if (path.endsWith('/bot/profile')) return ok({botName: '亚托莉喵', botUin: '3889001234', appId: '102076836', openId: 'AB1234567890ABCDEF1234567890ABCDEF', unionId: '1234567890ABCDEF1234567890ABCDEF00', env: 'production', connectionMode: 'webhook', apiBaseUrl: 'https://api.sgroup.qq.com', shareUrl: 'https://qun.qq.com/qunpro/robot/qunshare?robot_uin=3889001234'})
      if (path.endsWith('/bot/settings')) {
        if (route.request().method() === 'PUT') {
          const {updates} = route.request().postDataJSON()
          settings = settings.map(s => s.key in updates ? {...s, value: updates[s.key]} : s)
        }
        return ok(settings)
      }
      if (path.endsWith('/command-settings')) return ok([{name: 'question', displayName: '今日问题', description: '查看每日问题并选择答案', registered: true, rules: {}, groupConfigurable: true, aliases: [], usage: '/question'}])
      if (path.endsWith('/function-settings')) return ok([])
      if (path.includes('/join-welcome/')) return ok({config: {text: '欢迎加入', buttons: []}})
      if (path.includes('/cleanup/')) return ok({state: 'idle', running: false})
      return ok({})
    })
    await page.goto('http://127.0.0.1:5186/webui/bot-settings')
    await page.getByLabel('调试群 OpenID').waitFor()
    if (await page.locator('.bs-disclosure[open]').count()) throw Error('默认折叠失效')
    await page.getByLabel('消息记录保留天数').fill('31')
    await page.getByRole('button', {name: '保存修改', exact: true}).click()
    await page.getByText('已保存', {exact: true}).waitFor()
    await page.locator('.bot-settings-layout').evaluate(el => {el.scrollTop = 0})
    await page.screenshot({path: new URL(`../output/previews/bot-settings-${width}.png`, import.meta.url).pathname.replace(/^\/(\w:)/, '$1')})
    for (const section of ['默认加群欢迎', '指令管理', '功能配置']) {
      await page.locator('summary').filter({hasText: section}).click()
    }
    const overflow = await page.evaluate(() => [...document.querySelectorAll('.bot-settings-layout, .bot-settings-page, .bs-card')].filter(el => el.scrollWidth > el.clientWidth + 1).map(el => el.className))
    console.log(JSON.stringify({width, overflow, errors, saved: settings.at(-1).value}))
    if (errors.length || overflow.length) throw Error('页面检查失败')
    await page.close()
  }
} finally {
  await browser.close()
  await server.close()
}
