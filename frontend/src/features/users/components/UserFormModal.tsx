import { useEffect, useId, useState, type FormEvent } from 'react'
import Icon from '@/components/Icon'
import {
  bestUnitFor,
  parseQuota,
  quotaToInput,
  STORAGE_UNITS,
  type StorageUnit,
} from '../userQuota'
import { USER_ROLES, userRoleLabel, type UserRole } from '../userRoles'
import { hasErrors, validateNewUser, type NewUserFieldErrors } from '../userValidation'
import type { UserCreationPayload, UserDTO } from '../usersApi'

interface UserFormModalProps {
  /** Present → edit that user; absent → create a new user. */
  user?: UserDTO | null
  /** Pre-fills the organization field when creating. */
  defaultOrganization: string
  isSaving: boolean
  saveError: string | null
  onClose: () => void
  onCreate: (payload: UserCreationPayload) => void
  onUpdate: (user: UserDTO) => void
}

const INPUT_CLASS =
  'w-full rounded-ds-md border border-ds-outline-variant bg-ds-surface px-3 py-2 font-ds-body text-ds-body-sm text-ds-on-surface outline-none transition-colors focus:border-ds-primary focus:ring-1 focus:ring-ds-primary disabled:opacity-60'
const LABEL_CLASS = 'font-ds-label text-ds-label-md text-ds-on-surface-variant'

function Field({
  label,
  error,
  children,
}: {
  label: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className={LABEL_CLASS}>{label}</span>
      {children}
      {error ? <span className="font-ds-label text-ds-label-sm text-ds-error">{error}</span> : null}
    </label>
  )
}

