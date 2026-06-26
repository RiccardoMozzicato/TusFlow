import { useEffect, useRef, useState } from 'react'
import Icon from '@/components/Icon'
import type { UserDTO } from '@/features/users/usersApi'
import { filterUserSuggestions, isUuid } from '@/features/users/userSuggestions'

// Resolved share target: either a picked user or a pasted raw UUID.
export interface ShareTarget {
  userId: string
  label: string
}

interface ShareUserComboboxProps {
  users: UserDTO[]
  isLoading: boolean
  loadError: string | null
  /** Ids to hide from suggestions (already shared, owner). */
  excludeIds: ReadonlySet<string>
  disabled?: boolean
  /** Loads the user list lazily the first time the field is focused. */
  onFocusLoad: () => void
  /** Fires whenever the resolvable target changes (null when unresolved). */
  onResolvedChange: (target: ShareTarget | null) => void
}

function resolveTarget(selected: UserDTO | null, query: string): ShareTarget | null {
  if (selected) return { userId: selected.id, label: selected.fullName }
  const trimmed = query.trim()
  if (isUuid(trimmed)) return { userId: trimmed, label: trimmed }
  return null
}

function RoleChip({ role }: { role: string }) {
  return (
    <span className="rounded-ds-full bg-ds-surface-variant px-1.5 py-0.5 font-ds-label text-[10px] uppercase tracking-wide text-ds-on-surface-variant">
      {role.replace(/^ROLE_/, '')}
    </span>
  )
}

// Autocomplete for the share input: suggests org users by name/email, with a
// pasted UUID still accepted as a fallback target.
export default function ShareUserCombobox({
  users,
  isLoading,
  loadError,
  excludeIds,
  disabled,
  onFocusLoad,
  onResolvedChange,
}: ShareUserComboboxProps) {
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<UserDTO | null>(null)
  const [isOpen, setIsOpen] = useState(false)
  const [highlight, setHighlight] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)

  // A selected user means the list is settled; otherwise suggest against the query.
  const suggestions = selected ? [] : filterUserSuggestions(users, query, excludeIds)

  // Keep the parent's submit-enabled state in sync with what's resolvable.
  useEffect(() => {
    onResolvedChange(resolveTarget(selected, query))
  }, [selected, query, onResolvedChange])

  function openWith(value: string) {
    setQuery(value)
    setSelected(null)
    setIsOpen(true)
    setHighlight(0)
  }

  function pick(user: UserDTO) {
    setSelected(user)
    setQuery(user.fullName)
    setIsOpen(false)
  }

  function clear() {
    setSelected(null)
    setQuery('')
    setIsOpen(false)
    inputRef.current?.focus()
  }

  function handleKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (!isOpen || suggestions.length === 0) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setHighlight((h) => (h + 1) % suggestions.length)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setHighlight((h) => (h - 1 + suggestions.length) % suggestions.length)
    } else if (event.key === 'Enter') {
      event.preventDefault()
      pick(suggestions[highlight])
    } else if (event.key === 'Escape') {
      setIsOpen(false)
    }
  }

  const showDropdown = isOpen && !selected
  const hasQuery = query.trim().length > 0

  return (
    <div className="relative flex-1">
      <div className="relative">
        <input
          ref={inputRef}
          type="text"
          role="combobox"
          aria-expanded={showDropdown}
          aria-autocomplete="list"
          value={query}
          disabled={disabled}
          onFocus={() => {
            onFocusLoad()
            if (!selected) setIsOpen(true)
          }}
          onChange={(e) => openWith(e.target.value)}
          onKeyDown={handleKeyDown}
          // Delay close so an option's click registers before blur hides it.
          onBlur={() => window.setTimeout(() => setIsOpen(false), 120)}
          placeholder="Search by name, email, or paste a user ID"
          className="w-full rounded-ds-md border border-ds-outline-variant bg-ds-surface px-3 py-2 pr-8 font-ds-body text-ds-body-sm text-ds-on-surface outline-none transition-all placeholder:text-ds-secondary focus:border-ds-primary focus:ring-1 focus:ring-ds-primary disabled:opacity-50"
        />
        {hasQuery ? (
          <button
            type="button"
            aria-label="Clear"
            onClick={clear}
            className="absolute right-2 top-1/2 flex -translate-y-1/2 items-center text-ds-secondary transition-colors hover:text-ds-on-surface"
          >
            <Icon name={selected ? 'check_circle' : 'close'} className="text-[16px]" />
          </button>
        ) : null}
      </div>

      {showDropdown ? (
        <ul
          role="listbox"
          className="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-ds-md border border-ds-outline-variant bg-ds-surface-container-lowest py-1 shadow-lg"
        >
          {isLoading ? (
            <li className="px-3 py-2 font-ds-body text-ds-body-sm text-ds-secondary">
              Loading users…
            </li>
          ) : loadError ? (
            <li className="px-3 py-2 font-ds-body text-ds-body-sm text-ds-error">
              Couldn't load users — paste a user ID instead.
            </li>
          ) : suggestions.length === 0 ? (
            <li className="px-3 py-2 font-ds-body text-ds-body-sm text-ds-secondary">
              {isUuid(query) ? 'Press Share to use this ID.' : 'No matching users.'}
            </li>
          ) : (
            suggestions.map((user, index) => (
              <li key={user.id} role="option" aria-selected={index === highlight}>
                <button
                  type="button"
                  // Prevent the input's blur from firing before this click.
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => pick(user)}
                  onMouseEnter={() => setHighlight(index)}
                  className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-left transition-colors ${
                    index === highlight ? 'bg-ds-surface-container-high' : 'hover:bg-ds-surface'
                  }`}
                >
                  <span className="flex min-w-0 flex-col">
                    <span className="truncate font-ds-body text-ds-body-sm font-medium text-ds-on-surface">
                      {user.fullName}
                    </span>
                    <span className="truncate font-ds-label text-ds-label-sm text-ds-secondary">
                      {user.email}
                    </span>
                  </span>
                  <RoleChip role={user.role} />
                </button>
              </li>
            ))
          )}
        </ul>
      ) : null}
    </div>
  )
}
