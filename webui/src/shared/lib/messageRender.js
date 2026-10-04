const FACE_TAG_RE = /<faceType=\d+,faceId=\\?"[^"\\]*\\?",ext=\\?"([^"\\]*)\\?">/g
const CMD_INPUT_RE = /<qqbot-cmd-input\b[^>]*\/>/g
const ATTACHMENT_TAG_RE = /<attachmentType\s*=\s*\\?["']([^"'\\]+)\\?["'][^>]*>/g

export function renderAttachmentTags(text) {
  return String(text ?? '').replace(ATTACHMENT_TAG_RE, (tag, mime) => {
    const type = mime.trim().toLowerCase().split('/')[0]
    const label = type === 'image' ? '图片' : type === 'video' ? '视频' : ['audio', 'voice'].includes(type) ? '语音' : '文件'
    const encoded = tag.match(/\bdescription\s*=\s*\\?["']([^"'\\]*)\\?["']/)?.[1]
    try {
      const payload = JSON.parse(decodeBase64Utf8(encoded || ''))
      const raw = typeof payload === 'string' ? payload : payload?.text
      let description = typeof raw === 'string' ? raw.trim() : ''
      if (description.startsWith('[') && description.endsWith(']')) description = description.slice(1, -1).trim()
      return description ? `[${label}: ${description}]` : `[${label}]`
    } catch {
      return `[${label}]`
    }
  })
}

export function renderCommandInputTags(text) {
  return String(text ?? '').replace(CMD_INPUT_RE, tag => {
    const show = tag.match(/\bshow\s*=\s*(["'])(.*?)\1/s)?.[2]
    if (show === undefined) return tag
    const command = tag.match(/\btext\s*=\s*(["'])(.*?)\1/s)?.[2] || ''
    if (!/%[\da-f]{2}/i.test(show) && !/%[\da-f]{2}/i.test(command)) return show
    try {
      return decodeURIComponent(show.replace(/\+/g, ' '))
    } catch {
      return show
    }
  })
}

export function renderFaceTags(text) {
  if (!text) return ''
  return String(text).replace(FACE_TAG_RE, (_, ext) => decodeFaceExt(ext) || '[表情]')
}

function decodeFaceExt(ext) {
  try {
    const decoded = decodeBase64Utf8(ext)
    const payload = JSON.parse(decoded)
    const text = typeof payload === 'string' ? payload : payload?.text
    return formatFaceText(text)
  } catch {
    return ''
  }
}

function decodeBase64Utf8(value) {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(normalized.length + (4 - normalized.length % 4) % 4, '=')
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, char => char.charCodeAt(0))
  return new TextDecoder('utf-8').decode(bytes)
}

function formatFaceText(text) {
  if (typeof text !== 'string') return ''
  const value = text.trim()
  if (!value) return ''
  if (value.startsWith('[') || value.startsWith('/')) return value
  return `[${value}]`
}
