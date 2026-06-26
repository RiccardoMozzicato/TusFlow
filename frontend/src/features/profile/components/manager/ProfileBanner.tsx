import { initialsOf } from '@/lib/initials'

interface ProfileBannerProps {
  name: string
  /** Subtitle, e.g. "Department Manager". */
  title: string
  /** Optional second subtitle segment, e.g. the department name. */
  subtitle?: string
  photoProfileUrl?: string | null
  toggleShowProfileModal?: () => void
}

// Gradient banner header with an overlapping avatar (photo or initials) and an
// online presence dot. Used by the manager profile.
export default function ProfileBanner({ name, title, subtitle, photoProfileUrl, toggleShowProfileModal }: ProfileBannerProps) {
  return (
    <section className="overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-sm">
      <div className="relative h-32 bg-gradient-to-r from-ds-primary to-ds-primary-container">
        <div
          className="absolute inset-0 opacity-10"
          style={{
            backgroundImage:
              'radial-gradient(circle at 2px 2px, white 1px, transparent 0)',
            backgroundSize: '24px 24px',
          }}
        />
      </div>

      <div className="-mt-12 flex flex-col items-end gap-8 px-8 pb-8 md:flex-row">
        <div className="relative">
          <div className="flex h-32 w-32 items-center justify-center overflow-hidden rounded-ds-xl border-4 border-ds-surface-container-lowest bg-ds-primary font-ds-heading text-ds-display-lg text-ds-on-primary shadow-lg">
            {photoProfileUrl
              ? <img src={photoProfileUrl} alt={name} className="h-full w-full object-cover" />
              : initialsOf(name)}
          </div>
          <span className="absolute bottom-1 right-1 h-6 w-6 rounded-ds-full border-2 border-white bg-emerald-500" />
        </div>

        <div className="flex-1 pb-2">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <h2 className="font-ds-heading text-ds-display-lg text-ds-primary">{name}</h2>
              <p className="flex items-center gap-3 font-ds-body text-ds-body-lg text-ds-on-surface-variant">
                {title}
                {subtitle ? (
                  <>
                    <span className="h-1 w-1 rounded-ds-full bg-ds-outline-variant" />
                    {subtitle}
                  </>
                ) : null}
              </p>
            </div>
            <div className="flex gap-3">
              <button
                type="button"
                onClick={toggleShowProfileModal}
                className="rounded-ds-lg border border-ds-primary px-6 py-3 font-ds-label text-ds-label-md text-ds-primary transition-colors hover:bg-ds-surface-container"
              >
                Edit Profile
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
