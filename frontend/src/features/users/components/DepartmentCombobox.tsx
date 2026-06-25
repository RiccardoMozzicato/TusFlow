import { useState } from 'react'
import type { DepartmentDTO } from '../usersApi'
import { filterDepartmentSuggestions } from '../departmentSuggestions'

interface DepartmentComboboxProps {
  /** Current free-text value — the input string is the value. */
  value: string
  onChange: (value: string) => void
  departments: DepartmentDTO[]
  isLoading: boolean
  loadError: string | null
  disabled?: boolean
  /** Loads the department list lazily the first time the field is focused. */
  onFocusLoad: () => void
  /** Class for the text input, so it matches the surrounding form fields. */
  inputClassName: string
  placeholder?: string
}

// Autocomplete for the department field: suggests the org's existing departments
// as the user types, while still accepting a brand-new department (free text).
export default function DepartmentCombobox({
  value,
  onChange,
  departments,
  isLoading,
  loadError,
  disabled,
  onFocusLoad,
  inputClassName,
  placeholder,
}: DepartmentComboboxProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [highlight, setHighlight] = useState(0)

  const suggestions = filterDepartmentSuggestions(departments, value)
  const showDropdown = isOpen && (isLoading || Boolean(loadError) || suggestions.length > 0)

  function update(next: string) {
    onChange(next)
    setIsOpen(true)
    setHighlight(0)
  }

  function pick(name: string) {
    onChange(name)
    setIsOpen(false)
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
      pick(suggestions[highlight].name)
    } else if (event.key === 'Escape') {
      setIsOpen(false)
    }
  }

  return (
    <div className="relative">
      <input
        type="text"
        role="combobox"
        aria-expanded={showDropdown}
        aria-autocomplete="list"
        value={value}
        disabled={disabled}
        onFocus={() => {
          onFocusLoad()
          setIsOpen(true)
        }}
        onChange={(e) => update(e.target.value)}
        onKeyDown={handleKeyDown}
        // Delay close so an option's click registers before blur hides it.
        onBlur={() => window.setTimeout(() => setIsOpen(false), 120)}
        placeholder={placeholder}
        className={inputClassName}
      />

      {showDropdown ? (
        <ul
          role="listbox"
          className="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-ds-md border border-ds-outline-variant bg-ds-surface-container-lowest py-1 shadow-lg"
        >
          {isLoading ? (
            <li className="px-3 py-2 font-ds-body text-ds-body-sm text-ds-secondary">
              Loading departments…
            </li>
          ) : loadError ? (
            <li className="px-3 py-2 font-ds-body text-ds-body-sm text-ds-error">
              Couldn't load departments — type one in.
            </li>
          ) : (
            suggestions.map((dept, index) => (
              <li key={dept.name} role="option" aria-selected={index === highlight}>
                <button
                  type="button"
                  // Prevent the input's blur from firing before this click.
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => pick(dept.name)}
                  onMouseEnter={() => setHighlight(index)}
                  className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-left transition-colors ${
                    index === highlight ? 'bg-ds-surface-container-high' : 'hover:bg-ds-surface'
                  }`}
                >
                  <span className="truncate font-ds-body text-ds-body-sm text-ds-on-surface">
                    {dept.name}
                  </span>
                  <span className="shrink-0 font-ds-label text-ds-label-sm text-ds-secondary">
                    {dept.userCount}
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
