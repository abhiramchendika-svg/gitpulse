import { describe, expect, it } from 'vitest'
import {
  formatCount,
  formatDate,
  formatDays,
  formatHours,
  formatRelative,
  formatSizeKb,
  formatTime,
  remainingPercent,
} from './format'

describe('formatCount', () => {
  it('keeps small numbers exact and compacts large ones', () => {
    expect(formatCount(1284, 'en-US')).toBe('1,284')
    expect(formatCount(12_900, 'en-US')).toBe('12.9K')
    expect(formatCount(4_200_000, 'en-US')).toBe('4.2M')
  })
})

describe('formatDate', () => {
  it('formats in UTC so dates match the backend', () => {
    // 23:30 UTC is already the next day in India; the UTC date must still be shown.
    expect(formatDate('2026-03-12T23:30:00Z', 'en-GB')).toBe('12 Mar 2026')
  })

  it('handles missing values', () => {
    expect(formatDate(null)).toBe('—')
  })
})

describe('formatRelative', () => {
  it('describes past times', () => {
    expect(formatRelative('2026-09-22T12:00:00Z', new Date('2026-09-25T12:00:00Z'))).toMatch(
      /3 days ago/,
    )
  })
})

describe('formatHours', () => {
  it('picks a readable unit', () => {
    expect(formatHours(0.25)).toBe('15 min')
    expect(formatHours(5.24)).toBe('5.2 hours')
    expect(formatHours(165.5)).toBe('6.9 days')
  })
})

describe('formatSizeKb / formatDays', () => {
  it('formats sizes', () => {
    expect(formatSizeKb(108)).toBe('108 KB')
    expect(formatSizeKb(2048)).toBe('2.0 MB')
  })

  it('pluralises days', () => {
    expect(formatDays(1)).toBe('1 day')
    expect(formatDays(15.54)).toBe('15.5 days')
  })
})

describe('remainingPercent', () => {
  it('rounds down', () => {
    expect(remainingPercent(59, 60)).toBe(98)
  })

  it('handles an exhausted quota', () => {
    expect(remainingPercent(0, 60)).toBe(0)
  })

  it('does not divide by zero', () => {
    expect(remainingPercent(0, 0)).toBe(0)
  })

  it('clamps to 0..100', () => {
    expect(remainingPercent(120, 100)).toBe(100)
    expect(remainingPercent(-5, 100)).toBe(0)
  })
})

describe('formatTime', () => {
  it('returns a placeholder for invalid input', () => {
    expect(formatTime('not-a-date')).toBe('—')
  })

  it('formats a valid instant', () => {
    expect(formatTime('2026-09-25T10:05:00Z', 'en-GB')).toMatch(/^\d{2}:\d{2}$/)
  })
})
