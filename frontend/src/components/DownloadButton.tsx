import type { ReactNode } from 'react'
import styles from './DownloadButton.module.css'

interface DownloadButtonProps {
  /** Visible text, e.g. "CSV". */
  children: ReactNode
  /** What is downloaded, for screen readers, e.g. "Commits per week". */
  what: string
  onClick: () => void
  disabled?: boolean
  /** Why the button is disabled (shown as a tooltip). */
  disabledReason?: string
}

/** A small secondary button that saves data as a file. */
export function DownloadButton({
  children,
  what,
  onClick,
  disabled,
  disabledReason,
}: DownloadButtonProps) {
  return (
    <button
      type="button"
      className={styles.button}
      onClick={onClick}
      disabled={disabled}
      title={disabled ? disabledReason : undefined}
    >
      <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true" className={styles.icon}>
        <path
          d="M8 1.5v8.25m0 0L4.75 6.5M8 9.75l3.25-3.25M2.5 11v2.25c0 .41.34.75.75.75h9.5c.41 0 .75-.34.75-.75V11"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      {children}
      <span className="visually-hidden">: {what}</span>
    </button>
  )
}
