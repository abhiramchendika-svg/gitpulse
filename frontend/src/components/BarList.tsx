import type { ReactNode } from 'react'
import styles from './BarList.module.css'

export interface BarListItem {
  key: string
  label: ReactNode
  value: number
  /** Text shown at the bar's end (defaults to the value). */
  valueLabel?: string
}

interface BarListProps {
  items: BarListItem[]
  /** Accessible description of what the values are, e.g. "commits". */
  unit: string
  /** Scale bars to this value (default: the largest item). Use 100 for percentages. */
  max?: number
}

/**
 * Horizontal bars for ranking a handful of named items. One hue for every bar: the categories are
 * not ordered, and bar length already encodes the value. Every value is also printed as text, so
 * nothing depends on reading the bar length alone.
 */
export function BarList({ items, unit, max }: BarListProps) {
  const scale = max ?? Math.max(1, ...items.map((i) => i.value))
  return (
    <ul className={styles.list}>
      {items.map((item) => {
        const width = Math.max(0, Math.min(100, (item.value / scale) * 100))
        const text = item.valueLabel ?? item.value.toLocaleString()
        return (
          <li key={item.key} className={styles.row}>
            <span className={styles.label}>{item.label}</span>
            <span className={styles.value}>
              {text}
              <span className="visually-hidden"> {unit}</span>
            </span>
            <span className={styles.track} aria-hidden="true">
              <span className={styles.bar} style={{ width: `${width}%` }} />
            </span>
          </li>
        )
      })}
    </ul>
  )
}
