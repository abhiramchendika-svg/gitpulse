import { describe, expect, it } from 'vitest'
import { rangeToSince, readUrlState, toSearch } from './useUrlState'

describe('readUrlState', () => {
  it('reads repo, range and bot filter', () => {
    expect(readUrlState('?repo=facebook/react&range=90d&bots=exclude')).toEqual({
      repo: { owner: 'facebook', repo: 'react' },
      range: '90d',
      excludeBots: true,
      compare: null,
    })
  })

  it('falls back to defaults for missing or invalid values', () => {
    expect(readUrlState('?repo=../etc&range=forever')).toEqual({
      repo: null,
      range: '1y',
      excludeBots: false,
      compare: null,
    })
  })

  it('reads a comparison', () => {
    expect(readUrlState('?compare=facebook/react,vuejs/core').compare).toEqual([
      { owner: 'facebook', repo: 'react' },
      { owner: 'vuejs', repo: 'core' },
    ])
  })

  it('treats an empty or incomplete comparison as the empty compare form', () => {
    expect(readUrlState('?compare=').compare).toEqual([])
    expect(readUrlState('?compare=facebook/react').compare).toEqual([])
    expect(readUrlState('?compare=facebook/react,../x').compare).toEqual([])
  })
})

describe('toSearch', () => {
  it('writes a comparison without dashboard parameters', () => {
    expect(
      toSearch({
        repo: { owner: 'a', repo: 'b' },
        range: '90d',
        excludeBots: true,
        compare: [
          { owner: 'facebook', repo: 'react' },
          { owner: 'vuejs', repo: 'core' },
        ],
      }),
    ).toBe('?compare=facebook%2Freact%2Cvuejs%2Fcore')
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
