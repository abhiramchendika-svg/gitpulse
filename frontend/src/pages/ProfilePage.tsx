import { BarList } from '../components/BarList'
import { Card } from '../components/Card'
import { DownloadButton } from '../components/DownloadButton'
import { ErrorNotice, InfoNotice } from '../components/ErrorNotice'
import { ExportBar } from '../components/ExportBar'
import { StatGrid, StatTile } from '../components/StatTile'
import { useAsync } from '../hooks/useAsync'
import { fetchProfile } from '../services/repositoryService'
import type { ProfileRepositoryItem, ProfileResponse } from '../types/api'
import { buildReport, downloadJson, exportFileName } from '../utils/export'
import { formatCount, formatDate, formatRelative } from '../utils/format'
import type { RepoRef } from '../utils/parseRepoInput'
import styles from './ProfilePage.module.css'

interface ProfilePageProps {
  login: string
  onOpenRepository: (repo: RepoRef) => void
}

/**
 * Public facts about a GitHub account. Descriptive only: no score, no ranking, and nothing about
 * private activity, which the API does not expose.
 */
export function ProfilePage({ login, onOpenRepository }: ProfilePageProps) {
  const key = login.toLowerCase()
  const profile = useAsync(key, key, (signal) => fetchProfile(login, signal))

  if (profile.error) {
    return <ErrorNotice error={profile.error} onRetry={profile.reload} />
  }
  if (!profile.data) {
    return (
      <p className={styles.loading} role="status">
        Loading profile…
      </p>
    )
  }
  return <Profile data={profile.data} onOpenRepository={onOpenRepository} />
}

