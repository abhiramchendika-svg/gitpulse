import { useCallback, useEffect, useState } from 'react'
import { parseRepoInput, type RepoRef } from '../utils/parseRepoInput'

export const RANGES = ['30d', '90d', '1y'] as const
export type Range = (typeof RANGES)[number]
export const DEFAULT_RANGE: Range = '1y'

export interface DashboardState {
  repo: RepoRef | null
  range: Range
  excludeBots: boolean
}

/** Reads dashboard state from the URL, e.g. ?repo=facebook/react&range=90d&bots=exclude */
export function readUrlState(search: string): DashboardState {
  const params = new URLSearchParams(search)
  const parsed = parseRepoInput(params.get('repo') ?? '')
  const range = params.get('range')
  return {
    repo: parsed.ok ? parsed.value : null,
    range: RANGES.includes(range as Range) ? (range as Range) : DEFAULT_RANGE,
    excludeBots: params.get('bots') === 'exclude',
  }
}

export function toSearch(state: DashboardState): string {
  const params = new URLSearchParams()
  if (state.repo) params.set('repo', `${state.repo.owner}/${state.repo.repo}`)
  if (state.range !== DEFAULT_RANGE) params.set('range', state.range)
  if (state.excludeBots) params.set('bots', 'exclude')
  const qs = params.toString()
  return qs ? `?${qs}` : window.location.pathname
}

/**
 * Keeps dashboard state in the address bar, so every view is a shareable link and the browser's
 * back button works. Choosing a repository adds a history entry; changing filters replaces it.
 */
export function useUrlState(): [DashboardState, (next: Partial<DashboardState>) => void] {
  const [state, setState] = useState(() => readUrlState(window.location.search))

  useEffect(() => {
    const onPop = () => setState(readUrlState(window.location.search))
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

  const update = useCallback((next: Partial<DashboardState>) => {
    // The URL is the source of truth, so merge into what it says now. The history write must
    // happen here, not inside a setState updater: updaters must be pure (React's StrictMode calls
    // them twice in development, which pushed duplicate history entries and broke "back").
    const merged = { ...readUrlState(window.location.search), ...next }
    const url = toSearch(merged)
    if ('repo' in next) {
      window.history.pushState(null, '', url)
    } else {
      window.history.replaceState(null, '', url)
    }
    setState(merged)
  }, [])

  return [state, update]
}

/** yyyy-MM-dd (UTC) for the start of a range, matching the backend's inclusive-day windows. */
export function rangeToSince(range: Range, today = new Date()): string {
  const days = range === '30d' ? 30 : range === '90d' ? 90 : 365
  const start = new Date(
    Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate() - (days - 1)),
  )
  return start.toISOString().slice(0, 10)
}
