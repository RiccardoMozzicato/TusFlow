import { useEffect, useState } from 'react'

/**
 * Returns a debounced copy of `value` that only updates after `delayMs` has
 * elapsed without further changes. Useful for deferring expensive work (e.g. a
 * server request) until the user pauses typing.
 *
 * The pending update is cancelled on each change and on unmount, so a rapid
 * sequence of changes results in a single trailing update.
 */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const handle = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(handle)
  }, [value, delayMs])

  return debounced
}
