import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode } from 'react'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import App from './App'
import * as fx from './test/fixtures'

type Route = (url: URL) => Response | undefined

/**
 * Fake backend: the first matching route answers. Health and rate-limit always answer so the
 * header badge works. Returns the mock so tests can inspect which URLs were requested.
 */
function mockBackend(route: Route) {
  // `init` is unused here, but typing it lets tests inspect the method of recorded calls.
  const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
    const url = new URL(String(input), 'http://localhost')
    if (url.pathname === '/actuator/health') return Response.json({ status: 'UP' })
    if (url.pathname === '/api/v1/rate-limit') return Response.json(fx.rateLimit)
    const response = route(url)
    // Optional features are off unless a test's route turns them on.
    if (!response && url.pathname === '/api/v1/features') {
      return Response.json({ explanations: false })
    }
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

/** Captures files the app saves instead of letting jsdom try to navigate to a blob URL. */
function captureDownloads() {
  const files: { name: string; blob: Blob }[] = []
  let pending: Blob | null = null
  vi.spyOn(URL, 'createObjectURL').mockImplementation((blob) => {
    pending = blob as Blob
    return 'blob:test'
  })
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    if (pending) files.push({ name: this.download, blob: pending })
  })
  return {
    files,
    async json(index = -1) {
      return JSON.parse(await files.at(index)!.blob.text())
    },
  }
}

const DATE = /-\d{8}\./

async function analyse(text: string) {
  await userEvent.type(screen.getByRole('textbox', { name: 'GitHub repository or user' }), text)
  await userEvent.click(screen.getByRole('button', { name: 'Analyse' }))
}

