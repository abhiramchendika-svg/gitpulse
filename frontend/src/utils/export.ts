/**
 * Client-side export. The browser already holds every API response it rendered, so exporting
 * costs no GitHub requests and always matches what is on screen.
 */

export type CsvCell = string | number | boolean | null | undefined

/** Bump when the shape of an exported JSON report changes in a breaking way. */
export const REPORT_SCHEMA_VERSION = 1

export type ReportKind = 'repository' | 'profile' | 'comparison'

export interface ExportReport<T> {
  generator: 'GitPulse'
  schemaVersion: number
  kind: ReportKind
  subject: string
  exportedAt: string
  notes: string[]
  data: T
}

const REPORT_NOTES = [
  'Public GitHub data only. Field meanings: docs/api.md; formulas for calculated metrics: docs/metrics.md.',
  'Each section keeps the metadata the API returned with it (analysis window, sample size, truncation).',
  'Counts describe activity; they are not measures of productivity or code quality.',
]

export function buildReport<T>(
  kind: ReportKind,
  subject: string,
  data: T,
  now: Date = new Date(),
): ExportReport<T> {
  return {
    generator: 'GitPulse',
    schemaVersion: REPORT_SCHEMA_VERSION,
    kind,
    subject,
    exportedAt: now.toISOString(),
    notes: REPORT_NOTES,
    data,
  }
}

/** Characters that make spreadsheet apps treat a cell as a formula. */
const FORMULA_START = /^[=+\-@\t\r]/

/**
 * RFC 4180 CSV with CRLF line endings.
 *
 * Text cells that start like a formula are prefixed with an apostrophe (the OWASP recommendation
 * against CSV injection): commit messages, names and paths come from other people, and a cell
 * such as `=HYPERLINK(...)` would otherwise run when the file is opened in Excel. Numbers are
 * written as numbers, so negative values are unaffected.
 */
export function toCsv(columns: string[], rows: CsvCell[][]): string {
  const lines = [columns, ...rows].map((row) => row.map(csvCell).join(','))
  return lines.join('\r\n') + '\r\n'
}

function csvCell(value: CsvCell): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'number') return Number.isFinite(value) ? String(value) : ''
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  const text = FORMULA_START.test(value) ? `'${value}` : value
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

/** Lowercase, filesystem-safe name part: `Spring-Projects/Petclinic` → `spring-projects-petclinic`. */
export function slug(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9._]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

/** `gitpulse-<parts>-<yyyymmdd>.<ext>`, with the date in UTC. */
export function exportFileName(parts: string[], extension: string, now: Date = new Date()): string {
  const date = now.toISOString().slice(0, 10).replace(/-/g, '')
  const body = ['gitpulse', ...parts.map(slug).filter(Boolean), date].join('-')
  return `${body}.${extension}`
}

/** Save `content` as a file via a temporary object URL. */
export function downloadFile(fileName: string, content: string, mimeType: string): void {
  // The byte-order mark makes Excel read CSV as UTF-8 (names are often non-ASCII).
  const parts = mimeType.startsWith('text/csv') ? ['﻿', content] : [content]
  const url = URL.createObjectURL(new Blob(parts, { type: mimeType }))
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.rel = 'noopener'
  document.body.append(link)
  link.click()
  link.remove()
  // Revoke after the click has been handled, so the download has started.
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

export function downloadCsv(fileName: string, columns: string[], rows: CsvCell[][]): void {
  downloadFile(fileName, toCsv(columns, rows), 'text/csv;charset=utf-8')
}

export function downloadJson(fileName: string, value: unknown): void {
  downloadFile(fileName, JSON.stringify(value, null, 2) + '\n', 'application/json')
}
