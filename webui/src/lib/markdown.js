const TOKEN_OPEN = '\uE000'
const TOKEN_CLOSE = '\uE001'

export function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function safeUrl(value, allowImageData = false) {
  const url = String(value || '').trim()
  if (/^https?:\/\//i.test(url)) return escapeHtml(url)
  if (allowImageData && /^data:image\/(?:png|jpe?g|gif|webp);base64,/i.test(url)) return escapeHtml(url)
  return ''
}

function renderInline(value) {
  const tokens = []
  const store = html => {
    const index = tokens.push(html) - 1
    return `${TOKEN_OPEN}${index}${TOKEN_CLOSE}`
  }

  let text = String(value ?? '')

  text = text.replace(/`([^`\n]+)`/g, (_, code) => store(`<code>${escapeHtml(code)}</code>`))
  text = text.replace(/!\[([^\]]*)\]\(([^\s)]+)\)/g, (_, rawAlt, rawUrl) => {
    const url = safeUrl(rawUrl, true)
    if (!url) return escapeHtml(`[图片: ${rawAlt || '无法加载'}]`)

    const size = rawAlt.match(/#(\d+)px\s*#(\d+)px/i)
    const alt = rawAlt.replace(/#\d+px\s*#\d+px/i, '').trim()
    const dimensions = size
      ? ` width="${Math.min(Number(size[1]), 4096)}" height="${Math.min(Number(size[2]), 4096)}"`
      : ''
    return store(`<img src="${url}" alt="${escapeHtml(alt)}"${dimensions} loading="lazy" referrerpolicy="no-referrer">`)
  })
  text = text.replace(/\[([^\]]+)]\(([^\s)]+)\)/g, (_, label, rawUrl) => {
    const url = safeUrl(rawUrl)
    if (!url) return escapeHtml(label)
    return store(`<a href="${url}" target="_blank" rel="noreferrer noopener">${escapeHtml(label)}</a>`)
  })
  text = text.replace(/<(https?:\/\/[^>\s]+)>/g, (_, rawUrl) => {
    const url = safeUrl(rawUrl)
    return url ? store(`<a href="${url}" target="_blank" rel="noreferrer noopener">${url}</a>`) : escapeHtml(rawUrl)
  })

  text = escapeHtml(text)
  text = text.replace(/\*\*\*([^*]+)\*\*\*/g, '<strong><em>$1</em></strong>')
  text = text.replace(/___([^_]+)___/g, '<strong><em>$1</em></strong>')
  text = text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  text = text.replace(/__([^_]+)__/g, '<strong><u>$1</u></strong>')
  text = text.replace(/~~([^~]+)~~/g, '<del>$1</del>')
  text = text.replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>')
  text = text.replace(/(^|[^_])_([^_\n]+)_(?!_)/g, '$1<em>$2</em>')

  return text.replace(new RegExp(`${TOKEN_OPEN}(\\d+)${TOKEN_CLOSE}`, 'g'), (_, index) => tokens[Number(index)] || '')
}

function isBlockStart(line) {
  return /^```/.test(line)
    || /^#{1,6}\s+/.test(line)
    || /^\s*>/.test(line)
    || /^\s*[-+*]\s+/.test(line)
    || /^\s*\d+[.)]\s+/.test(line)
    || /^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)
}

export function renderMarkdown(value) {
  const source = String(value ?? '').replace(/\r\n?/g, '\n').trim()
  if (!source) return ''

  const lines = source.split('\n')
  const blocks = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index]
    if (!line.trim()) {
      index++
      continue
    }

    const fence = line.match(/^```([\w-]*)\s*$/)
    if (fence) {
      const code = []
      index++
      while (index < lines.length && !/^```\s*$/.test(lines[index])) {
        code.push(lines[index])
        index++
      }
      if (index < lines.length) index++
      const language = fence[1] ? ` data-language="${escapeHtml(fence[1])}"` : ''
      blocks.push(`<pre${language}><code>${escapeHtml(code.join('\n'))}</code></pre>`)
      continue
    }

    const heading = line.match(/^(#{1,6})\s+(.+)$/)
    if (heading) {
      const level = heading[1].length
      blocks.push(`<h${level}>${renderInline(heading[2])}</h${level}>`)
      index++
      continue
    }

    if (/^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)) {
      blocks.push('<hr>')
      index++
      continue
    }

    if (/^\s*>/.test(line)) {
      const quoteLines = []
      while (index < lines.length && /^\s*>/.test(lines[index])) {
        quoteLines.push(lines[index].replace(/^\s*>\s?/, ''))
        index++
      }
      blocks.push(`<blockquote>${quoteLines.map(renderInline).join('<br>')}</blockquote>`)
      continue
    }

    const unordered = line.match(/^\s*[-+*]\s+(.+)$/)
    const ordered = line.match(/^\s*\d+[.)]\s+(.+)$/)
    if (unordered || ordered) {
      const orderedList = !!ordered
      const matcher = orderedList ? /^\s*\d+[.)]\s+(.+)$/ : /^\s*[-+*]\s+(.+)$/
      const items = []
      while (index < lines.length) {
        const item = lines[index].match(matcher)
        if (!item) break
        items.push(`<li>${renderInline(item[1])}</li>`)
        index++
      }
      const tag = orderedList ? 'ol' : 'ul'
      blocks.push(`<${tag}>${items.join('')}</${tag}>`)
      continue
    }

    const paragraph = [line]
    index++
    while (index < lines.length && lines[index].trim() && !isBlockStart(lines[index])) {
      paragraph.push(lines[index])
      index++
    }
    blocks.push(`<p>${paragraph.map(renderInline).join('<br>')}</p>`)
  }

  return blocks.join('')
}