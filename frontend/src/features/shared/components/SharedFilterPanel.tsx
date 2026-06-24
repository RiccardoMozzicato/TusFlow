import FileTypeFilter from '@/features/documents/components/FileTypeFilter'
import { SORT_NEWEST, SORT_OLDEST, isoToDateInput } from '@/features/documents/filterOptions'
import type { UserDTO } from '@/features/users/usersApi'
import type { SharedFilters } from '../useSharedFiles'
import type { SharePermission } from '../types'
import OwnerFilterCombobox from './OwnerFilterCombobox'

interface SharedFilterPanelProps {
  filters: SharedFilters
  onChange: (partial: Partial<SharedFilters>) => void
  onClear: () => void
  // Owner autocomplete wiring (lazy-loaded org users from the user-service).
  users: UserDTO[]
  isLoadingUsers: boolean
  usersError: string | null
  onLoadUsers: () => void
  /** Bumped by the parent on "Clear All" to remount and reset the owner combobox. */
  ownerResetKey: number
}

// Small section wrapper to keep the panel's groups visually consistent.
function FilterGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-3">
      <h4 className="font-ds-label text-ds-label-md text-ds-on-surface">{title}</h4>
      {children}
    </div>
  )
}

// Left filter rail for the Shared-with-me page. Mirrors the Document Library's
// FilterPanel, but scoped to shared files: file type, date range, permission
// (the Can View / Can Edit buttons), owner, and sort — all server-side.
export default function SharedFilterPanel({
  filters,
  onChange,
  onClear,
  users,
  isLoadingUsers,
  usersError,
  onLoadUsers,
  ownerResetKey,
}: SharedFilterPanelProps) {
  const canEdit = filters.permissions.includes('edit')
  const canView = filters.permissions.includes('view')

  const togglePermission = (which: SharePermission) => {
    const next = filters.permissions.includes(which)
      ? filters.permissions.filter((permission) => permission !== which)
      : [...filters.permissions, which]
    onChange({ permissions: next })
  }

  return (
    <aside className="flex w-full flex-col gap-6 self-start rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-6 lg:sticky lg:top-0 lg:w-64 lg:shrink-0">
      <div className="flex items-center justify-between border-b border-ds-outline-variant pb-3">
        <h3 className="font-ds-label text-ds-label-md uppercase tracking-widest text-ds-primary">
          Filters
        </h3>
        <button
          type="button"
          onClick={onClear}
          className="font-ds-label text-ds-label-sm text-ds-secondary transition-colors hover:text-ds-primary"
        >
          Clear All
        </button>
      </div>

      <FilterGroup title="File Type">
        <FileTypeFilter
          value={{
            contentType: filters.contentType,
            excludeContentTypes: filters.excludeContentTypes,
          }}
          onChange={onChange}
        />
      </FilterGroup>

      <FilterGroup title="Created Date">
        <label className="flex flex-col gap-1">
          <span className="font-ds-body text-ds-body-sm text-ds-secondary">From</span>
          <input
            type="date"
            value={isoToDateInput(filters.dateFrom)}
            max={isoToDateInput(filters.dateTo) || undefined}
            onChange={(event) =>
              onChange({
                dateFrom: event.target.value ? `${event.target.value}T00:00:00Z` : undefined,
              })
            }
            className="w-full rounded-ds-md border border-ds-outline-variant bg-ds-surface-container-lowest px-3 py-2 font-ds-body text-ds-body-sm outline-none focus:border-ds-primary focus:ring-1 focus:ring-ds-primary"
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="font-ds-body text-ds-body-sm text-ds-secondary">To</span>
          <input
            type="date"
            value={isoToDateInput(filters.dateTo)}
            min={isoToDateInput(filters.dateFrom) || undefined}
            onChange={(event) =>
              onChange({
                dateTo: event.target.value ? `${event.target.value}T23:59:59Z` : undefined,
              })
            }
            className="w-full rounded-ds-md border border-ds-outline-variant bg-ds-surface-container-lowest px-3 py-2 font-ds-body text-ds-body-sm outline-none focus:border-ds-primary focus:ring-1 focus:ring-ds-primary"
          />
        </label>
      </FilterGroup>

      <FilterGroup title="Permission">
        <label className="group flex cursor-pointer items-center gap-3">
          <input
            type="checkbox"
            checked={canEdit}
            onChange={() => togglePermission('edit')}
            className="h-4 w-4 rounded border-ds-outline-variant bg-ds-surface text-ds-primary focus:ring-ds-primary"
          />
          <span className="font-ds-body text-ds-body-sm text-ds-secondary transition-colors group-hover:text-ds-on-surface">
            Can edit
          </span>
        </label>
        <label className="group flex cursor-pointer items-center gap-3">
          <input
            type="checkbox"
            checked={canView}
            onChange={() => togglePermission('view')}
            className="h-4 w-4 rounded border-ds-outline-variant bg-ds-surface text-ds-primary focus:ring-ds-primary"
          />
          <span className="font-ds-body text-ds-body-sm text-ds-secondary transition-colors group-hover:text-ds-on-surface">
            Can view
          </span>
        </label>
      </FilterGroup>

      <FilterGroup title="Owner">
        <OwnerFilterCombobox
          key={ownerResetKey}
          users={users}
          isLoading={isLoadingUsers}
          loadError={usersError}
          onFocusLoad={onLoadUsers}
          onSelect={(ownerId) => onChange({ ownerId: ownerId ?? undefined })}
        />
      </FilterGroup>

      <FilterGroup title="Sort">
        <select
          value={filters.sort}
          onChange={(event) => onChange({ sort: event.target.value })}
          className="w-full rounded-ds-md border border-ds-outline-variant bg-ds-surface-container-lowest px-3 py-2 font-ds-body text-ds-body-sm outline-none focus:border-ds-primary focus:ring-1 focus:ring-ds-primary"
        >
          <option value={SORT_NEWEST}>Newest first</option>
          <option value={SORT_OLDEST}>Oldest first</option>
        </select>
      </FilterGroup>
    </aside>
  )
}
