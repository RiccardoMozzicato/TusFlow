import { useRef, useState } from 'react'
import Icon from '@/components/Icon'
import type { UserDTO } from '@/features/users/usersApi'
import { filterUserSuggestions } from '@/features/users/userSuggestions'

// No suggestions are excluded for the owner filter (unlike sharing, which hides
// already-shared users). A stable empty set keeps the filter helper happy.
const NO_EXCLUSIONS: ReadonlySet<string> = new Set()

// Preferred dropdown height; the real cap shrinks to whatever space the viewport
// leaves, and the list flips above the input when there's more room up there.
const DROPDOWN_MAX_PX = 240
const VIEWPORT_MARGIN_PX = 8

interface OwnerFilterComboboxProps {
  users: UserDTO[]
  isLoading: boolean
  loadError: string | null
  /** Loads the user list lazily the first time the field is focused. */
  onFocusLoad: () => void
  /** Fires with the picked owner's id, or null when the filter is cleared. */
  onSelect: (ownerId: string | null) => void
}

// Autocomplete for the "owner" filter on the Shared-with-me page: suggests org
// users by name/email (from the user-service) and reports the picked owner's id.
// Reuses the suggestion-ranking and lazy-load utilities shared with the share UI.
export default function OwnerFilterCombobox({
  users,
  isLoading,
  loadError,
  onFocusLoad,
  onSelect,
}: OwnerFilterComboboxProps) {
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<UserDTO | null>(null)
  const [isOpen, setIsOpen] = useState(false)
  const [highlight, setHighlight] = useState(0)
  // Where the dropdown sits relative to the input, so it never spills off-screen
  // (the field lives low in the filter rail). Recomputed each time it opens.
  const [placement, setPlacement] = useState({ openUp: false, maxHeight: DROPDOWN_MAX_PX })
  const inputRef = useRef<HTMLInputElement>(null)

  // A picked owner settles the list; otherwise rank suggestions against the query.
  const suggestions = selected ? [] : filterUserSuggestions(users, query, NO_EXCLUSIONS)

  // Flip above the input when there's more room there than below, and cap the
  // height to the space actually available on the chosen side.
  function measurePlacement() {
    const rect = inputRef.current?.getBoundingClientRect()
    if (!rect) return
    const spaceBelow = window.innerHeight - rect.bottom
    const spaceAbove = rect.top
    const openUp = spaceBelow < DROPDOWN_MAX_PX && spaceAbove > spaceBelow
    const available = (openUp ? spaceAbove : spaceBelow) - VIEWPORT_MARGIN_PX
    setPlacement({ openUp, maxHeight: Math.max(120, Math.min(DROPDOWN_MAX_PX, available)) })
  }

  function openWith(value: string) {
    setQuery(value)
    setSelected(null)
    measurePlacement()
    setIsOpen(true)
    setHighlight(0)
  }

  function pick(user: UserDTO) {
    setSelected(user)
    setQuery(user.fullName)
    setIsOpen(false)
    onSelect(user.id)
  }

  function clear() {
    setSelected(null)
    setQuery('')
    setIsOpen(false)
    onSelect(null)
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
    <div className="relative">
      <div className="relative">
        <input
          ref={inputRef}
          type="text"
          role="combobox"
          aria-expanded={showDropdown}
          aria-autocomplete="list"
          value={query}
          onFocus={() => {
            onFocusLoad()
            if (!selected) {
              measurePlacement()
              setIsOpen(true)
            }
          }}
          onChange={(e) => openWith(e.target.value)}
          onKeyDown={handleKeyDown}
          // Delay close so an option's click registers before blur hides it.
          onBlur={() => window.setTimeout(() => setIsOpen(false), 120)}
          placeholder="Search by name or email"
          className="w-full rounded-ds-md border border-ds-outline-variant bg-ds-surface-container-lowest px-3 py-2 pr-8 font-ds-body text-ds-body-sm text-ds-on-surface outline-none transition-all placeholder:text-ds-secondary focus:border-ds-primary focus:ring-1 focus:ring-ds-primary"
        />
        {hasQuery ? (
          <button
            type="button"
            aria-label="Clear owner filter"
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
          style={{ maxHeight: placement.maxHeight }}
          className={`absolute z-10 w-full overflow-auto rounded-ds-md border border-ds-outline-variant bg-ds-surface-container-lowest py-1 shadow-lg ${
            placement.openUp ? 'bottom-full mb-1' : 'top-full mt-1'
          }`}
        >
          {isLoading ? (
            <li className="px-3 py-2 font-ds-body text-ds-body-sm text-ds-secondary">
              Loading users…
            </li>
          ) : loadError ? (
            <li className="px-3 py-2 font-ds-body text-ds-body-sm text-ds-error">
              Couldn't load users.
            </li>
          ) : suggestions.length === 0 ? (
            <li className="px-3 py-2 font-ds-body text-ds-body-sm text-ds-secondary">
              No matching users.
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
                </button>
              </li>
            ))
          )}
        </ul>
      ) : null}
    </div>
  )
}
