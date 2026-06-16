import { useCallback, useEffect, useState } from 'react'
import { getMyFavorites } from '@/features/favorites/favoritesApi'
import { addFavorite, removeFavorite } from './detailsApi'

export interface UseLibraryFavorites {
  /** Whether the current user has favorited this exact file version. */
  isFavorited: (fileId: string, version: number) => boolean
  /** Last toggle error message, or null. */
  error: string | null
  /** Toggles the favorite state of a file version, optimistically. */
  toggle: (fileId: string, version: number) => void
}

// Page size used while walking the user's favorites on mount.
const FAVORITES_PAGE_SIZE = 100
// Safety cap on pagination, so a backend bug can't loop forever.
const MAX_FAVORITES_PAGES = 20

// One favorite is keyed by the (file, version) pair it pins.
function keyOf(fileId: string, version: number): string {
  return `${fileId}:${version}`
}

// Builds a new set with the key added or removed (immutably).
function withToggled(keys: ReadonlySet<string>, key: string): Set<string> {
  const next = new Set(keys)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  return next
}

// Walks every page of the user's favorites and returns their (file, version) keys.
async function loadAllFavoriteKeys(): Promise<Set<string>> {
  const keys = new Set<string>()
  for (let page = 0; page < MAX_FAVORITES_PAGES; page += 1) {
    const response = await getMyFavorites(page, FAVORITES_PAGE_SIZE)
    response.content.forEach((dto) => keys.add(keyOf(dto.fileId, dto.version)))
    if (response.last) break
  }
  return keys
}

/**
 * Tracks which file versions the current user has favorited, across all files
 * in the library. `toggle` updates the UI optimistically and reverts on API
 * failure. Companion of `useVersionFavorites`, which is scoped to one file.
 */
export function useLibraryFavorites(): UseLibraryFavorites {
  const [favoriteKeys, setFavoriteKeys] = useState<ReadonlySet<string>>(new Set())
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let isActive = true
    loadAllFavoriteKeys()
      .then((keys) => {
        if (isActive) setFavoriteKeys(keys)
      })
      .catch(() => {
        // Favorites are an enhancement: failing to load them should not break
        // the library, so the set just stays empty.
      })
    return () => {
      isActive = false
    }
  }, [])

  const isFavorited = useCallback(
    (fileId: string, version: number) => favoriteKeys.has(keyOf(fileId, version)),
    [favoriteKeys],
  )

  const toggle = useCallback(
    (fileId: string, version: number) => {
      const key = keyOf(fileId, version)
      const wasFavorited = favoriteKeys.has(key)
      setError(null)
      // Optimistic flip; reverted below if the API call fails.
      setFavoriteKeys((current) => withToggled(current, key))

      const request = wasFavorited ? removeFavorite(fileId, version) : addFavorite(fileId, version)
      request.catch((err) => {
        setFavoriteKeys((current) => withToggled(current, key))
        setError(err instanceof Error ? err.message : 'Failed to update favorite')
      })
    },
    [favoriteKeys],
  )

  return { isFavorited, error, toggle }
}
