import type { IssueAnalyticsResponse } from '../types/api'
import { formatCount, formatDate, formatHours, formatRelative } from '../utils/format'
import { Card } from './Card'
import { WeeklyPairChart } from './charts/WeeklyPairChart'
import { InfoNotice } from './ErrorNotice'
import styles from './WorkItems.module.css'
import { StatGrid, StatTile } from './StatTile'

interface IssuesSectionProps {
  data: IssueAnalyticsResponse
  stale: boolean
}

export function IssuesSection({ data, stale }: IssuesSectionProps) {
  const { totals, meta, statistics: s } = data

  if (!data.issuesEnabled || !totals || !s) {
    return (
      <Card title="Issues" source="github">
        <InfoNotice>This repository has GitHub Issues turned off.</InfoNotice>
      </Card>
    )
  }

  return (
    <Card
      title="Issues"
      subtitle={`Opened ${formatDate(meta.since)} – ${formatDate(meta.until)} (UTC); pull requests excluded`}
      source="mixed"
      stale={stale}
    >
      <div className={styles.stack}>
        {meta.truncated && (
          <InfoNotice tone="warning">
            Very busy: statistics cover issues from {formatDate(meta.since)} (requested{' '}
            {formatDate(meta.requestedSince)}). GitHub lists pull requests together with issues, so
            fewer issues fit in each request.
          </InfoNotice>
        )}

        <StatGrid>
          <StatTile
            label="Open now"
            value={formatCount(totals.open)}
            hint="Open issues: GitHub's open-issue count minus open pull requests, which GitHub includes in it"
          />
          <StatTile
            label="Closed, all time"
            value={totals.closed === null ? 'Unavailable' : formatCount(totals.closed)}
            hint={
              totals.closed === null
                ? 'Counted with the GitHub Search API, which has a separate, smaller rate limit. Try again in a minute.'
                : 'All closed issues (GitHub Search API)'
            }
          />
          <StatTile
            label="Opened in period"
            value={s.opened.toLocaleString()}
            detail={`${s.closed} closed · ${s.stillOpen} open`}
            hint="Issues created in the selected period, and how many have been closed since"
          />
          <StatTile
            label="Time to close"
            value={s.timeToClose ? formatHours(s.timeToClose.medianHours) : '—'}
            detail={
              s.timeToClose
                ? `median · 90% within ${formatHours(s.timeToClose.p90Hours)}`
                : 'none closed'
            }
            hint="From creation to close, for issues opened in the period (median and 90th percentile)"
          />
        </StatGrid>

        {s.closed > 0 && (
          <p className={styles.reasons}>
            Of {s.closed} closed: <strong>{s.closedAsCompleted}</strong> completed,{' '}
            <strong>{s.closedAsNotPlanned}</strong> not planned, <strong>{s.closedOther}</strong>{' '}
            duplicate or no reason given (as recorded on GitHub).
          </p>
        )}

        {s.opened > 0 ? (
          <>
            <WeeklyPairChart
              caption="Issues per week"
              labels={['Opened', 'Closed']}
              data={s.weekly.map((w) => ({
                weekStart: w.weekStart,
                first: w.opened,
                second: w.closed,
              }))}
            />
            <h4 className={styles.subheading}>Most recent</h4>
            <ul className={styles.items}>
              {s.recent.slice(0, 5).map((issue) => (
                <li key={issue.number}>
                  <span className={`${styles.status} ${issue.open ? styles.open : styles.closed}`}>
                    {issue.open ? 'open' : 'closed'}
                  </span>
                  <a
                    href={issue.htmlUrl}
                    target="_blank"
                    rel="noreferrer"
                    className={styles.title}
                    title={issue.title}
                  >
                    #{issue.number} {issue.title}
                  </a>
                  <span className={styles.meta}>
                    {issue.authorLogin ?? 'unknown'} · {formatRelative(issue.createdAt)} ·{' '}
                    {issue.comments} {issue.comments === 1 ? 'comment' : 'comments'}
                  </span>
                </li>
              ))}
            </ul>
          </>
        ) : (
          <p className={styles.empty}>No issues were opened in this period.</p>
        )}
      </div>
    </Card>
  )
}
