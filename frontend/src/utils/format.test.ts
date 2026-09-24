import { describe, expect, it } from 'vitest'
import { formatTime, remainingPercent } from './format'

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
