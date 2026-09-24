import { describe, expect, it, vi } from 'vitest'
import { ApiError, getJson } from './apiClient'

function mockFetch(impl: () => Promise<Response>) {
  vi.stubGlobal('fetch', vi.fn(impl))
}

describe('getJson', () => {
  it('returns parsed JSON on success', async () => {
    mockFetch(async () => Response.json({ status: 'UP' }))

    await expect(getJson('/actuator/health')).resolves.toEqual({ status: 'UP' })
  })

  it('turns a ProblemDetail body into an ApiError with the backend code', async () => {
    mockFetch(async () =>
      Response.json(
        { status: 429, code: 'RATE_LIMITED', detail: 'GitHub API rate limit reached.' },
        { status: 429, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )

    const error = await getJson('/api/v1/rate-limit').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 429,
      code: 'RATE_LIMITED',
      message: 'GitHub API rate limit reached.',
    })
  })

  it('reports a non-JSON 502 (proxy cannot reach backend) as BACKEND_UNREACHABLE', async () => {
    mockFetch(async () => new Response('Bad Gateway', { status: 502 }))

    await expect(getJson('/api/v1/rate-limit')).rejects.toMatchObject({
      status: 502,
      code: 'BACKEND_UNREACHABLE',
    })
  })

  it('handles other non-JSON error bodies', async () => {
    mockFetch(async () => new Response('<html>oops</html>', { status: 500 }))

    await expect(getJson('/api/v1/rate-limit')).rejects.toMatchObject({
      status: 500,
      code: 'UNKNOWN_ERROR',
      message: 'Request failed with HTTP 500.',
    })
  })

  it('reports network failures as NETWORK_ERROR', async () => {
    mockFetch(async () => {
      throw new TypeError('Failed to fetch')
    })

    await expect(getJson('/api/v1/rate-limit')).rejects.toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
    })
  })

  it('rethrows aborts unchanged so callers can ignore them', async () => {
    mockFetch(async () => {
      throw new DOMException('aborted', 'AbortError')
    })

    await expect(getJson('/api/v1/rate-limit')).rejects.toMatchObject({ name: 'AbortError' })
  })
})
