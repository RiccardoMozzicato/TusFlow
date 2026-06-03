import { Link } from 'react-router-dom'
import Icon from '@/components/Icon'
import type { ManagementLink } from '../../types'

interface QuickLinksProps {
  links: ManagementLink[]
}

function LinkBody({ label, icon }: ManagementLink) {
  return (
    <>
      <div className="flex items-center gap-4">
        <div className="flex h-10 w-10 items-center justify-center rounded-ds-lg bg-ds-surface-container-high group-hover:bg-white/20">
          <Icon name={icon} filled={false} className="text-ds-primary group-hover:text-white" />
        </div>
        <span className="font-ds-label text-ds-label-md font-bold text-ds-primary group-hover:text-white">
          {label}
        </span>
      </div>
      <Icon
        name="chevron_right"
        filled={false}
        className="text-ds-outline group-hover:text-white"
      />
    </>
  )
}

const ROW_CLASS =
  'group flex items-center justify-between rounded-ds-xl border border-ds-outline-variant bg-white p-6 transition-all duration-200 hover:border-ds-primary hover:bg-ds-primary'

// Staff personal quick links. Items without a `to` are placeholders until
// those pages exist.
export default function QuickLinks({ links }: QuickLinksProps) {
  return (
    <div className="flex flex-col gap-6">
      {links.map((link) =>
        link.to ? (
          <Link key={link.label} to={link.to} className={ROW_CLASS}>
            <LinkBody {...link} />
          </Link>
        ) : (
          <a key={link.label} href="#" className={ROW_CLASS}>
            <LinkBody {...link} />
          </a>
        ),
      )}
    </div>
  )
}
