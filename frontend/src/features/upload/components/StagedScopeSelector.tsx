import { SharingScope } from '@/features/documents/documentsApi'
import { ALL_SCOPE_OPTIONS, type ScopeOption } from '../uploadScopes'

interface StagedScopeSelectorProps {
  scopes: ScopeOption[]
  /** What "Default" currently resolves to. */
  generalScope: SharingScope
  /** Current per-file override; undefined means inherit the general scope. */
  override?: SharingScope
  onChange: (scope?: SharingScope) => void
}

const PILL_BASE =
  'rounded-ds px-3 py-1 font-ds-label text-ds-label-sm transition-colors'
const PILL_ACTIVE = 'bg-ds-primary text-ds-on-primary'
const PILL_INACTIVE = 'text-ds-on-surface-variant hover:bg-ds-surface-variant'

// Compact segmented control letting a single staged file override the general
// scope. The leading "Default" pill inherits the general scope (and shows what
// that currently is); the rest pin a specific scope for this file only.
export default function StagedScopeSelector({
  scopes,
  generalScope,
  override,
  onChange,
}: StagedScopeSelectorProps) {
  const generalLabel =
    ALL_SCOPE_OPTIONS.find((option) => option.value === generalScope)?.label ?? generalScope
  const inherits = override === undefined

  return (
    <div className="flex items-center gap-2">
      <span className="font-ds-label text-ds-label-sm text-ds-on-surface-variant">Scope</span>
      <div className="flex flex-wrap gap-1 rounded-ds-lg border border-ds-outline-variant p-0.5">
        <button
          type="button"
          onClick={() => onChange(undefined)}
          aria-pressed={inherits}
          className={`${PILL_BASE} ${inherits ? PILL_ACTIVE : PILL_INACTIVE}`}
        >
          {`Default · ${generalLabel}`}
        </button>
        {scopes.map((option) => {
          const isActive = override === option.value
          return (
            <button
              key={option.value}
              type="button"
              onClick={() => onChange(option.value)}
              aria-pressed={isActive}
              className={`${PILL_BASE} ${isActive ? PILL_ACTIVE : PILL_INACTIVE}`}
            >
              {option.label}
            </button>
          )
        })}
      </div>
    </div>
  )
}
