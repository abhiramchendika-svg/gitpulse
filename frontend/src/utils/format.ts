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
