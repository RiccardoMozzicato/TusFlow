import { Link } from 'react-router-dom'
import Icon from '@/components/Icon'
import type { ManagementLink } from '../types'

interface ManagementLinksProps {
  links: ManagementLink[]
}

function LinkRow({ label, icon }: ManagementLink) {
  return (
    <>
      <div className="flex items-center gap-4">
        <div className="rounded-ds-lg bg-ds-surface-container p-3 transition-colors group-hover:bg-ds-primary group-hover:text-ds-on-primary">
          <Icon name={icon} filled={false} />
        </div>
        <span className="font-ds-body text-ds-body-md text-ds-on-surface">{label}</span>
      </div>
      <Icon name="chevron_right" filled={false} className="text-ds-outline" />
    </>
  )
}

const ROW_CLASS =
  'group flex items-center justify-between p-6 transition-colors hover:bg-ds-surface-container'

// Admin-only quick links to management surfaces. Items without a `to` are
// placeholders until those pages exist.
export default function ManagementLinks({ links }: ManagementLinksProps) {
  return (
    <div className="space-y-6">
      <h3 className="font-ds-heading text-ds-headline-sm text-ds-on-surface">Management</h3>
      <div className="overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-sm">
        <div className="divide-y divide-ds-outline-variant">
          {links.map((link) =>
            link.to ? (
              <Link key={link.label} to={link.to} className={ROW_CLASS}>
                <LinkRow {...link} />
              </Link>
            ) : (
              <a key={link.label} href="#" className={ROW_CLASS}>
                <LinkRow {...link} />
              </a>
            ),
          )}
        </div>
      </div>
    </div>
  )
}
