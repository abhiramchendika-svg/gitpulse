/**
 * Turns whatever the user typed into an owner/repo pair, or explains why it can't.
 *
 * Accepted forms:
 *   owner/repo
 *   https://github.com/owner/repo  (with optional .git, trailing slash, /tree/..., ?query, #hash)
 *   github.com/owner/repo
 *   git@github.com:owner/repo.git
 *
 * The same allow-list the backend enforces is checked here, so obviously bad input gets instant
 * feedback without a round trip. The backend still validates: never trust the client.
 */

export interface RepoRef {
  owner: string
  repo: string
}

export type ParseResult = { ok: true; value: RepoRef } | { ok: false; error: string }

const OWNER = /^[A-Za-z0-9][A-Za-z0-9-]{0,38}$/
const REPO = /^[A-Za-z0-9._-]{1,100}$/

export function parseRepoInput(raw: string): ParseResult {
  const input = raw.trim()
  if (input === '') {
    return { ok: false, error: 'Enter a repository, e.g. facebook/react.' }
  }

  let path: string
  const ssh = /^git@github\.com:(.+)$/i.exec(input)
  if (ssh) {
    path = ssh[1]
  } else if (/^(https?:\/\/)?(www\.)?github\.com\//i.test(input)) {
    const url = new URL(/^https?:\/\//i.test(input) ? input : `https://${input}`)
    path = url.pathname
  } else if (/^[a-z][a-z0-9+.-]*:\/\//i.test(input)) {
    return { ok: false, error: 'Only github.com repository URLs are supported.' }
  } else {
    path = input
  }

  const segments = path.split('/').filter((s) => s !== '')
  if (segments.length < 2) {
    return { ok: false, error: 'Use the form owner/repo, e.g. facebook/react.' }
  }
  // URLs may continue after owner/repo (/tree/main, /issues/1): only the first two segments count.
  // A bare "a/b/c" is not a URL, so extra segments there are a mistake.
  if (segments.length > 2 && path === input) {
    return { ok: false, error: 'Use the form owner/repo, e.g. facebook/react.' }
  }

  const owner = segments[0]
  const repo = segments[1].replace(/\.git$/i, '')

  if (!OWNER.test(owner)) {
    return { ok: false, error: `"${owner}" is not a valid GitHub user or organization name.` }
  }
  if (!REPO.test(repo) || repo === '.' || repo === '..') {
    return { ok: false, error: `"${repo}" is not a valid repository name.` }
  }
  return { ok: true, value: { owner, repo } }
}

export function formatRepo(ref: RepoRef): string {
  return `${ref.owner}/${ref.repo}`
}
