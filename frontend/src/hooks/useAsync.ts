import { useCallback, useEffect, useEffectEvent, useState } from 'react'
import { ApiError } from '../services/apiClient'

export interface AsyncState<T> {
  data: T | null
  error: ApiError | null
  /** True while a request is in flight (including refetches that keep old data on screen). */
  loading: boolean
  reload: () => void
}

interface Settled<T> {
  /** Which request this result belongs to. */
  requestId: string
  data: T | null
  error: ApiError | null
}

/**
 * Runs `fetcher` whenever `key` changes, cancelling the previous request.
 *
 * - `key === null` means "don't fetch yet" (e.g. waiting for another request).
 * - Previous data stays available while refetching, so the UI can dim it instead of flashing a
 *   skeleton. It is cleared only when `resetKey` changes (e.g. a different repository), because
 *   showing another repository's numbers, even dimmed, would be misleading.
 */
export function useAsync<T>(
  key: string | null,
  resetKey: string | null,
  fetcher: (signal: AbortSignal) => Promise<T>,
): AsyncState<T> {
  const [attempt, setAttempt] = useState(0)
  const [settled, setSettled] = useState<Settled<T> | null>(null)
  const [lastData, setLastData] = useState<T | null>(null)
  const [prevResetKey, setPrevResetKey] = useState(resetKey)

  // Reset when the subject changes. Adjusting state during render (instead of in an effect) is
  // React's documented pattern: the stale values are never painted.
  if (prevResetKey !== resetKey) {
    setPrevResetKey(resetKey)
    setLastData(null)
    setSettled(null)
  }

  const requestId = key === null ? null : `${key}#${attempt}`
  // Always call the latest fetcher without re-running the effect when its identity changes.
  const runFetcher = useEffectEvent((signal: AbortSignal) => fetcher(signal))

  useEffect(() => {
    if (requestId === null) return
    const controller = new AbortController()
    runFetcher(controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setLastData(result)
        setSettled({ requestId, data: result, error: null })
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        const error =
          e instanceof ApiError ? e : new ApiError(0, 'UNKNOWN_ERROR', 'Something went wrong.')
        setSettled({ requestId, data: null, error })
      })
    return () => controller.abort()
  }, [requestId])

  const reload = useCallback(() => setAttempt((n) => n + 1), [])

  const current = settled !== null && settled.requestId === requestId ? settled : null
  return {
    data: current?.data ?? lastData,
    error: current?.error ?? null,
    loading: requestId !== null && current === null,
    reload,
  }
}
