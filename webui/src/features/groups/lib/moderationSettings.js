export const defaultJoinPrompt = `你是一个QQ群入群审核助手，负责根据申请人填写的验证消息或问答内容，判断是否应当同意其加入本群。
需要判定为拒绝的情形包括但不限于：
- 验证内容包含广告、引流、推广等营销信息
- 验证内容为空、无意义乱码，或明显是批量注册的机器人号特征
- 验证内容与群主题严重不符
- 验证内容包含辱骂、色情、政治敏感等违规信息
如果验证内容正常、态度诚恳，符合入群要求，请判定为通过。
请只根据申请人提供的验证内容判断，不要臆测，信息不足时倾向于判定为通过，交由管理员人工复核。`

export function emptyAction() {
  return {remind: true, remindMessage: '你的消息违规了哦', recall: true, mute: false, muteSeconds: 0, notifyDebugGroup: false}
}

export function newKeywordRule() {
  return {ruleId: crypto.randomUUID(), type: 'KEYWORD', matchMode: 'CONTAINS', keyword: '', remark: '', action: emptyAction()}
}

export function newJoinRule(type) {
  return {id: crypto.randomUUID(), name: type === 'AI' ? 'AI 审核' : '关键词审核', enabled: true, type,
    matchMode: 'CONTAINS', keywords: [], onMatch: 'REJECT', aiSystemPrompt: type === 'AI' ? defaultJoinPrompt : '',
    onViolation: 'REJECT', onPass: 'CONTINUE', rejectReason: ''}
}

export function lines(value) {
  return String(value || '').split(/\r?\n/).map(item => item.trim()).filter(Boolean)
}

export function readModerationSettings(value = {}) {
  const data = JSON.parse(JSON.stringify(value || {}))
  const keyword = data.keywordRecall || {}
  const ai = data.aiRecall || {}
  const join = data.joinReview || {}
  const schedule = {enabled: false, startDate: '', endDate: '', startTime: '00:00', endTime: '23:59', daysOfWeek: [1, 2, 3, 4, 5, 6, 7], ...ai.schedule}
  if (!Array.isArray(schedule.daysOfWeek)) schedule.daysOfWeek = []
  delete ai.promptPresets
  delete ai.customPresets
  const result = {
    ...data,
    keywordRecall: {...keyword, enabled: !!keyword.enabled, action: {...emptyAction(), ...keyword.action},
      rules: (Array.isArray(keyword.rules) ? keyword.rules : []).map(rule => ({...rule,
        ruleId: rule.ruleId || crypto.randomUUID(), action: {...emptyAction(), ...(rule.action || keyword.action)}}))},
    aiRecall: {...ai, enabled: !!ai.enabled, type: [0, 1, 2, 3].includes(Number(ai.type)) ? Number(ai.type) : 2,
      systemPrompt: ai.systemPrompt || '', customOutput: ai.customOutput || '', useCustomOutputAsReminder: ai.useCustomOutputAsReminder !== false,
      allowedDomains: Array.isArray(ai.allowedDomains) ? ai.allowedDomains : [],
      action: {...emptyAction(), ...ai.action}, schedule},
    joinReview: {...join, enabled: !!join.enabled, mode: join.mode || 'DISABLED', rejectReason: join.rejectReason || '',
      notifyDebugGroup: !!join.notifyDebugGroup, rules: Array.isArray(join.rules) ? join.rules : []}
  }
  const config = result.joinReview
  if (!config.rules.length && ['KEYWORD', 'AI', 'ALL'].includes(config.mode)) {
    config.enabled = true
    if (['KEYWORD', 'ALL'].includes(config.mode)) config.rules.push({...newJoinRule('KEYWORD'),
      matchMode: config.keywordRule?.matchMode || 'CONTAINS', keywords: config.keywordRule?.keywords || [],
      onMatch: config.keywordRule?.onHit || 'REJECT', rejectReason: config.rejectReason})
    if (['AI', 'ALL'].includes(config.mode)) config.rules.push({...newJoinRule('AI'),
      aiSystemPrompt: config.aiSystemPrompt || defaultJoinPrompt, rejectReason: config.rejectReason})
  }
  return result
}

export function serializeModerationSettings(settings) {
  const result = JSON.parse(JSON.stringify(settings))
  result.aiRecall.allowedDomains = lines(result.aiRecall.allowedDomains.join('\n'))
  for (const rule of result.joinReview.rules) rule.keywords = lines((rule.keywords || []).join('\n'))
  // 规则列表取代旧审核模式，防止删除全部规则后重新启用旧配置。
  result.joinReview.mode = 'DISABLED'
  return result
}
