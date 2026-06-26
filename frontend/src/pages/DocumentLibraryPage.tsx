import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { useDebouncedValue } from '@/lib/useDebouncedValue'
import FilterPanel from '@/features/documents/components/FilterPanel'
import LibraryToolbar from '@/features/documents/components/LibraryToolbar'
import DocumentGrid from '@/features/documents/components/DocumentGrid'
import DocumentTable from '@/features/documents/components/DocumentTable'
import LibraryPager from '@/features/documents/components/LibraryPager'
import ShareDialog from '@/features/documents/components/ShareDialog'
import NewVersionDialog from '@/features/documents/components/NewVersionDialog'
import FileInfoDialog from '@/features/documents/components/FileInfoDialog'
import ConfirmDialog from '@/components/ConfirmDialog'
import {
  type DocumentFilters,
  loadDocuments,
  selectDocuments,
  selectDocumentsError,
  selectDocumentsPageInfo,
  selectDocumentsStatus,
  setFilters,
} from '@/features/documents/documentsSlice'
import {
  buildDocumentParams,
  parseDocumentParams,
} from '@/features/documents/filterParams'
import {
  deleteDocument,
  selectDeleteFileError,
  selectIsDeletingFile,
} from '@/features/documents/documentDetailsSlice'
import { useLibraryFavorites } from '@/features/documents/useLibraryFavorites'
import type { DocumentActionKind, LibraryDocument, ViewMode } from '@/features/documents/types'
import Icon from '@/components/Icon'

// Which action modal is open, and for which document.
type ActiveDialog = { kind: DocumentActionKind; doc: LibraryDocument } | null

// How long the search box waits after the last keystroke before querying the
// server, so a burst of typing results in a single request.
const SEARCH_DEBOUNCE_MS = 350