describe('App', () => {
  // The dashboard is lazy-loaded. Import it once up front so the first test that opens it is not
  // measuring module transformation time against Testing Library's 1s wait.
  // On a cold cache (first run, slow CI machine) transforming these modules can exceed the
  // default 10 s hook timeout, which made this hook fail once in local testing.
  beforeAll(async () => {
    await import('./pages/DashboardPage')
    await import('./pages/ComparePage')
  }, 30_000)

  afterEach(() => vi.restoreAllMocks())

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

  it('does not start file analysis for the next repository automatically', async () => {
    // Any octocat/* repository answers like octocat/hello-world.
    const fetchMock = mockBackend((url) =>
      happyRoutes()(
        new URL(
          url.pathname.replace(/^(\/api\/v1\/repositories\/octocat\/)[^/]+/, '$1hello-world'),
          url,
        ),
      ),
    )
    render(<App />)
    await analyse('octocat/hello-world')
    await userEvent.click(await screen.findByRole('button', { name: 'Analyse recent commits' }))
    await screen.findByText(/The 20 most recent commits/)

    const search = screen.getByRole('textbox', { name: 'GitHub repository or user' })
    await userEvent.clear(search)
    await analyse('octocat/spoon-knife')
    expect(await screen.findByRole('button', { name: 'Analyse recent commits' })).toBeVisible()
    expect(requested(fetchMock, '/files')).toEqual([`${REPO_BASE}/files`])
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
    // The header search box has a different placeholder, so these are the two compare inputs.
    await userEvent.type(inputs[0], 'facebook/react')
    await userEvent.type(inputs[1], 'https://github.com/vuejs/core')
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
    await userEvent.type(inputs[0], 'facebook/react')
    await userEvent.type(inputs[1], 'Facebook/React')
    await userEvent.click(screen.getByRole('button', { name: 'Compare' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Choose two different repositories.')
    expect(requested(fetchMock, '/compare')).toHaveLength(0)
  })

  it('opens a user profile from the search box', async () => {
    const fetchMock = mockBackend((url) =>
      url.pathname === '/api/v1/users/mona' ? Response.json(fx.profile()) : happyRoutes()(url),
    )
    render(<App />)

    await analyse('https://github.com/mona')

    expect(await screen.findByRole('link', { name: 'Mona Lisa' })).toBeInTheDocument()
    expect(window.location.search).toBe('?user=mona')
    expect(screen.getByText('51')).toBeInTheDocument() // stars received
    expect(screen.getByText(/Private\s+contributions are never visible here/)).toBeInTheDocument()
    // A javascript: "blog" URL from the profile must not become a clickable script link.
    const blog = screen.getByRole('link', { name: 'javascript:alert(1)' })
    expect(blog.getAttribute('href')).not.toMatch(/^javascript:/i)
    expect(requested(fetchMock, '/users/mona')).toHaveLength(1)

    // Repositories on the profile open GitPulse's own dashboard.
    await userEvent.click(screen.getAllByRole('link', { name: 'mona/app' })[0])
    expect(window.location.search).toBe('?repo=mona%2Fapp')
  })

  it('explains that organization events are not analysed', async () => {
    window.history.replaceState(null, '', '/?user=mona')
    mockBackend((url) =>
      url.pathname === '/api/v1/users/mona' ? Response.json(fx.profile('Organization')) : undefined,
    )

    render(<App />)

    expect(await screen.findByText(/not analysed for organizations/)).toBeInTheDocument()
  })

  it('shows a clear message for an unknown user', async () => {
    mockBackend((url) =>
      url.pathname === '/api/v1/users/ghost123'
        ? Response.json(
            { status: 404, code: 'USER_NOT_FOUND', detail: "No GitHub user named 'ghost123'." },
            { status: 404 },
          )
        : undefined,
    )
    render(<App />)

    await analyse('ghost123')

    expect(await screen.findByRole('alert')).toHaveTextContent('No GitHub user')
  })

  it('exports the dashboard as a JSON report, noting sections that are missing', async () => {
    mockBackend(
      happyRoutes({
        issues: () =>
          Response.json(
            { status: 429, code: 'RATE_LIMITED', detail: 'GitHub API rate limit reached.' },
            { status: 429 },
          ),
      }),
    )
    const downloads = captureDownloads()
    window.history.replaceState(null, '', '/?repo=octocat/hello-world&range=90d&bots=exclude')
    render(<App />)

    await screen.findByRole('table', { name: 'Contributors by commits' })
    await screen.findByRole('heading', { name: 'Commits per week' })
    const button = screen.getByRole('button', { name: /JSON report: octocat\/hello-world report/ })
    await waitFor(() => expect(button).toBeEnabled())
    await userEvent.click(button)

    expect(downloads.files[0].name).toMatch(/^gitpulse-octocat-hello-world-90d-\d{8}\.json$/)
    const report = await downloads.json()
    expect(report).toMatchObject({
      generator: 'GitPulse',
      kind: 'repository',
      subject: 'octocat/hello-world',
      data: {
        filters: { range: '90d', excludeBots: true },
        overview: { fullName: 'octocat/hello-world' },
        commits: { repository: 'octocat/hello-world' },
        issues: null,
        fileActivity: null,
      },
    })
    expect(report.data.notIncluded).toEqual([
      { section: 'issues', reason: 'RATE_LIMITED' },
      { section: 'fileActivity', reason: 'NOT_REQUESTED' },
    ])

    // Once file activity has been analysed, it is part of the report too.
    await userEvent.click(screen.getByRole('button', { name: 'Analyse recent commits' }))
    await screen.findByText(/The 20 most recent commits/)
    await waitFor(() => expect(button).toBeEnabled())
    await userEvent.click(button)
    const second = await downloads.json()
    expect(second.data.fileActivity.statistics.commitsAnalyzed).toBe(20)
  })

  it('downloads chart tables and the full contributor list as CSV', async () => {
    mockBackend(happyRoutes())
    const downloads = captureDownloads()
    window.history.replaceState(null, '', '/?repo=octocat/hello-world')
    render(<App />)

    await screen.findByRole('table', { name: 'Contributors by commits' })
    await userEvent.click(
      screen.getByRole('button', { name: 'Download CSV: All listed contributors' }),
    )
    const contributorsFile = downloads.files[0]
    expect(contributorsFile.name).toMatch(/^gitpulse-octocat-hello-world-contributors/)
    expect(contributorsFile.name).toMatch(DATE)
    const csv = await contributorsFile.blob.text()
    expect(csv.split('\r\n')[0]).toBe(
      'Rank,Login,Bot,Commits,Share of listed commits (%),Lines added,Lines deleted,Profile',
    )
    expect(csv).toContain('1,mona,false,7,70,1200,300,https://github.com/mona')

    await screen.findByRole('heading', { name: 'Commits per week' })
    await userEvent.click(screen.getByRole('button', { name: 'Download CSV: Commits per week' }))
    expect(downloads.files[1].name).toMatch(/^gitpulse-octocat-hello-world-commits-per-week-/)
    expect((await downloads.files[1].blob.text()).split('\r\n')[0]).toBe(
      'Week starting (UTC),Commits',
    )
  })

  it('exports a profile and a comparison as JSON', async () => {
    mockBackend((url) => {
      if (url.pathname === '/api/v1/users/mona') return Response.json(fx.profile())
      if (url.pathname === '/api/v1/compare')
        return Response.json({
          generatedAt: '2026-09-25T12:00:00Z',
          repositories: [
            fx.comparisonSummary('facebook/react', [2, 12]),
            fx.comparisonSummary('vuejs/core', [1, 3]),
          ],
        })
    })
    const downloads = captureDownloads()

    window.history.replaceState(null, '', '/?user=mona')
    const { unmount } = render(<App />)
    await userEvent.click(
      await screen.findByRole('button', { name: 'JSON report: Profile of mona' }),
    )
    expect(downloads.files[0].name).toMatch(/^gitpulse-user-mona-\d{8}\.json$/)
    expect(await downloads.json()).toMatchObject({ kind: 'profile', subject: 'mona' })
    unmount()

    window.history.replaceState(null, '', '/?compare=facebook/react,vuejs/core')
    render(<App />)
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'JSON report: Comparison of facebook/react and vuejs/core',
      }),
    )
    expect(downloads.files[1].name).toMatch(/^gitpulse-compare-facebook-react-vuejs-core-/)
    const report = await downloads.json()
    expect(report.data.repositories).toHaveLength(2)
    // Each repository's chart table gets its own file name.
    const chartButtons = screen.getAllByRole('button', { name: 'Download CSV: Commits per week' })
    await userEvent.click(chartButtons[0])
    await userEvent.click(chartButtons[1])
    expect(downloads.files.slice(2).map((f) => f.name.replace(DATE, '.'))).toEqual([
      'gitpulse-facebook-react-commits-per-week.csv',
      'gitpulse-vuejs-core-commits-per-week.csv',
    ])
  })

  describe('AI explanation', () => {
    const explanation = {
      repository: 'octocat/hello-world',
      generatedAt: '2026-09-25T12:00:00Z',
      model: 'claude-opus-5',
      window: { since: '2025-09-26T00:00:00Z', until: '2026-09-25T12:00:00Z', botsExcluded: false },
      sentences: [
        {
          text: 'In the last 365 days there were 42 commits.',
          basedOn: [
            { id: 'window.days', label: 'Length of the selected period, in days', value: 365 },
            { id: 'commits.total', label: 'Commits in the selected period', value: 42 },
          ],
        },
        {
          text: 'The repository has 12,345 stars.',
          basedOn: [{ id: 'repository.stars', label: 'Stars', value: 12345 }],
        },
      ],
      sentencesRemoved: 1,
    }

    function withExplanations(explain: () => Response = () => Response.json(explanation)) {
      return mockBackend((url) => {
        if (url.pathname === '/api/v1/features') return Response.json({ explanations: true })
        if (url.pathname === `${REPO_BASE}/explanation`) return explain()
        return happyRoutes()(url)
      })
    }

    const posts = (fetchMock: ReturnType<typeof mockBackend>) =>
      fetchMock.mock.calls.filter(
        ([input, init]) => String(input).includes('/explanation') && init?.method === 'POST',
      )

    it('is hidden when the backend has no AI key', async () => {
      mockBackend(happyRoutes())
      window.history.replaceState(null, '', '/?repo=octocat/hello-world')
      render(<App />)

      await screen.findByRole('heading', { name: 'Commits per week' })
      expect(screen.queryByRole('region', { name: 'Plain-English summary' })).toBeNull()
    })

    it('runs only when asked, and shows verified sentences with their sources', async () => {
      const fetchMock = withExplanations()
      window.history.replaceState(null, '', '/?repo=octocat/hello-world&range=90d')
      render(<App />)

      const card = await screen.findByRole('region', { name: 'Plain-English summary' })
      expect(within(card).getByText(/Only the numbers are sent/)).toBeInTheDocument()
      expect(posts(fetchMock)).toHaveLength(0)

      await userEvent.click(within(card).getByRole('button', { name: 'Explain these numbers' }))

      expect(
        await within(card).findByText('In the last 365 days there were 42 commits.'),
      ).toBeInTheDocument()
      expect(
        within(card).getByText(
          'Based on: Length of the selected period, in days · Commits in the selected period',
        ),
      ).toBeInTheDocument()
      expect(within(card).getByText(/1 sentence was left out/)).toBeInTheDocument()
      expect(within(card).getByText(/Not a metric/)).toBeInTheDocument()
      expect(posts(fetchMock)).toHaveLength(1)
      expect(String(posts(fetchMock)[0][0])).toMatch(/explanation\?since=\d{4}-\d{2}-\d{2}$/)
    })

    it('does not spend another call when the filters change', async () => {
      const fetchMock = withExplanations()
      window.history.replaceState(null, '', '/?repo=octocat/hello-world')
      render(<App />)

      const card = await screen.findByRole('region', { name: 'Plain-English summary' })
      await userEvent.click(within(card).getByRole('button', { name: 'Explain these numbers' }))
      await within(card).findByText('The repository has 12,345 stars.')

      await userEvent.click(screen.getByRole('radio', { name: 'Last 30 days' }))

      expect(
        await within(card).findByRole('button', { name: 'Explain these numbers' }),
      ).toBeVisible()
      expect(within(card).queryByText('The repository has 12,345 stars.')).toBeNull()
      expect(posts(fetchMock)).toHaveLength(1)
    })

    it('explains when the hourly AI limit is reached', async () => {
      withExplanations(() =>
        Response.json(
          {
            status: 429,
            code: 'AI_LIMIT_REACHED',
            detail: 'limit',
            resetAt: '2026-09-25T13:00:00Z',
          },
          { status: 429 },
        ),
      )
      window.history.replaceState(null, '', '/?repo=octocat/hello-world')
      render(<App />)

      const card = await screen.findByRole('region', { name: 'Plain-English summary' })
      await userEvent.click(within(card).getByRole('button', { name: 'Explain these numbers' }))

      expect(await within(card).findByRole('alert')).toHaveTextContent(
        'hourly limit for AI explanations',
      )
    })
  })

  it('opens the dashboard straight from a shared link', async () => {
    window.history.replaceState(null, '', '/?repo=octocat/hello-world&range=90d')
    mockBackend(happyRoutes())

    render(<App />)

    expect(await screen.findByRole('link', { name: 'octocat/hello-world' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Last 90 days' })).toBeChecked()
  })
})
