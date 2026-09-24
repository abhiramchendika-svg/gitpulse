import { StatusPanel } from '../components/StatusPanel'
import { useBackendStatus } from '../hooks/useBackendStatus'
import styles from './HomePage.module.css'

export function HomePage() {
  const { status, refresh } = useBackendStatus()

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>GitPulse</h1>
        <p className={styles.tagline}>
          Factual activity analytics for public GitHub repositories: commits, contributors,
          languages, pull requests and issues.
        </p>
      </header>

      <main className={styles.main}>
        <StatusPanel status={status} onRefresh={refresh} />
      </main>
    </div>
  )
}
