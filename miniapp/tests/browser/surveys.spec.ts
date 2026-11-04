import {test, expect} from '@playwright/test'
import {profile} from './profile-fixture'
import {activityResponse} from './activity-fixture'

const questions = [
  {id: 'q1', type: 'SINGLE', title: '最常用的功能', required: true, options: [{id: 'a', label: '游戏数据查询'}, {id: 'b', label: '小游戏'}]},
  {id: 'q2', type: 'MULTIPLE', title: '希望改进哪些体验', required: true, options: [{id: 'a', label: '响应速度'}, {id: 'b', label: '页面设计'}]},
  {id: 'q3', type: 'TEXT', title: '其他建议', required: false, options: []}
]
const survey = {id: '8f3a01c9d2b7', title: '机器人使用体验调查', description: '你的回答将帮助我们完善机器人功能', status: 'PUBLISHED', revision: 2, questions, responseCount: 0, rewardGold: 20, visible: true, pinned: true, repeatable: false}
const grant = () => ({token: 'b'.repeat(43), user: {userId: 'owner', displayName: '用户'}, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000})

test('repeatable survey starts a clean answer and retries with the same request ID', async ({page}) => {
  const current = {...survey, repeatable: true, questions: [questions[0]]}
  let submission: any = null
  const requests: string[] = []
  const received = new Map<string, any>()
  await page.route('**/dashboard/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/bot')) return route.fulfill({json: profile.bot})
    if (path.endsWith('/session')) return route.fulfill({json: {status: 'ready', access: grant(), canManage: false, webuiEnabled: true}})
    return route.fulfill({status: 204})
  })
  await page.route('**/atrimeow/profile/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/profile')) return route.fulfill({json: profile})
    if (path.endsWith('/activity')) return route.fulfill({json: activityResponse})
    if (path.endsWith('/responses')) {
      const body = route.request().postDataJSON()
      requests.push(body.requestId)
      if (!received.has(body.requestId)) {
        submission = {answers: body.answers, rewardGold: received.size ? 0 : 20, submittedAt: Date.now()}
        received.set(body.requestId, submission)
      }
      if (requests.length === 2) return route.abort('failed')
      return route.fulfill({json: {code: 0, data: received.get(body.requestId)}})
    }
    if (path.endsWith(`/surveys/${survey.id}`)) return route.fulfill({json: {code: 0, data: {survey: current, submission}}})
    return route.fulfill({status: 204})
  })
  await page.goto(`/dashboard/surveys/${survey.id}`)
  await page.getByLabel('游戏数据查询', {exact: true}).check()
  await page.getByRole('button', {name: '提交问卷', exact: true}).click()
  await page.getByRole('button', {name: '确认提交', exact: true}).click()
  await expect(page.getByText('已发放 20 金粒')).toBeVisible()
  await page.getByRole('button', {name: '再次填写', exact: true}).click()
  await expect(page.getByLabel('游戏数据查询', {exact: true})).not.toBeChecked()
  await page.getByLabel('小游戏', {exact: true}).check()
  await page.getByRole('button', {name: '提交问卷', exact: true}).click()
  await page.getByRole('button', {name: '确认提交', exact: true}).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await page.getByRole('button', {name: '提交问卷', exact: true}).click()
  await page.getByRole('button', {name: '确认提交', exact: true}).click()
  await expect(page.getByText('已提交，感谢参与')).toBeVisible()
  expect(received.size).toBe(2)
  expect(requests[0]).not.toBe(requests[1])
  expect(requests[1]).toBe(requests[2])
  await expect(page.getByText('已发放 20 金粒')).toHaveCount(0)
  await page.reload()
  await expect(page.getByLabel('小游戏', {exact: true})).toBeChecked()
  await expect(page.getByRole('button', {name: '再次填写', exact: true})).toBeVisible()
})

