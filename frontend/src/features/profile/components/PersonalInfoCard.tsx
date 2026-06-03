import Icon from '@/components/Icon'
import type { UserInfo } from '@/features/auth/types'

interface PersonalInfoCardProps {
  user: UserInfo
}

interface InfoRow {
  icon: string
  label: string
  value: string
}

// Base personal-information panel built from real userinfo. Shown to every
// role on /profile.
export default function PersonalInfoCard({ user }: PersonalInfoCardProps) {
  const rows: InfoRow[] = [
    { icon: 'person', label: 'Username', value: user.username || '—' },
    { icon: 'mail', label: 'Email', value: user.email || '—' },
    { icon: 'badge', label: 'Department', value: user.department || '—' },
    { icon: 'business', label: 'Organization', value: user.organization || '—' },
  ]

  return (
    <div className="space-y-6">
      <h3 className="font-ds-heading text-ds-headline-sm text-ds-on-surface">
        Personal Information
      </h3>
      <div className="overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-sm">
        <dl className="divide-y divide-ds-outline-variant">
          {rows.map((row) => (
            <div key={row.label} className="flex items-center gap-4 px-6 py-4">
              <div className="rounded-ds-lg bg-ds-surface-container p-3 text-ds-primary">
                <Icon name={row.icon} filled={false} />
              </div>
              <div className="flex min-w-0 flex-col">
                <dt className="font-ds-label text-ds-label-md text-ds-secondary">
                  {row.label}
                </dt>
                <dd className="truncate font-ds-body text-ds-body-md text-ds-on-surface">
                  {row.value}
                </dd>
              </div>
            </div>
          ))}
        </dl>
      </div>
    </div>
  )
}
