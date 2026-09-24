import type { HealthResponse, RateLimitResponse } from '../types/api'
import { getJson } from './apiClient'

export function fetchHealth(signal?: AbortSignal): Promise<HealthResponse> {
  return getJson<HealthResponse>('/actuator/health', signal)
}

export function fetchRateLimit(signal?: AbortSignal): Promise<RateLimitResponse> {
  return getJson<RateLimitResponse>('/api/v1/rate-limit', signal)
}
