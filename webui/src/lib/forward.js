const TOP_SEPARATOR = /^=== 消息 (\d+) ===$/
const NESTED_SEPARATOR = /^--- 第(\d+)条 ---$/

function separatorIndent(level) {
  return ' '.repeat(4 * Math.max(0, level - 1))
}

function itemIndent(level) {
  return ' '.repeat(4 * level)
}

function matchSeparator(line, level, expected) {
  if (level === 0) {
    const match = TOP_SEPARATOR.exec(line)
    return !!match && Number(match[1]) === expected[0]
  }
  const indent = separatorIndent(level)
  if (!line.startsWith(indent)) return false
  const match = NESTED_SEPARATOR.exec(line.slice(indent.length))
  return !!match && Number(match[1]) === expected[level]
}

function matchOpenSeparator(line, level, expected) {
  const top = TOP_SEPARATOR.exec(line)
  if (top && Number(top[1]) === expected[0]) return true
  for (let current = 1; current <= level; current++) {
    const indent = separatorIndent(current)
    if (!line.startsWith(indent)) continue
    const match = NESTED_SEPARATOR.exec(line.slice(indent.length))
    if (match && Number(match[1]) === expected[current]) return true
  }
  return false
}

function isField(line, indent) {
  const rest = line.startsWith(indent) ? line.slice(indent.length) : ''
  return rest.startsWith('[发送者] ') || /^\[附件\d+\] /.test(rest) ||
    rest.startsWith('[消息类型] ') || rest.startsWith('[卡片消息] ') || rest === '[关联消息]'
}

function dedent(line, indent) {
  return line.startsWith(indent) ? line.slice(indent.length) : line
}

function parseSize(value) {
  const match = /^([\d.]+)\s*(B|KB|MB|GB|TB)?$/i.exec(value)
  if (!match) return undefined
  const unit = (match[2] || '').toUpperCase()
  const multiplier = { '': 1, B: 1, KB: 1024, MB: 1024 ** 2, GB: 1024 ** 3, TB: 1024 ** 4 }[unit]
  return Number(match[1]) * multiplier
}

function parseAttachment(value) {
  const attachment = { type: '' }
  for (const token of value.split(' ')) {
    const index = token.indexOf(':')
    if (index < 0) continue
    const key = token.slice(0, index)
    const field = token.slice(index + 1)
    if (key === '类型') attachment.type = field
    else if (key === '文件名') attachment.filename = field
    else if (key === '尺寸') {
      const [width, height] = field.split('x').map(Number)
      if (Number.isFinite(width)) attachment.width = width
      if (Number.isFinite(height)) attachment.height = height
    } else if (key === '大小') {
      const size = parseSize(field)
      if (size !== undefined) attachment.size = size
    } else if (key === 'URL') attachment.url = field
  }
  return attachment
}

function parseCard(value) {
  const fields = {}
  const pattern = / ([^: ]+):/g
  const first = pattern.exec(value)
  if (!first) return { name: value, fields }
  const name = value.slice(0, first.index)
  let start = first.index + first[0].length
  let match = first
  while (match) {
    const key = match[1]
    match = pattern.exec(value)
    fields[key === '摘要' ? 'prompt' : key] = value.slice(start, match ? match.index : value.length).trim()
    if (match) start = match.index + match[0].length
  }
  return { name, fields }
}

function parseItems(lines, start, level, expected) {
  const items = []
  let index = start
  while (index < lines.length) {
    while (index < lines.length && lines[index].trim() === '') index++
    if (index >= lines.length || !matchSeparator(lines[index], level, expected)) break
    index++
    const parsed = parseItem(lines, index, level, expected)
    items.push(parsed.item)
    index = parsed.index
    expected[level]++
  }
  return { items, index }
}

function parseItem(lines, start, level, expected) {
  const indent = itemIndent(level)
  const item = { attachments: [] }
  let index = start
  while (index < lines.length && lines[index].trim() === '') index++

  if (lines[index]?.startsWith(`${indent}[消息内容] `)) {
    const content = [lines[index].slice(indent.length + '[消息内容] '.length)]
    index++
    while (index < lines.length) {
      const line = lines[index]
      if (isField(line, indent) || matchOpenSeparator(line, level, expected)) break
      content.push(dedent(line, indent))
      index++
    }
    item.content = content.join('\n')
  }
  if (lines[index]?.startsWith(`${indent}[发送者] `)) {
    item.author = lines[index].slice(indent.length + '[发送者] '.length)
    index++
  }
  while (lines[index]?.startsWith(`${indent}[附件`)) {
    const match = /^\[附件\d+\] (.*)$/.exec(lines[index].slice(indent.length))
    if (!match) break
    item.attachments.push(parseAttachment(match[1]))
    index++
  }
  if (lines[index]?.startsWith(`${indent}[消息类型] `)) {
    item.type = lines[index].slice(indent.length + '[消息类型] '.length)
    index++
  }
  if (lines[index]?.startsWith(`${indent}[卡片消息] `)) {
    item.card = parseCard(lines[index].slice(indent.length + '[卡片消息] '.length))
    index++
  }
  if (lines[index] === `${indent}[关联消息]`) {
    index++
    expected[level + 1] = 1
    const nested = parseItems(lines, index, level + 1, expected)
    item.forward = nested.items
    index = nested.index
  }
  return { item, index }
}

/**
 * Parse the textual representation of a QQ merged-forward message.
 * Returns null for ordinary text so callers can keep their existing renderer.
 */
export function parseForwardContent(content) {
  if (typeof content !== 'string' || !content.trim()) return null
  const lines = content.replace(/^\uFEFF/, '').split(/\r?\n/)
  let index = 0
  let title
  if (/^\[[^\]\n]+的聊天记录\]$/.test(lines[0] || '')) {
    title = lines[0]
    index++
  }
  const expected = [1]
  const parsed = parseItems(lines, index, 0, expected)
  if (!parsed.items.length) return null
  return { title, items: parsed.items }
}

