import { initialsOf } from '@/lib/initials'
import RoleBadge from '@/features/users/components/RoleBadge'
import type { UserDTO } from '@/features/users/usersApi'

// Read-only roster shown when the admin opens the Total Users tile.
export default function UsersOverviewList({ users }: { users: UserDTO[] }) {
  if (users.length === 0) {
    return <p className="py-6 text-center font-ds-body text-ds-body-sm text-ds-secondary">No users.</p>
  }

  return (
    <ul className="divide-y divide-ds-outline-variant/40">
      {users.map((user) => (
        <li key={user.id} className="flex items-center gap-3 py-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-ds-full bg-ds-secondary-fixed font-ds-label text-[10px] font-bold text-ds-on-secondary-fixed">
            {initialsOf(user.fullName || user.email)}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate font-semibold text-ds-on-surface">{user.fullName || '—'}</p>
            <p className="truncate font-ds-label text-ds-label-sm text-ds-secondary">
              {user.email}
              {user.department ? ` · ${user.department}` : ''}
            </p>
          </div>
          <RoleBadge role={user.role} />
        </li>
      ))}
    </ul>
  )
}
