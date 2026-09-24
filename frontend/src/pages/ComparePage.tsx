import { useId, useState, type FormEvent } from 'react'
import { Card } from '../components/Card'
import { WeeklyCommitsChart } from '../components/charts/WeeklyCommitsChart'
import { ErrorNotice, InfoNotice } from '../components/ErrorNotice'
import { useAsync } from '../hooks/useAsync'
import { fetchComparison } from '../services/repositoryService'
import type { ComparisonSummary } from '../types/api'
import { formatCount, formatDate, formatRelative } from '../utils/format'
import { formatRepo, parseRepoInput, type RepoRef } from '../utils/parseRepoInput'
import styles from './ComparePage.module.css'

interface ComparePageProps {
  repos: RepoRef[]
  onCompare: (repos: RepoRef[]) => void
}

interface Row {
  label: string
  hint?: string
  value: (s: ComparisonSummary) => string
}

interface RowGroup {
  title: string
  rows: Row[]
}

/** "12+" when the underlying sample did not cover the whole period (a lower bound). */
const atLeast = (value: number, partial: boolean) =>
  partial ? `${value.toLocaleString()}+` : value.toLocaleString()

/**
 * The same facts for both repositories, in the same order. Deliberately no colours for "better"
 * or "worse", no winner, and no combined score: what matters depends on why you are comparing.
 */
const GROUPS: RowGroup[] = [
  {
    title: 'Repository',
    rows: [
      { label: 'Stars', value: (s) => formatCount(s.stars) },
      { label: 'Forks', value: (s) => formatCount(s.forks) },
      {
        label: 'Contributors',
        hint: 'Accounts GitHub links to commits (up to 500)',
        value: (s) => (s.contributors === null ? 'Unavailable' : formatCount(s.contributors)),
      },
      { label: 'Age', value: (s) => `${s.ageYears} years (since ${formatDate(s.createdAt)})` },
      {
        label: 'Languages',
        value: (s) =>
          s.topLanguages.length === 0
            ? '—'
            : s.topLanguages.map((l) => `${l.name} ${l.percent}%`).join(', '),
      },
      { label: 'License', value: (s) => s.license ?? 'None detected' },
      {
        label: 'Open issues + PRs',
        hint: 'GitHub counts open pull requests as issues in this number',
        value: (s) => formatCount(s.openIssuesAndPullRequests),
      },
      { label: 'Archived', value: (s) => (s.archived ? 'Yes' : 'No') },
    ],
  },
  {
    title: 'Commits, last year',
    rows: [
      {
        label: 'Commits',
        value: (s) =>
          s.commits.truncated
            ? `${s.commits.total.toLocaleString()} since ${formatDate(s.commits.since)}`
            : s.commits.total.toLocaleString(),
      },
      { label: 'Average per week', value: (s) => s.commits.averagePerWeek.toLocaleString() },
      {
        label: 'Active weeks',
        value: (s) => `${s.commits.activeWeeks} of ${s.commits.totalWeeks}`,
      },
      { label: 'Commit authors', value: (s) => s.commits.distinctAuthors.toLocaleString() },
    ],
  },
  {
    title: 'Recent activity',
    rows: [
      {
        label: 'Last commit',
        value: (s) =>
          s.activity.lastCommitAt ? formatRelative(s.activity.lastCommitAt) : 'Over a year ago',
      },
      {
        label: 'Commits, 30 / 90 days',
        value: (s) =>
          `${atLeast(s.activity.commitsLast30Days, s.activity.commitsPartial)} / ${atLeast(s.activity.commitsLast90Days, s.activity.commitsPartial)}`,
      },
      {
        label: 'PRs opened / merged, 90 days',
        value: (s) =>
          `${atLeast(s.activity.pullRequestsOpenedLast90Days, s.activity.pullRequestsPartial)} / ${atLeast(s.activity.pullRequestsMergedLast90Days, s.activity.pullRequestsPartial)}`,
      },
      {
        label: 'Issues opened / closed, 90 days',
        value: (s) =>
          s.activity.issuesOpenedLast90Days === null || s.activity.issuesClosedLast90Days === null
            ? 'Issues disabled'
            : `${atLeast(s.activity.issuesOpenedLast90Days, s.activity.issuesPartial)} / ${atLeast(s.activity.issuesClosedLast90Days, s.activity.issuesPartial)}`,
      },
    ],
  },
]

