import { StatusPanel } from '../components/StatusPanel'
import type { BackendStatus } from '../hooks/useBackendStatus'
import type { RepoRef } from '../utils/parseRepoInput'
import styles from './HomePage.module.css'

/** Small, well-known repositories: cheap to analyse even without a GitHub token. */
const EXAMPLES: RepoRef[] = [
  { owner: 'spring-projects', repo: 'spring-petclinic' },
  { owner: 'octocat', repo: 'Hello-World' },
  { owner: 'expressjs', repo: 'express' },
]

interface HomePageProps {
  status: BackendStatus
  onRefreshStatus: () => void
  onPick: (repo: RepoRef) => void
}

/** Landing view shown before a repository is chosen. */
export function HomePage({ status, onRefreshStatus, onPick }: HomePageProps) {
  return (
    <div className={styles.page}>
      <section className={styles.intro}>
        <h2>Understand a repository&apos;s activity at a glance</h2>
        <p>
          Enter any public GitHub repository above. GitPulse reports commit activity, contributors
          and languages as facts (counts, distributions and gaps), not judgements about people.
        </p>
        <div className={styles.examples}>
          <span>Try:</span>
          {EXAMPLES.map((r) => (
            <button key={`${r.owner}/${r.repo}`} type="button" onClick={() => onPick(r)}>
              {r.owner}/{r.repo}
            </button>
          ))}
        </div>
      </section>
      <StatusPanel status={status} onRefresh={onRefreshStatus} />
    </div>
  )
}
