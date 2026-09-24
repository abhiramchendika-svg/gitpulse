// Adds DOM matchers such as toBeInTheDocument() and toHaveTextContent() to Vitest's expect().
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Testing Library only auto-unmounts when test globals are enabled; we import from 'vitest'
// explicitly instead, so unmount rendered components after every test ourselves.
afterEach(() => {
  cleanup()
  // Dashboard state lives in the URL; start every test from a clean address.
  window.history.replaceState(null, '', '/')
})

// jsdom has no layout engine and no ResizeObserver, which Recharts' ResponsiveContainer needs.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub as unknown as typeof ResizeObserver
