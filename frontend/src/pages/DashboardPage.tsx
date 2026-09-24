import type { ReactNode } from 'react'
import { ActivitySection } from '../components/ActivitySection'
import { Card } from '../components/Card'
import { CommitActivitySection } from '../components/CommitActivitySection'
import { ContributorsSection } from '../components/ContributorsSection'
import { ErrorNotice } from '../components/ErrorNotice'
import { FileActivitySection } from '../components/FileActivitySection'
import { FilterBar } from '../components/FilterBar'
import { IssuesSection } from '../components/IssuesSection'
import { LanguagesSection } from '../components/LanguagesSection'
import { OverviewSection } from '../components/OverviewSection'
import { PullRequestsSection } from '../components/PullRequestsSection'
import { useAsync, type AsyncState } from '../hooks/useAsync'
import { usePendingRetry } from '../hooks/usePendingRetry'
import { rangeToSince, type Range } from '../hooks/useUrlState'
import {
  fetchActivity,
  fetchCommits,
  fetchContributors,
  fetchIssues,
  fetchLanguages,
  fetchOverview,
  fetchPullRequests,
} from '../services/repositoryService'
import { formatRepo, type RepoRef } from '../utils/parseRepoInput'
import styles from './DashboardPage.module.css'

interface DashboardPageProps {
  repo: RepoRef
  range: Range
  excludeBots: boolean
  onFiltersChange: (next: { range?: Range; excludeBots?: boolean }) => void
}

export function DashboardPage({ repo, range, excludeBots, onFiltersChange }: DashboardPageProps) {
  const repoKey = formatRepo(repo).toLowerCase()
  const since = rangeToSince(range)

  const overview = useAsync(repoKey, repoKey, (signal) => fetchOverview(repo, signal))

  // Only start the other requests once the repository is known to exist: a typo then costs one
  // GitHub request instead of four, and shows one clear error instead of four.
  const gate = overview.data ? repoKey : null

  const languages = useAsync(gate, repoKey, (signal) => fetchLanguages(repo, signal))
  const commits = useAsync(gate && `${gate}|${since}|${excludeBots}`, repoKey, (signal) =>
    fetchCommits(repo, { since, excludeBots }, signal),
  )
  const contributors = useAsync(gate, repoKey, (signal) => fetchContributors(repo, signal))
  const pullRequests = useAsync(gate && `${gate}|${since}|${excludeBots}`, repoKey, (signal) =>
    fetchPullRequests(repo, { since, excludeBots }, signal),
  )
  const issues = useAsync(gate && `${gate}|${since}|${excludeBots}`, repoKey, (signal) =>
    fetchIssues(repo, { since, excludeBots }, signal),
  )
  // Activity reuses the backend's cached default-window data, so it is effectively free.
  const activity = useAsync(gate, repoKey, (signal) => fetchActivity(repo, signal))

  const linesPending = contributors.data?.statistics.lineStatsStatus === 'PENDING'
  const gaveUp = usePendingRetry(linesPending, contributors.loading, contributors.reload, repoKey)

  if (overview.error) {
    return (
      <div className={styles.page}>
        <ErrorNotice error={overview.error} onRetry={overview.reload} />
      </div>
    )
  }

  return (
    <div className={styles.page}>
      {overview.data ? (
        <OverviewSection repo={overview.data} />
      ) : (
        <Placeholder title="Repository overview" />
      )}

      {overview.data &&
        render(activity, 'Recent activity', (data) => <ActivitySection data={data} />)}

      <section className={styles.group} aria-labelledby="activity-heading">
        <div className={styles.groupHeader}>
          <h2 id="activity-heading">Activity in the selected period</h2>
          <FilterBar range={range} excludeBots={excludeBots} onChange={onFiltersChange} />
        </div>
        {render(commits, 'Commit activity', (data, stale) => (
          <CommitActivitySection data={data} stale={stale} />
        ))}
        <div className={styles.workItems}>
          {render(pullRequests, 'Pull requests', (data, stale) => (
            <PullRequestsSection data={data} stale={stale} />
          ))}
          {render(issues, 'Issues', (data, stale) => (
            <IssuesSection data={data} stale={stale} />
          ))}
        </div>
      </section>

      <section className={styles.group} aria-labelledby="alltime-heading">
        <div className={styles.groupHeader}>
          <h2 id="alltime-heading">All-time</h2>
        </div>
        <div className={styles.allTime}>
          {render(contributors, 'Contributors', (data) => (
            <ContributorsSection
              data={data}
              retrying={linesPending && !gaveUp}
              gaveUp={gaveUp}
              onRetry={contributors.reload}
            />
          ))}
          {render(languages, 'Languages', (data) => (
            <LanguagesSection data={data} />
          ))}
        </div>
      </section>

      {overview.data && (
        <section className={styles.group} aria-labelledby="files-heading">
          <div className={styles.groupHeader}>
            <h2 id="files-heading">Files</h2>
          </div>
          <FileActivitySection repo={repo} repoKey={repoKey} />
        </section>
      )}
    </div>
  )
}

/** Data if we have it (dimmed while refreshing), otherwise the error, otherwise a placeholder. */
function render<T>(
  state: AsyncState<T>,
  title: string,
  content: (data: T, stale: boolean) => ReactNode,
): ReactNode {
  if (state.data) return content(state.data, state.loading)
  if (state.error) {
    return (
      <Card title={title}>
        <ErrorNotice error={state.error} onRetry={state.reload} />
      </Card>
    )
  }
  return <Placeholder title={title} />
}

function Placeholder({ title }: { title: string }) {
  return (
    <Card title={title}>
      <p className={styles.loading} role="status">
        Loading {title.toLowerCase()}…
      </p>
    </Card>
  )
}
