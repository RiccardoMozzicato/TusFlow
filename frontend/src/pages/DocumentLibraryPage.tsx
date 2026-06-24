import { useEffect, useMemo, useState } from 'react'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
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
  loadDocuments,
  resetFilters,
  selectDocumentFilters,
  selectDocuments,
  selectDocumentsError,
  selectDocumentsPageInfo,
  selectDocumentsStatus,
  setFilters,
} from '@/features/documents/documentsSlice'
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

// Document Library: filter rail + a canvas with a toolbar and a card grid.
// Files come from the gateway-routed file service (getFiles), one page at a time.
// The filter rail drives the server-side query; the toolbar search narrows the
// currently-loaded page client-side.
export default function DocumentLibraryPage() {
  // Row layout is the default; the card grid is opt-in via the toolbar toggle.
  const [viewMode, setViewMode] = useState<ViewMode>('list')
  const [searchQuery, setSearchQuery] = useState('')
  const [dialog, setDialog] = useState<ActiveDialog>(null)
  const dispatch = useAppDispatch()

  const documents = useAppSelector(selectDocuments)
  const status = useAppSelector(selectDocumentsStatus)
  const error = useAppSelector(selectDocumentsError)
  const filters = useAppSelector(selectDocumentFilters)
  const { page, totalElements, totalPages, isFirst, isLast } = useAppSelector(
    selectDocumentsPageInfo,
  )
  const { isFavorited, error: favoriteError, toggle: toggleFavorite } = useLibraryFavorites()
  const isDeleting = useAppSelector(selectIsDeletingFile)
  const deleteError = useAppSelector(selectDeleteFileError)

  const { contentType, excludeContentTypes, dateFrom, dateTo, sort, sharingScope, ownership } =
    filters
  // Stable primitive keys so the effect re-runs when a set/array filter changes.
  const scopesKey = sharingScope.join(',')
  const excludeKey = excludeContentTypes?.join(',') ?? ''

  // Reload from page 0 on mount and whenever a server-side filter changes.
  // Pagination Prev/Next dispatch directly below (they don't touch filters).
  useEffect(() => {
    void dispatch(loadDocuments({ page: 0 }))
  }, [dispatch, contentType, excludeKey, dateFrom, dateTo, sort, scopesKey, ownership])

  // Reloads the current page after a mutating action (delete / new version).
  function refresh() {
    void dispatch(loadDocuments({ page }))
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

  const visibleDocuments = useMemo(() => {
    const query = searchQuery.trim().toLowerCase()
    if (!query) return documents
    return documents.filter((doc) => doc.title.toLowerCase().includes(query))
  }, [documents, searchQuery])

  // While searching, the count reflects the locally-filtered subset of the
  // loaded page; otherwise it's the server's total result count.
  const count = searchQuery.trim() ? visibleDocuments.length : totalElements

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
          onChange={(partial) => dispatch(setFilters(partial))}
          onClear={() => dispatch(resetFilters())}
        />

        <section className="flex min-w-0 flex-1 flex-col overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest">
          <LibraryToolbar
            count={count}
            searchQuery={searchQuery}
            onSearchChange={setSearchQuery}
            viewMode={viewMode}
            onViewModeChange={setViewMode}
          />
          <div className="min-w-0 p-6">
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
                    documents={visibleDocuments}
                    isFavorited={(doc) => isFavorited(doc.id, doc.versionNumber)}
                    onToggleFavorite={(doc) => toggleFavorite(doc.id, doc.versionNumber)}
                    onAction={(doc, kind) => setDialog({ kind, doc })}
                  />
                ) : (
                  <DocumentGrid
                    documents={visibleDocuments}
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
                  onPrev={() => void dispatch(loadDocuments({ page: page - 1 }))}
                  onNext={() => void dispatch(loadDocuments({ page: page + 1 }))}
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
