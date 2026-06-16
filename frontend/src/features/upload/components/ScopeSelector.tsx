import { SharingScope } from '@/features/documents/documentsApi'
import { ALL_SCOPE_OPTIONS, type ScopeOption } from '../uploadScopes'

interface ScopeSelectorProps {
  scope: SharingScope
  onScopeChange: (scope: SharingScope) => void
  /** Selectable scopes; defaults to all. Callers narrow this by role. */
  scopes?: ScopeOption[]
}

// Segmented control for the sharing scope applied to files added next. Lives in
// the page header so it doesn't add vertical height above the drop zone.
export default function ScopeSelector({
  scope,
  onScopeChange,
  scopes = ALL_SCOPE_OPTIONS,
}: ScopeSelectorProps) {
  return (
    <div className="flex items-center gap-2">
      <span className="font-ds-label text-ds-label-sm text-ds-on-surface-variant">
        Sharing scope
      </span>
      <div className="flex rounded-ds-lg border border-ds-outline-variant p-0.5">
        {scopes.map((option) => (
          <button
            key={option.value}
            type="button"
            onClick={() => onScopeChange(option.value)}
            className={`rounded-ds px-3 py-1 font-ds-label text-ds-label-sm transition-colors ${
              scope === option.value
                ? 'bg-ds-primary text-ds-on-primary'
                : 'text-ds-on-surface-variant hover:bg-ds-surface-variant'
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>
    </div>
  )
}
