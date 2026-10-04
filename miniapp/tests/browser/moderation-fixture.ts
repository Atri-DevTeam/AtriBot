import type { Moderation } from '../../src/moderation'
export const moderation: Moderation = {
  canManage: true, canCustomizePrompt: false, revision: 'revision-1', keywordEnabled: false,
  keywords: [{ id: '0', masked: '测***', matchMode: 'CONTAINS', remind: true, readOnly: [] }],
  aiEnabled: false, aiType: 2, aiRemind: true, allowAllLinks: true, customPrompt: null,
  aiSchedule: { enabled: false, startDate: '', endDate: '', startTime: '00:00', endTime: '23:59', daysOfWeek: [1, 2, 3, 4, 5, 6, 7] },
  domains: [{ id: '0', masked: '^*****************$', }], promptConfigured: false, aiReadOnly: [], otherRules: []
}
