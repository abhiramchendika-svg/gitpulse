import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatWeekLabel } from '../../utils/format'
import { ChartTable } from '../DataTable'
import styles from './charts.module.css'

export interface WeeklyPairPoint {
  weekStart: string
  first: number
  second: number
}

interface WeeklyPairChartProps {
  data: WeeklyPairPoint[]
  /** Series names, e.g. ["Opened", "Merged"]. Slot 1 is blue, slot 2 orange (validated pair). */
  labels: [string, string]
  caption: string
}

const COLORS = ['var(--series-1)', 'var(--series-2)'] as const

/**
 * Two weekly series (e.g. opened vs merged) as 2px lines on one axis. A legend is always shown
 * (identity never relies on colour matching alone), a crosshair tooltip lists both values at the
 * hovered week, and the table view has every number.
 */
export function WeeklyPairChart({ data, labels, caption }: WeeklyPairChartProps) {
  const totals = [data.reduce((s, d) => s + d.first, 0), data.reduce((s, d) => s + d.second, 0)]
  return (
    <figure className={styles.figure}>
      <ul className={styles.legend} aria-label="Legend">
        {labels.map((label, i) => (
          <li key={label}>
            <span
              className={styles.legendLine}
              style={{ background: COLORS[i] }}
              aria-hidden="true"
            />
            {label} <span className={styles.legendTotal}>({totals[i].toLocaleString()})</span>
          </li>
        ))}
      </ul>
      <div
        className={styles.plot}
        role="img"
        aria-label={`${caption}: ${labels[0]} ${totals[0]} and ${labels[1]} ${totals[1]} over ${data.length} weeks.`}
      >
        <ResponsiveContainer width="100%" height={200}>
          <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -12 }}>
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
              cursor={{ stroke: 'var(--axis)', strokeWidth: 1 }}
              content={(props) => (
                <PairTooltip active={props.active} payload={props.payload} labels={labels} />
              )}
              isAnimationActive={false}
            />
            {(['first', 'second'] as const).map((key, i) => (
              <Line
                key={key}
                dataKey={key}
                name={labels[i]}
                stroke={COLORS[i]}
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
                dot={false}
                activeDot={{ r: 4, strokeWidth: 2, stroke: 'var(--surface)' }}
                isAnimationActive={false}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
      <ChartTable
        caption={caption}
        columns={['Week starting (UTC)', labels[0], labels[1]]}
        rows={data.map((d) => [d.weekStart, d.first, d.second])}
      />
    </figure>
  )
}

interface PairTooltipProps {
  active?: boolean
  payload?: ReadonlyArray<{ payload?: unknown }>
  labels: [string, string]
}

function PairTooltip({ active, payload, labels }: PairTooltipProps) {
  if (!active || !payload?.length) return null
  const point = payload[0].payload as WeeklyPairPoint
  return (
    <div className={styles.tooltip}>
      <div className={styles.tooltipLabel}>Week of {formatWeekLabel(point.weekStart)}</div>
      {[point.first, point.second].map((value, i) => (
        <div key={labels[i]} className={styles.tooltipRow}>
          <span
            className={styles.legendLine}
            style={{ background: COLORS[i] }}
            aria-hidden="true"
          />
          <strong>{value}</strong> {labels[i].toLowerCase()}
        </div>
      ))}
    </div>
  )
}
