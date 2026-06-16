import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import Icon from '@/components/Icon'
import LibraryPager from '@/features/documents/components/LibraryPager'
import SharedFilesTable from '@/features/shared/components/SharedFilesTable'
import { useSharedFiles } from '@/features/shared/useSharedFiles'
import { useSharedDownload } from '@/features/shared/useSharedDownload'
import type { SharePermission } from '@/features/shared/types'

type PermissionFilter = 'all' | SharePermission

const FILTERS: { key: PermissionFilter; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'edit', label: 'Can Edit' },
  { key: 'view', label: 'Can View' },
]

function FilterChips({
  active,
  onChange,
}: {
  active: PermissionFilter
  onChange: (filter: PermissionFilter) => void
}) {
  return (
    <div className="flex items-center gap-1 rounded-ds-xl bg-ds-surface-container-low p-1">
      {FILTERS.map(({ key, label }) => (
        <button
          key={key}
          type="button"
          onClick={() => onChange(key)}
          className={`rounded-ds-lg px-4 py-1.5 font-ds-label text-ds-label-md transition-all ${
            active === key
              ? 'bg-ds-primary text-ds-on-primary shadow-sm'
              : 'text-ds-secondary hover:bg-ds-surface-container-high'
          }`}
        >
          {label}
        </button>
      ))}
    </div>
  )
}

function InfoCards({ total }: { total: number }) {
  return (
    <section className="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <div className="relative overflow-hidden rounded-ds-xl bg-ds-primary-container p-6 text-ds-on-primary-container shadow-md lg:col-span-2">
        <div className="relative z-10 flex h-full flex-col justify-between gap-6">
          <div>
            <h3 className="font-ds-heading text-ds-headline-sm">Collaboration</h3>
            <p className="mt-1 max-w-lg font-ds-body text-ds-body-md opacity-90">
              {total > 0
                ? `You have ${total} document${total === 1 ? '' : 's'} shared with you across your organization.`
                : 'Nothing has been shared with you yet — documents others share will show up here.'}
            </p>
          </div>
          <Link
            to="/documents"
            className="flex w-fit items-center gap-2 rounded-ds-lg bg-ds-surface-container-lowest px-4 py-2 font-ds-label text-ds-label-md text-ds-primary transition-all hover:opacity-90 active:scale-[0.98]"
          >
            Browse all documents
            <Icon name="trending_up" className="text-[18px]" />
          </Link>
        </div>
        <div className="absolute -right-16 -top-16 h-56 w-56 rounded-ds-full bg-white/5 blur-3xl" />
      </div>

      <div className="flex flex-col gap-3 rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-high p-6 shadow-sm">
        <div className="flex h-12 w-12 items-center justify-center rounded-ds-full bg-ds-primary/10 text-ds-primary">
          <Icon name="security" filled className="text-[24px]" />
        </div>
        <h3 className="font-ds-heading text-ds-headline-sm text-ds-on-surface">Security tip</h3>
        <p className="font-ds-body text-ds-body-sm text-ds-on-surface-variant">
          Review what's shared with you regularly. Open a document's details to see its owner and
          who else has access.
        </p>
        <Link
          to="/profile"
          className="mt-auto flex items-center gap-1 font-ds-label text-ds-label-md text-ds-primary hover:underline"
        >
          Profile settings
          <Icon name="arrow_forward" className="text-[16px]" />
        </Link>
      </div>
    </section>
  )
}

// Shared with me: a table of documents other members have shared with the
// current user, fetched one server page at a time from /files/my-shared. The
// permission chips and search narrow the currently-loaded page client-side.
export default function SharedWithMePage() {
  const { documents, status, error, pageInfo, goToPage, reload } = useSharedFiles()
  const { downloadingId, error: downloadError, download } = useSharedDownload()

  const [filter, setFilter] = useState<PermissionFilter>('all')
  const [search, setSearch] = useState('')

  const visible = useMemo(() => {
    const query = search.trim().toLowerCase()
    return documents.filter((doc) => {
      const matchesFilter = filter === 'all' || doc.permission === filter
      const matchesSearch = !query || doc.title.toLowerCase().includes(query)
      return matchesFilter && matchesSearch
    })
  }, [documents, filter, search])

  const isInitialLoading = status === 'loading' && documents.length === 0

  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <div>
          <h2 className="font-ds-heading text-ds-headline-md text-ds-on-surface">
            Shared with me
          </h2>
          <p className="mt-1 font-ds-body text-ds-body-sm text-ds-secondary">
            Documents shared with you by other members of your organization.
          </p>
        </div>
        <FilterChips active={filter} onChange={setFilter} />
      </section>

      <div className="overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-sm">
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
              {documents.length === 0
                ? 'No documents have been shared with you yet.'
                : 'No documents match your filters.'}
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
      </div>

      <InfoCards total={pageInfo.totalElements} />
    </div>
  )
}
