import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import {
  createUser,
  deleteUser,
  getUsers,
  updateUser,
  type UserCreationPayload,
  type UserDTO,
} from './usersApi'
import {
  buildUserParams,
  parseUserParams,
  type UserFilters,
} from './userFilterParams'

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
  /** Current search + sort selection, parsed from the URL. */
  filters: UserFilters
  /** Writes a new search/sort selection into the URL and resets to the first page. */
  applyFilters: (next: UserFilters) => void
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
 * delete mutations. The URL's query string is the source of truth for the
 * selection (search, sort, page): the filter controls and pager write into it,
 * and a single effect reads it back to drive the request, so the view is
 * bookmarkable and Back/Forward-aware — mirroring the Document Library.
 * Mutations refresh the current page on success so the table reflects the change
 * without a manual reload.
 */
export function useUserManagement(): UseUserManagement {
  const [searchParams, setSearchParams] = useSearchParams()
  const [reloadToken, setReloadToken] = useState(0)
  const [users, setUsers] = useState<UserDTO[]>([])
  const [pageInfo, setPageInfo] = useState<UsersPageInfo>(EMPTY_PAGE_INFO)
  const [status, setStatus] = useState<Status>('loading')
  const [error, setError] = useState<string | null>(null)

  const [isSaving, setIsSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  // The canonical selection, parsed from the URL. Memoized on the raw query
  // string so it keeps a stable identity until the URL actually changes.
  const paramsKey = searchParams.toString()
  const { filters, page } = useMemo(
    () => parseUserParams(new URLSearchParams(paramsKey)),
    [paramsKey],
  )

  useEffect(() => {
    let isActive = true
    setStatus('loading')
    setError(null)

    getUsers({ page, size: PAGE_SIZE, search: filters.search, sort: filters.sort })
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
    // Re-fetch whenever the URL selection changes, or after a mutation (reloadToken).
  }, [paramsKey, page, filters.search, filters.sort, reloadToken])

  // Writes a new search/sort selection into the URL. Filter changes always return
  // to the first page.
  const applyFilters = useCallback(
    (next: UserFilters) => setSearchParams(buildUserParams(next, 0)),
    [setSearchParams],
  )

  const goToPage = useCallback(
    (next: number) => {
      if (next < 0 || next === page) return
      setSearchParams(buildUserParams(filters, next))
    },
    [setSearchParams, filters, page],
  )

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
    filters,
    applyFilters,
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
