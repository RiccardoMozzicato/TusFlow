import { useCallback, useEffect, useRef, useState } from 'react'
import { getDepartments, type DepartmentDTO } from './usersApi'

export interface UseDepartments {
  departments: DepartmentDTO[]
  isLoading: boolean
  /** Non-null when the fetch failed; suggestions silently degrade in that case. */
  error: string | null
  /** Triggers the fetch the first time it's needed (e.g. on input focus). */
  load: () => void
}

/**
 * Lazily loads the org's distinct departments for the department autocomplete.
 * The fetch runs once, the first time `load()` is called, so the network hit is
 * deferred until the user actually focuses the field. Filtering against the
 * typed query is done client-side (see filterDepartmentSuggestions).
 */
export function useDepartments(): UseDepartments {
  const [departments, setDepartments] = useState<DepartmentDTO[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Guards against re-fetching and against setting state after unmount.
  const hasLoadedRef = useRef(false)
  const isMountedRef = useRef(true)

  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
    }
  }, [])

  const load = useCallback(() => {
    if (hasLoadedRef.current) return
    hasLoadedRef.current = true

    setIsLoading(true)
    setError(null)
    getDepartments()
      .then((list) => {
        if (isMountedRef.current) setDepartments(list)
      })
      .catch((err) => {
        if (!isMountedRef.current) return
        // Allow a later retry if the first attempt failed.
        hasLoadedRef.current = false
        setError(err instanceof Error ? err.message : 'Failed to load departments')
      })
      .finally(() => {
        if (isMountedRef.current) setIsLoading(false)
      })
  }, [])

  return { departments, isLoading, error, load }
}
