import type { ReactNode } from 'react'
import type { ApiError } from '../services/apiClient'
import { describeError } from '../utils/errors'
import styles from './Notice.module.css'

interface ErrorNoticeProps {
  error: ApiError
  onRetry?: () => void
}

export function ErrorNotice({ error, onRetry }: ErrorNoticeProps) {
  return (
    <div className={`${styles.notice} ${styles.error}`} role="alert">
      <span aria-hidden="true" className={styles.icon}>
        !
      </span>
      <div>
        <p>{describeError(error)}</p>
        {onRetry && error.code !== 'REPOSITORY_NOT_FOUND' && error.code !== 'INVALID_INPUT' && (
          <button type="button" onClick={onRetry}>
            Try again
          </button>
        )}
      </div>
    </div>
  )
}

interface InfoNoticeProps {
  children: ReactNode
  tone?: 'info' | 'warning'
}

/** Neutral or cautionary note. Always an icon + text, never colour alone. */
export function InfoNotice({ children, tone = 'info' }: InfoNoticeProps) {
  return (
    <div className={`${styles.notice} ${tone === 'warning' ? styles.warning : styles.info}`}>
      <span aria-hidden="true" className={styles.icon}>
        {tone === 'warning' ? '!' : 'i'}
      </span>
      <div>{children}</div>
    </div>
  )
}
