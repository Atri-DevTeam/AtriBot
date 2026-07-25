export function parseArkMessage(raw) {
  const value = parseJsonLike(raw)
  if (!value || typeof value !== 'object') return null

  const ark = Array.isArray(value) ? value.find(item => item && typeof item === 'object') : value
  if (!ark || typeof ark !== 'object') return null

  const fields = ark.fields && typeof ark.fields === 'object' ? ark.fields : {}
  const typeName = firstText(ark.ark_name, ark.arkName, ark.ark_type, ark.arkType, fields.type) || '卡片消息'
  const type = firstText(ark.ark_type, ark.arkType)
  const tag = normalizeTags(fields.tags) || firstText(fields.tag, ark.tag, ark.tags)
  const title = firstText(fields.title, ark.title, fields.nickname, ark.nickname, fields.address, ark.address, fields.desc, fields.description, ark.prompt, fields.prompt)
  const prompt = firstText(ark.prompt, fields.prompt, fields.desc, fields.description, fields.address, ark.address, fields.summary)
  const source = firstText(fields.source, ark.source, fields.source_name, ark.source_name, tag)
  const sourceLogo = firstText(fields.source_logo, fields.sourceLogo, fields.tag_icon, fields.tagIcon, ark.source_logo, ark.sourceLogo, ark.tag_icon, ark.tagIcon)
  const preview = firstText(fields.preview, fields.avatar, fields.cover, fields.image, fields.img, fields.pic, fields.thumbnail, ark.preview, ark.avatar)
  const targetUrl = findTargetUrl(ark, fields, preview, sourceLogo)

  if (!title && !prompt && !source && !tag && !preview) return null

  return {
    type,
    typeName,
    title: title || prompt || typeName,
    prompt,
    source,
    sourceLogo,
    tag,
    preview,
    targetUrl
  }
}

export function hasArkMessage(raw) {
  return !!parseArkMessage(raw)
}

export function renderArkSummary(raw) {
  const ark = parseArkMessage(raw)
  if (!ark) return ''
  const title = ark.title || ark.prompt || ''
  return title ? `[${ark.typeName}] ${title}` : `[${ark.typeName}]`
}

function parseJsonLike(raw) {
  if (!raw) return null
  if (typeof raw !== 'string') return raw
  const text = raw.trim()
  if (!text || text === 'null') return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function firstText(...values) {
  for (const value of values) {
    if (typeof value !== 'string') continue
    const text = value.trim()
    if (text) return text
  }
  return ''
}

function normalizeTags(value) {
  if (Array.isArray(value)) {
    return value
      .map(item => typeof item === 'string' ? item.trim() : '')
      .filter(Boolean)
      .join(' / ')
  }
  return firstText(value)
}

function findTargetUrl(ark, fields, preview, sourceLogo) {
  const direct = firstUrl(
    fields.jump_url,
    fields.jumpUrl,
    fields.target_url,
    fields.targetUrl,
    fields.web_url,
    fields.webUrl,
    fields.page_url,
    fields.pageUrl,
    fields.qqdocurl,
    fields.url,
    fields.link,
    fields.href,
    ark.jump_url,
    ark.jumpUrl,
    ark.target_url,
    ark.targetUrl,
    ark.web_url,
    ark.webUrl,
    ark.page_url,
    ark.pageUrl,
    ark.qqdocurl,
    ark.url,
    ark.link,
    ark.href
  )
  if (direct && !sameUrl(direct, preview) && !sameUrl(direct, sourceLogo)) return direct

  return findUrlDeep(ark, new Set([preview, sourceLogo].filter(Boolean)))
}

function findUrlDeep(value, excluded, seen = new Set()) {
  if (!value || typeof value !== 'object' || seen.has(value)) return ''
  seen.add(value)

  if (Array.isArray(value)) {
    for (const item of value) {
      const found = findUrlDeep(item, excluded, seen)
      if (found) return found
    }
    return ''
  }

  const preferredKeys = ['jump_url', 'jumpUrl', 'target_url', 'targetUrl', 'web_url', 'webUrl', 'page_url', 'pageUrl', 'qqdocurl', 'url', 'link', 'href']
  for (const key of preferredKeys) {
    const candidate = firstUrl(value[key])
    if (candidate && !isExcludedUrl(key, candidate, excluded)) return candidate
  }

  for (const [key, item] of Object.entries(value)) {
    if (isMediaKey(key)) continue
    if (typeof item === 'string') {
      const candidate = firstUrl(item)
      if (candidate && !isExcludedUrl(key, candidate, excluded)) return candidate
    } else {
      const found = findUrlDeep(item, excluded, seen)
      if (found) return found
    }
  }
  return ''
}

function firstUrl(...values) {
  for (const value of values) {
    if (typeof value !== 'string') continue
    const text = value.trim()
    if (isNavigableUrl(text)) return text
  }
  return ''
}

function isNavigableUrl(value) {
  return /^(https?:\/\/|mqqapi:\/\/|qqminiapp:\/\/)/i.test(value)
}

function isMediaKey(key) {
  return /preview|source[_-]?logo|tag[_-]?icon|logo|icon|image|img|pic|cover|thumb|thumbnail|avatar/i.test(key || '')
}

function isExcludedUrl(key, value, excluded) {
  if (isMediaKey(key)) return true
  for (const item of excluded) {
    if (sameUrl(value, item)) return true
  }
  return false
}

function sameUrl(a, b) {
  return !!a && !!b && String(a).trim() === String(b).trim()
}
