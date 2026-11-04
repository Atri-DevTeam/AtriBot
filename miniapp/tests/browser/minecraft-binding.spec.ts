import {test, expect} from '@playwright/test'
import {mkdir} from 'node:fs/promises'
import {resolve} from 'node:path'
import {profile} from './profile-fixture'
import {activity} from './activity-fixture'

test('reviewed Minecraft binding loads independently and displays name, avatar and UUID', async ({page}, testInfo) => {
  const uuid = '38a720d6-087b-4d37-b858-89eed51934bc'
  const fallback = testInfo.project.name === 'mobile'
  const name = fallback ? 'Y*****_' : 'YZ_Ljc_'
  const avatar = 'data:image/svg+xml;base64,' + Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 8" shape-rendering="crispEdges"><path fill="#b07e5b" d="M0 0h8v8H0z"/><path fill="#37251c" d="M0 0h8v3H0zM0 3h1v3H0zM7 3h1v3H7z"/><path fill="#463125" d="M2 4h1v1H2zM5 4h1v1H5z"/><path fill="#6d4831" d="M3 7h3v1H3z"/></svg>`).toString('base64')
  let release: (() => void) | undefined
  const pending = new Promise<void>(done => { release = done })
  await page.route('**/atrimeow/profile/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/exchange')) return route.fulfill({json: {token: 'b'.repeat(43), user: {userId: 'owner', displayName: '用户'}, expiresAt: Date.now() + 7200000, idleTimeoutMillis: 90000}})
    if (path.endsWith('/profile')) return route.fulfill({json: {...profile, avatarUrl: null,
      bot: {...profile.bot, avatarUrl: null}, account: {...profile.account, minecraftUuid: uuid}}})
    if (path.endsWith('/activity')) return route.fulfill({json: activity})
    if (path.endsWith('/minecraft')) {
      await pending
      return route.fulfill({json: {uuid, username: name, avatarUrl: avatar}})
    }
    return route.fulfill({status: 204})
  })
  await page.goto(`/atrimeow/profile/?userId=owner&ticket=${'a'.repeat(43)}`)
  await expect(page.getByRole('navigation', {name: '栏目导航'})).toBeVisible()
  const binding = page.locator('.minecraft-binding')
  await expect(binding).toContainText(uuid)
  await expect(binding).toContainText('Minecraft 玩家')
  release!()
  await expect(binding).toContainText(name)
  await expect(binding.getByRole('img', {name: 'Minecraft 头像'})).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await binding.scrollIntoViewIfNeeded()
  await page.evaluate(() => window.scrollBy(0, -120))
  const directory = resolve('../_tmp/miniapp-minecraft-preview')
  await mkdir(directory, {recursive: true})
  await page.screenshot({path: resolve(directory, `${testInfo.project.name}.png`)})
})
