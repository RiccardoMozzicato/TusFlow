import { useEffect, useRef, useState } from 'react'
import Icon from '@/components/Icon'

interface DocumentActionsMenuProps {
  /** Opens the file information modal; shown on every row (incl. read-only). */
  onInfo?: () => void
  onShare?: () => void
  onNewVersion?: () => void
  onDelete?: () => void
}

interface MenuItem {
  label: string
  icon: string
  onSelect: () => void
  /** Renders the item in the error colour (used for destructive actions). */
  destructive?: boolean
}

// A `more_vert` trigger with a popover of document actions: Information (opens the
// file info modal) plus, when the caller may modify the file, Share / New Version /
// Delete. Mirrors the click-outside + Escape pattern used by DocumentDetailsHeader.
// All buttons stop propagation so they never trigger the surrounding row/card nav.
export default function DocumentActionsMenu({
  onInfo,
  onShare,
  onNewVersion,
  onDelete,
}: DocumentActionsMenuProps) {
  const [open, setOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onPointer = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) setOpen(false)
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    window.addEventListener('mousedown', onPointer)
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener('mousedown', onPointer)
      window.removeEventListener('keydown', onKey)
    }
  }, [open])

  const items: MenuItem[] = [
    onInfo && { label: 'Information', icon: 'info', onSelect: onInfo },
    onShare && { label: 'Share', icon: 'share', onSelect: onShare },
    onNewVersion && { label: 'New Version', icon: 'upload_file', onSelect: onNewVersion },
    onDelete && { label: 'Delete', icon: 'delete', onSelect: onDelete, destructive: true },
  ].filter((item): item is MenuItem => Boolean(item))

  // Nothing to offer — don't render an empty trigger.
  if (items.length === 0) return null

  // preventDefault + stopPropagation so a click never activates the surrounding
  // anchor (card view) or row navigation (table view).
  function select(event: React.MouseEvent, onSelect: () => void) {
    event.preventDefault()
    event.stopPropagation()
    setOpen(false)
    onSelect()
  }

  return (
    <div ref={menuRef} className="relative inline-block text-left">
      <button
        type="button"
        title="More actions"
        aria-label="More actions"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={(event) => {
          event.preventDefault()
          event.stopPropagation()
          setOpen((value) => !value)
        }}
        className="rounded-ds-full p-1.5 text-ds-secondary transition-colors hover:bg-ds-surface-container-high hover:text-ds-primary"
      >
        <Icon name="more_vert" filled={false} className="text-[18px]" />
      </button>

      {open ? (
        <div
          role="menu"
          className="absolute right-0 z-20 mt-2 min-w-[180px] overflow-hidden rounded-ds-lg border border-ds-outline-variant bg-ds-surface-container-lowest py-1 shadow-xl"
        >
          {items.map((item) => (
            <button
              key={item.label}
              type="button"
              role="menuitem"
              onClick={(event) => select(event, item.onSelect)}
              className={`flex w-full items-center gap-2 px-4 py-2 text-left font-ds-label text-ds-label-md transition-colors ${
                item.destructive
                  ? 'text-ds-error hover:bg-ds-error-container'
                  : 'text-ds-on-surface hover:bg-ds-surface-container'
              }`}
            >
              <Icon name={item.icon} filled={false} className="text-[18px]" />
              {item.label}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  )
}
