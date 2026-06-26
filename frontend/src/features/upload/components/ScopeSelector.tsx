import Icon from '@/components/Icon'
import { SharingScope } from '@/features/documents/documentsApi'
import { ALL_SCOPE_OPTIONS, type ScopeOption } from '../uploadScopes'

interface ScopeSelectorProps {
  scope: SharingScope
  onScopeChange: (scope: SharingScope) => void
  /** Selectable scopes; defaults to all. Callers narrow this by role. */
  scopes?: ScopeOption[]
}

// Selectable cards for the default sharing scope applied to files added next.
// Each file can still override this in its staging card.
export default function ScopeSelector({
  scope,
  onScopeChange,
  scopes = ALL_SCOPE_OPTIONS,
}: ScopeSelectorProps) {
  return (
    <div>
      <p className="mb-2 font-ds-label text-ds-label-sm text-ds-on-surface-variant">
        Default sharing scope
      </p>
      <div className="flex flex-wrap gap-3">
        {scopes.map((option) => {
          const isSelected = scope === option.value
          return (
            <button
              key={option.value}
              type="button"
              onClick={() => onScopeChange(option.value)}
              aria-pressed={isSelected}
              className={`flex w-44 flex-col gap-1 rounded-ds-lg border p-3 text-left transition-colors ${
                isSelected
                  ? 'border-ds-primary bg-ds-primary-container'
                  : 'border-ds-outline-variant bg-ds-surface hover:bg-ds-surface-variant'
              }`}
            >
              <span
                className={`flex items-center gap-2 font-ds-label text-ds-label-md ${
                  isSelected ? 'text-ds-on-primary-container' : 'text-ds-on-surface'
                }`}
              >
                <Icon name={option.icon} filled={isSelected} className="text-[20px]" />
                {option.label}
              </span>
              <span
                className={`text-ds-body-sm ${
                  isSelected ? 'text-ds-on-primary-container' : 'text-ds-on-surface-variant'
                }`}
              >
                {option.description}
              </span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
