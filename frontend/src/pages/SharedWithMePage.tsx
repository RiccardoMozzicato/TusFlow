import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Icon from '@/components/Icon'
import { useDebouncedValue } from '@/lib/useDebouncedValue'
import LibraryPager from '@/features/documents/components/LibraryPager'
import SharedFilesTable from '@/features/shared/components/SharedFilesTable'
import SharedFilterPanel from '@/features/shared/components/SharedFilterPanel'
import { useSharedFiles, type SharedFilters } from '@/features/shared/useSharedFiles'
import { useSharedDownload } from '@/features/shared/useSharedDownload'
import { buildSharedParams, parseSharedParams } from '@/features/shared/sharedFilterParams'
import { useOrgUsers } from '@/features/users/useOrgUsers'

// How long the search box waits after the last keystroke before querying the
// server, so a burst of typing results in a single request.
const SEARCH_DEBOUNCE_MS = 350

// Shared with me: a table of documents other members have shared with the
// current user. The filter rail (file type, date, permission, owner, sort) and
// the toolbar search drive the server-side query one page at a time.
//
// As in the Document Library, the URL's query string is the single source of
// truth: the filter rail, search box, and pager all write the next selection
// into the URL, and a single parse reads it back to drive the request. This keeps
// the view bookmarkable, shareable, and Back/Forward-aware. Any filter change
// resets to the first page; the debounced search box is committed the same way.
export default function SharedWithMePage() {
  const [searchParams, setSearchParams] = useSearchParams()

  // The canonical selection, parsed from the URL. Memoized on the raw query
  // string so it keeps a stable identity until the URL actually changes.
  const paramsKey = searchParams.toString()
  const { filters, page: requestedPage } = useMemo(
    () => parseSharedParams(new URLSearchParams(paramsKey)),
    [paramsKey],
  )
  const { search } = filters

  const { documents, status, error, pageInfo, reload } = useSharedFiles(filters, requestedPage)
  const { downloadingId, error: downloadError, download } = useSharedDownload()
  const { users, isLoading: isLoadingUsers, error: usersError, load: loadUsers } = useOrgUsers()

  // Bumped on "Clear All" to remount the owner combobox so its input resets too.
  const [ownerResetKey, setOwnerResetKey] = useState(0)

  // Writes a new selection into the URL. Filter changes always return to the
  // first page; only the pager carries a page forward.
  function applyFilters(next: SharedFilters) {
    setSearchParams(buildSharedParams(next, 0))
  }
  function goToPage(nextPage: number) {
    setSearchParams(buildSharedParams(filters, nextPage))
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

  // Whether any server-side filter is narrowing the result (sort doesn't count),
  // so the empty state can tell "nothing shared yet" apart from "filtered to none".
  const hasActiveFilters =
    Boolean(
      filters.contentType ||
        filters.excludeContentTypes?.length ||
        filters.dateFrom ||
        filters.dateTo ||
        filters.ownerId ||
        filters.search,
    ) || filters.permissions.length !== 2

  // Clears every filter by emptying the query string. The box's local state and
  // the owner combobox are reset here too, so they clear immediately even if a
  // typed-but-not-yet-debounced query hasn't reached the URL.
  function handleClear() {
    setSearchInput('')
    setSearchParams(new URLSearchParams())
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
          onChange={(partial) => applyFilters({ ...filters, ...partial })}
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
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Search shared documents…"
              className="w-full bg-transparent font-ds-body text-ds-body-sm text-ds-on-surface outline-none placeholder:text-ds-secondary"
            />
          </div>

          {/* min-height keeps the card a consistent size whether it holds one row
              or a full page, so it never collapses to a thin strip. */}
          <div className="min-h-[28rem]">
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
          ) : documents.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
              <Icon name="group_off" filled={false} className="text-[40px] text-ds-secondary" />
              <p className="font-ds-body text-ds-body-sm text-ds-secondary">
                {filters.search
                  ? 'No documents match your search.'
                  : hasActiveFilters
                    ? 'No documents match your filters.'
                    : 'No documents have been shared with you yet.'}
              </p>
            </div>
          ) : (
            <SharedFilesTable
              documents={documents}
              downloadingId={downloadingId}
              onDownload={download}
            />
          )}
          </div>

          <div className="flex flex-col gap-2 border-t border-ds-outline-variant bg-ds-surface-container-low px-6 py-3">
            {downloadError ? (
              <p className="flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-error">
                <Icon name="error" className="text-[14px]" />
                {downloadError}
              </p>
            ) : null}
            <div className="flex items-center justify-between">
              <p className="font-ds-label text-ds-label-sm text-ds-secondary">
                Showing {documents.length} of {pageInfo.totalElements} shared document
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
