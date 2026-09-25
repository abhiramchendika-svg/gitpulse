import { parseRepoInput, type RepoRef } from './parseRepoInput'

export type SearchTarget =
  | { ok: true; kind: 'repo'; repo: RepoRef }
  | { ok: true; kind: 'user'; login: string }
  | { ok: false; error: string }

const LOGIN = /^[A-Za-z0-9][A-Za-z0-9-]{0,38}$/

/**
 * The header search box accepts a repository ("owner/repo", repository URL) or an account
 * ("mona", "@mona", "https://github.com/mona"). Anything with two path segments is a repository;
 * a single segment is a user or organization.
 */
export function parseSearchInput(raw: string): SearchTarget {
  const input = raw.trim()
  const repo = parseRepoInput(input)
  if (repo.ok) return { ok: true, kind: 'repo', repo: repo.value }

  const login = singleSegment(input)
  if (login !== null) {
    return LOGIN.test(login)
      ? { ok: true, kind: 'user', login }
      : { ok: false, error: `"${login}" is not a valid GitHub user or organization name.` }
  }
  return { ok: false, error: input === '' ? 'Enter a repository or a username.' : repo.error }
}

/** "mona", "@mona", "github.com/mona/", "https://github.com/mona?tab=x" -> "mona"; else null. */
function singleSegment(input: string): string | null {
  if (input === '') return null
  let path = input
  if (/^(https?:\/\/)?(www\.)?github\.com(\/|$)/i.test(input)) {
    path = new URL(/^https?:\/\//i.test(input) ? input : `https://${input}`).pathname
  } else if (/^[a-z][a-z0-9+.-]*:\/\//i.test(input)) {
    return null
  }
  const segments = path.split('/').filter((s) => s !== '')
  if (segments.length !== 1) return null
  return segments[0].replace(/^@/, '')
}
