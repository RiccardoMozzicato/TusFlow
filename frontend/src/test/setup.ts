// Vitest setup: registers jest-dom matchers (toBeInTheDocument, toBeDisabled, …)
// and augments Vitest's `expect` types. Auto-loaded via vite.config `setupFiles`.
import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

afterEach(() => {
  cleanup()
})
