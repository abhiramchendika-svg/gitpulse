import type { BackendStatus } from '../hooks/useBackendStatus'
import { formatTime } from '../utils/format'
import styles from './QuotaBadge.module.css'

/** Compact header indicator: is the backend up, and how much GitHub quota is left? */
export function QuotaBadge({ status }: { status: BackendStatus }) {
  if (status.state === 'loading') {
    return <span className={styles.badge}>Checking…</span>
  }
  if (status.state === 'down') {
    return (
      <span className={`${styles.badge} ${styles.down}`} title={status.message}>
        <span aria-hidden="true">●</span> Backend offline
      </span>
    )
  }
  const core = status.rateLimit?.core
  if (!core) {
    return <span className={styles.badge}>GitHub quota unknown</span>
  }
  const low = core.remaining < core.limit * 0.1
  return (
    <span
      className={`${styles.badge} ${low ? styles.low : ''}`}
      title={`${status.rateLimit?.authenticated ? 'Using a GitHub token' : 'Anonymous access'}; resets at ${formatTime(core.resetAt)}`}
    >
      GitHub quota {core.remaining.toLocaleString()}/{core.limit.toLocaleString()}
      {low && ' (low)'}
    </span>
  )
}
