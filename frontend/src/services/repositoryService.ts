import type {
  ActivityResponse,
  CommitAnalyticsResponse,
  ContributorAnalyticsResponse,
  IssueAnalyticsResponse,
  LanguageResponse,
  PullRequestAnalyticsResponse,
  RepositoryOverview,
} from '../types/api'
import type { RepoRef } from '../utils/parseRepoInput'
import { getJson } from './apiClient'

function base(ref: RepoRef): string {
  // Names are already validated by parseRepoInput; encoding is defence in depth.
  return `/api/v1/repositories/${encodeURIComponent(ref.owner)}/${encodeURIComponent(ref.repo)}`
}

export function fetchOverview(ref: RepoRef, signal?: AbortSignal): Promise<RepositoryOverview> {
  return getJson(base(ref), signal)
}

export function fetchLanguages(ref: RepoRef, signal?: AbortSignal): Promise<LanguageResponse> {
  return getJson(`${base(ref)}/languages`, signal)
}

export interface CommitQuery {
  /** yyyy-MM-dd (UTC); omitted = backend default */
  since?: string
  excludeBots: boolean
}

export function fetchCommits(
  ref: RepoRef,
  query: CommitQuery,
  signal?: AbortSignal,
): Promise<CommitAnalyticsResponse> {
  return getJson(`${base(ref)}/commits${windowQuery(query)}`, signal)
}

function windowQuery(query: CommitQuery): string {
  const params = new URLSearchParams()
  if (query.since) params.set('since', query.since)
  if (query.excludeBots) params.set('excludeBots', 'true')
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

export function fetchPullRequests(
  ref: RepoRef,
  query: CommitQuery,
  signal?: AbortSignal,
): Promise<PullRequestAnalyticsResponse> {
  return getJson(`${base(ref)}/pull-requests${windowQuery(query)}`, signal)
}

export function fetchIssues(
  ref: RepoRef,
  query: CommitQuery,
  signal?: AbortSignal,
): Promise<IssueAnalyticsResponse> {
  return getJson(`${base(ref)}/issues${windowQuery(query)}`, signal)
}

export function fetchActivity(ref: RepoRef, signal?: AbortSignal): Promise<ActivityResponse> {
  return getJson(`${base(ref)}/activity`, signal)
}

export function fetchContributors(
  ref: RepoRef,
  signal?: AbortSignal,
): Promise<ContributorAnalyticsResponse> {
  return getJson(`${base(ref)}/contributors`, signal)
}
