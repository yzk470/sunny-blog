const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']

function toDate(value?: string | null): Date | null {
  if (!value) return null
  const date = new Date(value.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDate(value?: string | null, withTime = false): string {
  const date = toDate(value)
  if (!date) return '—'
  const base = `${date.getFullYear()} 年 ${date.getMonth() + 1} 月 ${date.getDate()} 日`
  if (!withTime) return base
  return `${base} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

export function formatShortDate(value?: string | null): string {
  const date = toDate(value)
  if (!date) return '—'
  return `${String(date.getMonth() + 1).padStart(2, '0')} / ${String(date.getDate()).padStart(2, '0')}`
}

export function formatWeekday(value?: string | null): string {
  const date = toDate(value)
  return date ? `星期${WEEKDAYS[date.getDay()]}` : ''
}

export function relativeTime(value?: string | null): string {
  const date = toDate(value)
  if (!date) return ''
  const diff = Date.now() - date.getTime()
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return formatDate(value)
}

export function formatNumber(value?: number | null): string {
  if (value == null) return '0'
  if (value >= 10000) return `${(value / 10000).toFixed(1).replace('.0', '')}w`
  if (value >= 1000) return `${(value / 1000).toFixed(1).replace('.0', '')}k`
  return String(value)
}

export function initialOf(name?: string | null): string {
  return (name || '晴').trim().charAt(0).toUpperCase()
}

export function readingLabel(minutes?: number | null): string {
  return `${Math.max(1, minutes || 1)} 分钟阅读`
}

export function todayLabel(): string {
  const date = new Date()
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日 · 星期${WEEKDAYS[date.getDay()]}`
}

export function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了，早点休息'
  if (hour < 11) return '早上好，今天也要开心'
  if (hour < 14) return '中午好，记得好好吃饭'
  if (hour < 18) return '下午好，来杯咖啡吧'
  return '晚上好，今天辛苦啦'
}
