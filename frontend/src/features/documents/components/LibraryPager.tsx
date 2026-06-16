import Icon from '@/components/Icon'

interface LibraryPagerProps {
  /** Zero-based index of the current page. */
  pageNumber: number
  totalPages: number
  isFirst: boolean
  isLast: boolean
  onPrev: () => void
  onNext: () => void
}

// Prev/Next pager for the document grid. Hidden when there is at most one page.
export default function LibraryPager({
  pageNumber,
  totalPages,
  isFirst,
  isLast,
  onPrev,
  onNext,
}: LibraryPagerProps) {
  if (totalPages <= 1) return null

  const buttonClass =
    'inline-flex items-center gap-1 rounded-ds border border-ds-outline-variant bg-ds-surface-container px-3 py-1.5 font-ds-label text-ds-label-sm text-ds-on-surface transition-colors hover:bg-ds-surface-container-high disabled:cursor-not-allowed disabled:opacity-40'

  return (
    <div className="flex items-center justify-between pt-6">
      <button type="button" onClick={onPrev} disabled={isFirst} className={buttonClass}>
        <Icon name="chevron_left" className="text-base" />
        Previous
      </button>

      <span className="font-ds-body text-ds-body-sm text-ds-secondary">
        Page {pageNumber + 1} of {totalPages}
      </span>

      <button type="button" onClick={onNext} disabled={isLast} className={buttonClass}>
        Next
        <Icon name="chevron_right" className="text-base" />
      </button>
    </div>
  )
}
