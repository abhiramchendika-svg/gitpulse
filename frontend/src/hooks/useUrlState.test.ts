import { describe, expect, it } from 'vitest'
import { rangeToSince, readUrlState } from './useUrlState'

describe('readUrlState', () => {
  it('reads repo, range and bot filter', () => {
    expect(readUrlState('?repo=facebook/react&range=90d&bots=exclude')).toEqual({
      repo: { owner: 'facebook', repo: 'react' },
      range: '90d',
      excludeBots: true,
    })
  })

  it('falls back to defaults for missing or invalid values', () => {
    expect(readUrlState('?repo=../etc&range=forever')).toEqual({
      repo: null,
      range: '1y',
      excludeBots: false,
    })
  })
})

describe('rangeToSince', () => {
  const today = new Date('2026-09-25T15:00:00Z')

  it('counts today as the last included day', () => {
    expect(rangeToSince('30d', today)).toBe('2026-08-27')
    expect(rangeToSince('90d', today)).toBe('2026-06-28')
    expect(rangeToSince('1y', today)).toBe('2025-09-26')
  })
})
