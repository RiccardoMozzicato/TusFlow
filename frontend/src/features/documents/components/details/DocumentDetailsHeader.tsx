import { Fragment, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Icon from '@/components/Icon'
import type { Breadcrumb } from '../../types'

interface DocumentDetailsHeaderProps {
  breadcrumbs: Breadcrumb[]
  /** Prints the current document. */
  onPrint: () => void
  /** Busy flag while the document is being fetched for printing. */
  isPrinting: boolean
  /** Downloads the current document. */
  onDownload: () => void
  /** Busy flag while the document is being downloaded. */
  isDownloading: boolean
  /** Whether to surface the destructive "Delete file" action (needs write access). */
  canDelete: boolean
  onDeleteFile: () => void
}

// Location trail + lightweight header actions (print / download / overflow
// menu). The trailing crumb (no `to`) renders as the current, non-link page
// title.
export default function DocumentDetailsHeader({
  breadcrumbs,
  onPrint,
  isPrinting,
  onDownload,
  isDownloading,
  canDelete,
  onDeleteFile,
}: DocumentDetailsHeaderProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  // Close the overflow menu on outside-click and Escape.
  useEffect(() => {
    if (!menuOpen) return
    const onPointer = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMenuOpen(false)
    }
    window.addEventListener('mousedown', onPointer)
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener('mousedown', onPointer)
      window.removeEventListener('keydown', onKey)
    }
  }, [menuOpen])

  return (
    <div className="flex flex-col justify-between gap-3 md:flex-row md:items-center">
      <nav
        aria-label="Breadcrumb"
        className="flex items-center gap-2 font-ds-body text-ds-body-sm text-ds-secondary"
      >
        {breadcrumbs.map((crumb, index) => {
          const isLast = index === breadcrumbs.length - 1
          return (
            <Fragment key={`${crumb.label}-${index}`}>
              {crumb.to && !isLast ? (
                <Link to={crumb.to} className="transition-colors hover:text-ds-primary">
                  {crumb.label}
                </Link>
              ) : (
                <span className="max-w-[200px] truncate font-medium text-ds-on-surface md:max-w-none">
                  {crumb.label}
                </span>
              )}
              {!isLast ? (
                <Icon name="chevron_right" filled={false} className="text-[16px]" />
              ) : null}
            </Fragment>
          )
        })}
      </nav>

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onPrint}
          disabled={isPrinting}
          title="Print"
          aria-label="Print"
          className="rounded-ds-md p-2 text-ds-secondary transition-colors hover:bg-ds-surface-container hover:text-ds-primary disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Icon
            name={isPrinting ? 'hourglass_empty' : 'print'}
            filled={false}
            className="text-[18px]"
          />
        </button>

        <button
          type="button"
          onClick={onDownload}
          disabled={isDownloading}
          title="Download"
          aria-label="Download"
          className="rounded-ds-md p-2 text-ds-secondary transition-colors hover:bg-ds-surface-container hover:text-ds-primary disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Icon
            name={isDownloading ? 'hourglass_empty' : 'download'}
            filled={false}
            className="text-[18px]"
          />
        </button>

        {/* Overflow menu — only rendered when there is an action to offer. */}
        {canDelete ? (
          <div ref={menuRef} className="relative">
            <button
              type="button"
              title="More actions"
              aria-label="More actions"
              aria-haspopup="menu"
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((open) => !open)}
              className="rounded-ds-md p-2 text-ds-secondary transition-colors hover:bg-ds-surface-container hover:text-ds-primary"
            >
              <Icon name="more_vert" filled={false} className="text-[18px]" />
            </button>

            {menuOpen ? (
              <div
                role="menu"
                className="absolute right-0 z-10 mt-1 min-w-[180px] overflow-hidden rounded-ds-lg border border-ds-outline-variant bg-ds-surface-container-lowest py-1 shadow-xl"
              >
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setMenuOpen(false)
                    onDeleteFile()
                  }}
                  className="flex w-full items-center gap-2 px-4 py-2 text-left font-ds-label text-ds-label-md text-ds-error transition-colors hover:bg-ds-surface-container"
                >
                  <Icon name="delete" filled={false} className="text-[18px]" />
                  Delete file
                </button>
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  )
}