function Profile({
  data,
  onOpenRepository,
}: {
  data: ProfileResponse
  onOpenRepository: (repo: RepoRef) => void
}) {
  const { profile: p, meta, statistics: s } = data
  const isOrganization = p.type === 'Organization'

  return (
    <div className={styles.page}>
      <ExportBar hint="Everything on this page, as exact values">
        <DownloadButton
          what={`Profile of ${p.login}`}
          onClick={() =>
            downloadJson(
              exportFileName(['user', p.login], 'json'),
              buildReport('profile', p.login, data),
            )
          }
        >
          JSON report
        </DownloadButton>
      </ExportBar>
      <Card title={isOrganization ? 'Organization' : 'Profile'} source="github">
        <div className={styles.header}>
          <img className={styles.avatar} src={p.avatarUrl} alt="" width={64} height={64} />
          <div className={styles.identity}>
            <h2 className={styles.name}>
              <a href={p.htmlUrl} target="_blank" rel="noreferrer">
                {p.name ?? p.login}
              </a>
            </h2>
            {p.name && <span className={styles.login}>@{p.login}</span>}
            {p.bio && <p className={styles.bio}>{p.bio}</p>}
            <p className={styles.facts}>
              {[
                p.company,
                p.location,
                `Joined ${formatDate(p.createdAt)}`,
                !isOrganization &&
                  `${formatCount(p.followers)} followers · ${formatCount(p.following)} following`,
              ]
                .filter(Boolean)
                .join(' · ')}
              {p.blog && (
                <>
                  {' · '}
                  <a href={safeUrl(p.blog)} target="_blank" rel="noreferrer nofollow">
                    {p.blog.replace(/^https?:\/\//, '')}
                  </a>
                </>
              )}
            </p>
          </div>
        </div>
      </Card>

      <Card
        title="Public repositories"
        subtitle={
          meta.repositoriesTruncated
            ? `The ${s.repositoriesAnalyzed} most recently pushed of ${p.publicRepos}`
            : `${s.repositoriesAnalyzed} owned repositories`
        }
        source="calculated"
      >
        <StatGrid>
          <StatTile
            label="Stars received"
            value={formatCount(s.starsReceived)}
            hint="Sum of stars on the account's own repositories (forks excluded)"
          />
          <StatTile
            label="Forks received"
            value={formatCount(s.forksReceived)}
            hint="How often the account's own repositories were forked"
          />
          <StatTile
            label="Own repositories"
            value={s.originalRepositories.toLocaleString()}
            detail={`${s.forkedRepositories} forks · ${s.archivedRepositories} archived`}
          />
          <StatTile
            label="Pushed to recently"
            value={s.pushedLast90Days.toLocaleString()}
            detail={`in 90 days · ${s.pushedLast30Days} in 30 · ${s.pushedLastYear} in a year`}
            hint="Repositories (own or forks) with a push in the period"
          />
        </StatGrid>
      </Card>

      <div className={styles.columns}>
        <Card
          title="Languages"
          subtitle="Own repositories by primary language (not lines of code)"
          source="calculated"
        >
          {s.languages.length === 0 ? (
            <p className={styles.empty}>No detected languages.</p>
          ) : (
            <BarList
              unit="repositories"
              items={s.languages.map((l) => ({
                key: l.name,
                label: l.name,
                value: l.repositories,
                valueLabel: `${l.repositories} (${l.percent}%)`,
              }))}
            />
          )}
        </Card>

        <Card title="Repositories created per year" source="calculated">
          {s.createdPerYear.length === 0 ? (
            <p className={styles.empty}>No repositories.</p>
          ) : (
            <BarList
              unit="repositories"
              items={s.createdPerYear.map((y) => ({
                key: String(y.year),
                label: String(y.year),
                value: y.repositories,
              }))}
            />
          )}
        </Card>
      </div>

      <div className={styles.columns}>
        <Card title="Most starred" subtitle="Own repositories" source="github">
          <RepositoryList items={s.mostStarred} onOpen={onOpenRepository} />
        </Card>
        <Card title="Recently pushed" source="github">
          <RepositoryList items={s.recentlyPushed} onOpen={onOpenRepository} />
        </Card>
      </div>

      {s.events ? (
        <Card
          title="Recent public activity"
          subtitle={
            s.events.count === 0
              ? 'No public events in the last 90 days'
              : `${s.events.count} public events, ${formatDate(s.events.from)} – ${formatDate(s.events.to)}`
          }
          source="calculated"
        >
          <InfoNotice>
            GitHub only keeps public events from the last 90 days (at most 300). Private
            contributions are never visible here.
          </InfoNotice>
          {s.events.count > 0 && (
            <div className={styles.eventStack}>
              <StatGrid>
                <StatTile
                  label="Active days"
                  value={s.events.activeDays}
                  detail="with a public event"
                />
                <StatTile
                  label="Repositories"
                  value={s.events.repositoriesTouched}
                  detail="with public activity"
                />
              </StatGrid>
              <div className={styles.columns}>
                <div>
                  <h4 className={styles.subheading}>By type</h4>
                  <BarList
                    unit="events"
                    items={s.events.byType.map((t) => ({
                      key: t.type,
                      label: <span title={t.type}>{t.label}</span>,
                      value: t.count,
                    }))}
                  />
                </div>
                <div>
                  <h4 className={styles.subheading}>By repository</h4>
                  <BarList
                    unit="events"
                    items={s.events.topRepositories.map((r) => ({
                      key: r.repository,
                      label: r.repository,
                      value: r.events,
                    }))}
                  />
                </div>
              </div>
            </div>
          )}
        </Card>
      ) : (
        <InfoNotice>
          Public events are not analysed for organizations: they mix the activity of many members.
        </InfoNotice>
      )}
    </div>
  )
}

function RepositoryList({
  items,
  onOpen,
}: {
  items: ProfileRepositoryItem[]
  onOpen: (repo: RepoRef) => void
}) {
  if (items.length === 0) return <p className={styles.empty}>None.</p>
  return (
    <ul className={styles.repos}>
      {items.map((r) => {
        const [owner, repo] = r.fullName.split('/')
        return (
          <li key={r.fullName}>
            <a
              href={`?repo=${encodeURIComponent(r.fullName)}`}
              className={styles.repoName}
              onClick={(e) => {
                e.preventDefault()
                onOpen({ owner, repo })
              }}
            >
              {r.fullName}
            </a>
            {r.fork && <span className={styles.tag}>fork</span>}
            {r.archived && <span className={styles.tag}>archived</span>}
            <span className={styles.meta}>
              ★ {formatCount(r.stars)} · {r.language ?? 'no language'} · pushed{' '}
              {formatRelative(r.pushedAt)}
            </span>
          </li>
        )
      })}
    </ul>
  )
}

/** Profile blog fields are free text; only link to http(s) URLs. */
function safeUrl(blog: string): string {
  const withScheme = /^https?:\/\//i.test(blog) ? blog : `https://${blog}`
  try {
    const url = new URL(withScheme)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : '#'
  } catch {
    return '#'
  }
}
