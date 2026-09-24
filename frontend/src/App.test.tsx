import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode } from 'react'
import { beforeAll, describe, expect, it, vi } from 'vitest'
import App from './App'
import * as fx from './test/fixtures'

type Route = (url: URL) => Response | undefined

/**
 * Fake backend: the first matching route answers. Health and rate-limit always answer so the
 * header badge works. Returns the mock so tests can inspect which URLs were requested.
 */
function mockBackend(route: Route) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = new URL(String(input), 'http://localhost')
    if (url.pathname === '/actuator/health') return Response.json({ status: 'UP' })
    if (url.pathname === '/api/v1/rate-limit') return Response.json(fx.rateLimit)
    const response = route(url)
    if (!response) throw new Error(`Unexpected request: ${url.pathname}${url.search}`)
    return response
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const REPO_BASE = '/api/v1/repositories/octocat/hello-world'

interface RouteOverrides {
  contributors?: () => Response
  pullRequests?: () => Response
  issues?: () => Response
  activity?: () => Response
  files?: () => Response
}

function happyRoutes(overrides: RouteOverrides = {}): Route {
  return (url) => {
    switch (url.pathname) {
      case REPO_BASE:
        return Response.json(fx.overview)
      case `${REPO_BASE}/languages`:
        return Response.json(fx.languages)
      case `${REPO_BASE}/commits`:
        return Response.json(fx.commits())
      case `${REPO_BASE}/contributors`:
        return overrides.contributors?.() ?? Response.json(fx.contributors())
      case `${REPO_BASE}/pull-requests`:
        return overrides.pullRequests?.() ?? Response.json(fx.pullRequests())
      case `${REPO_BASE}/issues`:
        return overrides.issues?.() ?? Response.json(fx.issues())
      case `${REPO_BASE}/activity`:
        return overrides.activity?.() ?? Response.json(fx.activity())
      case `${REPO_BASE}/files`:
        return overrides.files?.() ?? Response.json(fx.fileActivity())
    }
  }
}

function requested(fetchMock: ReturnType<typeof mockBackend>, pathPart: string) {
  return fetchMock.mock.calls.map(([input]) => String(input)).filter((u) => u.includes(pathPart))
}

async function analyse(text: string) {
  await userEvent.type(screen.getByRole('textbox', { name: 'GitHub repository' }), text)
  await userEvent.click(screen.getByRole('button', { name: 'Analyse' }))
}

