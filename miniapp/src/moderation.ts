export type ReadOnlyField = { label: string; value: string }
export type SavedKeyword = { id: string; masked: string; matchMode: 'CONTAINS' | 'EQUALS'; remind: boolean; readOnly: ReadOnlyField[] }
export type SavedDomain = { id: string; masked: string }
export type ModerationSchedule = {
  enabled: boolean; startDate: string; endDate: string; startTime: string; endTime: string; daysOfWeek: number[]
}
export type Moderation = {
  canManage: boolean; canCustomizePrompt: boolean; revision: string
  keywordEnabled: boolean; keywords: SavedKeyword[]
  aiEnabled: boolean; aiType: number; aiRemind: boolean; allowAllLinks: boolean; domains: SavedDomain[]; promptConfigured: boolean
  customPrompt: string | null
  aiSchedule: ModerationSchedule
  aiReadOnly: ReadOnlyField[]; otherRules: { title: string; fields: ReadOnlyField[] }[]
}
