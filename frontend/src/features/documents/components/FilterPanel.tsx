import { SORT_NEWEST, SORT_OLDEST, isoToDateInput } from '../filterOptions'
import { Ownership, SharingScope } from '../documentsApi'
import type { DocumentFilters } from '../documentsSlice'
import FileTypeFilter from './FileTypeFilter'

interface FilterPanelProps {
  filters: DocumentFilters
  onChange: (partial: Partial<DocumentFilters>) => void
  onClear: () => void
}

const SHARING_SCOPE_OPTIONS: { scope: SharingScope; label: string }[] = [
  { scope: SharingScope.PERSONAL, label: 'Personal' },
  { scope: SharingScope.DEPARTMENT, label: 'Department' },
  { scope: SharingScope.ORGANIZATION, label: 'Organization' },
]

// Ownership is a mutually-exclusive partition (a file is either yours or not), so
// the two checkboxes act like a clearable radio: re-clicking the active one clears it.
const OWNERSHIP_OPTIONS: { value: Ownership; label: string }[] = [
  { value: Ownership.OWNED, label: 'Owned by you' },
  { value: Ownership.SHARED, label: 'Shared with you' },
]

// Small section wrapper to keep the panel's groups visually consistent.
function FilterGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-3">
      <h4 className="font-ds-label text-ds-label-md text-ds-on-surface">{title}</h4>
      {children}
    </div>
  )
}

// Left filter rail. Controlled by the documents slice's filter state — every
// change bubbles up via onChange, and the page re-fetches page 0.
export default function FilterPanel({ filters, onChange, onClear }: FilterPanelProps) {
  const toggleScope = (scope: SharingScope) => {
    const next = filters.sharingScope.includes(scope)
      ? filters.sharingScope.filter((s) => s !== scope)
      : [...filters.sharingScope, scope]
    onChange({ sharingScope: next })
  }

  const toggleOwnership = (value: Ownership) => {
    onChange({ ownership: filters.ownership === value ? undefined : value })
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

      <FilterGroup title="Sharing Scope">
        {SHARING_SCOPE_OPTIONS.map(({ scope, label }) => (
          <label key={scope} className="group flex cursor-pointer items-center gap-3">
            <input
              type="checkbox"
              checked={filters.sharingScope.includes(scope)}
              onChange={() => toggleScope(scope)}
              className="h-4 w-4 rounded border-ds-outline-variant bg-ds-surface text-ds-primary focus:ring-ds-primary"
            />
            <span className="font-ds-body text-ds-body-sm text-ds-secondary transition-colors group-hover:text-ds-on-surface">
              {label}
            </span>
          </label>
        ))}
      </FilterGroup>

      <FilterGroup title="Ownership">
        {OWNERSHIP_OPTIONS.map(({ value, label }) => (
          <label key={value} className="group flex cursor-pointer items-center gap-3">
            <input
              type="checkbox"
              checked={filters.ownership === value}
              onChange={() => toggleOwnership(value)}
              className="h-4 w-4 rounded border-ds-outline-variant bg-ds-surface text-ds-primary focus:ring-ds-primary"
            />
            <span className="font-ds-body text-ds-body-sm text-ds-secondary transition-colors group-hover:text-ds-on-surface">
              {label}
            </span>
          </label>
        ))}
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
