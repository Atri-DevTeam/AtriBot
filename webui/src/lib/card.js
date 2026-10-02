export function parseCardMessage(raw) {
  let value = raw
  if (typeof value === 'string') {
    try { value = JSON.parse(value) } catch { return null }
  }
  if (!value || value.type !== 'tuwen' || !value.content || typeof value.content !== 'object') return null
  const text = key => typeof value.content[key] === 'string' ? value.content[key].trim() : ''
  const card = { title: text('title'), description: text('description'), picUrl: text('pic_url'), jumpUrl: text('url') }
  return card.title || card.description || card.picUrl ? card : null
}

export function renderCardSummary(raw) {
  const card = parseCardMessage(raw)
  return card ? `[卡片] ${card.title || card.description || '图文消息'}` : ''
}
