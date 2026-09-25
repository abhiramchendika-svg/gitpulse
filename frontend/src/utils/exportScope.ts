import { createContext, useContext } from 'react'

/**
 * What the current page is about (e.g. `['spring-projects', 'spring-petclinic']`), so a table
 * deep inside a section can name its CSV file without every section passing the name down.
 */
export const ExportScope = createContext<string[]>([])

export function useExportScope(): string[] {
  return useContext(ExportScope)
}
