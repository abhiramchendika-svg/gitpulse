import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../services/apiClient'
import { fetchHealth, fetchRateLimit } from '../services/statusService'
import type { RateLimitResponse } from '../types/api'

export type BackendStatus =
  | { state: 'loading' }
  | { state: 'down'; message: string }
  | { state: 'up'; rateLimit: RateLimitResponse | null; rateLimitError: string | null }

/**
 * Checks that the backend is alive, then asks it for the remaining GitHub API quota.
 * The two are reported separately: the backend can be healthy while GitHub is unreachable.
 */
export function useBackendStatus(refreshKey?: string): {
  status: BackendStatus
  refresh: () => void
} {
  const [status, setStatus] = useState<BackendStatus>({ state: 'loading' })
  const [attempt, setAttempt] = useState(0)

  const refresh = useCallback(() => setAttempt((n) => n + 1), [])

  useEffect(() => {
    const controller = new AbortController()

    async function load() {
      setStatus({ state: 'loading' })
      try {
        const health = await fetchHealth(controller.signal)
        if (health.status !== 'UP') {
          setStatus({ state: 'down', message: `Backend reports status ${health.status}.` })
          return
        }
      } catch (error) {
        if (controller.signal.aborted) return
        setStatus({ state: 'down', message: messageOf(error) })
        return
      }

      try {
        const rateLimit = await fetchRateLimit(controller.signal)
        setStatus({ state: 'up', rateLimit, rateLimitError: null })
      } catch (error) {
        if (controller.signal.aborted) return
        setStatus({ state: 'up', rateLimit: null, rateLimitError: messageOf(error) })
      }
    }

    void load()
    // Cancel in-flight requests if the component unmounts or refresh() is called again.
    return () => controller.abort()
    // refreshKey: callers pass e.g. the current repository so quota is re-read after analyses.
  }, [attempt, refreshKey])

  return { status, refresh }
}

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return 'Unexpected error while contacting the backend.'
}
