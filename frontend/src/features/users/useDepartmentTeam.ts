import { useCallback, useEffect, useState } from 'react'
import { fetchAllUsers } from './usersApi'
import { computeDepartmentTeam, type DepartmentTeam } from './departmentTeam'

type Status = 'loading' | 'succeeded' | 'failed'

export interface UseDepartmentTeam {
  team: DepartmentTeam | null
  status: Status
  error: string | null
  reload: () => void
}

/**
 * Loads the members of the given user's department by walking the (org-scoped)
 * user list and filtering client-side. The department is resolved from the
 * authoritative user record in that list (matched by id) rather than the
 * gateway userinfo claim, which may be empty. Resolves to a null team when the
 * user has no department set.
 */
export function useDepartmentTeam(userId: string | null | undefined): UseDepartmentTeam {
  const [team, setTeam] = useState<DepartmentTeam | null>(null)
  const [status, setStatus] = useState<Status>('loading')
  const [error, setError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    if (!userId) {
      setTeam(null)
      setStatus('succeeded')
      setError(null)
      return
    }

    let isActive = true
    setStatus('loading')
    setError(null)

    fetchAllUsers()
      .then((users) => {
        if (!isActive) return
        const me = users.find((u) => u.id === userId)
        const department = me?.department?.trim()
        setTeam(department ? computeDepartmentTeam(users, department) : null)
        setStatus('succeeded')
      })
      .catch((err) => {
        if (!isActive) return
        setError(err instanceof Error ? err.message : 'Failed to load the department')
        setStatus('failed')
      })

    return () => {
      isActive = false
    }
  }, [userId, reloadToken])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  return { team, status, error, reload }
}
