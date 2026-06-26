import { useCallback, useEffect, useState } from 'react'
import { addFavorite, getFavoritedVersions, removeFavorite } from './detailsApi'

export interface UseVersionFavorites {
  /** Version numbers of this file favorited by the current user. */
  favoritedVersions: ReadonlySet<number>
  /** Last toggle error message, or null. */
  error: string | null
  /** Toggles the favorite state of a version, optimistically. */
  toggle: (version: number) => void
}

// Builds a new set with the version added or removed (immutably).
function withToggled(versions: ReadonlySet<number>, version: number): Set<number> {
  const next = new Set(versions)
  if (next.has(version)) {
    next.delete(version)
  } else {
    next.add(version)
  }
  return next
}

/**
 * Tracks which versions of a file the current user has favorited.
 * `toggle` updates the UI optimistically and reverts on API failure.
 */
export function useVersionFavorites(fileId: string): UseVersionFavorites {
  const [favoritedVersions, setFavoritedVersions] = useState<ReadonlySet<number>>(new Set())
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let isActive = true
    getFavoritedVersions(fileId)
      .then((versions) => {
        if (isActive) setFavoritedVersions(new Set(versions))
      })
      .catch(() => {
        // Favorites are an enhancement: failing to load them should not break
        // the version history, so the set just stays empty.
      })
    return () => {
      isActive = false
    }
  }, [fileId])

  const toggle = useCallback(
    (version: number) => {
      const isFavorited = favoritedVersions.has(version)
      setError(null)
      // Optimistic flip; reverted below if the API call fails.
      setFavoritedVersions((current) => withToggled(current, version))

      const request = isFavorited ? removeFavorite(fileId, version) : addFavorite(fileId, version)
      request.catch((err) => {
        setFavoritedVersions((current) => withToggled(current, version))
        setError(err instanceof Error ? err.message : 'Failed to update favorite')
      })
    },
    [fileId, favoritedVersions],
  )

  return { favoritedVersions, error, toggle }
}
