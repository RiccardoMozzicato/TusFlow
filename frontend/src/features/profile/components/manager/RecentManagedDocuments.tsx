import Icon from '@/components/Icon'
import type { ManagedDocument } from '../../types'

interface RecentManagedDocumentsProps {
  documents: ManagedDocument[]
}

function DocumentRow({ name, meta, icon, iconClassName, tag }: ManagedDocument) {
  return (
    <div className="group flex cursor-pointer items-center gap-6 px-8 py-4 transition-colors hover:bg-ds-surface-container-low">
      <div className={`flex h-12 w-12 items-center justify-center rounded-ds-lg bg-ds-surface-container ${iconClassName}`}>
        <Icon name={icon} className="text-3xl" />
      </div>
      <div className="min-w-0 flex-1">
        <h4 className="truncate font-ds-body text-ds-body-md font-semibold text-ds-on-surface">
          {name}
        </h4>
        <p className="font-ds-label text-ds-label-md text-ds-on-surface-variant">{meta}</p>
      </div>
      <div className="flex items-center gap-8">
        <span className="rounded-ds-full bg-ds-surface-container-highest px-4 py-1 font-ds-label text-ds-label-sm text-ds-on-secondary-container">
          {tag}
        </span>
        <button
          type="button"
          aria-label="Document actions"
          className="rounded-ds-full p-2 opacity-0 transition-all hover:bg-ds-surface-container group-hover:opacity-100"
        >
          <Icon name="more_vert" filled={false} />
        </button>
      </div>
    </div>
  )
}

// Recently managed documents list (manager only).
export default function RecentManagedDocuments({
  documents,
}: RecentManagedDocumentsProps) {
  return (
    <div className="overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-sm">
      <div className="flex items-center justify-between border-b border-ds-outline-variant px-8 py-6">
        <h3 className="font-ds-heading text-ds-headline-sm text-ds-primary">
          Recently Managed Documents
        </h3>
        <button
          type="button"
          className="font-ds-label text-ds-label-md text-ds-primary hover:underline"
        >
          View All
        </button>
      </div>
      <div className="divide-y divide-ds-outline-variant">
        {documents.map((doc) => (
          <DocumentRow key={doc.name} {...doc} />
        ))}
      </div>
    </div>
  )
}