test('webui duplicates a closed survey into an independent editable draft', async ({page}) => {
  const original = {...survey, status: 'CLOSED', description: '原有说明', rewardGold: 35, visible: false, pinned: true, repeatable: true, responseCount: 23, updatedAt: Date.now()}
  const originalSnapshot = structuredClone(original)
  let created: any = null
  let creates = 0
  const writes: string[] = []
  await page.route('**/webui/api/**', route => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()
    const ok = (data: unknown) => route.fulfill({json: {code: 0, data}})
    if (method !== 'GET') writes.push(`${method} ${path}`)
    if (path.endsWith('/auth/verify')) return ok({role: 'ADMIN', username: '管理员'})
    if (path.endsWith('/surveys') && method === 'GET') return ok({items: created ? [created, original] : [original], total: created ? 2 : 1})
    if (path.endsWith('/surveys') && method === 'POST') {
      creates++
      const body = route.request().postDataJSON()
      expect(Object.keys(body).sort()).toEqual(['title', 'description', 'questions', 'rewardGold', 'visible', 'pinned', 'repeatable'].sort())
      created = {...body, id: 'a3f1b7c8d9e0', status: 'DRAFT', revision: 1, responseCount: 0, updatedAt: Date.now()}
      return ok({id: created.id})
    }
    if (path.endsWith(`/surveys/${original.id}`)) return ok({survey: original})
    if (path.endsWith('/surveys/a3f1b7c8d9e0')) return ok({survey: created})
    return ok({})
  })
  await page.route('**/webui/meta/**', route => route.fulfill({json: {appId: '', botOpenId: ''}}))
  await page.goto('http://127.0.0.1:5175/webui/surveys')
  await page.locator('.survey-card-open').click()
  await expect(page.getByLabel('问卷标题', {exact: true})).toBeDisabled()
  await page.getByRole('button', {name: '返回列表', exact: true}).click()
  await page.getByRole('button', {name: /的更多操作$/}).click()
  await page.getByRole('menuitem', {name: '复制为新问卷', exact: true}).click()
  await expect(page.getByLabel('问卷标题', {exact: true})).toHaveValue(`${original.title}（副本）`)
  await expect(page.getByLabel('问卷标题', {exact: true})).toBeEnabled()
  await expect(page.getByLabel('问卷说明', {exact: true})).toHaveValue(original.description)
  await expect(page.getByRole('spinbutton', {name: '奖励金粒'})).toHaveValue('35')
  await expect(page.getByLabel('在问卷列表显示')).not.toBeChecked()
  await expect(page.getByLabel('置顶问卷', {exact: true})).toBeChecked()
  await expect(page.getByLabel('允许重复填写', {exact: true})).toBeChecked()
  await expect(page.locator('.survey-question-editor')).toHaveCount(3)
  await expect(page.getByRole('button', {name: /^答卷/})).toHaveCount(0)
  expect(creates).toBe(0)
  await page.getByLabel('问卷标题', {exact: true}).fill('新一期调查')
  await page.getByLabel('问题 1 选项 1', {exact: true}).fill('新版选项')
  await page.getByRole('button', {name: '保存草稿', exact: true}).click()
  await expect(page.getByText('草稿已保存')).toBeVisible()
  await expect(page.getByRole('button', {name: '发布问卷', exact: true})).toBeEnabled()
  expect(creates).toBe(1)
  expect(created.id).not.toBe(original.id)
  expect(created.responseCount).toBe(0)
  expect(created.questions.map((q: any) => q.type)).toEqual(['SINGLE', 'MULTIPLE', 'TEXT'])
  expect(created.questions[0].id).not.toBe(original.questions[0].id)
  expect(created.questions[0].options[0].id).not.toBe(original.questions[0].options[0].id)
  expect(created.questions[0].options[0].label).toBe('新版选项')
  expect(original).toEqual(originalSnapshot)
  expect(writes).toEqual(['POST /webui/api/surveys'])
  await page.getByRole('button', {name: '返回列表', exact: true}).click()
  await expect(page.locator('.survey-card')).toHaveCount(2)
  await page.locator('.survey-card').filter({hasText: original.title}).locator('.survey-card-open').click()
  await expect(page.getByLabel('问题 1 选项 1', {exact: true})).toHaveValue('游戏数据查询')
})

