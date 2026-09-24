import type { PullRequestAnalyticsResponse } from '../types/api'
import { formatCount, formatDate, formatHours, formatRelative } from '../utils/format'
import { BarList } from './BarList'
import { Card } from './Card'
import { WeeklyPairChart } from './charts/WeeklyPairChart'
import { InfoNotice } from './ErrorNotice'
import styles from './WorkItems.module.css'
import { StatGrid, StatTile } from './StatTile'

const UNAVAILABLE_HINT =
  'Counted with the GitHub Search API, which has a separate, smaller rate limit. Try again in a minute.'

interface PullRequestsSectionProps {
  data: PullRequestAnalyticsResponse
  stale: boolean
}

export function PullRequestsSection({ data, stale }: PullRequestsSectionProps) {
  const { totals, meta, statistics: s } = data
  return (
    <Card
      title="Pull requests"
      subtitle={`Opened ${formatDate(meta.since)} – ${formatDate(meta.until)} (UTC)`}
      source="mixed"
      stale={stale}
    >
      <div className={styles.stack}>
        {meta.truncated && (
          <InfoNotice tone="warning">
            Very busy: statistics cover the {meta.sampleSize.toLocaleString()} most recent pull
            requests, from {formatDate(meta.since)} (requested {formatDate(meta.requestedSince)}).
          </InfoNotice>
        )}

        <StatGrid>
          <StatTile
            label="Open now"
            value={formatCount(totals.open)}
            hint="All currently open pull requests (GitHub API)"
          />
          <StatTile
            label="Merged, all time"
            value={totals.merged === null ? 'Unavailable' : formatCount(totals.merged)}
            detail={
              totals.closedWithoutMerge === null
                ? undefined
                : `${formatCount(totals.closedWithoutMerge)} closed unmerged`
            }
            hint={
              totals.merged === null
                ? UNAVAILABLE_HINT
                : 'All merged pull requests (GitHub Search API)'
            }
          />
          <StatTile
            label="Opened in period"
            value={s.opened.toLocaleString()}
            detail={`${s.merged} merged · ${s.stillOpen} open`}
            hint="Pull requests created in the selected period, and what has happened to them since"
          />
          <StatTile
            label="Time to merge"
            value={s.timeToMerge ? formatHours(s.timeToMerge.medianHours) : '—'}
            detail={
              s.timeToMerge
                ? `median · 90% within ${formatHours(s.timeToMerge.p90Hours)}`
                : 'no merges'
            }
            hint="From creation to merge, for pull requests opened in the period. Median, because a few very old pull requests would distort an average."
          />
        </StatGrid>

        {s.opened > 0 && (
          <>
            <WeeklyPairChart
              caption="Pull requests per week"
              labels={['Opened', 'Merged']}
              data={s.weekly.map((w) => ({
                weekStart: w.weekStart,
                first: w.opened,
                second: w.merged,
              }))}
            />
            <div className={styles.columns}>
              <div>
                <h4 className={styles.subheading}>Most pull requests opened</h4>
                <BarList
                  unit="pull requests"
                  items={s.topAuthors.slice(0, 5).map((a) => ({
                    key: a.login,
                    label: (
                      <>
                        {a.login}
                        {a.bot && <span className={styles.tag}>bot</span>}
                      </>
                    ),
                    value: a.count,
                  }))}
                />
              </div>
              <div>
                <h4 className={styles.subheading}>Most recent</h4>
                <ul className={styles.items}>
                  {s.recent.slice(0, 5).map((pr) => (
                    <li key={pr.number}>
                      <span className={`${styles.status} ${styles[pr.status]}`}>{pr.status}</span>
                      <a
                        href={pr.htmlUrl}
                        target="_blank"
                        rel="noreferrer"
                        className={styles.title}
                        title={pr.title}
                      >
                        #{pr.number} {pr.title}
                      </a>
                      <span className={styles.meta}>
                        {pr.authorLogin ?? 'unknown'} · {formatRelative(pr.createdAt)}
                        {pr.draft && ' · draft'}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </>
        )}
        {s.opened === 0 && (
          <p className={styles.empty}>No pull requests were opened in this period.</p>
        )}
      </div>
    </Card>
  )
}
