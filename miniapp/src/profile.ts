export type Messages = { count: number; firstAt: string | null; lastAt: string | null }
export type Profile = {
  bot: { name: string; avatarUrl: string | null }
  userId: string
  username: string | null
  avatarUrl: string | null
  account: { uuid: string; username: string | null; createdAt: string | null; minecraftUuid: string | null; qqNumber: string | null } | null
  privateMessages: Messages | null
  groupMessages: Messages | null
  signIn: { days: number; lastDate: string | null } | null
  coins: number | null
  collection: { total: number; kinds: number } | null
  unavailable: string[]
  updatedAt: string
}
