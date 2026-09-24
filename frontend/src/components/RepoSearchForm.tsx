import { useId, useState, type FormEvent } from 'react'
import { formatRepo, parseRepoInput, type RepoRef } from '../utils/parseRepoInput'
import styles from './RepoSearchForm.module.css'

interface RepoSearchFormProps {
  current: RepoRef | null
  onSubmit: (repo: RepoRef) => void
}

/**
 * Search box. The parent gives it `key={current repo}`, so navigating with back/forward remounts
 * it with the right text instead of syncing state in an effect.
 */
export function RepoSearchForm({ current, onSubmit }: RepoSearchFormProps) {
  const [value, setValue] = useState(current ? formatRepo(current) : '')
  const [error, setError] = useState<string | null>(null)
  const errorId = useId()

  function submit(event: FormEvent) {
    event.preventDefault()
    const result = parseRepoInput(value)
    if (!result.ok) {
      setError(result.error)
      return
    }
    setError(null)
    onSubmit(result.value)
  }

  return (
    <form className={styles.form} onSubmit={submit} role="search" noValidate>
      <label htmlFor={`${errorId}-input`} className="visually-hidden">
        GitHub repository
      </label>
      <input
        id={`${errorId}-input`}
        className={styles.input}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="owner/repo or GitHub URL"
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
