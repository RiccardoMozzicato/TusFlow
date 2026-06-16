import { Link } from 'react-router-dom'
import Icon from '@/components/Icon'
import { JobStatus } from '../jobsApi'
import type { DocumentScope, DocumentStatus, FileType, LibraryDocument } from '../types'

// Status badge styling, keyed by status. Mirrors the dashboard's STATUS_STYLES
// pattern. Routine lifecycle states (ready, processing) are intentionally
// omitted — the card only badges states that need the user's attention.
const STATUS_STYLES: Partial<
  Record<DocumentStatus, { label: string; className: string; icon?: string; dot?: boolean }>
> = {
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
  txt: { icon: 'article', chipClassName: 'bg-ds-surface-container-high text-ds-secondary' },
  image: { icon: 'image', chipClassName: 'bg-ds-secondary-fixed text-ds-secondary' },
  video: { icon: 'movie', chipClassName: 'bg-ds-surface-container-high text-ds-secondary' },
  file: { icon: 'insert_drive_file', chipClassName: 'bg-ds-surface-container-high text-ds-secondary' },
}

// Visibility-scope indicator shown next to the title: one person for personal,
// a group for department, a building for organization.
const SCOPE_STYLES: Record<DocumentScope, { icon: string; label: string }> = {
  personal: { icon: 'person', label: 'Personal' },
  department: { icon: 'groups', label: 'Department' },
  organization: { icon: 'apartment', label: 'Organization' },
}

function ScopeIndicator({ scope }: { scope: DocumentScope }) {
  const { icon, label } = SCOPE_STYLES[scope]
  return (
    <span
      title={`${label} file`}
      aria-label={`${label} file`}
      className="inline-flex shrink-0 self-start items-center gap-1 rounded-ds-full border border-ds-outline-variant/50 bg-ds-surface-container px-2 py-0.5 font-ds-label text-ds-label-sm text-ds-on-surface-variant"
    >
      <Icon name={icon} className="text-[14px]" />
      {label}
    </span>
  )
}

