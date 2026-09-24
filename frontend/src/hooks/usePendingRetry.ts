import { useEffect, useState } from 'react'

/** Delays between retries, in ms. GitHub usually finishes computing statistics within seconds. */
export const RETRY_DELAYS_MS = [3_000, 5_000, 8_000, 13_000, 20_000]

/**
 * While `pending` is true (GitHub answered "still computing"), calls `retry` on a backoff
 * schedule. Returns true once all retries are used up, so the UI can stop showing a spinner.
 * The schedule restarts whenever `resetKey` changes.
 */
export function usePendingRetry(
  pending: boolean,
  loading: boolean,
  retry: () => void,
  resetKey: string | null,
): boolean {
  const [attempts, setAttempts] = useState(0)
  const [prevResetKey, setPrevResetKey] = useState(resetKey)
  if (prevResetKey !== resetKey) {
    setPrevResetKey(resetKey)
    setAttempts(0)
  }

  const exhausted = attempts >= RETRY_DELAYS_MS.length

  useEffect(() => {
    if (!pending || loading || exhausted) return
    const timer = setTimeout(() => {
      setAttempts((n) => n + 1)
      retry()
    }, RETRY_DELAYS_MS[attempts])
    return () => clearTimeout(timer)
  }, [pending, loading, exhausted, attempts, retry])

  return pending && exhausted
}
