import { useCallback, useEffect, useState } from 'react'
import { isAxiosError } from 'axios'
import {
  createUser,
  deleteUser,
  getUsers,
  updateUser,
  type UserCreationPayload,
  type UserDTO,
} from './usersApi'

const PAGE_SIZE = 10

type Status = 'loading' | 'succeeded' | 'failed'

export interface UsersPageInfo {
  page: number
  totalElements: number
  totalPages: number
  isFirst: boolean
  isLast: boolean
}

export interface UseUserManagement {
  users: UserDTO[]
  status: Status
  /** Non-null when the list fetch failed. */
  error: string | null
  pageInfo: UsersPageInfo
  goToPage: (page: number) => void
  reload: () => void
  /** True while a create/update/delete request is in flight. */
  isSaving: boolean
  /** Non-null when the last mutation failed; cleared on the next attempt. */
  saveError: string | null
  clearSaveError: () => void
  /** Each resolves true on success (caller closes the dialog), false on failure. */
  create: (payload: UserCreationPayload) => Promise<boolean>
  update: (user: UserDTO) => Promise<boolean>
  remove: (id: string) => Promise<boolean>
}

const EMPTY_PAGE_INFO: UsersPageInfo = {
  page: 0,
  totalElements: 0,
  totalPages: 0,
  isFirst: true,
  isLast: true,
}

// Surfaces the backend's ProblemDetail/message when present; the generic axios
// "Request failed with status code 4xx" is rarely useful to an admin.
function errorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { detail?: string; message?: string } | undefined
    return data?.detail ?? data?.message ?? err.message
  }
  return err instanceof Error ? err.message : fallback
}

/**
 * Loads the org's users one server page at a time and exposes create/update/
 * delete mutations. Mutations refresh the current page on success so the table
 * reflects the change without a manual reload.
 */
export function useUserManagement(): UseUserManagement {
  const [page, setPage] = useState(0)
  const [reloadToken, setReloadToken] = useState(0)
  const [users, setUsers] = useState<UserDTO[]>([])
  const [pageInfo, setPageInfo] = useState<UsersPageInfo>(EMPTY_PAGE_INFO)
  const [status, setStatus] = useState<Status>('loading')
  const [error, setError] = useState<string | null>(null)

  const [isSaving, setIsSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  useEffect(() => {
    let isActive = true
    setStatus('loading')
    setError(null)

    getUsers(page, PAGE_SIZE)
      .then((response) => {
        if (!isActive) return
        setUsers(response.content)
        setPageInfo({
          page: response.number,
          totalElements: response.totalElements,
          totalPages: response.totalPages,
          isFirst: response.first,
          isLast: response.last,
        })
        setStatus('succeeded')
      })
      .catch((err) => {
        if (!isActive) return
        setError(errorMessage(err, 'Failed to load users'))
        setStatus('failed')
      })

    return () => {
      isActive = false
    }
  }, [page, reloadToken])

  const goToPage = useCallback((next: number) => {
    setPage((current) => (next < 0 || next === current ? current : next))
  }, [])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])
  const clearSaveError = useCallback(() => setSaveError(null), [])

  // Runs a mutation with shared pending/error handling; refreshes on success.
  const runMutation = useCallback(
    async (action: () => Promise<void>, fallback: string): Promise<boolean> => {
      setIsSaving(true)
      setSaveError(null)
      try {
        await action()
        reload()
        return true
      } catch (err) {
        setSaveError(errorMessage(err, fallback))
        return false
      } finally {
        setIsSaving(false)
      }
    },
    [reload],
  )

  const create = useCallback(
    (payload: UserCreationPayload) =>
      runMutation(() => createUser(payload), 'Failed to create user'),
    [runMutation],
  )

  const update = useCallback(
    (user: UserDTO) => runMutation(() => updateUser(user), 'Failed to update user'),
    [runMutation],
  )

  const remove = useCallback(
    (id: string) => runMutation(() => deleteUser(id), 'Failed to delete user'),
    [runMutation],
  )

  return {
    users,
    status,
    error,
    pageInfo,
    goToPage,
    reload,
    isSaving,
    saveError,
    clearSaveError,
    create,
    update,
    remove,
  }
}
