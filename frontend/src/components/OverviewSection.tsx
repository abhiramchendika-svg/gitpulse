import type { RepositoryOverview } from '../types/api'
import { formatCount, formatDate, formatExact, formatRelative, formatSizeKb } from '../utils/format'
import { Card } from './Card'
import styles from './OverviewSection.module.css'
import { StatGrid, StatTile } from './StatTile'

export function OverviewSection({ repo }: { repo: RepositoryOverview }) {
  return (
    <Card title="Repository overview" source="github">
      <div className={styles.heading}>
        {repo.owner?.avatarUrl && (
          <img className={styles.avatar} src={repo.owner.avatarUrl} alt="" width={40} height={40} />
        )}
        <div className={styles.titleBlock}>
          <h2 className={styles.name}>
            <a href={repo.htmlUrl} target="_blank" rel="noreferrer">
              {repo.fullName}
            </a>
          </h2>
          <div className={styles.badges}>
            {repo.archived && <span className={styles.badge}>Archived</span>}
            {repo.fork && <span className={styles.badge}>Fork</span>}
          </div>
        </div>
      </div>
      {repo.description && <p className={styles.description}>{repo.description}</p>}
      {repo.topics.length > 0 && (
        <ul className={styles.topics} aria-label="Topics">
          {repo.topics.map((t) => (
            <li key={t}>{t}</li>
          ))}
        </ul>
      )}

      <StatGrid>
        <StatTile label="Stars" value={formatCount(repo.stars)} hint={formatExact(repo.stars)} />
        <StatTile label="Forks" value={formatCount(repo.forks)} hint={formatExact(repo.forks)} />
        <StatTile
          label="Watchers"
          value={formatCount(repo.watchers)}
          hint="Accounts watching the repository for notifications"
        />
        <StatTile
          label="Open issues + PRs"
          value={formatCount(repo.openIssuesAndPullRequests)}
          hint="GitHub counts open pull requests together with open issues in this number"
        />
      </StatGrid>

      <dl className={styles.facts}>
        <div>
          <dt>Primary language</dt>
          <dd>{repo.primaryLanguage ?? '—'}</dd>
        </div>
        <div>
          <dt>License</dt>
          <dd>{repo.license ? (repo.license.spdxId ?? repo.license.name) : 'None detected'}</dd>
        </div>
        <div>
          <dt>Created</dt>
          <dd>{formatDate(repo.createdAt)}</dd>
        </div>
        <div>
          <dt>Last push</dt>
          <dd title={formatDate(repo.pushedAt)}>{formatRelative(repo.pushedAt)}</dd>
        </div>
        <div>
          <dt>Size</dt>
          <dd>{formatSizeKb(repo.sizeKb)}</dd>
        </div>
        <div>
          <dt>Default branch</dt>
          <dd>
            <code>{repo.defaultBranch}</code>
          </dd>
        </div>
      </dl>
    </Card>
  )
}
