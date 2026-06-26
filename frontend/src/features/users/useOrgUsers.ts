import { useCallback, useEffect, useRef, useState } from 'react'
import { getUsers, type UserDTO } from './usersApi'

export interface UseOrgUsers {
  users: UserDTO[]
  isLoading: boolean
  /** Non-null when the fetch failed; suggestions silently degrade in that case. */
  error: string | null
  /** Triggers the fetch the first time it's needed (e.g. on input focus). */
  load: () => void
}

/**
 * Lazily loads the org's users for share suggestions. The fetch runs once, the
 * first time `load()` is called, so the network hit is deferred until the user
 * actually interacts with the share input.
 */
export function useOrgUsers(): UseOrgUsers {
  const [users, setUsers] = useState<UserDTO[]>([])
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
    getUsers()
      .then((page) => {
        if (isMountedRef.current) setUsers(page.content)
      })
      .catch((err) => {
        if (!isMountedRef.current) return
        // Allow a later retry if the first attempt failed.
        hasLoadedRef.current = false
        setError(err instanceof Error ? err.message : 'Failed to load users')
      })
      .finally(() => {
        if (isMountedRef.current) setIsLoading(false)
      })
  }, [])

  return { users, isLoading, error, load }
}
