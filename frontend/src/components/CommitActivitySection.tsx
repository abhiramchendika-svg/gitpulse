import type { CommitAnalyticsResponse } from '../types/api'
import { formatCount, formatDate, formatDays, formatExact, formatRelative } from '../utils/format'
import { BarList } from './BarList'
import { Card } from './Card'
import { CommitHeatmap } from './charts/CommitHeatmap'
import { WeeklyCommitsChart } from './charts/WeeklyCommitsChart'
import styles from './CommitActivitySection.module.css'
import { InfoNotice } from './ErrorNotice'
import { StatGrid, StatTile } from './StatTile'

interface CommitActivitySectionProps {
  data: CommitAnalyticsResponse
  stale: boolean
}

export function CommitActivitySection({ data, stale }: CommitActivitySectionProps) {
  const { meta, statistics: s } = data
  const windowText = `${formatDate(meta.since)} – ${formatDate(meta.until)} (UTC)`

  return (
    <div className={styles.stack}>
      {data.emptyRepository && <InfoNotice>This repository has no commits yet.</InfoNotice>}
      {meta.truncated && (
        <InfoNotice tone="warning">
          This window holds more than {formatExact(meta.sampleSize)} commits, the most GitPulse
          fetches per analysis. Statistics cover only the most recent part:{' '}
          <strong>{windowText}</strong>, instead of the requested start{' '}
          {formatDate(meta.requestedSince)}.
        </InfoNotice>
      )}
      {!data.emptyRepository && s.totalCommits === 0 && (
        <InfoNotice>
          No commits in this window. The last push to any branch may be older; try a longer range.
        </InfoNotice>
      )}

      <Card title="Summary" subtitle={windowText} source="calculated" stale={stale}>
        <StatGrid>
          <StatTile
            label="Commits"
            value={formatCount(s.totalCommits)}
            detail={meta.botsExcluded ? 'bots excluded' : `${s.mergeCommits} merges`}
            hint="Commits on the default branch authored in this window"
          />
          <StatTile
            label="Average per week"
            value={s.averagePerWeek.toLocaleString()}
            detail={`${s.averagePerMonth.toLocaleString()} per month`}
            hint="Commits divided by the number of weeks in the window, quiet weeks included"
          />
          <StatTile
            label="Active weeks"
            value={s.activeWeeks}
            detail={`of ${s.totalWeeks} weeks`}
            hint="Weeks with at least one commit"
          />
          <StatTile
            label="Authors"
            value={s.distinctAuthors}
            detail={s.botCommits > 0 ? `${s.botCommits} bot commits` : undefined}
            hint="Distinct commit authors in this window"
          />
          <StatTile
            label="All-time commits"
            value={formatCount(data.totalCommitsAllTime)}
            detail="default branch"
            hint={`${formatExact(data.totalCommitsAllTime)} commits on the default branch (GitHub API)`}
          />
        </StatGrid>
      </Card>

      {s.totalCommits > 0 && (
        <>
          <Card
            title="Commits per week"
            subtitle="Weeks start on Monday (UTC)"
            source="calculated"
            stale={stale}
          >
            <WeeklyCommitsChart weekly={s.weekly} />
          </Card>

          <div className={styles.columns}>
            <Card
              title="When commits are made"
              subtitle="Weekday and hour of the author date, in UTC"
              source="calculated"
              stale={stale}
            >
              <CommitHeatmap heatmap={s.heatmap} />
            </Card>

            <Card
              title="Most active authors"
              subtitle="Commits in this window; unlinked authors are shown by Git name"
              source="calculated"
              stale={stale}
            >
              <BarList
                unit="commits"
                items={s.topAuthors.map((a) => ({
                  key: a.key,
                  label: (
                    <>
                      {a.login ? (
                        <a href={`https://github.com/${a.login}`} target="_blank" rel="noreferrer">
                          {a.name}
                        </a>
                      ) : (
                        a.name
                      )}
                      {a.bot && <span className={styles.tag}>bot</span>}
                    </>
                  ),
                  value: a.commits,
                  valueLabel: `${a.commits} (${a.sharePercent}%)`,
                }))}
              />
            </Card>
          </div>

          <div className={styles.columns}>
            <Card
              title="Periods of inactivity"
              subtitle="Gaps of more than 14 days between commits"
              source="calculated"
              stale={stale}
            >
              {s.inactivityPeriods.length === 0 ? (
                <p className={styles.empty}>No gaps longer than 14 days in this window.</p>
              ) : (
                <ul className={styles.gaps}>
                  {s.inactivityPeriods.map((g) => (
                    <li key={g.from}>
                      <span className={styles.gapDays}>{formatDays(g.days)}</span>
                      <span className={styles.gapRange}>
                        {formatDate(g.from)} →{' '}
                        {g.ongoing ? 'now (no commits since)' : formatDate(g.to)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card
              title="Recent commits"
              subtitle="Newest first, by author date"
              source="github"
              stale={stale}
            >
              <ul className={styles.commits}>
                {s.recentCommits.map((c) => (
                  <li key={c.sha}>
                    <a href={c.htmlUrl} target="_blank" rel="noreferrer" className={styles.sha}>
                      {c.shortSha}
                    </a>
                    <span className={styles.headline} title={c.headline}>
                      {c.headline || '(no message)'}
                    </span>
                    <span className={styles.meta}>
                      {c.authorName} ·{' '}
                      <span title={formatDate(c.authoredAt)}>{formatRelative(c.authoredAt)}</span>
                      {c.merge && ' · merge'}
                    </span>
                  </li>
                ))}
              </ul>
            </Card>
          </div>
        </>
      )}
    </div>
  )
}
