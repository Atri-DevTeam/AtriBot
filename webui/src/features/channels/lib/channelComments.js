/**
 * @Author YZ_Ljc_
 * @ClassName channelComments
 * @Created_at 2026/09/11
 * @Project AtriMeow
 * @Package webui.src.lib
 */
export function channelUserName(item = {}) {
  return [item.author, item.nickname, item['昵称'], item.author_name, item.replier_name, item.author?.nickname, item.author?.name]
    .find(value => typeof value === 'string' && value.trim()) || channelUserId(item) || '频道成员'
}

export function channelUserId(item = {}) {
  const value = item.author_id || item.tinyid || item.tiny_id || item.user_id || item.comment_author_id || item.reply_author_id || item.replier_id || item.author?.id
  return value == null ? '' : String(value)
}

export function replyRows(value) {
  const candidates = [value, value?.replies, value?.replies_preview, value?.reply_list, value?.replyList, value?.reply_info?.replies]
  return (candidates.find(Array.isArray) || []).filter(row => row && typeof row === 'object')
}

export function replyKey(item) {
  const id = item.reply_id || item.id
  return id ? String(id) : JSON.stringify([channelUserId(item), channelUserName(item), item.create_time_raw || item.create_time, item.content_richtext || item.content])
}

export function mergeReplies(existing, incoming) {
  return [...new Map([...existing, ...incoming].map(item => [replyKey(item), item])).values()]
}

export function replyCount(comment) {
  const value = comment.reply_count ?? comment.reply_num ?? comment.replyCount
  if (value === null || value === undefined || value === '') return null
  const count = Number(value)
  return Number.isFinite(count) && count >= 0 ? count : null
}

export function replyCursor(value) {
  const cursor = value?.attach_info ?? value?.next_attach_info ?? value?.reply_attach_info
  return typeof cursor === 'string' && cursor.trim() ? cursor : ''
}

export function createReplyThread(comment) {
  const items = mergeReplies([], replyRows(comment))
  const cursor = replyCursor(comment)
  const count = replyCount(comment)
  const more = typeof comment.has_more_replies === 'boolean'
    ? comment.has_more_replies : count === null || count > items.length
  return { expanded: true, loading: false, error: '', items, cursor, cursors: new Set(), requested: false,
    more: Boolean(cursor) && more }
}
