export type BoundGroup = {
  groupId: string; groupNumber: string | null; name: string | null; description: string | null; category: string | null
  tags: string[]; memberCount: number | null; joinedAt: string | null; botRole: string | null; receiveMode: string | null
  proactive: boolean; restricted: boolean; boundAt: string | null; available: boolean
}
export type BindingChallenge = { groupId: string; code: string; command: string; expiresAt: number }
export type GroupBinding = Pick<BoundGroup, 'groupId' | 'groupNumber' | 'name' | 'boundAt'>
export type GroupOverview = { groups: GroupBinding[]; pending: BindingChallenge | null }
export type PrivatePost = <T>(path: string, body: unknown) => Promise<T | null>
export type GroupWelcome = {
  enabled: boolean; custom: boolean; text: string; buttonSize: string
  keyboard: { label: string; style: string }[][]
}
