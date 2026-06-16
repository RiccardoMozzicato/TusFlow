import { useCallback, useEffect, useState } from 'react'
import { getSharedWithMe } from './sharedApi'
import { mapToSharedDocument } from './mappers'
import type { SharedDocument } from './types'

const PAGE_SIZE = 10

type Status = 'loading' | 'succeeded' | 'failed'

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

// Loads files shared with the current user, one server page at a time.
export function useSharedFiles(): UseSharedFiles {
  const [page, setPage] = useState(0)
  // Bumped to re-run the fetch effect for the same page (Retry).
  const [reloadToken, setReloadToken] = useState(0)
  const [documents, setDocuments] = useState<SharedDocument[]>([])
  const [pageInfo, setPageInfo] = useState<SharedPageInfo>(EMPTY_PAGE_INFO)
  const [status, setStatus] = useState<Status>('loading')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let isActive = true
    setStatus('loading')
    setError(null)

    getSharedWithMe(page, PAGE_SIZE)
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
  }, [page, reloadToken])

  const goToPage = useCallback((next: number) => {
    setPage((current) => (next < 0 || next === current ? current : next))
  }, [])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  return { documents, status, error, pageInfo, goToPage, reload }
}
