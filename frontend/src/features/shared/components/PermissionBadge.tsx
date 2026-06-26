import Icon from '@/components/Icon'
import type { SharePermission } from '../types'

const STYLES: Record<SharePermission, { label: string; icon: string; className: string }> = {
  edit: {
    label: 'Can Edit',
    icon: 'edit',
    className: 'bg-ds-tertiary-fixed text-ds-on-tertiary-fixed',
  },
  view: {
    label: 'View Only',
    icon: 'visibility',
    className: 'bg-ds-secondary-container text-ds-on-secondary-container',
  },
}

export default function PermissionBadge({ permission }: { permission: SharePermission }) {
  const { label, icon, className } = STYLES[permission]
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-ds-full px-2 py-0.5 font-ds-label text-ds-label-sm ${className}`}
    >
      <Icon name={icon} className="text-[14px]" />
      {label}
    </span>
  )
}
