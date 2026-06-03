import Icon from '@/components/Icon'

// TODO: wire search + notifications to real data/handlers.
export default function Topbar() {
  return (
    <header className="sticky top-0 z-10 flex h-16 w-full items-center justify-between border-b border-ds-outline-variant bg-ds-surface pl-6 pr-10 shadow-sm">
      <div className="group relative w-96">
        <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
          <Icon name="search" filled={false} className="text-ds-on-surface-variant" />
        </div>
        <input
          type="text"
          placeholder="Search documents, folders, or users..."
          className="block w-full rounded-ds-lg border border-ds-outline-variant bg-ds-surface-container-lowest py-2 pl-10 pr-3 text-ds-body-sm leading-5 placeholder-ds-on-surface-variant transition-all focus:border-ds-primary focus:outline-none focus:ring-2 focus:ring-ds-primary"
        />
      </div>
      <div className="flex items-center gap-4">
        <button
          type="button"
          aria-label="Notifications"
          className="relative rounded-ds-full p-2 text-ds-on-surface-variant transition-all hover:bg-ds-surface-container-low"
        >
          <Icon name="notifications" />
          <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-ds-full bg-ds-error" />
        </button>
      </div>
    </header>
  )
}
