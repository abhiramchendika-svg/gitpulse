import type {
  CommitAnalyticsResponse,
  ContributorAnalyticsResponse,
  LanguageResponse,
  RateLimitResponse,
  RepositoryOverview,
} from '../types/api'

export const rateLimit: RateLimitResponse = {
  authenticated: false,
  core: { limit: 60, remaining: 45, used: 15, resetAt: '2026-09-25T10:00:00Z' },
  search: { limit: 10, remaining: 10, used: 0, resetAt: '2026-09-25T10:00:00Z' },
}

export const overview: RepositoryOverview = {
  fullName: 'octocat/hello-world',
  name: 'hello-world',
  owner: { login: 'octocat', type: 'User', avatarUrl: 'https://example.test/a.png', htmlUrl: '' },
  description: 'My first repository',
  htmlUrl: 'https://github.com/octocat/hello-world',
  homepage: null,
  stars: 12_345,
  forks: 67,
  watchers: 8,
  openIssuesAndPullRequests: 3,
  primaryLanguage: 'Java',
  license: { spdxId: 'MIT', name: 'MIT License' },
  topics: ['demo'],
  createdAt: '2011-01-26T19:01:12Z',
  updatedAt: '2026-09-20T00:00:00Z',
  pushedAt: '2026-09-20T00:00:00Z',
  sizeKb: 2048,
  defaultBranch: 'main',
  archived: false,
  fork: false,
}

export const languages: LanguageResponse = {
  repository: 'octocat/hello-world',
  statistics: {
    totalBytes: 1000,
    languages: [
      { name: 'Java', bytes: 700, percent: 70 },
      { name: 'TypeScript', bytes: 300, percent: 30 },
    ],
  },
}

function emptyHeatmap(): number[][] {
  return Array.from({ length: 7 }, () => Array.from({ length: 24 }, () => 0))
}

export function commits(overrides: Partial<CommitAnalyticsResponse> = {}): CommitAnalyticsResponse {
  const heatmap = emptyHeatmap()
  heatmap[1][14] = 3
  return {
    repository: 'octocat/hello-world',
    emptyRepository: false,
    totalCommitsAllTime: 1234,
    meta: {
      generatedAt: '2026-09-25T12:00:00Z',
      since: '2025-09-26T00:00:00Z',
      until: '2026-09-25T12:00:00Z',
      requestedSince: '2025-09-26T00:00:00Z',
      sampleSize: 5,
      truncated: false,
      botsExcluded: false,
      timezone: 'UTC',
    },
    statistics: {
      totalCommits: 5,
      mergeCommits: 1,
      botCommits: 0,
      distinctAuthors: 2,
      firstCommitAt: '2026-09-01T10:00:00Z',
      lastCommitAt: '2026-09-20T10:00:00Z',
      averagePerWeek: 0.1,
      averagePerMonth: 0.41,
      activeWeeks: 2,
      totalWeeks: 53,
      weekly: [
        { weekStart: '2026-09-01', commits: 2 },
        { weekStart: '2026-09-08', commits: 3 },
      ],
      monthly: [{ month: '2026-09', commits: 5 }],
      byDayOfWeek: [0, 3, 0, 0, 0, 0, 0],
      byHourOfDay: Array.from({ length: 24 }, (_, h) => (h === 14 ? 3 : 0)),
      heatmap,
      topAuthors: [
        { key: 'mona', name: 'mona', login: 'mona', bot: false, commits: 3, sharePercent: 60 },
        { key: 'git:Jane', name: 'Jane', login: null, bot: false, commits: 2, sharePercent: 40 },
      ],
      inactivityPeriods: [
        { from: '2026-09-20T10:00:00Z', to: '2026-09-25T12:00:00Z', days: 15.5, ongoing: true },
      ],
      longestInactivity: null,
      recentCommits: [
        {
          sha: 'abcdef1234567890',
          shortSha: 'abcdef1',
          headline: 'Fix the login redirect',
          authorName: 'mona',
          authorLogin: 'mona',
          authoredAt: '2026-09-20T10:00:00Z',
          merge: false,
          htmlUrl: 'https://github.com/octocat/hello-world/commit/abcdef1',
        },
      ],
    },
    ...overrides,
  }
}

export function contributors(
  lineStatsStatus: 'AVAILABLE' | 'PENDING' | 'UNAVAILABLE' = 'AVAILABLE',
): ContributorAnalyticsResponse {
  const available = lineStatsStatus === 'AVAILABLE'
  return {
    repository: 'octocat/hello-world',
    generatedAt: '2026-09-25T12:00:00Z',
    available: true,
    truncated: false,
    statistics: {
      contributorCount: 2,
      totalCommits: 10,
      topContributorSharePercent: 70,
      contributorsForHalfOfCommits: 1,
      botCount: 0,
      lineStatsStatus,
      contributors: [
        {
          login: 'mona',
          avatarUrl: 'https://example.test/m.png',
          htmlUrl: 'https://github.com/mona',
          bot: false,
          commits: 7,
          sharePercent: 70,
          additions: available ? 1200 : null,
          deletions: available ? 300 : null,
        },
        {
          login: 'hubot',
          avatarUrl: 'https://example.test/h.png',
          htmlUrl: 'https://github.com/hubot',
          bot: false,
          commits: 3,
          sharePercent: 30,
          additions: null,
          deletions: null,
        },
      ],
    },
  }
}