test('survey link survives login and opens directly for administrators', async ({page}, testInfo) => {
  let loggedIn = false, submits = 0
  let submission: any = null
  let start: any = null
  await page.route('https://thirdqq.qlogo.cn/**', route => route.fulfill({status: 404}))
  await page.route('**/dashboard/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/bot')) return route.fulfill({json: profile.bot})
    if (path.endsWith('/session')) return route.fulfill({json: loggedIn ? {status: 'ready', access: grant(), canManage: true, webuiEnabled: true} : {status: 'anonymous'}})
    if (path.endsWith('/start')) {
      start = route.request().postDataJSON()
      return route.fulfill({json: {url: 'https://graph.qq.com/oauth2.0/authorize?state=test'}})
    }
    return route.fulfill({status: 204})
  })
  await page.route('https://graph.qq.com/**', route => route.fulfill({contentType: 'text/html; charset=utf-8', body: '<h1>授权测试页</h1>'}))
  await page.route('**/atrimeow/profile/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/profile')) return route.fulfill({json: profile})
    if (path.endsWith('/activity')) return route.fulfill({json: activityResponse})
    if (path.endsWith('/surveys/8f3a01c9d2b7/responses')) {
      submits++
      const body = route.request().postDataJSON()
      expect(body.requestId).toMatch(/^[0-9a-f-]{36}$/)
      expect(body).toEqual({requestId: body.requestId, revision: 2, answers: {q1: ['a'], q2: ['a', 'b'], q3: ['希望增加更多查询功能']}})
      submission = {answers: body.answers, submittedAt: Date.now(), rewardGold: 20}
      return route.fulfill({json: {code: 0, data: submission}})
    }
    if (path.endsWith('/surveys/8f3a01c9d2b7')) return route.fulfill({json: {code: 0, data: {survey, submission}}})
    if (path.endsWith('/surveys')) return route.fulfill({json: {code: 0, data: {items: [{...survey, submitted: !!submission}], total: 1}}})
    if (path.endsWith('/about')) return route.fulfill({json: {version: 'test'}})
    return route.fulfill({status: 204})
  })
  await page.goto('/dashboard/surveys/8f3a01c9d2b7?qq=1')
  await expect(page.getByRole('heading', {name: '授权测试页'})).toBeVisible()
  expect(start.survey).toBe('8f3a01c9d2b7')
  expect(start.qq).toBe(true)
  loggedIn = true
  await page.goto('/dashboard/surveys/8f3a01c9d2b7?qq=1')
  await expect(page.getByRole('heading', {name: survey.title})).toBeVisible()
  await expect(page.getByText('提交奖励：20 金粒')).toBeVisible()
  await expect(page.getByRole('heading', {name: '选择工作台'})).toHaveCount(0)
  await expect(page.locator('.topbar')).toHaveCSS('padding-top', '44px')
  await page.getByRole('button', {name: '提交问卷', exact: true}).click()
  await expect(page.getByRole('alert')).toContainText('请填写：最常用的功能')
  expect(submits).toBe(0)
  await page.getByLabel('游戏数据查询', {exact: true}).check()
  await page.getByLabel('响应速度', {exact: true}).check()
  await page.getByLabel('页面设计', {exact: true}).check()
  await page.getByLabel('其他建议', {exact: true}).fill('希望增加更多查询功能')
  await page.screenshot({path: testInfo.outputPath('survey-fill.png'), fullPage: true})
  await page.getByRole('button', {name: '提交问卷', exact: true}).click()
  const confirm = page.getByRole('dialog', {name: '确认提交问卷？'})
  await expect(confirm).toBeVisible()
  expect(submits).toBe(0)
  await confirm.getByRole('button', {name: '继续填写'}).click()
  await expect(confirm).not.toBeVisible()
  expect(submits).toBe(0)
  await expect(page.getByLabel('其他建议', {exact: true})).toHaveValue('希望增加更多查询功能')
  await page.getByRole('button', {name: '提交问卷', exact: true}).click()
  await page.screenshot({path: testInfo.outputPath('survey-submit-confirm.png'), fullPage: true})
  await confirm.getByRole('button', {name: '确认提交', exact: true}).click()
  await expect(page.getByText('已提交，感谢参与')).toBeVisible()
  await expect(page.getByText('已发放 20 金粒')).toBeVisible()
  await expect(page.getByRole('button', {name: '提交问卷', exact: true})).toHaveCount(0)
  await page.reload()
  await expect(page.getByText('已提交，感谢参与')).toBeVisible()
  await expect(page.getByLabel('其他建议', {exact: true})).toHaveValue('希望增加更多查询功能')
  await expect(page.getByLabel('其他建议', {exact: true})).toBeDisabled()
  expect(submits).toBe(1)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', {name: '问卷列表', exact: true}).click()
  await expect(page).toHaveURL(/\/dashboard\/surveys\?qq=1$/)
  await expect(page.locator('.survey-list-item .survey-pinned')).toHaveText('置顶')
  await page.getByRole('button', {name: '关于', exact: true}).first().click()
  await expect(page.getByRole('button', {name: /问卷填写.*参与问卷调查/})).toBeVisible()
  await page.getByRole('button', {name: /问卷填写.*参与问卷调查/}).click()
  await expect(page.getByRole('heading', {name: '问卷填写', exact: true})).toBeVisible()
})

