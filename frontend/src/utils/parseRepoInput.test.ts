import { describe, expect, it } from 'vitest'
import { parseRepoInput } from './parseRepoInput'

function ok(input: string) {
  const result = parseRepoInput(input)
  if (!result.ok) throw new Error(`expected "${input}" to parse, got: ${result.error}`)
  return result.value
}

function error(input: string) {
  const result = parseRepoInput(input)
  if (result.ok) throw new Error(`expected "${input}" to be rejected`)
  return result.error
}

describe('parseRepoInput', () => {
  it.each([
    ['facebook/react', 'facebook', 'react'],
    ['  facebook/react  ', 'facebook', 'react'],
    ['https://github.com/facebook/react', 'facebook', 'react'],
    ['https://github.com/facebook/react/', 'facebook', 'react'],
    ['https://github.com/facebook/react.git', 'facebook', 'react'],
    ['https://github.com/facebook/react/tree/main/packages', 'facebook', 'react'],
    ['https://github.com/facebook/react?tab=readme', 'facebook', 'react'],
    ['https://github.com/facebook/react#readme', 'facebook', 'react'],
    ['http://www.github.com/facebook/react', 'facebook', 'react'],
    ['github.com/facebook/react', 'facebook', 'react'],
    ['GitHub.com/Facebook/React', 'Facebook', 'React'],
    ['git@github.com:facebook/react.git', 'facebook', 'react'],
    ['spring-projects/spring-petclinic', 'spring-projects', 'spring-petclinic'],
    ['owner/my.repo_v2-x', 'owner', 'my.repo_v2-x'],
  ])('parses %s', (input, owner, repo) => {
    expect(ok(input)).toEqual({ owner, repo })
  })

  it('rejects empty input', () => {
    expect(error('   ')).toMatch(/Enter a repository/)
  })

  it('rejects a single segment', () => {
    expect(error('facebook')).toMatch(/owner\/repo/)
    expect(error('https://github.com/facebook')).toMatch(/owner\/repo/)
  })

  it('rejects extra segments in a bare path', () => {
    expect(error('a/b/c')).toMatch(/owner\/repo/)
  })

  it('rejects other hosts', () => {
    expect(error('https://gitlab.com/group/project')).toMatch(/github\.com/)
  })

  it('rejects names the backend would reject', () => {
    expect(error('-bad/repo')).toMatch(/not a valid GitHub user/)
    expect(error('under_score/repo')).toMatch(/not a valid GitHub user/)
    expect(error('owner/..')).toMatch(/not a valid repository/)
    expect(error('owner/bad name')).toMatch(/not a valid repository/)
  })
})