// Create/edit dialog for a user. In edit mode only role and storage quota are
// editable — identity fields are passed through unchanged to satisfy the
// backend's full-DTO update validation.
export default function UserFormModal({
  user,
  defaultOrganization,
  isSaving,
  saveError,
  onClose,
  onCreate,
  onUpdate,
}: UserFormModalProps) {
  const isEdit = Boolean(user)
  const titleId = useId()

  const [username, setUsername] = useState('')
  const [email, setEmail] = useState(user?.email ?? '')
  const [organization, setOrganization] = useState(user?.organization ?? defaultOrganization)
  const [department, setDepartment] = useState(user?.department ?? '')
  const [role, setRole] = useState<UserRole>(
    (USER_ROLES as readonly string[]).includes(user?.role ?? '')
      ? (user!.role as UserRole)
      : 'ROLE_staff',
  )
  // Pick a sensible unit for an existing quota, then express the value in it.
  const initialUnit = bestUnitFor(user?.storageQuota)
  const [quotaUnit, setQuotaUnit] = useState<StorageUnit>(initialUnit)
  const [quotaValue, setQuotaValue] = useState(quotaToInput(user?.storageQuota, initialUnit))
  const [errors, setErrors] = useState<NewUserFieldErrors>({})

  // Close on Escape for keyboard users.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  function handleSubmit(e: FormEvent) {
    e.preventDefault()

    const quota = parseQuota(quotaValue, quotaUnit)
    const department_ = department.trim() || null

    // Edit: identity is fixed; only the quota number needs to be a valid value.
    // Unlimited stays allowed here (clearing a quota), unlike on creation.
    if (isEdit && user) {
      if (!quota.ok) {
        setErrors({ quota: 'Storage quota must be a non-negative number.' })
        return
      }
      setErrors({})
      onUpdate({ ...user, role, storageQuota: quota.bytes, department: department_ })
      return
    }

    const nextErrors = validateNewUser({
      username,
      email,
      organization,
      quotaOk: quota.ok,
      quotaBytes: quota.bytes,
    })
    if (hasErrors(nextErrors)) {
      setErrors(nextErrors)
      return
    }

    setErrors({})
    onCreate({
      username: username.trim(),
      email: email.trim(),
      organization: organization.trim(),
      role,
      department: department_,
      storageQuota: quota.bytes,
    })
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-lg overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-xl"
      >
        <div className="flex items-center justify-between border-b border-ds-outline-variant px-6 py-4">
          <h2 id={titleId} className="font-ds-heading text-ds-headline-sm text-ds-on-surface">
            {isEdit ? 'Edit user' : 'Add user'}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="flex h-9 w-9 items-center justify-center rounded-ds text-ds-secondary transition-colors hover:bg-ds-surface-container-high"
          >
            <Icon name="close" filled={false} />
          </button>
        </div>

        <form noValidate onSubmit={handleSubmit} className="space-y-4 px-6 py-5">
          {isEdit ? (
            <div className="rounded-ds-md bg-ds-surface-container px-4 py-3">
              <p className="font-semibold text-ds-on-surface">{user?.fullName || user?.email}</p>
              <p className="font-ds-label text-ds-label-sm text-ds-secondary">{user?.email}</p>
            </div>
          ) : (
            <>
              <Field label="Username" error={errors.username}>
                <input
                  className={INPUT_CLASS}
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoFocus
                  disabled={isSaving}
                />
              </Field>
              <Field label="Email" error={errors.email}>
                <input
                  type="email"
                  className={INPUT_CLASS}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  disabled={isSaving}
                />
              </Field>
              <Field label="Organization" error={errors.organization}>
                <input
                  className={INPUT_CLASS}
                  value={organization}
                  onChange={(e) => setOrganization(e.target.value)}
                  disabled={isSaving}
                />
              </Field>
            </>
          )}

          <Field label="Department (optional)">
            <input
              className={INPUT_CLASS}
              value={department}
              onChange={(e) => setDepartment(e.target.value)}
              disabled={isSaving}
            />
          </Field>

          <div className="grid grid-cols-2 gap-4">
            <Field label="Role">
              <select
                className={`${INPUT_CLASS} cursor-pointer`}
                value={role}
                onChange={(e) => setRole(e.target.value as UserRole)}
                disabled={isSaving}
              >
                {USER_ROLES.map((r) => (
                  <option key={r} value={r}>
                    {userRoleLabel(r)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Storage quota" error={errors.quota}>
              <div className="flex gap-2">
                <input
                  type="number"
                  min="0"
                  step="any"
                  aria-label="Storage quota"
                  placeholder={isEdit ? 'Unlimited' : 'e.g. 50'}
                  className={INPUT_CLASS}
                  value={quotaValue}
                  onChange={(e) => setQuotaValue(e.target.value)}
                  disabled={isSaving}
                />
                <select
                  aria-label="Storage quota unit"
                  className={`${INPUT_CLASS} w-auto cursor-pointer`}
                  value={quotaUnit}
                  onChange={(e) => setQuotaUnit(e.target.value as StorageUnit)}
                  disabled={isSaving}
                >
                  {STORAGE_UNITS.map((u) => (
                    <option key={u} value={u}>
                      {u}
                    </option>
                  ))}
                </select>
              </div>
            </Field>
          </div>

          {saveError ? (
            <p className="flex items-center gap-1.5 font-ds-body text-ds-body-sm text-ds-error">
              <Icon name="error" className="text-[16px]" />
              {saveError}
            </p>
          ) : null}

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={isSaving}
              className="rounded-ds-lg border border-ds-outline px-5 py-2.5 font-ds-label text-ds-label-md text-ds-on-surface transition-colors hover:bg-ds-surface-container disabled:opacity-60"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSaving}
              className="flex items-center gap-2 rounded-ds-lg bg-ds-primary px-5 py-2.5 font-ds-label text-ds-label-md text-ds-on-primary transition-opacity hover:opacity-90 disabled:opacity-60"
            >
              {isSaving ? (
                <Icon name="progress_activity" filled={false} className="animate-spin text-[18px]" />
              ) : null}
              {isEdit ? 'Save changes' : 'Create user'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