test('webui creates a draft, publishes it and reads responses', async ({page}, testInfo) => {
  await page.addInitScript(() => Object.defineProperty(navigator, 'clipboard', {value: {writeText: async (value: string) => { (window as any).__copiedSurveyLink = value }}}))
  let saved: any = null
  const stars = new Set<number>()
  let details = 0
  const response = (id: number) => ({id, username: `用户${id}`, userOpenId: `test-user-${id}`, submittedAt: Date.now(), starred: stars.has(id)})
  await page.route('**/webui/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()
    const ok = (data: unknown) => route.fulfill({json: {code: 0, data}})
    if (path.endsWith('/auth/verify')) return ok({role: 'ADMIN', username: '管理员'})
    if (path.endsWith('/surveys') && method === 'GET') return ok({items: saved ? [saved] : [], total: saved ? 1 : 0, entryBase: 'https://bot.example.test'})
    if (path.endsWith('/surveys') && method === 'POST') {
      saved = {...route.request().postDataJSON(), id: 'a3f1b7c8d9e0', status: 'DRAFT', revision: 1, responseCount: 0, updatedAt: Date.now()}
      return ok({id: 'a3f1b7c8d9e0'})
    }
    if (path.endsWith('/surveys/a3f1b7c8d9e0')) {
      if (method === 'DELETE') { saved = null; return ok(null) }
      if (method === 'PUT') { const body = route.request().postDataJSON(); expect(body.revision).toBe(saved.revision); saved = {...saved, ...body.definition, revision: saved.revision + 1}; return ok(null) }
      return ok({survey: saved})
    }
    if (path.endsWith('/status')) {
      const body = route.request().postDataJSON()
      expect(body.revision).toBe(saved.revision)
      saved.status = body.status; saved.revision++
      return ok(null)
    }
    if (path.endsWith('/settings')) {
      expect(method).toBe('PUT')
      const body = route.request().postDataJSON()
      expect(body.revision).toBe(saved.revision)
      saved = {...saved, ...body, revision: saved.revision + 1}
      return ok(saved)
    }
    if (path.endsWith('/responses')) {
      const number = Number(new URL(route.request().url()).searchParams.get('page') || 1)
      const filtered = new URL(route.request().url()).searchParams.get('starredOnly') === 'true'
      const all = Array.from({length: 23}, (_, i) => 23 - i).filter(id => !filtered || stars.has(id))
      const current = Math.min(number, Math.max(1, Math.ceil(all.length / 20)))
      return ok({items: all.slice((current - 1) * 20, current * 20).map(response), total: all.length, snapshot: 23, page: current, pageSize: 20})
    }
    if (path.includes('/responses/at/')) {
      details++
      expect(new URL(route.request().url()).searchParams.get('snapshot')).toBe('23')
      const position = Number(path.split('/').at(-1))
      const filtered = new URL(route.request().url()).searchParams.get('starredOnly') === 'true'
      const ids = Array.from({length: 23}, (_, i) => 23 - i).filter(id => !filtered || stars.has(id))
      const id = ids[position - 1]
      return ok({answer: {...response(id), answers: {[saved.questions[0].id]: [saved.questions[0].options[0].id]}}, starred: stars.has(id), total: ids.length, position, snapshot: 23})
    }
    if (path.endsWith('/star')) {
      expect(method).toBe('PUT')
      const id = Number(path.split('/').at(-2))
      if (route.request().postDataJSON().starred) stars.add(id); else stars.delete(id)
      return ok(null)
    }
    return ok({})
  })
  await page.route('**/webui/meta/**', route => route.fulfill({json: {appId: '', botOpenId: ''}}))
  await page.goto('http://127.0.0.1:5175/webui/surveys')
  await page.getByRole('button', {name: '新建问卷', exact: true}).click()
  await page.getByLabel('问卷标题', {exact: true}).fill('功能调查')
  await page.getByLabel('问题 1 标题', {exact: true}).fill('你常用哪项功能？')
  await page.getByLabel('问题 1 选项 1', {exact: true}).fill('查询')
  await page.getByLabel('问题 1 选项 2', {exact: true}).fill('小游戏')
  await page.getByRole('spinbutton', {name: '奖励金粒'}).fill('20')
  await page.getByLabel('置顶问卷', {exact: true}).check()
  await page.getByRole('button', {name: '保存草稿', exact: true}).click()
  await expect(page.getByText('草稿已保存')).toBeVisible()
  expect(saved.pinned).toBe(true)
  await page.locator('.surveys-scroll').evaluate(element => { element.scrollTop = 0 })
  await page.screenshot({path: testInfo.outputPath('survey-admin.png'), fullPage: true})
  page.on('dialog', dialog => dialog.accept())
  await page.getByRole('button', {name: '发布问卷', exact: true}).click()
  await expect(page.getByText('问卷已发布', {exact: true})).toBeVisible()
  await expect(page.getByLabel('问卷标题', {exact: true})).toBeDisabled()
  await page.getByRole('spinbutton', {name: '奖励金粒'}).fill('35')
  await page.getByLabel('在问卷列表显示').uncheck()
  await page.getByLabel('置顶问卷', {exact: true}).uncheck()
  await page.getByRole('button', {name: '保存设置', exact: true}).click()
  await expect(page.getByText('设置已保存', {exact: true})).toBeVisible()
  expect(saved.rewardGold).toBe(35)
  expect(saved.visible).toBe(false)
  expect(saved.pinned).toBe(false)
  await page.getByRole('button', {name: '返回列表', exact: true}).click()
  await page.getByRole('button', {name: /的更多操作$/}).click()
  const cardBox = await page.locator('.survey-card').boundingBox()
  const moreBox = await page.getByRole('button', {name: /的更多操作$/}).boundingBox()
  expect(moreBox!.x).toBeGreaterThan(cardBox!.x + cardBox!.width / 2)
  expect(moreBox!.y).toBeGreaterThan(cardBox!.y + cardBox!.height / 2)
  await page.getByRole('menuitem', {name: '复制填写链接', exact: true}).click()
  await expect(page.getByText('填写链接已复制', {exact: true})).toBeVisible()
  expect(await page.evaluate(() => (window as any).__copiedSurveyLink)).toBe('https://bot.example.test/dashboard/surveys/a3f1b7c8d9e0?_wwv=9983&qq=1')
  await page.getByRole('button', {name: /的更多操作$/}).click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('menu')).toHaveCount(0)
  await page.getByRole('button', {name: /的更多操作$/}).click()
  await page.screenshot({path: testInfo.outputPath('survey-actions-menu.png'), fullPage: true})
  await page.getByRole('menuitem', {name: '停止收集', exact: true}).click()
  await expect(page.getByText('问卷已关闭', {exact: true})).toBeVisible()
  await page.locator('.survey-card-open').click()
  await page.getByLabel('在问卷列表显示').check()
  await page.getByLabel('置顶问卷', {exact: true}).check()
  await page.getByRole('button', {name: '保存设置', exact: true}).click()
  await expect(page.getByText('设置已保存', {exact: true})).toBeVisible()
  expect(saved.status).toBe('CLOSED')
  expect(saved.visible).toBe(true)
  expect(saved.pinned).toBe(true)
  await page.screenshot({path: testInfo.outputPath('survey-settings.png'), fullPage: true})
  await expect(page.getByRole('button', {name: '编辑', exact: true})).toBeVisible()
  await page.getByRole('button', {name: '编辑', exact: true}).click()
  await expect(page.getByLabel('问卷标题', {exact: true})).toBeEnabled()
  await expect(page.getByRole('button', {name: '多选', exact: true})).toBeDisabled()
  await expect(page.getByLabel('必填', {exact: true})).toBeDisabled()
  await expect(page.getByRole('button', {name: /添加问题|添加选项|删除选项/})).toHaveCount(0)
  const originalIds = saved.questions.map((q: any) => ({id: q.id, options: q.options.map((o: any) => o.id)}))
  await page.getByLabel('问题 1 标题', {exact: true}).fill('更正后的题目')
  await page.getByRole('button', {name: '取消编辑', exact: true}).click()
  await expect(page.getByLabel('问题 1 标题', {exact: true})).toHaveValue('你常用哪项功能？')
  await page.getByRole('button', {name: '编辑', exact: true}).click()
  await page.getByLabel('问题 1 选项 1', {exact: true}).fill('查询（已纠错）')
  await page.getByRole('button', {name: '保存修改', exact: true}).first().click()
  await expect(page.getByText('修改已保存', {exact: true})).toBeVisible()
  await expect(page.getByLabel('问卷标题', {exact: true})).toBeDisabled()
  expect(saved.questions.map((q: any) => ({id: q.id, options: q.options.map((o: any) => o.id)}))).toEqual(originalIds)
  await page.getByRole('button', {name: /^答卷/}).click()
  const list = page.getByRole('region', {name: '问卷答卷'})
  await expect(list.locator('.survey-response-row')).toHaveCount(20)
  await expect(list).not.toContainText('你常用哪项功能')
  expect(details).toBe(0)
  await page.screenshot({path: testInfo.outputPath('survey-response-list.png'), fullPage: true})
  await list.locator('.survey-response-open').first().click()
  const dialog = page.getByRole('dialog', {name: '查看答卷'})
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText('查询')
  await expect(dialog).toContainText('用户23')
  await expect(dialog.getByRole('button', {name: '上一份'})).toBeDisabled()
  await dialog.getByRole('button', {name: '标记星标'}).click()
  await expect(dialog.getByRole('button', {name: '取消星标'})).toHaveAttribute('aria-pressed', 'true')
  await dialog.getByRole('button', {name: '下一份'}).click()
  await expect(dialog).toContainText('用户22')
  await dialog.getByRole('button', {name: '上一份'}).click()
  await expect(dialog).toContainText('用户23')
  await expect(dialog.getByRole('button', {name: '取消星标'})).toBeVisible()
  await page.screenshot({path: testInfo.outputPath('survey-answer-dialog.png'), fullPage: true})
  await dialog.getByLabel('答卷序号').fill('23')
  await dialog.getByRole('button', {name: '跳转', exact: true}).click()
  await expect(dialog).toContainText('用户1')
  await expect(dialog.getByRole('button', {name: '下一份'})).toBeDisabled()
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(list.getByRole('button', {name: '取消星标'})).toHaveCount(1)
  await list.getByLabel('答卷列表页码').fill('2')
  await list.getByRole('button', {name: '跳转', exact: true}).click()
  await expect(list.locator('.survey-response-row')).toHaveCount(3)
  await list.locator('.survey-response-open').first().click()
  await expect(dialog).toContainText('用户3')
  await expect(dialog.locator('.survey-answer-navigation')).toContainText('21 / 23')
  await dialog.getByRole('button', {name: '关闭答卷'}).click()
  await list.getByRole('button', {name: '刷新答卷'}).click()
  await expect(list.locator('.survey-response-row')).toHaveCount(20)
  await expect(list.getByRole('button', {name: '取消星标'})).toHaveCount(1)
  await list.getByRole('button', {name: '标记星标', exact: true}).first().click()
  await list.getByLabel('仅看星标').check()
  await expect(list.locator('.survey-response-row')).toHaveCount(2)
  await expect(page.getByRole('button', {name: /^答卷/})).toContainText('23')
  await list.locator('.survey-response-open').first().click()
  await expect(dialog.locator('.survey-answer-navigation')).toContainText('1 / 2')
  await dialog.getByRole('button', {name: '下一份'}).click()
  await expect(dialog).toContainText('用户22')
  await dialog.getByLabel('答卷序号').fill('1')
  await dialog.getByRole('button', {name: '跳转', exact: true}).click()
  await expect(dialog).toContainText('用户23')
  await dialog.getByRole('button', {name: '取消星标'}).click()
  await expect(dialog).not.toBeVisible()
  await expect(list.locator('.survey-response-row')).toHaveCount(1)
  await list.getByRole('button', {name: '取消星标'}).click()
  await expect(list.getByText('暂无星标答卷')).toBeVisible()
  await list.getByLabel('仅看星标').uncheck()
  await expect(list.locator('.survey-response-row')).toHaveCount(20)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({path: testInfo.outputPath('survey-responses.png'), fullPage: true})
  await page.getByRole('button', {name: '返回列表', exact: true}).click()
  await expect(page.locator('.survey-card')).toContainText('功能调查')
  await expect(page.locator('.survey-card .pinned')).toHaveText('置顶')
  await expect(page.getByRole('button', {name: '新建问卷', exact: true})).toHaveCSS('min-height', '30px')
  await page.screenshot({path: testInfo.outputPath('survey-list.png'), fullPage: true})
  await page.getByRole('button', {name: /的更多操作$/}).click()
  await page.getByRole('menuitem', {name: '删除问卷', exact: true}).click()
  await expect(page.getByText('问卷已删除', {exact: true})).toBeVisible()
  await expect(page.locator('.survey-card')).toHaveCount(0)
})
