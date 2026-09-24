import type { ReactNode } from 'react'
import styles from './DataTable.module.css'

interface DataTableProps {
  caption: string
  columns: string[]
  rows: (string | number)[][]
}

/**
 * The table view of a chart: the same numbers without needing to read colours or bar lengths.
 * Collapsed by default so it doesn't crowd the dashboard.
 */
export function ChartTable({ caption, columns, rows }: DataTableProps): ReactNode {
  return (
    <details className={styles.details}>
      <summary>Show data as table</summary>
      <div className={styles.scroll}>
        <table className={styles.table}>
          <caption className="visually-hidden">{caption}</caption>
          <thead>
            <tr>
              {columns.map((c) => (
                <th key={c} scope="col">
                  {c}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, i) => (
              <tr key={i}>
                {row.map((cell, j) => (
                  <td key={j}>{cell}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  )
}
