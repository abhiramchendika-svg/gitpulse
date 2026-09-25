import { describe, expect, it } from 'vitest'
import { parseSearchInput } from './parseSearchInput'

describe('parseSearchInput', () => {
  it('recognises repositories', () => {
    expect(parseSearchInput('facebook/react')).toEqual({
      ok: true,
      kind: 'repo',
      repo: { owner: 'facebook', repo: 'react' },
    })
    expect(parseSearchInput('https://github.com/facebook/react/tree/main')).toMatchObject({
      kind: 'repo',
    })
  })

  it.each([
    ['octocat', 'octocat'],
    ['@octocat', 'octocat'],
    ['  octo-cat  ', 'octo-cat'],
    ['https://github.com/octocat', 'octocat'],
    ['github.com/octocat/', 'octocat'],
    ['https://github.com/octocat?tab=repositories', 'octocat'],
  ])('recognises the user in %s', (input, login) => {
    expect(parseSearchInput(input)).toEqual({ ok: true, kind: 'user', login })
  })

  it('rejects invalid names and other hosts', () => {
    expect(parseSearchInput('-bad')).toMatchObject({ ok: false })
    expect(parseSearchInput('under_score')).toMatchObject({ ok: false })
    expect(parseSearchInput('https://gitlab.com/someone')).toMatchObject({ ok: false })
    expect(parseSearchInput('')).toEqual({ ok: false, error: 'Enter a repository or a username.' })
  })
})
