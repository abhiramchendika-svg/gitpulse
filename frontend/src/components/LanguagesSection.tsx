import type { LanguageResponse } from '../types/api'
import { BarList } from './BarList'
import { Card } from './Card'
import { ChartTable } from './DataTable'

const SHOWN = 8

export function LanguagesSection({ data }: { data: LanguageResponse }) {
  const { languages, totalBytes } = data.statistics
  const shown = languages.slice(0, SHOWN)
  const rest = languages.slice(SHOWN)
  const restPercent = Math.round(rest.reduce((s, l) => s + l.percent, 0) * 10) / 10

  return (
    <Card
      title="Languages"
      subtitle="Share of code by size in bytes, as detected by GitHub Linguist"
      source="mixed"
    >
      {languages.length === 0 ? (
        <p>GitHub detected no programming languages in this repository.</p>
      ) : (
        <>
          <BarList
            unit="percent of code"
            max={100}
            items={[
              ...shown.map((l) => ({
                key: l.name,
                label: l.name,
                value: l.percent,
                valueLabel: `${l.percent}%`,
              })),
              ...(rest.length > 0
                ? [
                    {
                      key: '__other',
                      label: `Other (${rest.length} languages)`,
                      value: restPercent,
                      valueLabel: `${restPercent}%`,
                    },
                  ]
                : []),
            ]}
          />
          <ChartTable
            caption="Languages by bytes of code"
            columns={['Language', 'Bytes', 'Percent']}
            rows={[
              ...languages.map((l) => [l.name, l.bytes.toLocaleString(), `${l.percent}%`]),
              ['Total', totalBytes.toLocaleString(), '100%'],
            ]}
          />
        </>
      )}
    </Card>
  )
}