function StatusBadge({ status }: { status: DocumentStatus }) {
  const style = STATUS_STYLES[status]
  if (!style) return null
  const { label, className, icon, dot } = style
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

// AI-job indicator shown by the version badge: a sparkle once metadata is ready,
// a spinner while it's still being generated. Other statuses (failed, unsupported,
// or no job) render nothing.
function AiIndicator({ status }: { status?: LibraryDocument['aiStatus'] }) {
  if (status === JobStatus.COMPLETED) {
    return (
      <span
        title="AI-generated metadata"
        aria-label="AI-generated metadata"
        className="flex items-center rounded-ds-full bg-ds-tertiary-fixed p-1 text-ds-on-tertiary-fixed-variant"
      >
        <Icon name="auto_awesome" className="text-[14px]" />
      </span>
    )
  }

  if (status === JobStatus.PENDING || status === JobStatus.PROCESSING) {
    return (
      <span
        title="AI processing"
        aria-label="AI processing"
        className="flex items-center rounded-ds-full bg-ds-surface-container-high p-1 text-ds-on-surface-variant"
      >
        <Icon name="progress_activity" className="animate-spin text-[14px]" />
      </span>
    )
  }

  return null
}

// Star toggle for the document's current version. A favorited star stays
// visible; an unfavorited one is revealed on card hover. Clicks must not
// trigger the card's navigation, so the handler stops the Link's default.
function FavoriteButton({
  title,
  isFavorited,
  onToggle,
}: {
  title: string
  isFavorited: boolean
  onToggle: () => void
}) {
  function handleClick(event: React.MouseEvent<HTMLButtonElement>) {
    event.preventDefault()
    event.stopPropagation()
    onToggle()
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      aria-pressed={isFavorited}
      aria-label={isFavorited ? `Unfavorite ${title}` : `Favorite ${title}`}
      title={isFavorited ? `Unfavorite ${title}` : `Favorite ${title}`}
      className={`flex h-6 w-6 items-center justify-center rounded-ds-full transition-all duration-200 hover:bg-ds-primary-fixed/40 focus-visible:opacity-100 group-hover:opacity-100 ${
        isFavorited ? 'text-ds-primary opacity-100' : 'text-ds-secondary opacity-0 hover:text-ds-primary'
      }`}
    >
      <Icon name="star" filled={isFavorited} className="text-[16px]" />
    </button>
  )
}

interface DocumentCardProps {
  doc: LibraryDocument
  /** Whether the doc's current version is favorited; pairs with onToggleFavorite. */
  isFavorited?: boolean
  /** Toggles the favorite on the doc's current version; omit to hide the star. */
  onToggleFavorite?: () => void
}

export default function DocumentCard({ doc, isFavorited = false, onToggleFavorite }: DocumentCardProps) {
  const fileType = FILE_TYPE_STYLES[doc.fileType]
  const isFailed = doc.status === 'scan_failed'
  const hasAiIndicator =
    doc.aiStatus === JobStatus.COMPLETED ||
    doc.aiStatus === JobStatus.PENDING ||
    doc.aiStatus === JobStatus.PROCESSING
  const hasFavoriteButton = Boolean(onToggleFavorite)
  // Keep the title clear of the corner cluster (star + AI indicator + version badge).
  const titleRightPadding =
    hasAiIndicator && hasFavoriteButton
      ? 'pr-28'
      : hasAiIndicator || hasFavoriteButton
        ? 'pr-20'
        : 'pr-10'

  return (
    <Link
      to={`/documents/${doc.id}`}
      className={`group relative flex cursor-pointer flex-col gap-4 rounded-ds-xl border bg-ds-surface-container-lowest p-4 transition-all hover:shadow-md ${
        isFailed
          ? 'border-ds-error/30 hover:border-ds-error'
          : 'border-ds-outline-variant hover:border-ds-primary/50'
      }`}
    >
      <div className="absolute right-4 top-4 flex items-center gap-1.5">
        {onToggleFavorite ? (
          <FavoriteButton
            title={doc.title}
            isFavorited={isFavorited}
            onToggle={onToggleFavorite}
          />
        ) : null}
        <AiIndicator status={doc.aiStatus} />
        <span className="rounded-ds-full border border-ds-outline-variant/50 bg-ds-surface-container px-2 py-0.5 font-ds-label text-ds-label-sm text-ds-on-surface-variant">
          {doc.version}
        </span>
      </div>

      <div className={`flex items-start gap-3 ${titleRightPadding}`}>
        <div className={`shrink-0 rounded-ds-lg p-2 ${fileType.chipClassName}`}>
          <Icon name={fileType.icon} />
        </div>
        <div className="flex min-w-0 flex-col gap-1.5">
          <h3 className="line-clamp-2 font-ds-heading text-ds-headline-sm text-ds-on-surface transition-colors group-hover:text-ds-primary">
            {doc.title}
          </h3>
          <ScopeIndicator scope={doc.scope} />
        </div>
      </div>

      <div className="mt-auto flex flex-col gap-1 pt-3">
        <div className="flex items-center justify-between font-ds-label text-ds-label-sm text-ds-secondary">
          <span className="flex items-center gap-1">
            <Icon name="person" className="text-[14px]" />
            {doc.owner}
          </span>
          <span className="flex items-center gap-1">
            <Icon name="event" className="text-[14px]" />
            Created {doc.created}
          </span>
        </div>
        <div className="flex items-center font-ds-label text-ds-label-sm text-ds-secondary">
          <span className="flex items-center gap-1">
            <Icon name="hard_drive" className="text-[14px]" />
            {doc.size}
          </span>
        </div>
        {STATUS_STYLES[doc.status] ? (
          <div className="mt-1 flex">
            <StatusBadge status={doc.status} />
          </div>
        ) : null}
      </div>
    </Link>
  )
}
