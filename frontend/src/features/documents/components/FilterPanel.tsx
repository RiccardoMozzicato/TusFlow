import {
  CATEGORIES,
  DEPARTMENTS,
  FILE_TYPE_OPTIONS,
  TAGS,
} from '../mockData'
import Icon from '@/components/Icon'

// Small section wrapper to keep the panel's groups visually consistent.
function FilterGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-3">
      <h4 className="font-ds-label text-ds-label-md text-ds-on-surface">{title}</h4>
      {children}
    </div>
  )
}

// Left filter rail. Presentational for now (defaultChecked / styling only) — the
// controls become stateful once the library query is wired to the file service.
export default function FilterPanel() {
  return (
    <aside className="sticky top-0 flex w-64 shrink-0 flex-col gap-6 self-start rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-6">
      <div className="flex items-center justify-between border-b border-ds-outline-variant pb-3">
        <h3 className="font-ds-label text-ds-label-md uppercase tracking-widest text-ds-primary">
          Filters
        </h3>
        <button
          type="button"
          className="font-ds-label text-ds-label-sm text-ds-secondary transition-colors hover:text-ds-primary"
        >
          Clear All
        </button>
      </div>

      <FilterGroup title="Category">
        {CATEGORIES.map((category) => (
          <label
            key={category.id}
            className="group flex cursor-pointer items-center gap-3"
          >
            <input
              type="checkbox"
              defaultChecked={category.defaultChecked}
              className="h-4 w-4 rounded border-ds-outline-variant bg-ds-surface text-ds-primary focus:ring-ds-primary"
            />
            <span className="font-ds-body text-ds-body-sm text-ds-secondary transition-colors group-hover:text-ds-on-surface">
              {category.label}
            </span>
          </label>
        ))}
      </FilterGroup>

      <FilterGroup title="Department">
        <select className="w-full rounded-ds-md border border-ds-outline-variant bg-ds-surface-container-lowest px-3 py-2 font-ds-body text-ds-body-sm outline-none focus:border-ds-primary focus:ring-1 focus:ring-ds-primary">
          {DEPARTMENTS.map((department) => (
            <option key={department}>{department}</option>
          ))}
        </select>
      </FilterGroup>

      <FilterGroup title="File Type">
        <div className="grid grid-cols-2 gap-3">
          {FILE_TYPE_OPTIONS.map((option) => (
            <label
              key={option.type}
              className="flex cursor-pointer items-center gap-1 rounded-ds border border-transparent bg-ds-surface-container px-2 py-1.5 transition-colors hover:border-ds-outline-variant"
            >
              <Icon name={option.icon} className={`text-[16px] ${option.iconClassName}`} />
              <span className="font-ds-label text-ds-label-sm text-ds-on-surface">
                {option.label}
              </span>
            </label>
          ))}
        </div>
      </FilterGroup>

      <FilterGroup title="Tags">
        <div className="flex flex-wrap gap-1">
          {TAGS.map((tag) => (
            <button
              key={tag.id}
              type="button"
              className={`rounded-ds-full px-2 py-1 font-ds-label text-ds-label-sm transition-colors ${
                tag.defaultSelected
                  ? 'bg-ds-primary text-ds-on-primary'
                  : 'bg-ds-surface-container-high text-ds-on-surface hover:bg-ds-outline-variant'
              }`}
            >
              {tag.label}
            </button>
          ))}
        </div>
      </FilterGroup>
    </aside>
  )
}
