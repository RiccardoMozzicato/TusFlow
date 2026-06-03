import { Link } from 'react-router-dom'
import Icon from '@/components/Icon'
import type { DocumentStatus, FileType, LibraryDocument } from '../types'

// Status badge styling, keyed by status. Mirrors the dashboard's STATUS_STYLES
// pattern but covers the library's four lifecycle states.
const STATUS_STYLES: Record<
  DocumentStatus,
  { label: string; className: string; icon?: string; dot?: boolean }
> = {
  processing: {
    label: 'Processing',
    className: 'bg-ds-primary-fixed text-ds-on-primary-fixed-variant',
    dot: true,
  },
  ready: {
    label: 'Ready',
    className: 'bg-ds-tertiary-fixed text-ds-on-tertiary-fixed-variant',
    icon: 'check_circle',
  },
  draft: {
    label: 'Draft',
    className: 'bg-ds-secondary-fixed text-ds-on-secondary-fixed-variant',
  },
  scan_failed: {
    label: 'Scan Failed',
    className: 'bg-ds-error-container text-ds-on-error-container',
    icon: 'error',
  },
}

// File-type icon chip styling, keyed by file type.
const FILE_TYPE_STYLES: Record<FileType, { icon: string; chipClassName: string }> = {
  pdf: { icon: 'picture_as_pdf', chipClassName: 'bg-ds-error-container text-ds-on-error-container' },
  docx: { icon: 'description', chipClassName: 'bg-ds-primary-fixed text-ds-primary' },
  image: { icon: 'image', chipClassName: 'bg-ds-secondary-fixed text-ds-secondary' },
  video: { icon: 'movie', chipClassName: 'bg-ds-surface-container-high text-ds-secondary' },
}

function StatusBadge({ status }: { status: DocumentStatus }) {
  const { label, className, icon, dot } = STATUS_STYLES[status]
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-ds px-2 py-1 font-ds-label text-ds-label-sm ${className}`}
    >
      {dot ? <span className="h-1.5 w-1.5 rounded-ds-full bg-ds-primary" /> : null}
      {icon ? <Icon name={icon} className="text-[12px]" /> : null}
      {label}
    </span>
  )
}

export default function DocumentCard({ doc }: { doc: LibraryDocument }) {
  const fileType = FILE_TYPE_STYLES[doc.fileType]
  const isFailed = doc.status === 'scan_failed'

  return (
    <Link
      to={`/documents/${doc.id}`}
      className={`group relative flex cursor-pointer flex-col gap-4 rounded-ds-xl border bg-ds-surface-container-lowest p-4 transition-all hover:shadow-md ${
        isFailed
          ? 'border-ds-error/30 hover:border-ds-error'
          : 'border-ds-outline-variant hover:border-ds-primary/50'
      }`}
    >
      <span className="absolute right-4 top-4 rounded-ds-full border border-ds-outline-variant/50 bg-ds-surface-container px-2 py-0.5 font-ds-label text-ds-label-sm text-ds-on-surface-variant">
        {doc.version}
      </span>

      <div className="flex items-start gap-3 pr-10">
        <div className={`shrink-0 rounded-ds-lg p-2 ${fileType.chipClassName}`}>
          <Icon name={fileType.icon} />
        </div>
        <h3 className="line-clamp-2 font-ds-heading text-ds-headline-sm text-ds-on-surface transition-colors group-hover:text-ds-primary">
          {doc.title}
        </h3>
      </div>

      <div className="mt-auto flex flex-col gap-1 pt-3">
        <div className="flex items-center justify-between font-ds-label text-ds-label-sm text-ds-secondary">
          <span className="flex items-center gap-1">
            <Icon name="person" className="text-[14px]" />
            {doc.owner}
          </span>
          <span>{doc.modified}</span>
        </div>
        <div className="mt-1 flex">
          <StatusBadge status={doc.status} />
        </div>
      </div>
    </Link>
  )
}
