import { useCallback, useEffect, useState } from 'react'
import { getMyFavorites } from './favoritesApi'
import { mapToFavoriteDocument } from './mappers'
import type { FavoriteDocument } from './types'

const PAGE_SIZE = 10

type Status = 'loading' | 'succeeded' | 'failed'

export interface FavoritesPageInfo {
  page: number
  totalElements: number
  totalPages: number
  isFirst: boolean
  isLast: boolean
}

export interface UseFavorites {
  documents: FavoriteDocument[]
  status: Status
  error: string | null
  pageInfo: FavoritesPageInfo
  goToPage: (page: number) => void
  reload: () => void
}

const EMPTY_PAGE_INFO: FavoritesPageInfo = {
  page: 0,
  totalElements: 0,
  totalPages: 0,
  isFirst: true,
  isLast: true,
}

// Loads the current user's favorited versions, one server page at a time.
export function useFavorites(): UseFavorites {
  const [page, setPage] = useState(0)
  // Bumped to re-run the fetch effect for the same page (Retry / unfavorite).
  const [reloadToken, setReloadToken] = useState(0)
  const [documents, setDocuments] = useState<FavoriteDocument[]>([])
  const [pageInfo, setPageInfo] = useState<FavoritesPageInfo>(EMPTY_PAGE_INFO)
  const [status, setStatus] = useState<Status>('loading')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let isActive = true
    setStatus('loading')
    setError(null)

    getMyFavorites(page, PAGE_SIZE)
      .then((response) => {
        if (!isActive) return
        setDocuments(response.content.map(mapToFavoriteDocument))
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
        setError(err instanceof Error ? err.message : 'Failed to load favorites')
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
