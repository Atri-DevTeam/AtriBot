// 时间展示工具：输入兼容 'YYYY-MM-DD HH:mm:ss' / ISO 字符串 / Date / 秒或毫秒级时间戳
export function parseTime(value) {
  if (value == null || value === '') return null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
  if (typeof value === 'number') return epochToDate(value)
  const raw = String(value).trim()
  if (/^\d+$/.test(raw)) return epochToDate(Number(raw))
  const date = new Date(raw.includes('T') ? raw : raw.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

function epochToDate(n) {
  // 小于 1e12 视为秒级时间戳
  const ms = n < 1e12 ? n * 1000 : n
  return Number.isNaN(ms) ? null : new Date(ms)
}

export function formatTime(value) {
  if (!value) return '-'
  const date = parseTime(value)
  if (!date) return String(value)
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function relativeTime(value) {
  const date = parseTime(value)
  if (!date) return formatTime(value)
  const diff = Date.now() - date.getTime()
  if (diff < 0) return formatTime(value)
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 30) return `${day} 天前`
  const pad = n => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
