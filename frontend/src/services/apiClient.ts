import type { ProblemDetail } from '../types/api'

/**
 * Error thrown for any failed backend call. `code` is the backend's stable error code
 * (e.g. RATE_LIMITED), or NETWORK_ERROR / UNKNOWN_ERROR when the backend gave us nothing usable.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  /** For RATE_LIMITED: when GitHub's limit resets (ISO instant), if the backend knows. */
  readonly resetAt?: string

  constructor(status: number, code: string, message: string, resetAt?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.resetAt = resetAt
  }
}

/** GET a same-origin JSON endpoint. Paths are relative (e.g. /api/v1/rate-limit). */
export function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  return requestJson('GET', path, signal)
}

/** POST (no body) to a same-origin JSON endpoint: used for actions that cost something. */
export function postJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  return requestJson('POST', path, signal)
}

async function requestJson<T>(method: 'GET' | 'POST', path: string, signal?: AbortSignal) {
  let response: Response
  try {
    response = await fetch(path, { method, headers: { Accept: 'application/json' }, signal })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new ApiError(0, 'NETWORK_ERROR', 'Could not reach the GitPulse backend.')
  }

  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  const fallback = `Request failed with HTTP ${response.status}.`
  try {
    const problem = (await response.json()) as Partial<ProblemDetail>
    return new ApiError(
      response.status,
      problem.code ?? 'UNKNOWN_ERROR',
      problem.detail ?? problem.title ?? fallback,
      problem.resetAt,
    )
  } catch {
    // Not JSON. A bare 502/503/504 comes from a proxy (e.g. Vite in dev) that cannot reach the
    // backend, not from the backend itself, which always answers with a ProblemDetail.
    if (response.status >= 502 && response.status <= 504) {
      return new ApiError(
        response.status,
        'BACKEND_UNREACHABLE',
        'Could not reach the GitPulse backend. Is it running?',
      )
    }
    return new ApiError(response.status, 'UNKNOWN_ERROR', fallback)
  }
}
