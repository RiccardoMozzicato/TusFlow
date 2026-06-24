import { useMemo, useState } from 'react'
import Icon from '@/components/Icon'
import LibraryPager from '@/features/documents/components/LibraryPager'
import SharedFilesTable from '@/features/shared/components/SharedFilesTable'
import SharedFilterPanel from '@/features/shared/components/SharedFilterPanel'
import { useSharedFiles } from '@/features/shared/useSharedFiles'
import { useSharedDownload } from '@/features/shared/useSharedDownload'
import { useOrgUsers } from '@/features/users/useOrgUsers'

// Shared with me: a table of documents other members have shared with the
// current user. The filter rail (file type, date, permission, owner, sort)
// drives the server-side query one page at a time; the toolbar search narrows
// the currently-loaded page client-side, matching the Document Library.
export default function SharedWithMePage() {
  const {
    documents,
    status,
    error,
    pageInfo,
    filters,
    setFilters,
    resetFilters,
    goToPage,
    reload,
  } = useSharedFiles()
  const { downloadingId, error: downloadError, download } = useSharedDownload()
  const { users, isLoading: isLoadingUsers, error: usersError, load: loadUsers } = useOrgUsers()

  const [search, setSearch] = useState('')
  // Bumped on "Clear All" to remount the owner combobox so its input resets too.
  const [ownerResetKey, setOwnerResetKey] = useState(0)

  const visible = useMemo(() => {
    const query = search.trim().toLowerCase()
    if (!query) return documents
    return documents.filter((doc) => doc.title.toLowerCase().includes(query))
  }, [documents, search])

  // Whether any server-side filter is narrowing the result (sort doesn't count),
  // so the empty state can tell "nothing shared yet" apart from "filtered to none".
  const hasActiveFilters =
    Boolean(
      filters.contentType ||
        filters.excludeContentTypes?.length ||
        filters.dateFrom ||
        filters.dateTo ||
        filters.ownerId,
    ) || filters.permissions.length !== 2

  function handleClear() {
    resetFilters()
    setOwnerResetKey((key) => key + 1)
  }

  const isInitialLoading = status === 'loading' && documents.length === 0

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 className="font-ds-heading text-ds-headline-md text-ds-on-surface">Shared with me</h2>
        <p className="mt-1 font-ds-body text-ds-body-sm text-ds-secondary">
          Documents shared with you by other members of your organization.
        </p>
      </div>

      <div className="flex flex-col gap-6 lg:flex-row">
        <SharedFilterPanel
          filters={filters}
          onChange={setFilters}
          onClear={handleClear}
          users={users}
          isLoadingUsers={isLoadingUsers}
          usersError={usersError}
          onLoadUsers={loadUsers}
          ownerResetKey={ownerResetKey}
        />

        <section className="flex min-w-0 flex-1 flex-col overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-sm">
          <div className="flex items-center gap-2 border-b border-ds-outline-variant bg-ds-surface-bright px-6 py-3">
            <Icon name="search" className="text-[20px] text-ds-on-surface-variant" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search shared documents…"
              className="w-full bg-transparent font-ds-body text-ds-body-sm text-ds-on-surface outline-none placeholder:text-ds-secondary"
            />
          </div>

          {isInitialLoading ? (
            <p className="px-6 py-12 text-center font-ds-body text-ds-body-sm text-ds-secondary">
              Loading shared documents…
            </p>
          ) : status === 'failed' ? (
            <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
              <p className="font-ds-body text-ds-body-sm text-ds-error">
                {error ?? 'Failed to load shared documents.'}
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
              <Icon name="group_off" filled={false} className="text-[40px] text-ds-secondary" />
              <p className="font-ds-body text-ds-body-sm text-ds-secondary">
                {documents.length > 0
                  ? 'No documents match your search.'
                  : hasActiveFilters
                    ? 'No documents match your filters.'
                    : 'No documents have been shared with you yet.'}
              </p>
            </div>
          ) : (
            <SharedFilesTable
              documents={visible}
              downloadingId={downloadingId}
              onDownload={download}
            />
          )}

          <div className="flex flex-col gap-2 border-t border-ds-outline-variant bg-ds-surface-container-low px-6 py-3">
            {downloadError ? (
              <p className="flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-error">
                <Icon name="error" className="text-[14px]" />
                {downloadError}
              </p>
            ) : null}
            <div className="flex items-center justify-between">
              <p className="font-ds-label text-ds-label-sm text-ds-secondary">
                Showing {visible.length} of {pageInfo.totalElements} shared document
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
        </section>
      </div>
    </div>
  )
}
