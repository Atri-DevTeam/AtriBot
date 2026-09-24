import type { Activity, Item, Gain, Page } from '../../src/activity'

export const inventoryImage = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=', 'base64')

export const activity: Activity = {
  reaction: { plays: 24, completed: 21, bestMs: 18240, bestMisses: 0, bestDate: '2026-09-18', rank: 12 },
  freeDraw: 'available', unavailable: [],
  games: [
    { game: 'minesweeper', operations: 156, correct: 0, plays: 0, wins: 0 },
    { game: 'sound', operations: 25, correct: 20, plays: 0, wins: 0 },
    { game: 'connect4', operations: 0, correct: 0, plays: 16, wins: 10 },
    { game: 'roulette', operations: 0, correct: 0, plays: 12, wins: 8 },
    { game: 'rsp', operations: 0, correct: 0, plays: 30, wins: 15 }
  ],
  settings: { bilibiliUid: '12345678', reward: { enabled: true, mode: 'priority', priorities: [{ item: 'BEDWARS', value: 30 }, { item: 'SKYWARS', value: 20 }] },
    share: { url: 'https://qun.qq.com/example', invitedCount: 6, invitedBySomeone: false, createdAt: '2026-09-18T12:00:00' } }
}
const names = ['钻石', '草方块', '末影珍珠', '附魔金苹果', '海晶石', '紫水晶', '红石粉', '绿宝石', '铁锭', '下界合金锭', '火焰棒', '蜂蜜瓶', '橡木', '萤石', '南瓜', '青金石', '煤炭', '书']
const ids = ['diamond', 'grass_block', 'ender_pearl', 'enchanted_golden_apple', 'prismarine', 'amethyst_shard', 'redstone', 'emerald', 'iron_ingot', 'netherite_ingot', 'blaze_rod', 'honey_bottle', 'oak_log', 'glowstone', 'pumpkin', 'lapis_lazuli', 'coal', 'book']
export const inventoryItems: Item[] = names.map((name, i) => ({ itemId: 'minecraft:' + ids[i], name, count: 2,
  firstAt: 1789819200 - i * 86400, lastAt: 1789819200 - i * 86400, source: '抽奖-每日免费' }))
export const gainItems: Gain[] = [
  { id: 4, source: 'mc_bind', amount: 200, timestamp: 1789819200 },
  { id: 3, source: 'image_upload_reward', amount: 100, timestamp: 1789732800 },
  { id: 2, source: 'group_invite', amount: 200, timestamp: 1789646400 },
  { id: 1, source: 'friend_add', amount: 100, timestamp: 1789560000 }
]
export function activityResponse(url: string): Activity | Page<Item> | Page<Gain> | null {
  const parsed = new URL(url)
  if (parsed.pathname.endsWith('/activity')) return activity
  if (parsed.pathname.endsWith('/inventory')) {
    const offset = Number(parsed.searchParams.get('offset') || 0)
    return { items: inventoryItems.slice(offset, offset + 12), offset, limit: 12, hasMore: offset + 12 < inventoryItems.length, available: true }
  }
  if (parsed.pathname.endsWith('/gains')) return { items: gainItems, offset: 0, limit: 8, hasMore: false, available: true }
  return null
}
