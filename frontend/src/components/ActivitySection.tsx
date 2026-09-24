import type { ActivityResponse } from '../types/api'
import { formatDate, formatDays, formatRelative } from '../utils/format'
import { Card } from './Card'
import { StatGrid, StatTile } from './StatTile'

/** "12" or, when the sample did not reach back far enough, "12+" (a lower bound). */
function count(value: number, partial: boolean): string {
  return partial ? `${value.toLocaleString()}+` : value.toLocaleString()
}

/**
 * Recent activity at a glance: counts and dates only. There is deliberately no overall score
 * or "health" label; readers interpret the numbers for their own purpose.
 */
export function ActivitySection({ data }: { data: ActivityResponse }) {
  const a = data.indicators
  const anyPartial = a.commitsPartial || a.pullRequestsPartial || a.issuesPartial
  return (
    <Card
      title="Recent activity"
      subtitle={`Last 30 and 90 days, up to ${formatDate(data.generatedAt)}`}
      source="calculated"
    >
      <StatGrid>
        <StatTile
          label="Last commit"
          value={a.lastCommitAt ? formatRelative(a.lastCommitAt) : 'Over a year ago'}
          detail={
            a.daysSinceLastCommit !== null
              ? `${formatDays(a.daysSinceLastCommit)} · last push ${formatRelative(a.lastPushAt)}`
              : `last push ${formatRelative(a.lastPushAt)}`
          }
          hint="Author date of the newest commit on the default branch. 'Last push' covers any branch."
        />
        <StatTile
          label="Commits, 30 days"
          value={count(a.commitsLast30Days, a.commitsPartial)}
          detail={`${count(a.commitsLast90Days, a.commitsPartial)} in 90 days`}
          hint="Commits on the default branch authored in the last 30 and 90 days"
        />
        <StatTile
          label="Active weeks"
          value={`${a.activeWeeksOfLast12} of 12`}
          detail="weeks with a commit"
          hint="Of the last twelve 7-day periods, how many contain at least one commit"
        />
        <StatTile
          label="PRs, 90 days"
          value={count(a.pullRequestsOpenedLast90Days, a.pullRequestsPartial)}
          detail={`opened · ${count(a.pullRequestsMergedLast90Days, a.pullRequestsPartial)} merged`}
          hint="Pull requests opened, and pull requests merged, in the last 90 days"
        />
        <StatTile
          label="Issues, 90 days"
          value={
            a.issuesOpenedLast90Days === null
              ? 'Disabled'
              : count(a.issuesOpenedLast90Days, a.issuesPartial)
          }
          detail={
            a.issuesClosedLast90Days === null
              ? 'issues are turned off'
              : `opened · ${count(a.issuesClosedLast90Days, a.issuesPartial)} closed`
          }
          hint="Issues (not pull requests) opened, and closed, in the last 90 days"
        />
      </StatGrid>
      {anyPartial && (
        <p style={{ margin: '0.6rem 0 0', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
          + means at least: this repository is busy enough that GitPulse did not fetch the full 90
          days.
        </p>
      )}
    </Card>
  )
}
