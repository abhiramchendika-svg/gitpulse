import { useState } from 'react'
import { useAsync } from '../hooks/useAsync'
import { requestExplanation, type CommitQuery } from '../services/repositoryService'
import type { ExplanationResponse } from '../types/api'
import type { RepoRef } from '../utils/parseRepoInput'
import { Card } from './Card'
import { ErrorNotice } from './ErrorNotice'
import styles from './ExplanationSection.module.css'

interface ExplanationSectionProps {
  repo: RepoRef
  repoKey: string
  query: CommitQuery
}

/**
 * An optional AI-written summary of the dashboard's numbers. It costs money per request, so it
 * never runs on its own: not on page load, and not again when the filters change.
 */
export function ExplanationSection({ repo, repoKey, query }: ExplanationSectionProps) {
  const key = `${repoKey}|${query.since ?? ''}|${query.excludeBots}`
  // Remember which filters the user asked about; changing them shows the button again.
  const [requestedFor, setRequestedFor] = useState<string | null>(null)
  const requested = requestedFor === key
  const explanation = useAsync(requested ? key : null, repoKey, (signal) =>
    requestExplanation(repo, query, signal),
  )

  return (
    <Card
      title="Plain-English summary"
      subtitle="Written by AI from the numbers on this page, for the selected period"
      source="ai"
    >
      {!requested ? (
        <div className={styles.intro}>
          <p>
            Claude, an AI model by Anthropic, can describe this page's numbers in a few sentences.
            Only the numbers are sent: no names, commit messages or descriptions. GitPulse checks
            every number in the answer against the dashboard and leaves out any sentence that does
            not match.
          </p>
          <button type="button" onClick={() => setRequestedFor(key)}>
            Explain these numbers
          </button>
        </div>
      ) : explanation.error ? (
        <ErrorNotice error={explanation.error} onRetry={explanation.reload} />
      ) : explanation.loading || !explanation.data ? (
        <p className={styles.muted} role="status">
          Writing a summary and checking it against the numbers…
        </p>
      ) : (
        <Explanation data={explanation.data} />
      )}
    </Card>
  )
}

function Explanation({ data }: { data: ExplanationResponse }) {
  return (
    <div className={styles.result}>
      <ul className={styles.sentences}>
        {data.sentences.map((s, i) => (
          <li key={i}>
            <span>{s.text}</span>
            <span className={styles.basedOn}>
              Based on: {s.basedOn.map((f) => f.label).join(' · ')}
            </span>
          </li>
        ))}
      </ul>
      <p className={styles.muted}>
        Written by {data.model}.
        {data.sentencesRemoved > 0 &&
          ` ${data.sentencesRemoved} ${data.sentencesRemoved === 1 ? 'sentence was' : 'sentences were'} left out because ${data.sentencesRemoved === 1 ? 'it' : 'they'} did not match the numbers.`}{' '}
        It describes activity only; it is not an assessment of the project or its people.
      </p>
    </div>
  )
}
