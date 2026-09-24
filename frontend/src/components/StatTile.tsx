import type { ReactNode } from 'react'
import styles from './StatTile.module.css'

interface StatTileProps {
  label: string
  value: ReactNode
  /** Small secondary line, e.g. "of 53 weeks". */
  detail?: ReactNode
  /** Full explanation shown on hover/focus: what the number is and where it comes from. */
  hint?: string
}

/** A single number with a label. Used instead of a chart when the story is one value. */
export function StatTile({ label, value, detail, hint }: StatTileProps) {
  return (
    <div className={styles.tile} title={hint}>
      <dt className={styles.label}>{label}</dt>
      <dd className={styles.value}>{value}</dd>
      {detail && <dd className={styles.detail}>{detail}</dd>}
    </div>
  )
}

export function StatGrid({ children }: { children: ReactNode }) {
  return <dl className={styles.grid}>{children}</dl>
}
