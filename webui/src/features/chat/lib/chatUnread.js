export function latestMessageId(records) {
  return records.reduce((latest, record) => {
    const value = String(record.id ?? '')
    const id = /^\d+$/.test(value) ? BigInt(value) : 0n
    return id > latest ? id : latest
  }, 0n)
}

// 日志自增 ID 区分新消息和引用跳转后未加载的旧消息；总量差补足超过一页的突发消息。
export function countNewMessages(records, knownId, previousTotal, total) {
  const newer = records.filter(record => latestMessageId([record]) > knownId).length
  return newer > 0 ? Math.max(newer, Math.max(0, total - previousTotal)) : 0
}
