import { useId, useState, type FormEvent } from 'react'
import type { RepoRef } from '../utils/parseRepoInput'
import { parseSearchInput } from '../utils/parseSearchInput'
import styles from './RepoSearchForm.module.css'

interface RepoSearchFormProps {
  /** Text to show initially, e.g. the current "owner/repo" or login. */
  current: string
  onRepo: (repo: RepoRef) => void
  onUser: (login: string) => void
}

/**
 * Header search: a repository ("owner/repo", repository URL) or an account ("mona", "@mona",
 * profile URL). The parent gives it a `key` for the current view, so navigating with back/forward
 * remounts it with the right text instead of syncing state in an effect.
 */
export function RepoSearchForm({ current, onRepo, onUser }: RepoSearchFormProps) {
  const [value, setValue] = useState(current)
  const [error, setError] = useState<string | null>(null)
  const errorId = useId()

  function submit(event: FormEvent) {
    event.preventDefault()
    const result = parseSearchInput(value)
    if (!result.ok) {
      setError(result.error)
      return
    }
    setError(null)
    if (result.kind === 'repo') onRepo(result.repo)
    else onUser(result.login)
  }

  return (
    <form className={styles.form} onSubmit={submit} role="search" noValidate>
      <label htmlFor={`${errorId}-input`} className="visually-hidden">
        GitHub repository or user
      </label>
      <input
        id={`${errorId}-input`}
        className={styles.input}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="owner/repo, username or GitHub URL"
        autoComplete="off"
        spellCheck={false}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
      />
      <button type="submit" className={styles.button}>
        Analyse
      </button>
      {error && (
        <p id={errorId} className={styles.error} role="alert">
          {error}
        </p>
      )}
    </form>
  )
}
