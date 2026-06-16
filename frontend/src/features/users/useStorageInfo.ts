import { useCallback, useEffect, useRef, useState } from 'react'
import { getCurrentUser } from './usersApi'
import { storageSummary, type StorageSummary } from './storageSummary'

export interface UseStorageInfo {
  /** Null while loading or after a failed fetch. */
  summary: StorageSummary | null
  hasError: boolean
  /** Re-fetches the usage, e.g. after an upload completes. */
  refresh: () => void
}

/** Loads the current user's storage usage and exposes a manual refresh. */
export function useStorageInfo(): UseStorageInfo {
  const [summary, setSummary] = useState<StorageSummary | null>(null)
  const [hasError, setHasError] = useState(false)
  const isMounted = useRef(true)

  const refresh = useCallback(() => {
    getCurrentUser()
      .then((user) => {
        if (!isMounted.current) return
        setSummary(storageSummary(user.storageUsed ?? 0, user.storageQuota ?? null))
        setHasError(false)
      })
      .catch(() => {
        if (!isMounted.current) return
        setHasError(true)
      })
  }, [])

  useEffect(() => {
    isMounted.current = true
    refresh()
    return () => {
      isMounted.current = false
    }
  }, [refresh])

  return { summary, hasError, refresh }
}
