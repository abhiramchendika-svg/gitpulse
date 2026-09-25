import { lazy, Suspense } from 'react'
import { QuotaBadge } from './components/QuotaBadge'
import { RepoSearchForm } from './components/RepoSearchForm'
import { useBackendStatus } from './hooks/useBackendStatus'
import { useUrlState } from './hooks/useUrlState'
import { HomePage } from './pages/HomePage'
import { ProfilePage } from './pages/ProfilePage'
import { formatRepo, type RepoRef } from './utils/parseRepoInput'
import styles from './App.module.css'

// The dashboard and compare pages (and the charting library they use) load only when opened,
// so the landing page stays small.
const DashboardPage = lazy(() =>
  import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })),
)
const ComparePage = lazy(() =>
  import('./pages/ComparePage').then((m) => ({ default: m.ComparePage })),
)

function App() {
  const [state, update] = useUrlState()
  const repoKey = state.repo ? formatRepo(state.repo) : ''
  const compareKey = state.compare?.map(formatRepo).join(',') ?? ''
  // Re-read the quota whenever what is being analysed changes.
  const { status, refresh } = useBackendStatus(
    `${repoKey}|${state.range}|${state.excludeBots}|${compareKey}|${state.user ?? ''}`,
  )
  const openRepository = (repo: RepoRef) => update({ repo, compare: null, user: null })
  const openUser = (user: string) => update({ user, repo: null, compare: null })

  let page
  if (state.compare !== null) {
    page = (
      <Suspense fallback={<p role="status">Loading…</p>}>
        <ComparePage repos={state.compare} onCompare={(compare) => update({ compare })} />
      </Suspense>
    )
  } else if (state.user !== null) {
    page = (
      <ProfilePage
        key={state.user.toLowerCase()}
        login={state.user}
        onOpenRepository={openRepository}
      />
    )
  } else if (state.repo) {
    page = (
      <Suspense fallback={<p role="status">Loading dashboard…</p>}>
        <DashboardPage
          key={repoKey.toLowerCase()}
          repo={state.repo}
          range={state.range}
          excludeBots={state.excludeBots}
          onFiltersChange={update}
        />
      </Suspense>
    )
  } else {
    page = <HomePage status={status} onRefreshStatus={refresh} onPick={openRepository} />
  }

  return (
    <div className={styles.app}>
      <header className={styles.header}>
        <a
          href="/"
          className={styles.brand}
          onClick={(e) => {
            e.preventDefault()
            update({ repo: null, compare: null, user: null })
          }}
        >
          GitPulse
        </a>
        <RepoSearchForm
          key={`${repoKey}|${state.user ?? ''}|${state.compare !== null}`}
          current={state.compare !== null ? '' : (state.user ?? repoKey)}
          onRepo={openRepository}
          onUser={openUser}
        />
        <a
          href="?compare="
          className={styles.navLink}
          aria-current={state.compare !== null ? 'page' : undefined}
          onClick={(e) => {
            e.preventDefault()
            update({ compare: state.compare ?? [], user: null })
          }}
        >
          Compare
        </a>
        <QuotaBadge status={status} />
      </header>

      <main className={styles.main}>{page}</main>

      <footer className={styles.footer}>
        GitPulse describes repository activity; it does not measure people. Times are in UTC.
      </footer>
    </div>
  )
}

export default App
