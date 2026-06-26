import { useCallback, useEffect, useState } from 'react'
import { fetchAllUsers, type UserDTO } from './usersApi'
import { computeOrgStats, type OrgStats } from './orgStats'

type Status = 'loading' | 'succeeded' | 'failed'

export interface UseOrgStats {
  stats: OrgStats | null
  /** The org's users, as fetched for the aggregation (drives the detail lists). */
  users: UserDTO[]
  status: Status
  error: string | null
  reload: () => void
}

/**
 * Walks the (organization-scoped) user list and derives aggregate stats for the
 * admin overview. The backend has no aggregate endpoint, so this pages through
 * the list once and computes the numbers client-side.
 */
export function useOrgStats(): UseOrgStats {
  const [stats, setStats] = useState<OrgStats | null>(null)
  const [users, setUsers] = useState<UserDTO[]>([])
  const [status, setStatus] = useState<Status>('loading')
  const [error, setError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    let isActive = true
    setStatus('loading')
    setError(null)

    fetchAllUsers()
      .then((loaded) => {
        if (!isActive) return
        setUsers(loaded)
        setStats(computeOrgStats(loaded))
        setStatus('succeeded')
      })
      .catch((err) => {
        if (!isActive) return
        setError(err instanceof Error ? err.message : 'Failed to load statistics')
        setStatus('failed')
      })

    return () => {
      isActive = false
    }
  }, [reloadToken])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  return { stats, users, status, error, reload }
}
