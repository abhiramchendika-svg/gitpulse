import { lazy, Suspense } from 'react'
import { QuotaBadge } from './components/QuotaBadge'
import { RepoSearchForm } from './components/RepoSearchForm'
import { useBackendStatus } from './hooks/useBackendStatus'
import { useUrlState } from './hooks/useUrlState'
import { HomePage } from './pages/HomePage'
import { formatRepo } from './utils/parseRepoInput'
import styles from './App.module.css'

// The dashboard (and the charting library it uses) is loaded only when a repository is opened,
// so the landing page stays small.
const DashboardPage = lazy(() =>
  import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })),
)

function App() {
  const [state, update] = useUrlState()
  const repoKey = state.repo ? formatRepo(state.repo) : ''
  // Re-read the quota whenever the analysed repository or window changes.
  const { status, refresh } = useBackendStatus(`${repoKey}|${state.range}|${state.excludeBots}`)

  return (
    <div className={styles.app}>
      <header className={styles.header}>
        <a
          href="/"
          className={styles.brand}
          onClick={(e) => {
            e.preventDefault()
            update({ repo: null })
          }}
        >
          GitPulse
        </a>
        <RepoSearchForm key={repoKey} current={state.repo} onSubmit={(repo) => update({ repo })} />
        <QuotaBadge status={status} />
      </header>

      <main className={styles.main}>
        {state.repo ? (
          <Suspense fallback={<p role="status">Loading dashboard…</p>}>
            <DashboardPage
              key={repoKey.toLowerCase()}
              repo={state.repo}
              range={state.range}
              excludeBots={state.excludeBots}
              onFiltersChange={update}
            />
          </Suspense>
        ) : (
          <HomePage status={status} onRefreshStatus={refresh} onPick={(repo) => update({ repo })} />
        )}
      </main>

      <footer className={styles.footer}>
        GitPulse describes repository activity; it does not measure people. Times are in UTC.
      </footer>
    </div>
  )
}

export default App
