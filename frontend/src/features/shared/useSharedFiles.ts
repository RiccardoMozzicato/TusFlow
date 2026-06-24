import { useCallback, useEffect, useState } from 'react'
import { getSharedWithMe, type PermissionLevel } from './sharedApi'
import { mapToSharedDocument } from './mappers'
import { SORT_NEWEST } from '@/features/documents/filterOptions'
import type { SharedDocument, SharePermission } from './types'

const PAGE_SIZE = 10

type Status = 'loading' | 'succeeded' | 'failed'

// Server-side filters for the shared listing. `undefined` fields are omitted
// from the request, matching the backend's optional params.
export interface SharedFilters {
  /** Exact-ish MIME type the backend matches "contains"; undefined means any. */
  contentType?: string
  /** Content-type tokens to exclude (the "Other" file-type bucket); undefined means none. */
  excludeContentTypes?: string[]
  /** ISO-8601 lower bound on createdAt (inclusive). */
  dateFrom?: string
  /** ISO-8601 upper bound on createdAt (inclusive). */
  dateTo?: string
  /** Sort spec, e.g. 'createdAt,desc'. */
  sort: string
  /**
   * Permission buckets to include. Both (the default) means "any"; exactly one
   * narrows to that level; an empty set means "exclude everything" (show none).
   */
  permissions: SharePermission[]
  /** Selected owner's user id; undefined means any owner. */
  ownerId?: string
}

const DEFAULT_FILTERS: SharedFilters = { sort: SORT_NEWEST, permissions: ['edit', 'view'] }

// The view's coarse permission bucket maps onto the backend permission level.
const PERMISSION_TO_LEVEL: Record<SharePermission, PermissionLevel> = {
  edit: 'READ_WRITE',
  view: 'READ_ONLY',
}

export interface SharedPageInfo {
  page: number
  totalElements: number
  totalPages: number
  isFirst: boolean
  isLast: boolean
}

export interface UseSharedFiles {
  documents: SharedDocument[]
  status: Status
  error: string | null
  pageInfo: SharedPageInfo
  filters: SharedFilters
  /** Merges a partial filter change and resets to page 0. */
  setFilters: (partial: Partial<SharedFilters>) => void
  resetFilters: () => void
  goToPage: (page: number) => void
  reload: () => void
}

const EMPTY_PAGE_INFO: SharedPageInfo = {
  page: 0,
  totalElements: 0,
  totalPages: 0,
  isFirst: true,
  isLast: true,
}

// Loads files shared with the current user, one server page at a time, honoring
// the active server-side filters. Any filter change re-queries from page 0.
export function useSharedFiles(): UseSharedFiles {
  const [page, setPage] = useState(0)
  // Bumped to re-run the fetch effect for the same page+filters (Retry).
  const [reloadToken, setReloadToken] = useState(0)
  const [filters, setFiltersState] = useState<SharedFilters>(DEFAULT_FILTERS)
  const [documents, setDocuments] = useState<SharedDocument[]>([])
  const [pageInfo, setPageInfo] = useState<SharedPageInfo>(EMPTY_PAGE_INFO)
  const [status, setStatus] = useState<Status>('loading')
  const [error, setError] = useState<string | null>(null)

  const { contentType, excludeContentTypes, dateFrom, dateTo, sort, permissions, ownerId } = filters
  // Stable key for the exclusion list so the fetch effect doesn't re-run on every
  // render (arrays compare by reference); it changes only when the tokens change.
  const excludeKey = excludeContentTypes?.join(',') ?? ''
  // No permission selected reads as "exclude everything"; exactly one narrows to
  // that level; both (or, defensively, an unexpected count) means no filter.
  const showNothing = permissions.length === 0
  const apiPermission = permissions.length === 1 ? PERMISSION_TO_LEVEL[permissions[0]] : undefined

  useEffect(() => {
    let isActive = true

    // The backend can't express an empty permission set, so short-circuit to an
    // empty result without a request (mirrors the library's empty-scope case).
    if (showNothing) {
      setDocuments([])
      setPageInfo(EMPTY_PAGE_INFO)
      setStatus('succeeded')
      setError(null)
      return () => {
        isActive = false
      }
    }

    setStatus('loading')
    setError(null)

    getSharedWithMe({
      page,
      size: PAGE_SIZE,
      contentType,
      excludeContentTypes,
      dateFrom,
      dateTo,
      sort,
      permission: apiPermission,
      ownerId,
    })
      .then((response) => {
        if (!isActive) return
        setDocuments(response.content.map(mapToSharedDocument))
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
        setError(err instanceof Error ? err.message : 'Failed to load shared documents')
        setStatus('failed')
      })

    return () => {
      isActive = false
    }
  }, [page, reloadToken, contentType, excludeKey, dateFrom, dateTo, sort, showNothing, apiPermission, ownerId])

  const setFilters = useCallback((partial: Partial<SharedFilters>) => {
    setFiltersState((current) => ({ ...current, ...partial }))
    setPage(0)
  }, [])

  const resetFilters = useCallback(() => {
    setFiltersState(DEFAULT_FILTERS)
    setPage(0)
  }, [])

  const goToPage = useCallback((next: number) => {
    setPage((current) => (next < 0 || next === current ? current : next))
  }, [])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  return {
    documents,
    status,
    error,
    pageInfo,
    filters,
    setFilters,
    resetFilters,
    goToPage,
    reload,
  }
}
