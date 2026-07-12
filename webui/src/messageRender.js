const FACE_TAG_RE = /<faceType=\d+,faceId=\\?"[^"\\]*\\?",ext=\\?"([^"\\]*)\\?">/g

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
