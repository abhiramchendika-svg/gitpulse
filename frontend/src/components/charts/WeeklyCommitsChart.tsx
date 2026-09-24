import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatWeekLabel } from '../../utils/format'
import { ChartTable } from '../DataTable'
import styles from './charts.module.css'

interface WeeklyCommitsChartProps {
  weekly: { weekStart: string; commits: number }[]
}

/**
 * Commits per week as columns: change over time for a single series. One hue, no legend (the card
 * title names the series), recessive hairline grid, hover tooltip per column, and a table view.
 */
export function WeeklyCommitsChart({ weekly }: WeeklyCommitsChartProps) {
  const total = weekly.reduce((sum, w) => sum + w.commits, 0)
  const peak = weekly.reduce((best, w) => (w.commits > best.commits ? w : best), weekly[0])

  return (
    <figure className={styles.figure}>
      <div
        className={styles.plot}
        role="img"
        aria-label={`Column chart of commits per week over ${weekly.length} weeks, ${total} commits in total${
          peak && peak.commits > 0
            ? `; busiest week starting ${formatWeekLabel(peak.weekStart)} with ${peak.commits}`
            : ''
        }.`}
      >
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={weekly} margin={{ top: 8, right: 4, bottom: 0, left: -12 }}>
            <CartesianGrid vertical={false} stroke="var(--grid)" />
            <XAxis
              dataKey="weekStart"
              tickFormatter={(v: string) => formatWeekLabel(v)}
              tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
              tickLine={false}
              axisLine={{ stroke: 'var(--axis)' }}
              minTickGap={24}
            />
            <YAxis
              allowDecimals={false}
              tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
              tickLine={false}
              axisLine={false}
              width={44}
            />
            <Tooltip
              cursor={{ fill: 'var(--grid)', opacity: 0.5 }}
              content={(props) => <WeekTooltip active={props.active} payload={props.payload} />}
              isAnimationActive={false}
            />
            <Bar
              dataKey="commits"
              fill="var(--series-1)"
              radius={[4, 4, 0, 0]}
              maxBarSize={24}
              isAnimationActive={false}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <figcaption className="visually-hidden">
        Commits per week (weeks start on Monday, UTC)
      </figcaption>
      <ChartTable
        caption="Commits per week"
        columns={['Week starting (UTC)', 'Commits']}
        rows={weekly.map((w) => [w.weekStart, w.commits])}
      />
    </figure>
  )
}

interface WeekTooltipProps {
  active?: boolean
  payload?: ReadonlyArray<{ payload?: unknown }>
}

function WeekTooltip({ active, payload }: WeekTooltipProps) {
  if (!active || !payload?.length) return null
  const week = payload[0].payload as { weekStart: string; commits: number }
  return (
    <div className={styles.tooltip}>
      <strong>{week.commits.toLocaleString()}</strong> {week.commits === 1 ? 'commit' : 'commits'}
      <div className={styles.tooltipLabel}>Week of {formatWeekLabel(week.weekStart)}</div>
    </div>
  )
}
