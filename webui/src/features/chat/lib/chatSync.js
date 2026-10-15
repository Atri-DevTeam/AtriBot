export function conversationKey(value) {
  return `${value.type}:${value.openId}`
}

export function mergeConversations(current, changed, removed, pinnedKeys, limit) {
  const records = new Map(current.map(value => [conversationKey(value), value]))
  for (const key of removed) records.delete(key)
  for (const value of changed) records.set(conversationKey(value), value)
  const pinned = new Set(pinnedKeys)
  const compare = (a, b) => a < b ? -1 : a > b ? 1 : 0
  const sorted = [...records.values()].sort((a, b) =>
    compare(b.lastCreatedAt || '', a.lastCreatedAt || '') || compare(conversationKey(a), conversationKey(b)))
  let count = 0
  return sorted.filter(value => pinned.has(conversationKey(value)) || count++ < limit)
}

export function mergeMessageChanges(current, changed, latestId, history) {
  const records = new Map(current.map(value => [String(value.id), value]))
  for (const value of changed) {
    if (records.has(String(value.id)) || (!history && BigInt(value.id) > latestId)) records.set(String(value.id), value)
  }
  return [...records.values()].sort((a, b) => BigInt(a.id) > BigInt(b.id) ? -1 : BigInt(a.id) < BigInt(b.id) ? 1 : 0)
}
