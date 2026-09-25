import type { ReactNode } from 'react'
import styles from './ExportBar.module.css'

interface ExportBarProps {
  /** One line saying what the export contains. */
  hint: string
  className?: string
  children: ReactNode
}

/** A right-aligned row of download buttons for a whole page. */
export function ExportBar({ hint, className, children }: ExportBarProps) {
  return (
    <div className={`${styles.bar} ${className ?? ''}`}>
      <span>{hint}</span>
      {children}
    </div>
  )
}
