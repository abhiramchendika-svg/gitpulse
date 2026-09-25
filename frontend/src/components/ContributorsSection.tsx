import { useState } from 'react'
import type { ContributorAnalyticsResponse } from '../types/api'
import { downloadCsv, exportFileName } from '../utils/export'
import { useExportScope } from '../utils/exportScope'
import { Card } from './Card'
import styles from './ContributorsSection.module.css'
import { DownloadButton } from './DownloadButton'
import { InfoNotice } from './ErrorNotice'
import { StatGrid, StatTile } from './StatTile'

const INITIAL_ROWS = 15

const CSV_COLUMNS = [
  'Rank',
  'Login',
  'Bot',
  'Commits',
  'Share of listed commits (%)',
  'Lines added',
  'Lines deleted',
  'Profile',
]

interface ContributorsSectionProps {
  data: ContributorAnalyticsResponse
  /** GitHub is still computing line statistics and GitPulse is retrying. */
  retrying: boolean
  /** Retries were used up while GitHub was still computing. */
  gaveUp: boolean
  onRetry: () => void
}

export function ContributorsSection({ data, retrying, gaveUp, onRetry }: ContributorsSectionProps) {
  const [showAll, setShowAll] = useState(false)
  const scope = useExportScope()
  const s = data.statistics

  if (!data.available) {
    return (
      <Card title="Contributors" source="github">
        <InfoNotice>
          GitHub does not list contributors for this repository through its API (this happens for
          some very large repositories).
        </InfoNotice>
      </Card>
    )
  }

  const rows = showAll ? s.contributors : s.contributors.slice(0, INITIAL_ROWS)
  // Bars are scaled to the largest share so differences are visible; the exact % is printed.
  const maxShare = Math.max(1, ...s.contributors.map((c) => c.sharePercent))

  return (
    <Card
      title="Contributors"
      subtitle="All-time commits on the default branch by linked GitHub accounts"
      source="mixed"
    >
      <StatGrid>
        <StatTile
          label="Contributors"
          value={s.contributorCount.toLocaleString()}
          detail={
            data.truncated
              ? 'first 500 listed'
              : s.botCount > 0
                ? `incl. ${s.botCount} bots`
                : undefined
          }
          hint="Accounts GitHub links to commits on the default branch"
        />
        <StatTile
          label="Top contributor share"
          value={`${s.topContributorSharePercent}%`}
          hint="Commits by the contributor with the most commits, as a share of all listed commits"
        />
        <StatTile
          label="Half of all commits by"
          value={s.contributorsForHalfOfCommits.toLocaleString()}
          detail={s.contributorsForHalfOfCommits === 1 ? 'contributor' : 'contributors'}
          hint="The fewest contributors whose commits add up to at least 50% of all listed commits"
        />
      </StatGrid>

      <div className={styles.status}>
        {s.lineStatsStatus === 'PENDING' && !gaveUp && (
          <InfoNotice>
            GitHub is computing line statistics for this repository.{' '}
            {retrying ? 'Checking again shortly…' : ''}
          </InfoNotice>
        )}
        {s.lineStatsStatus === 'PENDING' && gaveUp && (
          <InfoNotice>
            GitHub has not finished computing line statistics yet.{' '}
            <button type="button" onClick={onRetry}>
              Check again
            </button>
          </InfoNotice>
        )}
        {s.lineStatsStatus === 'UNAVAILABLE' && (
          <InfoNotice>
            GitHub does not provide line counts for this repository (for example, repositories with
            10,000+ commits).
          </InfoNotice>
        )}
      </div>

      <div className={styles.scroll}>
        <table className={styles.table}>
          <caption className="visually-hidden">Contributors by commits</caption>
          <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">Contributor</th>
              <th scope="col">Commits</th>
              <th scope="col">Share</th>
              <th scope="col" title="Lines added (GitHub, top 100, excludes merges)">
                Lines +
              </th>
              <th scope="col" title="Lines deleted (GitHub, top 100, excludes merges)">
                Lines −
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((c, i) => (
              <tr key={c.login}>
                <td className={styles.rank}>{i + 1}</td>
                <td className={styles.who}>
                  <img src={c.avatarUrl} alt="" width={20} height={20} loading="lazy" />
                  <a href={c.htmlUrl} target="_blank" rel="noreferrer">
                    {c.login}
                  </a>
                  {c.bot && <span className={styles.tag}>bot</span>}
                </td>
                <td>{c.commits.toLocaleString()}</td>
                <td className={styles.share}>
                  <span className={styles.shareValue}>{c.sharePercent}%</span>
                  <span className={styles.shareTrack} aria-hidden="true">
                    <span
                      className={styles.shareBar}
                      style={{ width: `${(c.sharePercent / maxShare) * 100}%` }}
                    />
                  </span>
                </td>
                <td>{lines(c.additions, s.lineStatsStatus)}</td>
                <td>{lines(c.deletions, s.lineStatsStatus)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className={styles.footer}>
        {s.contributors.length > INITIAL_ROWS && (
          <button type="button" onClick={() => setShowAll((v) => !v)}>
            {showAll ? 'Show fewer' : `Show all ${s.contributors.length}`}
          </button>
        )}
        {s.contributors.length > 0 && (
          <DownloadButton
            what="All listed contributors"
            onClick={() =>
              downloadCsv(
                exportFileName([...scope, 'contributors'], 'csv'),
                CSV_COLUMNS,
                s.contributors.map((c, i) => [
                  i + 1,
                  c.login,
                  c.bot,
                  c.commits,
                  c.sharePercent,
                  c.additions,
                  c.deletions,
                  c.htmlUrl,
                ]),
              )
            }
          >
            Download CSV
          </DownloadButton>
        )}
      </div>
    </Card>
  )
}

function lines(value: number | null, status: string) {
  if (value !== null) return value.toLocaleString()
  return (
    <span
      title={
        status === 'AVAILABLE'
          ? 'Not provided by GitHub (only the top 100 contributors, merge commits excluded)'
          : 'Not available'
      }
    >
      —
    </span>
  )
}
