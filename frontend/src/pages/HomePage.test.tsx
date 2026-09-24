import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { HomePage } from './HomePage'

const rateLimit = {
  authenticated: false,
  core: { limit: 60, remaining: 45, used: 15, resetAt: '2026-09-25T10:00:00Z' },
  search: { limit: 10, remaining: 10, used: 0, resetAt: '2026-09-25T10:00:00Z' },
}

/** Fake backend: maps each URL to a response factory. */
function mockBackend(routes: Record<string, () => Response>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input)
    const route = routes[url]
    if (!route) throw new Error(`Unexpected request: ${url}`)
    return route()
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('HomePage', () => {
  it('shows backend health and GitHub quota', async () => {
    mockBackend({
      '/actuator/health': () => Response.json({ status: 'UP' }),
      '/api/v1/rate-limit': () => Response.json(rateLimit),
    })

    render(<HomePage />)

    expect(await screen.findByText(/45 \/ 60 left/)).toBeInTheDocument()
    expect(screen.getByText(/Anonymous \(60 requests\/hour\)/)).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Core API quota' })).toHaveAttribute('value', '45')
  })

  it('shows an alert when the backend is unreachable', async () => {
    mockBackend({
      '/actuator/health': () => new Response('Bad Gateway', { status: 502 }),
    })

    render(<HomePage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Backend unavailable')
  })

  it('keeps the backend "up" but reports a GitHub rate-limit error separately', async () => {
    mockBackend({
      '/actuator/health': () => Response.json({ status: 'UP' }),
      '/api/v1/rate-limit': () =>
        Response.json(
          { status: 429, code: 'RATE_LIMITED', detail: 'GitHub API rate limit reached.' },
          { status: 429 },
        ),
    })

    render(<HomePage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('GitHub API rate limit reached.')
    expect(screen.getByText('Up')).toBeInTheDocument()
  })

  it('refetches when Refresh is clicked', async () => {
    const fetchMock = mockBackend({
      '/actuator/health': () => Response.json({ status: 'UP' }),
      '/api/v1/rate-limit': () => Response.json(rateLimit),
    })
    render(<HomePage />)
    await screen.findByText(/45 \/ 60 left/)
    const callsBefore = fetchMock.mock.calls.length

    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }))

    await screen.findByText(/45 \/ 60 left/)
    expect(fetchMock.mock.calls.length).toBeGreaterThan(callsBefore)
  })
})
