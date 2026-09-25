import type { AsyncState } from '../hooks/useAsync'
import type { FileActivityResponse, FileStat } from '../types/api'
import { formatDate, formatRelative } from '../utils/format'
import { BarList } from './BarList'
import { Card } from './Card'
import { ErrorNotice, InfoNotice } from './ErrorNotice'
import styles from './FileActivitySection.module.css'

interface FileActivitySectionProps {
  /** Whether the user has asked for the analysis (the request is only made then). */
  requested: boolean
  onRequest: () => void
  files: AsyncState<FileActivityResponse>
}

/**
 * Most-changed files. Unlike the rest of the dashboard this costs one GitHub request per
 * analysed commit, so it only runs when the user asks, and the cost is stated up front.
 */
export function FileActivitySection({ requested, onRequest, files }: FileActivitySectionProps) {
  if (!requested) {
    return (
      <Card title="File activity" subtitle="Which files and directories change most often">
        <p className={styles.cost}>
          This needs one GitHub request per commit analysed: the most recent 20 commits without a
          GitHub token, 100 with one. Results are cached for 24 hours.
        </p>
        <button type="button" className={styles.start} onClick={onRequest}>
          Analyse recent commits
        </button>
      </Card>
    )
  }

  if (files.error) {
    return (
      <Card title="File activity">
        <ErrorNotice error={files.error} onRetry={files.reload} />
      </Card>
    )
  }

  if (!files.data) {
    return (
      <Card title="File activity">
        <p className={styles.cost} role="status">
          Fetching changed files for recent commits…
        </p>
      </Card>
    )
  }

  const { meta, statistics: s } = files.data
  const limited = meta.requestedSample > meta.sampleLimit

  return (
    <Card
      title="File activity"
      subtitle={
        s.commitsAnalyzed === 0
          ? 'No commits in the last year to analyse'
          : `The ${s.commitsAnalyzed} most recent commits (merges excluded), ${formatDate(s.sampleFrom)} – ${formatDate(s.sampleTo)}`
      }
      source="calculated"
    >
      <div className={styles.stack}>
        {!meta.authenticated && (
          <InfoNotice>
            Limited to {meta.sampleLimit} commits because the backend has no GitHub token.
            {limited && ` (${meta.requestedSample} were requested.)`}
          </InfoNotice>
        )}
        {s.commitsWithTruncatedFiles > 0 && (
          <InfoNotice tone="warning">
            {s.commitsWithTruncatedFiles} commit(s) changed more than 300 files; GitHub lists only
            the first 300, so their counts are lower bounds.
          </InfoNotice>
        )}

        {s.commitsAnalyzed > 0 && (
          <div className={styles.columns}>
            <div>
              <h4 className={styles.subheading}>Changed in the most commits</h4>
              <BarList
                unit="commits"
                items={s.mostFrequentlyChanged.slice(0, 8).map((f) => ({
                  key: f.path,
                  label: <FileLabel file={f} />,
                  value: f.commits,
                  valueLabel: `${f.commits} · ${f.distinctAuthors} ${f.distinctAuthors === 1 ? 'author' : 'authors'}`,
                }))}
              />
            </div>
            <div>
              <h4 className={styles.subheading}>Most lines changed (churn)</h4>
              <BarList
                unit="lines changed"
                items={s.highestChurn.slice(0, 8).map((f) => ({
                  key: f.path,
                  label: <FileLabel file={f} />,
                  value: f.churn,
                  valueLabel: `+${f.additions.toLocaleString()} −${f.deletions.toLocaleString()}`,
                }))}
              />
            </div>
            <div>
              <h4 className={styles.subheading}>Busiest directories</h4>
              <BarList
                unit="commits"
                items={s.directories.slice(0, 8).map((d) => ({
                  key: d.path || '/',
                  label: (
                    <span title={d.path || 'repository root'}>{d.path || '(repository root)'}</span>
                  ),
                  value: d.commits,
                  valueLabel: `${d.commits} commits · ${d.filesTouched} files`,
                }))}
              />
            </div>
            <div>
              <h4 className={styles.subheading}>Recently changed</h4>
              <ul className={styles.recent}>
                {s.recentlyChanged.slice(0, 8).map((f) => (
                  <li key={f.path}>
                    <FileLabel file={f} />
                    <span className={styles.when}>{formatRelative(f.lastChangedAt)}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}
      </div>
    </Card>
  )
}

/** File name first (it is what people recognise), directory de-emphasised, full path on hover. */
function FileLabel({ file }: { file: FileStat }) {
  const slash = file.path.lastIndexOf('/')
  const name = file.path.slice(slash + 1)
  const dir = slash < 0 ? '' : file.path.slice(0, slash)
  return (
    <span className={styles.file} title={file.path}>
      <span className={file.deleted ? styles.deleted : undefined}>{name}</span>
      {file.deleted && <span className={styles.tag}>deleted</span>}
      {dir && <span className={styles.dir}>{dir}</span>}
    </span>
  )
}
