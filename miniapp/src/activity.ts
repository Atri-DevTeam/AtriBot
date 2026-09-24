export type Reaction = { plays: number; completed: number; bestMs: number | null; bestMisses: number | null; bestDate: string | null; rank: number | null }
export type Activity = {
  reaction: Reaction | null
  freeDraw: 'available' | 'used' | 'locked' | null
  games: { game: string; operations: number; correct: number; plays: number; wins: number }[] | null
  settings: {
    bilibiliUid: string | null
    reward: { enabled: boolean; mode: string; priorities: { item: string; value: number }[] }
    share: { url: string | null; invitedCount: number; invitedBySomeone: boolean; createdAt: string | null }
  } | null
  unavailable: string[]
}
const rewardNames: Record<string, string> = {
  dust: '神秘之尘', souls: '空岛战争灵魂', tokens: '代币', WALLS3: '超级战墙', coins: '硬币', UHC: '极限生存冠军',
  experience: '大厅经验', QUAKECRAFT: '未来射击', SUPER_SMASH: '星碎英雄', WALLS: '经典战墙', BATTLEGROUND: '战争领主',
  PAINTBALL: '彩蛋射击', BUILD_BATTLE: '建筑大师', BEDWARS: '起床战争', ARCADE: '街机游戏', ARENA: '竞技场乱斗',
  DUELS: '决斗游戏', MCGO: '警匪大战', LEGACY: '经典游戏', VAMPIREZ: '吸血鬼大战', TNTGAMES: 'TNT游戏',
  MURDER_MYSTERY: '密室杀手', adsense_token: '每日奖励代币', SURVIVAL_GAMES: '闪电饥饿游戏',
  housing_package: '家园世界装饰品', GINGERBREAD: '卡丁车竞赛', SKYWARS: '空岛战争'
}
export const rewardName = (key: string) => rewardNames[key] || key
export type Item = { itemId: string; name: string; count: number; firstAt: number; lastAt: number; source: string }
export type Gain = { id: number; timestamp: number; source: string; amount: number }
export type Page<T> = { items: T[]; offset: number; limit: number; hasMore: boolean; available: boolean }
export type PrivateRequest = <T>(path: string) => Promise<T | null>

const sources: Record<string, string> = {
  group_invite: '邀请机器人入群', friend_add: '添加机器人好友', join_my_group: '加入官方群',
  join_guild: '加入官方频道', read_doc: '阅读文档', mc_bind: '绑定 Minecraft',
  bv_follow: 'B站关注奖励', image_upload_reward: '图源投稿奖励', sign_in: '签到',
  share_bot: '分享机器人'
}
export const sourceName = (source: string) => sources[source] || source || '未记录来源'
export const number = (n: number | null | undefined) => n == null ? '—' : n.toLocaleString('zh-CN')
export const recordDate = (date: string | null | undefined) => date ? date.slice(0, 10).replace(/-/g, '.') : '—'
// Both loot receive timestamps and coin gain timestamps are epoch seconds.
export const timestampDate = (seconds: number) => seconds > 0
  ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(seconds * 1000)) : '—'