// Document Library: filter rail + a canvas with a toolbar and a card grid.
// Files come from the gateway-routed file service (getFiles), one page at a time.
//
// The URL's query string is the single source of truth for the selection: the
// filter rail, toolbar search, and pager all write the next selection into the
// URL, and a single effect reads it back to drive the paginated request. This
// keeps the current view bookmarkable, shareable, and Back/Forward-aware. Any
// filter change resets to the first page; the debounced search box is committed
// the same way.
export default function DocumentLibraryPage() {
  // Row layout is the default; the card grid is opt-in via the toolbar toggle.
  const [viewMode, setViewMode] = useState<ViewMode>('list')
  const [dialog, setDialog] = useState<ActiveDialog>(null)
  const dispatch = useAppDispatch()
  const [searchParams, setSearchParams] = useSearchParams()

  const documents = useAppSelector(selectDocuments)
  const status = useAppSelector(selectDocumentsStatus)
  const error = useAppSelector(selectDocumentsError)
  const { page, totalElements, totalPages, isFirst, isLast } = useAppSelector(
    selectDocumentsPageInfo,
  )
  const { isFavorited, error: favoriteError, toggle: toggleFavorite } = useLibraryFavorites()
  const isDeleting = useAppSelector(selectIsDeletingFile)
  const deleteError = useAppSelector(selectDeleteFileError)

  // The canonical selection, parsed from the URL. Memoized on the raw query
  // string so it keeps a stable identity until the URL actually changes.
  const paramsKey = searchParams.toString()
  const { filters, page: requestedPage } = useMemo(
    () => parseDocumentParams(new URLSearchParams(paramsKey)),
    [paramsKey],
  )
  const { sort, search } = filters

  // Writes a new selection into the URL. Filter changes always return to the
  // first page; only the pager carries a page forward.
  function applyFilters(next: DocumentFilters) {
    setSearchParams(buildDocumentParams(next, 0))
  }
  function goToPage(nextPage: number) {
    setSearchParams(buildDocumentParams(filters, nextPage))
  }

  // The search box updates this local state immediately (responsive typing); the
  // debounced value is committed into the URL's `search` param.
  const [searchInput, setSearchInput] = useState(search ?? '')
  const debouncedSearch = useDebouncedValue(searchInput, SEARCH_DEBOUNCE_MS)

  // Keep the box in sync when the query changes from outside (Back/Forward, or
  // Clear All). Typing flows the other way and settles on the same value.
  useEffect(() => {
    setSearchInput(search ?? '')
  }, [search])

  // Commit the debounced query into the URL as a normalized value (trimmed,
  // blank → undefined). Guarded so an unchanged query doesn't push a new entry.
  useEffect(() => {
    const next = debouncedSearch.trim() || undefined
    if (next !== search) applyFilters({ ...filters, search: next })
    // applyFilters/filters are derived from the URL; depending on the committed
    // value keeps this from re-firing on unrelated renders.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch, search])

  // Mirror the URL selection into the slice, then load the requested page. The
  // thunk reads the filters back out of the store, so they must be set first.
  // Re-runs only when the query string changes.
  useEffect(() => {
    dispatch(setFilters(filters))
    void dispatch(loadDocuments({ page: requestedPage }))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dispatch, paramsKey])

  // Reloads the current page after a mutating action (delete / new version).
  function refresh() {
    void dispatch(loadDocuments({ page: requestedPage }))
  }

  function handleConfirmDelete() {
    if (dialog?.kind !== 'delete') return
    void dispatch(deleteDocument({ id: dialog.doc.id }))
      .unwrap()
      .then(() => {
        setDialog(null)
        refresh()
      })
      .catch(() => {
        // Error is surfaced via deleteError in the dialog.
      })
  }

  const isInitialLoading = status === 'loading' && documents.length === 0

  // Search is server-side, so the count is always the server's total result count.
  const count = totalElements

  // Clears every filter by emptying the query string. The box's local state is
  // reset here too, so it clears immediately even if a typed-but-not-yet-debounced
  // query hasn't reached the URL.
  function handleClearFilters() {
    setSearchInput('')
    setSearchParams(new URLSearchParams())
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 className="font-ds-heading text-ds-headline-md text-ds-on-surface">
          Document Library
        </h2>
        <p className="mt-1 text-ds-body-sm text-ds-secondary">
          Browse, filter, and manage your organization's documents.
        </p>
      </div>

      <div className="flex flex-col gap-6 lg:flex-row">
        <FilterPanel
          filters={filters}
          onChange={(partial) => applyFilters({ ...filters, ...partial })}
          onClear={handleClearFilters}
        />

        <section className="flex min-w-0 flex-1 flex-col overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest">
          <LibraryToolbar
            count={count}
            searchQuery={searchInput}
            onSearchChange={setSearchInput}
            viewMode={viewMode}
            onViewModeChange={setViewMode}
          />
          {/* min-height keeps the canvas a consistent size whether it holds one
              row or a full page, so the panel never collapses to a single line. */}
          <div className="min-h-[28rem] min-w-0 p-6">
            {isInitialLoading ? (
              <p className="py-12 text-center font-ds-body text-ds-body-sm text-ds-secondary">
                Loading documents…
              </p>
            ) : status === 'failed' ? (
              <div className="flex flex-col items-center gap-3 py-12 text-center">
                <p className="font-ds-body text-ds-body-sm text-ds-error">
                  {error ?? 'Failed to load documents.'}
                </p>
                <button
                  type="button"
                  onClick={() => void dispatch(loadDocuments({ page }))}
                  className="inline-flex items-center rounded-ds border border-ds-outline-variant bg-ds-surface-container px-3 py-1.5 font-ds-label text-ds-label-sm text-ds-on-surface transition-colors hover:bg-ds-surface-container-high"
                >
                  Retry
                </button>
              </div>
            ) : (
              <>
                {favoriteError ? (
                  <p className="mb-4 flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-error">
                    <Icon name="error" className="text-[14px]" />
                    {favoriteError}
                  </p>
                ) : null}
                {viewMode === 'list' ? (
                  <DocumentTable
                    documents={documents}
                    isFavorited={(doc) => isFavorited(doc.id, doc.versionNumber)}
                    onToggleFavorite={(doc) => toggleFavorite(doc.id, doc.versionNumber)}
                    onAction={(doc, kind) => setDialog({ kind, doc })}
                    sort={sort}
                    onSortChange={(next) => applyFilters({ ...filters, sort: next })}
                  />
                ) : (
                  <DocumentGrid
                    documents={documents}
                    isFavorited={(doc) => isFavorited(doc.id, doc.versionNumber)}
                    onToggleFavorite={(doc) => toggleFavorite(doc.id, doc.versionNumber)}
                    onAction={(doc, kind) => setDialog({ kind, doc })}
                  />
                )}
                <LibraryPager
                  pageNumber={page}
                  totalPages={totalPages}
                  isFirst={isFirst}
                  isLast={isLast}
                  onPrev={() => goToPage(page - 1)}
                  onNext={() => goToPage(page + 1)}
                />
              </>
            )}
          </div>
        </section>
      </div>

      {dialog?.kind === 'info' ? (
        <FileInfoDialog doc={dialog.doc} onClose={() => setDialog(null)} />
      ) : null}

      {dialog?.kind === 'share' ? (
        <ShareDialog doc={dialog.doc} onClose={() => setDialog(null)} />
      ) : null}

      {dialog?.kind === 'newVersion' ? (
        <NewVersionDialog
          doc={dialog.doc}
          onClose={() => setDialog(null)}
          onUploaded={refresh}
        />
      ) : null}

      {dialog?.kind === 'delete' ? (
        <ConfirmDialog
          title="Delete file"
          message={
            deleteError ??
            `Permanently delete "${dialog.doc.title}" and all its versions? This cannot be undone.`
          }
          confirmLabel="Delete"
          destructive
          isBusy={isDeleting}
          onConfirm={handleConfirmDelete}
          onCancel={() => setDialog(null)}
        />
      ) : null}
    </div>
  )
}
