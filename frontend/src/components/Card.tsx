import type { ReactNode } from 'react'
import styles from './Card.module.css'

export type Source = 'github' | 'calculated' | 'mixed'

const SOURCE_TEXT: Record<Source, string> = {
  github: 'Source: GitHub API',
  calculated: 'Calculated by GitPulse from GitHub data (formulas: docs/metrics.md)',
  mixed: 'GitHub API data; percentages calculated by GitPulse (docs/metrics.md)',
}

interface CardProps {
  title: string
  subtitle?: ReactNode
  source?: Source
  /** Dim the content while newer data loads, instead of flashing a skeleton. */
  stale?: boolean
  className?: string
  children: ReactNode
}

/** A titled panel. Every data card states where its numbers come from. */
export function Card({ title, subtitle, source, stale, className, children }: CardProps) {
  return (
    <section
      className={`${styles.card} ${className ?? ''}`}
      aria-label={title}
      aria-busy={stale || undefined}
    >
      <header className={styles.header}>
        <h3>{title}</h3>
        {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
      </header>
      <div className={stale ? styles.stale : undefined}>{children}</div>
      {source && <footer className={styles.source}>{SOURCE_TEXT[source]}</footer>}
    </section>
  )
}
