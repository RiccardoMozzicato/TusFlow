import { useCallback, useMemo, useState } from 'react'
import Icon from '@/components/Icon'
import LibraryPager from '@/features/documents/components/LibraryPager'
import FavoritesTable from '@/features/favorites/components/FavoritesTable'
import { useFavorites } from '@/features/favorites/useFavorites'
import { useFavoriteDownload } from '@/features/favorites/useFavoriteDownload'
import { removeFavorite } from '@/features/documents/detailsApi'
import type { FavoriteDocument } from '@/features/favorites/types'

// Removes a favorite from the table, tracking the in-flight row so its star
// button can spin, then refreshes the current page.
function useRemoveFavorite(reload: () => void) {
  const [removingId, setRemovingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const remove = useCallback(
    (doc: FavoriteDocument) => {
      if (removingId != null) return
      setRemovingId(doc.id)
      setError(null)
      removeFavorite(doc.fileId, doc.version)
        .then(() => reload())
        .catch((err) => {
          setError(err instanceof Error ? err.message : 'Failed to remove favorite')
        })
        .finally(() => setRemovingId(null))
    },
    [removingId, reload],
  )

  return { removingId, error, remove }
}

// Favorites: a table of the file versions the current user has starred,
// fetched one server page at a time from /files/favorites. The search narrows
// the currently-loaded page client-side.
export default function FavoritesPage() {
  const { documents, status, error, pageInfo, goToPage, reload } = useFavorites()
  const { downloadingId, error: downloadError, download } = useFavoriteDownload()
  const { removingId, error: removeError, remove } = useRemoveFavorite(reload)

  const [search, setSearch] = useState('')

  const visible = useMemo(() => {
    const query = search.trim().toLowerCase()
    if (!query) return documents
    return documents.filter((doc) => doc.title.toLowerCase().includes(query))
  }, [documents, search])

  const isInitialLoading = status === 'loading' && documents.length === 0
  const actionError = downloadError ?? removeError

  return (
    <div className="flex flex-col gap-6">
      <section>
        <h2 className="font-ds-heading text-ds-headline-md text-ds-on-surface">Favorites</h2>
        <p className="mt-1 font-ds-body text-ds-body-sm text-ds-secondary">
          Specific document versions you starred, pinned even as new versions arrive.
        </p>
      </section>

      <div className="overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-sm">
        <div className="flex items-center gap-2 border-b border-ds-outline-variant bg-ds-surface-bright px-6 py-3">
          <Icon name="search" className="text-[20px] text-ds-on-surface-variant" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search favorites…"
            className="w-full bg-transparent font-ds-body text-ds-body-sm text-ds-on-surface outline-none placeholder:text-ds-secondary"
          />
        </div>

        {isInitialLoading ? (
          <p className="px-6 py-12 text-center font-ds-body text-ds-body-sm text-ds-secondary">
            Loading favorites…
          </p>
        ) : status === 'failed' ? (
          <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
            <p className="font-ds-body text-ds-body-sm text-ds-error">
              {error ?? 'Failed to load favorites.'}
            </p>
            <button
              type="button"
              onClick={reload}
              className="inline-flex items-center rounded-ds border border-ds-outline-variant bg-ds-surface-container px-3 py-1.5 font-ds-label text-ds-label-sm text-ds-on-surface transition-colors hover:bg-ds-surface-container-high"
            >
              Retry
            </button>
          </div>
        ) : visible.length === 0 ? (
          <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
            <Icon name="star" filled={false} className="text-[40px] text-ds-secondary" />
            <p className="font-ds-body text-ds-body-sm text-ds-secondary">
              {documents.length === 0
                ? 'No favorite versions yet — star one from a document’s version history.'
                : 'No favorites match your search.'}
            </p>
          </div>
        ) : (
          <FavoritesTable
            documents={visible}
            downloadingId={downloadingId}
            removingId={removingId}
            onDownload={download}
            onRemove={remove}
          />
        )}

        <div className="flex flex-col gap-2 border-t border-ds-outline-variant bg-ds-surface-container-low px-6 py-3">
          {actionError ? (
            <p className="flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-error">
              <Icon name="error" className="text-[14px]" />
              {actionError}
            </p>
          ) : null}
          <div className="flex items-center justify-between">
            <p className="font-ds-label text-ds-label-sm text-ds-secondary">
              Showing {visible.length} of {pageInfo.totalElements} favorite
              {pageInfo.totalElements === 1 ? '' : 's'}
            </p>
            <LibraryPager
              pageNumber={pageInfo.page}
              totalPages={pageInfo.totalPages}
              isFirst={pageInfo.isFirst}
              isLast={pageInfo.isLast}
              onPrev={() => goToPage(pageInfo.page - 1)}
              onNext={() => goToPage(pageInfo.page + 1)}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
