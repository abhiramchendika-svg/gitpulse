/** Formats an ISO instant as a local time, e.g. "14:05". Returns "—" for invalid input. */
export function formatTime(iso: string, locale?: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
}

/** Share of the quota still available, 0–100, rounded down. Returns 0 when limit is 0. */
export function remainingPercent(remaining: number, limit: number): number {
  if (limit <= 0) return 0
  return Math.max(0, Math.min(100, Math.floor((remaining / limit) * 100)))
}

/** 1,284 / 12.9K / 4.2M. Compact only from 10,000 up, so small counts stay exact. */
export function formatCount(value: number, locale?: string): string {
  if (Math.abs(value) < 10_000) return value.toLocaleString(locale)
  return new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 1 }).format(
    value,
  )
}

/** Exact number with thousands separators: 1,483,612. */
export function formatExact(value: number, locale?: string): string {
  return value.toLocaleString(locale)
}

/** "12 Mar 2026" (UTC date, so it matches the backend's UTC grouping). "—" if missing/invalid. */
export function formatDate(iso: string | null | undefined, locale?: string): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString(locale, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  })
}

/** Short label for a week-start date "2026-03-09" -> "9 Mar". */
export function formatWeekLabel(isoDate: string, locale?: string): string {
  const date = new Date(`${isoDate}T00:00:00Z`)
  if (Number.isNaN(date.getTime())) return isoDate
  return date.toLocaleDateString(locale, { day: 'numeric', month: 'short', timeZone: 'UTC' })
}

/** "3 days ago", "in 2 hours", relative to `now`. */
export function formatRelative(iso: string | null | undefined, now = new Date()): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  const seconds = Math.round((date.getTime() - now.getTime()) / 1000)
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 31_536_000],
    ['month', 2_592_000],
    ['week', 604_800],
    ['day', 86_400],
    ['hour', 3_600],
    ['minute', 60],
  ]
  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
  for (const [unit, size] of units) {
    if (Math.abs(seconds) >= size) return rtf.format(Math.round(seconds / size), unit)
  }
  return rtf.format(seconds, 'second')
}

/** Human size from kilobytes: 108 KB, 9.4 MB, 1.2 GB. */
export function formatSizeKb(kb: number): string {
  if (kb < 1024) return `${kb} KB`
  if (kb < 1024 * 1024) return `${(kb / 1024).toFixed(1)} MB`
  return `${(kb / 1024 / 1024).toFixed(1)} GB`
}

/** "1 day", "15.5 days". */
export function formatDays(days: number): string {
  const rounded = Math.round(days * 10) / 10
  return `${rounded} ${rounded === 1 ? 'day' : 'days'}`
}
