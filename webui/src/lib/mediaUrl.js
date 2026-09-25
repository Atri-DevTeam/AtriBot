const BLOCKED_COS_DOMAIN = 'cos.ap-guangzhou.myqcloud.com'

export function isBlockedCosUrl(value) {
  if (!value) return false
  try {
    const raw = String(value).trim()
    const absolute = raw.startsWith('//') ? `https:${raw}` : /^https?:\/\//i.test(raw) ? raw : `https://${raw}`
    const url = new URL(absolute)
    const host = url.hostname.toLowerCase()
    return host === BLOCKED_COS_DOMAIN || host.endsWith(`.${BLOCKED_COS_DOMAIN}`)
  } catch {
    return false
  }
}

export function mediaUrl(value) {
  if (!value || isBlockedCosUrl(value)) return ''
  if (/^(https?:)?\/\//i.test(value)) return value.startsWith('//') ? `https:${value}` : value
  if (value.startsWith('data:')) return value
  return `https://${value}`
}
