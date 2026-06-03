import { Fragment } from 'react'
import { Link } from 'react-router-dom'
import Icon from '@/components/Icon'
import type { Breadcrumb } from '../../types'

interface DocumentDetailsHeaderProps {
  breadcrumbs: Breadcrumb[]
}

// Location trail + lightweight header actions (print / overflow menu). The
// trailing crumb (no `to`) renders as the current, non-link page title.
export default function DocumentDetailsHeader({ breadcrumbs }: DocumentDetailsHeaderProps) {
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
          title="Print"
          aria-label="Print"
          className="rounded-ds-md p-2 text-ds-secondary transition-colors hover:bg-ds-surface-container hover:text-ds-primary"
        >
          <Icon name="print" filled={false} className="text-[18px]" />
        </button>
        <button
          type="button"
          title="More actions"
          aria-label="More actions"
          className="rounded-ds-md p-2 text-ds-secondary transition-colors hover:bg-ds-surface-container hover:text-ds-primary"
        >
          <Icon name="more_vert" filled={false} className="text-[18px]" />
        </button>
      </div>
    </div>
  )
}
