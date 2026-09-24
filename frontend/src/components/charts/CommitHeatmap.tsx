import { useState } from 'react'
import { ChartTable } from '../DataTable'
import styles from './charts.module.css'
import { HEAT_LEVELS, heatLevel } from './heatLevel'

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

interface CommitHeatmapProps {
  /** heatmap[day][hour], day 0 = Monday, hours in UTC. */
  heatmap: number[][]
}

/**
 * Day-of-week x hour-of-day grid. Magnitude uses a single-hue sequential ramp (light -> dark),
 * with a scale legend. Hovering a cell shows its exact value in a readout; the table view lists
 * every value.
 */
export function CommitHeatmap({ heatmap }: CommitHeatmapProps) {
  const [hovered, setHovered] = useState<{ day: number; hour: number } | null>(null)
  const max = Math.max(0, ...heatmap.flat())
  const busiest = findBusiest(heatmap)

  const readout = hovered
    ? `${DAYS[hovered.day]} ${pad(hovered.hour)}:00–${pad(hovered.hour)}:59 UTC: ${heatmap[hovered.day][hovered.hour]} commits`
    : busiest
      ? `Busiest: ${DAYS[busiest.day]} ${pad(busiest.hour)}:00 UTC (${busiest.count} commits)`
      : 'No commits in this window'

  return (
    <figure className={styles.figure}>
      <p className={styles.readout} aria-live="polite">
        {readout}
      </p>
      <div
        className={styles.heatmap}
        role="img"
        aria-label={`Heatmap of commits by weekday and UTC hour. ${
          busiest
            ? `Busiest slot: ${DAYS[busiest.day]} ${pad(busiest.hour)}:00 UTC with ${busiest.count} commits.`
            : 'No commits.'
        }`}
        onMouseLeave={() => setHovered(null)}
      >
        {heatmap.map((row, day) => (
          <div key={DAYS[day]} className={styles.heatRow}>
            <span className={styles.heatDay}>{DAYS[day]}</span>
            {row.map((count, hour) => (
              <span
                key={hour}
                className={`${styles.heatCell} ${
                  hovered?.day === day && hovered.hour === hour ? styles.heatCellActive : ''
                }`}
                style={{ background: `var(--heat-${heatLevel(count, max)})` }}
                onMouseEnter={() => setHovered({ day, hour })}
              />
            ))}
          </div>
        ))}
        <div className={styles.heatRow} aria-hidden="true">
          <span className={styles.heatDay} />
          {Array.from({ length: 24 }, (_, hour) => (
            <span key={hour} className={styles.heatHour}>
              {hour % 6 === 0 ? pad(hour) : ''}
            </span>
          ))}
        </div>
      </div>
      <div className={styles.heatLegend} aria-hidden="true">
        {/* The zero swatch is separated: "no commits" is a category, not the low end of the ramp. */}
        <span className={styles.heatSwatch} style={{ background: 'var(--heat-0)' }} />
        <span>0</span>
        <span className={styles.heatLegendGap} />
        <span>1</span>
        {Array.from({ length: HEAT_LEVELS }, (_, i) => (
          <span
            key={i}
            className={styles.heatSwatch}
            style={{ background: `var(--heat-${i + 1})` }}
          />
        ))}
        <span>{max} commits</span>
      </div>
      <ChartTable
        caption="Commits by weekday and UTC hour"
        columns={['Day', ...Array.from({ length: 24 }, (_, h) => pad(h))]}
        rows={heatmap.map((row, day) => [DAYS[day], ...row])}
      />
    </figure>
  )
}

function findBusiest(heatmap: number[][]) {
  let best: { day: number; hour: number; count: number } | null = null
  heatmap.forEach((row, day) =>
    row.forEach((count, hour) => {
      if (count > 0 && (!best || count > best.count)) best = { day, hour, count }
    }),
  )
  return best as { day: number; hour: number; count: number } | null
}

function pad(n: number): string {
  return n.toString().padStart(2, '0')
}
