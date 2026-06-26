import Icon from '@/components/Icon'
import { initialsOf } from '@/lib/initials'

interface StaffProfileHeaderProps {
  name: string
  roleLabel: string
  department: string,
  toggleShowProfileModal: () => void,
  photoProfileUrl: string | null
}

// Staff profile header: large rounded initials avatar with an edit affordance,
// name, role badge, department, and Share / Manage Account actions.
export default function StaffProfileHeader({
  name,
  roleLabel,
  department,
  toggleShowProfileModal,
  photoProfileUrl
}: StaffProfileHeaderProps) {
  return (
    <section className="mb-12 flex flex-col gap-8 md:flex-row md:items-end">
      <div className="relative">
          <div className="flex h-32 w-32 items-center justify-center overflow-hidden rounded-2xl border-4 border-white bg-ds-primary font-ds-heading text-ds-display-lg text-ds-on-primary shadow-xl">
              { photoProfileUrl
                ? <img src={photoProfileUrl} alt={name} className="h-full w-full object-cover" />
                : initialsOf(name) }
          </div>
          <button
          type="button"
          aria-label="Edit profile picture"
          className="absolute -bottom-2 -right-2 rounded-ds-lg bg-ds-primary p-2 text-ds-on-primary shadow-lg transition-transform hover:scale-110"
          onClick={toggleShowProfileModal}
        >
          <Icon name="edit" filled={false} className="text-sm" />
        </button>
      </div>

      <div className="flex-1 pb-1">
        <h2 className="mb-1 font-ds-heading text-ds-display-lg text-ds-on-surface">{name}</h2>
        <div className="flex items-center gap-4">
          <span className="rounded-ds-full bg-ds-primary-container px-4 py-2 font-ds-label text-ds-label-md text-ds-on-primary-container">
            {roleLabel}
          </span>
          <span className="flex items-center gap-1.5 font-ds-body text-ds-body-md text-ds-on-surface-variant">
            <Icon name="domain" filled={false} className="text-base" />
            {department || '—'}
          </span>
        </div>
      </div>

      <div className="flex gap-4">
        <button
          type="button"
          className="flex items-center gap-2 rounded-ds-xl border border-ds-outline-variant bg-white px-8 py-3 font-ds-label text-ds-label-md text-ds-primary transition-colors hover:bg-ds-surface-container"
        >
          <Icon name="share" filled={false} />
          Share Profile
        </button>
        <button
          type="button"
          className="flex items-center gap-2 rounded-ds-xl bg-ds-primary px-8 py-3 font-ds-label text-ds-label-md text-ds-on-primary transition-opacity hover:opacity-90"
        >
          <Icon name="settings" filled={false} />
          Manage Account
        </button>
      </div>
    </section>
  )
}
