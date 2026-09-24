// TypeScript mirrors of the backend's JSON responses. Keep in sync with backend api/dto and
// analysis/*Statistics. Field meanings and formulas: docs/metrics.md.

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

/** GET /api/v1/repositories/{owner}/{repo} */
export interface RepositoryOverview {
  fullName: string
  name: string
  owner: { login: string; type: string; avatarUrl: string; htmlUrl: string } | null
  description: string | null
  htmlUrl: string
  homepage: string | null
  stars: number
  forks: number
  watchers: number
  /** GitHub counts open pull requests as issues in this number. */
  openIssuesAndPullRequests: number
  primaryLanguage: string | null
  license: { spdxId: string | null; name: string } | null
  topics: string[]
  createdAt: string
  updatedAt: string
  pushedAt: string | null
  sizeKb: number
  defaultBranch: string
  archived: boolean
  fork: boolean
}

export interface LanguageShare {
  name: string
  bytes: number
  percent: number
}

/** GET /api/v1/repositories/{owner}/{repo}/languages */
export interface LanguageResponse {
  repository: string
  statistics: { totalBytes: number; languages: LanguageShare[] }
}

export interface AnalysisMeta {
  generatedAt: string
  since: string
  until: string
  requestedSince: string
  sampleSize: number
  truncated: boolean
  botsExcluded: boolean
  timezone: string
}

export interface AuthorActivity {
  key: string
  name: string
  login: string | null
  bot: boolean
  commits: number
  sharePercent: number
}

export interface InactivityPeriod {
  from: string
  to: string
  days: number
  ongoing: boolean
}

export interface RecentCommit {
  sha: string
  shortSha: string
  headline: string
  authorName: string
  authorLogin: string | null
  authoredAt: string
  merge: boolean
  htmlUrl: string
}

export interface CommitStatistics {
  totalCommits: number
  mergeCommits: number
  botCommits: number
  distinctAuthors: number
  firstCommitAt: string | null
  lastCommitAt: string | null
  averagePerWeek: number
  averagePerMonth: number
  activeWeeks: number
  totalWeeks: number
  weekly: { weekStart: string; commits: number }[]
  monthly: { month: string; commits: number }[]
  /** 7 values, index 0 = Monday. */
  byDayOfWeek: number[]
  /** 24 values, UTC hours. */
  byHourOfDay: number[]
  /** heatmap[day][hour], day 0 = Monday, UTC. */
  heatmap: number[][]
  topAuthors: AuthorActivity[]
  inactivityPeriods: InactivityPeriod[]
  longestInactivity: InactivityPeriod | null
  recentCommits: RecentCommit[]
}

/** GET /api/v1/repositories/{owner}/{repo}/commits */
export interface CommitAnalyticsResponse {
  repository: string
  meta: AnalysisMeta
  emptyRepository: boolean
  totalCommitsAllTime: number
  statistics: CommitStatistics
}

export type LineStatsStatus = 'AVAILABLE' | 'PENDING' | 'UNAVAILABLE'

export interface Contributor {
  login: string
  avatarUrl: string
  htmlUrl: string
  bot: boolean
  commits: number
  sharePercent: number
  additions: number | null
  deletions: number | null
}

/** GET /api/v1/repositories/{owner}/{repo}/contributors */
export interface ContributorAnalyticsResponse {
  repository: string
  generatedAt: string
  available: boolean
  truncated: boolean
  statistics: {
    contributorCount: number
    totalCommits: number
    topContributorSharePercent: number
    contributorsForHalfOfCommits: number
    botCount: number
    lineStatsStatus: LineStatsStatus
    contributors: Contributor[]
  }
}
