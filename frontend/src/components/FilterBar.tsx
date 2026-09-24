import { RANGES, type Range } from '../hooks/useUrlState'
import styles from './FilterBar.module.css'

const RANGE_LABELS: Record<Range, string> = {
  '30d': 'Last 30 days',
  '90d': 'Last 90 days',
  '1y': 'Last year',
}

interface FilterBarProps {
  range: Range
  excludeBots: boolean
  onChange: (next: { range?: Range; excludeBots?: boolean }) => void
}

/** One filter row above the commit charts; every commit card re-renders against the same slice. */
export function FilterBar({ range, excludeBots, onChange }: FilterBarProps) {
  return (
    <div className={styles.bar}>
      <fieldset className={styles.segmented}>
        <legend className="visually-hidden">Time range</legend>
        {RANGES.map((r) => (
          <label key={r} className={r === range ? styles.selected : undefined}>
            <input
              type="radio"
              name="range"
              value={r}
              checked={r === range}
              onChange={() => onChange({ range: r })}
            />
            {RANGE_LABELS[r]}
          </label>
        ))}
      </fieldset>
      <label className={styles.toggle}>
        <input
          type="checkbox"
          checked={excludeBots}
          onChange={(e) => onChange({ excludeBots: e.target.checked })}
        />
        Exclude bots
      </label>
    </div>
  )
}