describe('App', () => {
  // The dashboard is lazy-loaded. Import it once up front so the first test that opens it is not
  // measuring module transformation time against Testing Library's 1s wait.
  beforeAll(async () => {
    await import('./pages/DashboardPage')
    await import('./pages/ComparePage')
  })

  it('shows the landing page with backend status before a repository is chosen', async () => {
    mockBackend(() => undefined)

    render(<App />)

    expect(await screen.findByText(/45 \/ 60 left/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'spring-projects/spring-petclinic' })).toBeVisible()
  })

  it('rejects invalid input without calling the backend', async () => {
    const fetchMock = mockBackend(() => undefined)
    render(<App />)

    await analyse('https://gitlab.com/a/b')

    expect(screen.getByRole('alert')).toHaveTextContent('Only github.com repository URLs')
    expect(requested(fetchMock, '/repositories/')).toHaveLength(0)
  })

  it('analyses a repository end to end and puts it in the URL', async () => {
    mockBackend(happyRoutes())
    render(<App />)

    await analyse('https://github.com/octocat/hello-world')

    // Overview
    expect(await screen.findByRole('link', { name: 'octocat/hello-world' })).toBeInTheDocument()
    expect(screen.getByText('12.3K')).toBeInTheDocument()
    // Commit activity
    expect(await screen.findByRole('heading', { name: 'Commits per week' })).toBeInTheDocument()
    expect(screen.getByText('Fix the login redirect')).toBeInTheDocument()
    expect(screen.getByText(/Busiest: Tue 14:00 UTC \(3 commits\)/)).toBeInTheDocument()
    expect(screen.getByText(/now \(no commits since\)/)).toBeInTheDocument()
    // All-time
    const table = await screen.findByRole('table', { name: 'Contributors by commits' })
    expect(within(table).getByRole('link', { name: 'mona' })).toBeInTheDocument()
    expect(within(table).getByText('1,200')).toBeInTheDocument()
    const languagesCard = await screen.findByRole('region', { name: 'Languages' })
    const bars = within(languagesCard).getAllByRole('listitem')
    expect(bars.map((li) => li.textContent)).toEqual([
      'Java70% percent of code',
      'TypeScript30% percent of code',
    ])

    expect(window.location.search).toBe('?repo=octocat%2Fhello-world')
  })

  it('shows one clear error for a missing repository and skips the other requests', async () => {
    const fetchMock = mockBackend((url) =>
      url.pathname === '/api/v1/repositories/octocat/nope'
        ? Response.json(
            { status: 404, code: 'REPOSITORY_NOT_FOUND', detail: 'not found' },
            { status: 404 },
          )
        : undefined,
    )
    render(<App />)

    await analyse('octocat/nope')

    expect(await screen.findByRole('alert')).toHaveTextContent('Repository not found')
    expect(requested(fetchMock, '/commits')).toHaveLength(0)
    expect(requested(fetchMock, '/contributors')).toHaveLength(0)
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })

  it('refetches commits for a new range, keeps other sections, and updates the URL', async () => {
    const fetchMock = mockBackend(happyRoutes())
    render(<App />)
    await analyse('octocat/hello-world')
    await screen.findByRole('heading', { name: 'Commits per week' })

    await userEvent.click(screen.getByRole('radio', { name: 'Last 30 days' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Exclude bots' }))

    await waitFor(() => {
      const last = requested(fetchMock, '/commits').at(-1) ?? ''
      expect(last).toMatch(/since=\d{4}-\d{2}-\d{2}/)
      expect(last).toContain('excludeBots=true')
    })
    expect(window.location.search).toBe('?repo=octocat%2Fhello-world&range=30d&bots=exclude')
    // The same filter applies to pull requests and issues...
    expect(requested(fetchMock, '/pull-requests').at(-1)).toContain('excludeBots=true')
    expect(requested(fetchMock, '/issues').at(-1)).toContain('excludeBots=true')
    // ...but all-time sections and the fixed 90-day activity summary are not refetched.
    expect(requested(fetchMock, '/contributors')).toHaveLength(1)
    expect(requested(fetchMock, '/languages')).toHaveLength(1)
    expect(requested(fetchMock, '/activity')).toHaveLength(1)
  })

  it('shows pull requests, issues and recent activity', async () => {
    mockBackend(happyRoutes())
    render(<App />)

    await analyse('octocat/hello-world')

    // Wait for real content: the loading placeholder card has the same title.
    await screen.findByRole('link', { name: /#42 Speed up the build/ })
    const prs = screen.getByRole('region', { name: 'Pull requests' })
    expect(within(prs).getByText('80')).toBeInTheDocument() // merged, all time
    expect(within(prs).getByText('30 hours')).toBeInTheDocument() // median time to merge
    expect(within(prs).getByRole('list', { name: 'Legend' })).toHaveTextContent('Opened (3)')

    await screen.findByRole('link', { name: /#43 Crash on startup/ })
    const issues = screen.getByRole('region', { name: 'Issues' })
    expect(within(issues).getByText('6')).toBeInTheDocument() // open now
    expect(within(issues).getByText(/of 1 closed/i)).toHaveTextContent('1 completed')

    await screen.findByText('4 of 12')
    expect(screen.getByRole('region', { name: 'Recent activity' })).toHaveTextContent(
      'Commits, 30 days',
    )
  })

  it('shows "Unavailable" instead of 0 when the Search API count is missing', async () => {
    mockBackend(happyRoutes({ pullRequests: () => Response.json(fx.pullRequests(null)) }))
    render(<App />)

    await analyse('octocat/hello-world')

    await screen.findByRole('link', { name: /#42 Speed up the build/ })
    const prs = screen.getByRole('region', { name: 'Pull requests' })
    expect(within(prs).getByText('Unavailable')).toBeInTheDocument()
    expect(within(prs).queryByText('0')).not.toBeInTheDocument()
  })

  it('explains when a repository has issues turned off', async () => {
    mockBackend(happyRoutes({ issues: () => Response.json(fx.issues(false)) }))
    render(<App />)

    await analyse('octocat/hello-world')

    expect(await screen.findByText('This repository has GitHub Issues turned off.')).toBeVisible()
  })

  it('marks lower-bound activity counts with +', async () => {
    mockBackend(happyRoutes({ activity: () => Response.json(fx.activity(true)) }))
    render(<App />)

    await analyse('octocat/hello-world')

    expect(await screen.findByText('5+')).toBeInTheDocument()
    const activity = screen.getByRole('region', { name: 'Recent activity' })
    expect(within(activity).getByText(/\+ means at least/)).toBeInTheDocument()
  })

  it('keeps other sections working when one endpoint is rate limited', async () => {
    mockBackend(
      happyRoutes({
        pullRequests: () =>
          Response.json(
            { status: 429, code: 'RATE_LIMITED', detail: 'rate limited' },
            { status: 429 },
          ),
      }),
    )
    render(<App />)

    await analyse('octocat/hello-world')

    await screen.findByText(/rate limit reached/)
    const prCard = screen.getByRole('region', { name: 'Pull requests' })
    expect(within(prCard).getByRole('alert')).toHaveTextContent('rate limit reached')
    expect(within(prCard).getByRole('button', { name: 'Try again' })).toBeInTheDocument()
    // Everything else still renders.
    expect(await screen.findByRole('region', { name: 'Issues' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Commits per week' })).toBeInTheDocument()
  })

  it('explains when GitHub is still computing line statistics', async () => {
    mockBackend(happyRoutes({ contributors: () => Response.json(fx.contributors('PENDING')) }))
    render(<App />)

    await analyse('octocat/hello-world')

    expect(await screen.findByText(/GitHub is computing line statistics/)).toBeInTheDocument()
  })

  it('warns when the commit sample was truncated', async () => {
    mockBackend((url) =>
      url.pathname === `${REPO_BASE}/commits`
        ? Response.json(
            fx.commits({
              meta: {
                ...fx.commits().meta,
                truncated: true,
                sampleSize: 1000,
                since: '2026-09-15T00:00:00Z',
              },
            }),
          )
        : happyRoutes()(url),
    )
    render(<App />)

    await analyse('octocat/hello-world')

    expect(await screen.findByText(/more than 1,000 commits/)).toBeInTheDocument()
  })

  it('browser back returns from the landing page to the previous repository', async () => {
    mockBackend(happyRoutes())
    const lengthBefore = window.history.length
    render(
      <StrictMode>
        <App />
      </StrictMode>,
    )
    await analyse('octocat/hello-world')
    await screen.findByRole('link', { name: 'octocat/hello-world' })

    await userEvent.click(screen.getByRole('link', { name: 'GitPulse' }))
    expect(window.location.search).toBe('')
    // Exactly two entries added (repo, then home), even under StrictMode's double-invocation.
    expect(window.history.length).toBe(lengthBefore + 2)

    act(() => {
      window.history.back()
    })
    await waitFor(() => expect(window.location.search).toBe('?repo=octocat%2Fhello-world'))
    expect(await screen.findByRole('link', { name: 'octocat/hello-world' })).toBeInTheDocument()
  })

  it('analyses file activity only when asked, stating the cost first', async () => {
    const fetchMock = mockBackend(happyRoutes())
    render(<App />)
    await analyse('octocat/hello-world')
    await screen.findByRole('heading', { name: 'Commits per week' })

    // Nothing expensive has been requested yet.
    expect(requested(fetchMock, '/files')).toHaveLength(0)
    expect(screen.getByText(/one GitHub request per commit analysed/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Analyse recent commits' }))

    expect(await screen.findByText(/The 20 most recent commits/)).toBeInTheDocument()
    expect(requested(fetchMock, '/files')).toHaveLength(1)
    const files = screen.getByRole('region', { name: 'File activity' })
    // File name first, directory de-emphasised, full path on hover.
    expect(within(files).getAllByTitle('src/main/App.java')[0]).toHaveTextContent('App.java')
    expect(within(files).getByText('+499 −1')).toBeInTheDocument()
    expect(
      within(files).getByText(/Limited to 20 commits because the backend has no/),
    ).toBeVisible()
  })

  it('compares two repositories side by side on a shared scale', async () => {
    const fetchMock = mockBackend((url) =>
      url.pathname === '/api/v1/compare'
        ? Response.json({
            generatedAt: '2026-09-25T12:00:00Z',
            repositories: [
              fx.comparisonSummary('facebook/react', [2, 12]),
              fx.comparisonSummary('vuejs/core', [1, 3]),
            ],
          })
        : undefined,
    )
    render(<App />)

    await userEvent.click(screen.getByRole('link', { name: 'Compare' }))
    // The compare page is lazy-loaded: wait for its two inputs to appear.
    await screen.findByRole('heading', { name: 'Compare two repositories' })
    const inputs = screen.getAllByPlaceholderText('owner/repo or GitHub URL')
    // [0] is the header search box.
    await userEvent.type(inputs[1], 'facebook/react')
    await userEvent.type(inputs[2], 'https://github.com/vuejs/core')
    await userEvent.click(screen.getByRole('button', { name: 'Compare' }))

    const table = await screen.findByRole('table', { name: 'Comparison of two repositories' })
    expect(within(table).getByRole('link', { name: 'facebook/react' })).toBeInTheDocument()
    expect(within(table).getByRole('link', { name: 'vuejs/core' })).toBeInTheDocument()
    // Unavailable data is labelled, not shown as 0.
    expect(within(table).getAllByText('Unavailable')).toHaveLength(2)
    expect(within(table).getAllByText('14').length + within(table).getAllByText('4').length).toBe(2)
    expect(requested(fetchMock, '/api/v1/compare').at(-1)).toContain(
      encodeURIComponent('facebook/react,vuejs/core'),
    )
    expect(window.location.search).toBe('?compare=facebook%2Freact%2Cvuejs%2Fcore')
    expect(screen.getAllByText('Both charts use the same scale')).toHaveLength(2)
  })

  it('rejects comparing a repository with itself without calling the backend', async () => {
    const fetchMock = mockBackend(() => undefined)
    window.history.replaceState(null, '', '/?compare=')
    render(<App />)

    const inputs = await screen.findAllByPlaceholderText('owner/repo or GitHub URL')
    await userEvent.type(inputs[1], 'facebook/react')
    await userEvent.type(inputs[2], 'Facebook/React')
    await userEvent.click(screen.getByRole('button', { name: 'Compare' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Choose two different repositories.')
    expect(requested(fetchMock, '/compare')).toHaveLength(0)
  })

  it('opens the dashboard straight from a shared link', async () => {
    window.history.replaceState(null, '', '/?repo=octocat/hello-world&range=90d')
    mockBackend(happyRoutes())

    render(<App />)

    expect(await screen.findByRole('link', { name: 'octocat/hello-world' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Last 90 days' })).toBeChecked()
  })
})
