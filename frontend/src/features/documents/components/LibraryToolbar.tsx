import Icon from '@/components/Icon'
import type { ViewMode } from '../types'

interface LibraryToolbarProps {
  /** Number of documents currently shown. */
  count: number
  viewMode: ViewMode
  onViewModeChange: (mode: ViewMode) => void
}

interface ViewToggleButton {
  mode: ViewMode
  icon: string
  title: string
}

const VIEW_BUTTONS: ViewToggleButton[] = [
  { mode: 'grid', icon: 'grid_view', title: 'Grid View' },
  { mode: 'list', icon: 'view_list', title: 'List View' },
]

// Library toolbar: local search, result count, and grid/list view toggle.
export default function LibraryToolbar({
  count,
  viewMode,
  onViewModeChange,
}: LibraryToolbarProps) {
  return (
    <div className="flex shrink-0 items-center justify-between border-b border-ds-outline-variant bg-ds-surface-container-lowest px-6 py-4">
      <div className="group relative flex w-96 items-center">
        <Icon
          name="search"
          filled={false}
          className="absolute left-3 text-ds-secondary transition-colors group-focus-within:text-ds-primary"
        />
        <input
          type="text"
          placeholder="Search within library..."
          className="w-full rounded-ds-md border border-ds-outline-variant bg-ds-surface py-2 pl-10 pr-4 font-ds-body text-ds-body-sm shadow-sm transition-all focus:border-ds-primary focus:outline-none focus:ring-2 focus:ring-ds-primary"
        />
      </div>

      <div className="flex items-center gap-4">
        <span className="font-ds-body text-ds-body-sm text-ds-secondary">
          Showing {count} {count === 1 ? 'document' : 'documents'}
        </span>
        <div className="mx-3 h-6 w-px bg-ds-outline-variant" />
        <div className="flex overflow-hidden rounded-ds border border-ds-outline-variant bg-ds-surface-container">
          {VIEW_BUTTONS.map((button) => {
            const isActive = viewMode === button.mode
            return (
              <button
                key={button.mode}
                type="button"
                title={button.title}
                aria-pressed={isActive}
                onClick={() => onViewModeChange(button.mode)}
                className={`p-1.5 transition-colors ${
                  isActive
                    ? 'bg-ds-surface-container-highest text-ds-primary'
                    : 'text-ds-secondary hover:bg-ds-surface-container-high'
                }`}
              >
                <Icon name={button.icon} className="text-base" />
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}
