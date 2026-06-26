import { useEffect, useState } from 'react'
import { getCurrentUser } from '@/features/users/usersApi'
import { getFiles, Ownership } from '@/features/documents/documentsApi'
import { storageSummary, type StorageSummary } from '@/features/users/storageSummary'

type Status = 'loading' | 'succeeded' | 'failed'

export interface WorkspaceStats {
  /** Number of documents the user owns (the file listing's total). */
  documentCount: number
  /** Storage usage view model (used / quota / fraction). */
  storage: StorageSummary
}

export interface UseWorkspaceStats {
  stats: WorkspaceStats | null
  status: Status
  error: string | null
}

/**
 * Loads the staff "My Workspace" tiles from live data: the owned-document count
 * (the file listing's `totalElements`, scoped to files the caller owns) and the
 * user's storage usage (used / quota bytes from GET /api/v1/users/me). Both
 * requests run in parallel; either failing surfaces a single error.
 */
export function useWorkspaceStats(): UseWorkspaceStats {
  const [stats, setStats] = useState<WorkspaceStats | null>(null)
  const [status, setStatus] = useState<Status>('loading')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let isActive = true
    setStatus('loading')
    setError(null)

    Promise.all([
      getCurrentUser(),
      getFiles({ size: 1, ownership: Ownership.OWNED }),
    ])
      .then(([user, page]) => {
        if (!isActive) return
        setStats({
          documentCount: page.totalElements,
          storage: storageSummary(user.storageUsed ?? 0, user.storageQuota ?? null),
        })
        setStatus('succeeded')
      })
      .catch((err) => {
        if (!isActive) return
        setError(err instanceof Error ? err.message : 'Failed to load workspace stats')
        setStatus('failed')
      })

    return () => {
      isActive = false
    }
  }, [])

  return { stats, status, error }
}