export function ComparePage({ repos, onCompare }: ComparePageProps) {
  const ready = repos.length === 2
  const key = ready ? repos.map(formatRepo).join(',').toLowerCase() : null
  const comparison = useAsync(key, key, (signal) => fetchComparison(repos[0], repos[1], signal))

  return (
    <div className={styles.page}>
      <section>
        <h2 className={styles.title}>Compare two repositories</h2>
        <p className={styles.intro}>
          The same facts side by side. GitPulse does not pick a winner: which numbers matter depends
          on why you are comparing.
        </p>
        <CompareForm key={key ?? 'empty'} initial={repos} onSubmit={onCompare} />
      </section>

      {comparison.error && <ErrorNotice error={comparison.error} onRetry={comparison.reload} />}

      {ready && !comparison.data && !comparison.error && (
        <p className={styles.loading} role="status">
          Analysing both repositories…
        </p>
      )}

      {comparison.data && (
        <Results summaries={comparison.data.repositories} stale={comparison.loading} />
      )}
    </div>
  )
}

function Results({ summaries, stale }: { summaries: ComparisonSummary[]; stale: boolean }) {
  // One shared y-axis for both charts, otherwise their bars are not comparable.
  const yMax = Math.max(1, ...summaries.flatMap((s) => s.weekly.map((w) => w.commits)))
  const anyPartial = summaries.some(
    (s) => s.activity.commitsPartial || s.activity.pullRequestsPartial || s.activity.issuesPartial,
  )
  return (
    <div className={`${styles.results} ${stale ? styles.stale : ''}`}>
      <Card title="Side by side" source="mixed">
        <div className={styles.scroll}>
          <table className={styles.table}>
            <caption className="visually-hidden">Comparison of two repositories</caption>
            <thead>
              <tr>
                <th scope="col">
                  <span className="visually-hidden">Metric</span>
                </th>
                {summaries.map((s) => (
                  <th key={s.fullName} scope="col">
                    <a href={s.htmlUrl} target="_blank" rel="noreferrer">
                      {s.fullName}
                    </a>
                    {s.description && <span className={styles.description}>{s.description}</span>}
                  </th>
                ))}
              </tr>
            </thead>
            {GROUPS.map((group) => (
              <tbody key={group.title}>
                <tr className={styles.groupRow}>
                  <th scope="rowgroup" colSpan={summaries.length + 1}>
                    {group.title}
                  </th>
                </tr>
                {group.rows.map((row) => (
                  <tr key={row.label}>
                    <th scope="row" title={row.hint}>
                      {row.label}
                    </th>
                    {summaries.map((s) => (
                      <td key={s.fullName}>{row.value(s)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            ))}
          </table>
        </div>
        {anyPartial && (
          <p className={styles.note}>+ means at least: GitPulse did not fetch the full 90 days.</p>
        )}
      </Card>

      {summaries.some((s) => s.commits.truncated) && (
        <InfoNotice tone="warning">
          At least one repository had more commits last year than GitPulse fetches, so its numbers
          cover a shorter period (shown in the Commits row).
        </InfoNotice>
      )}

      <div className={styles.charts}>
        {summaries.map((s) => (
          <Card
            key={s.fullName}
            title={`Commits per week: ${s.fullName}`}
            subtitle="Both charts use the same scale"
            source="calculated"
          >
            <WeeklyCommitsChart weekly={s.weekly} yMax={yMax} height={180} />
          </Card>
        ))}
      </div>
    </div>
  )
}

function CompareForm({
  initial,
  onSubmit,
}: {
  initial: RepoRef[]
  onSubmit: (r: RepoRef[]) => void
}) {
  const [values, setValues] = useState<[string, string]>([
    initial[0] ? formatRepo(initial[0]) : '',
    initial[1] ? formatRepo(initial[1]) : '',
  ])
  const [error, setError] = useState<string | null>(null)
  const id = useId()

  function submit(event: FormEvent) {
    event.preventDefault()
    const parsed = values.map((v) => parseRepoInput(v))
    const failed = parsed.find((p) => !p.ok)
    if (failed && !failed.ok) {
      setError(failed.error)
      return
    }
    const refs = parsed.flatMap((p) => (p.ok ? [p.value] : []))
    if (formatRepo(refs[0]).toLowerCase() === formatRepo(refs[1]).toLowerCase()) {
      setError('Choose two different repositories.')
      return
    }
    setError(null)
    onSubmit(refs)
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      {(['First', 'Second'] as const).map((label, i) => (
        <label key={label} className={styles.field}>
          <span>{label} repository</span>
          <input
            value={values[i]}
            onChange={(e) =>
              setValues((v) => (i === 0 ? [e.target.value, v[1]] : [v[0], e.target.value]))
            }
            placeholder="owner/repo or GitHub URL"
            autoComplete="off"
            spellCheck={false}
            aria-describedby={error ? `${id}-error` : undefined}
          />
        </label>
      ))}
      <button type="submit" className={styles.submit}>
        Compare
      </button>
      {error && (
        <p id={`${id}-error`} className={styles.error} role="alert">
          {error}
        </p>
      )}
    </form>
  )
}
