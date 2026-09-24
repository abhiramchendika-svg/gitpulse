// TypeScript mirrors of the backend's JSON responses. Keep in sync with backend api/dto.

export interface Quota {
  limit: number
  remaining: number
  used: number
  /** ISO-8601 instant */
  resetAt: string
}

/** GET /api/v1/rate-limit */
export interface RateLimitResponse {
  authenticated: boolean
  core: Quota
  search: Quota | null
}

/** GET /actuator/health */
export interface HealthResponse {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'
}

/** RFC 9457 error body returned by the backend for every error. */
export interface ProblemDetail {
  status: number
  title?: string
  detail?: string
  code?: string
  resetAt?: string
}
