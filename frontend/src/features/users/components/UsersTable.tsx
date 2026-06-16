import Icon from '@/components/Icon'
import { initialsOf } from '@/lib/initials'
import { storageSummary } from '../storageSummary'
import { formatQuota } from '../userQuota'
import type { UserDTO } from '../usersApi'
import RoleBadge from './RoleBadge'

const HEADERS = ['User', 'Department', 'Role', 'Storage']

function StorageCell({ user }: { user: UserDTO }) {
  const summary = storageSummary(user.storageUsed ?? 0, user.storageQuota ?? null)

  return (
    <div className="min-w-[140px]">
      <p className="font-ds-label text-ds-label-sm text-ds-on-surface">
        {summary.usedLabel} / {formatQuota(user.storageQuota)}
      </p>
      {summary.quotaLabel ? (
        <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-ds-full bg-ds-surface-container-high">
          <div
            className={`h-full rounded-ds-full ${
              summary.isNearLimit ? 'bg-ds-error' : 'bg-ds-primary'
            }`}
            style={{ width: `${Math.round(summary.fraction * 100)}%` }}
          />
        </div>
      ) : null}
    </div>
  )
}

function ActionButton({
  icon,
  title,
  onClick,
  tone = 'text-ds-secondary',
}: {
  icon: string
  title: string
  onClick: () => void
  tone?: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={title}
      aria-label={title}
      className={`flex h-9 w-9 items-center justify-center rounded-ds ${tone} transition-all duration-200 hover:bg-ds-surface-container-high hover:text-ds-primary`}
    >
      <Icon name={icon} filled={false} className="text-[18px]" />
    </button>
  )
}

function UserRow({
  user,
  last,
  onEdit,
  onDelete,
}: {
  user: UserDTO
  last: boolean
  onEdit: (user: UserDTO) => void
  onDelete: (user: UserDTO) => void
}) {
  return (
    <tr
      className={`group transition-colors hover:bg-ds-surface/60 ${
        last ? '' : 'border-b border-ds-outline-variant/30'
      }`}
    >
      <td className="px-6 py-4">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-ds-full bg-ds-secondary-fixed font-ds-label text-[11px] font-bold text-ds-on-secondary-fixed">
            {initialsOf(user.fullName || user.email)}
          </span>
          <div className="min-w-0">
            <p className="truncate font-semibold text-ds-on-surface">
              {user.fullName || '—'}
            </p>
            <p className="truncate font-ds-label text-ds-label-sm text-ds-secondary">
              {user.email}
            </p>
          </div>
        </div>
      </td>
      <td className="px-6 py-4 text-ds-secondary">{user.department || '—'}</td>
      <td className="px-6 py-4">
        <RoleBadge role={user.role} />
      </td>
      <td className="px-6 py-4">
        <StorageCell user={user} />
      </td>
      <td className="px-6 py-4">
        <div className="flex items-center justify-end gap-1">
          <ActionButton icon="edit" title="Edit user" onClick={() => onEdit(user)} tone="text-ds-primary" />
          <ActionButton
            icon="delete"
            title="Delete user"
            onClick={() => onDelete(user)}
            tone="text-ds-error"
          />
        </div>
      </td>
    </tr>
  )
}

export default function UsersTable({
  users,
  onEdit,
  onDelete,
}: {
  users: UserDTO[]
  onEdit: (user: UserDTO) => void
  onDelete: (user: UserDTO) => void
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-left">
        <thead>
          <tr className="border-b border-ds-outline-variant bg-ds-surface-container font-ds-label text-ds-label-md text-ds-on-surface-variant">
            {HEADERS.map((header) => (
              <th key={header} className="px-6 py-3 font-medium">
                {header}
              </th>
            ))}
            <th className="px-6 py-3 text-right font-medium">Actions</th>
          </tr>
        </thead>
        <tbody className="font-ds-body text-ds-body-sm text-ds-on-surface">
          {users.map((user, index) => (
            <UserRow
              key={user.id}
              user={user}
              last={index === users.length - 1}
              onEdit={onEdit}
              onDelete={onDelete}
            />
          ))}
        </tbody>
      </table>
    </div>
  )
}
