import type { BackendStatus } from '../hooks/useBackendStatus'
import type { Quota } from '../types/api'
import { formatTime, remainingPercent } from '../utils/format'
import styles from './StatusPanel.module.css'

interface StatusPanelProps {
  status: BackendStatus
  onRefresh: () => void
}

export function StatusPanel({ status, onRefresh }: StatusPanelProps) {
  return (
    <section className={styles.panel} aria-labelledby="status-heading">
      <div className={styles.header}>
        <h2 id="status-heading">System status</h2>
        <button type="button" onClick={onRefresh} disabled={status.state === 'loading'}>
          Refresh
        </button>
      </div>

      {status.state === 'loading' && <p role="status">Checking backend…</p>}

      {status.state === 'down' && (
        <p className={styles.error} role="alert">
          Backend unavailable: {status.message}
        </p>
      )}

      {status.state === 'up' && (
        <dl className={styles.grid}>
          <dt>Backend</dt>
          <dd>
            <span className={styles.ok}>●</span> Up
          </dd>

          <dt>GitHub access</dt>
          <dd>
            {status.rateLimit
              ? status.rateLimit.authenticated
                ? 'Token configured'
                : 'Anonymous (60 requests/hour). Set GITHUB_TOKEN for 5,000/hour.'
              : '—'}
          </dd>

          {status.rateLimit && (
            <>
              <dt>Core API quota</dt>
              <dd>
                <QuotaMeter label="Core API quota" quota={status.rateLimit.core} />
              </dd>
              {status.rateLimit.search && (
                <>
                  <dt>Search API quota</dt>
                  <dd>
                    <QuotaMeter label="Search API quota" quota={status.rateLimit.search} />
                  </dd>
                </>
              )}
            </>
          )}

          {status.rateLimitError && (
            <>
              <dt>GitHub</dt>
              <dd className={styles.error} role="alert">
                {status.rateLimitError}
              </dd>
            </>
          )}
        </dl>
      )}
    </section>
  )
}

function QuotaMeter({ label, quota }: { label: string; quota: Quota }) {
  const percent = remainingPercent(quota.remaining, quota.limit)
  return (
    <div className={styles.quota}>
      <meter
        aria-label={label}
        min={0}
        max={quota.limit}
        low={quota.limit * 0.2}
        optimum={quota.limit}
        value={quota.remaining}
      />
      <span>
        {quota.remaining} / {quota.limit} left ({percent}%) · resets {formatTime(quota.resetAt)}
      </span>
    </div>
  )
}
