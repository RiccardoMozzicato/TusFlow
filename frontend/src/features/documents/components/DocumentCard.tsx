import { Link } from 'react-router-dom'
import Icon from '@/components/Icon'
import { JobStatus } from '../jobsApi'
import type { DocumentActionKind, DocumentStatus, LibraryDocument } from '../types'
import { AiIndicator, FILE_TYPE_STYLES, ScopeIndicator } from './documentIndicators'
import DocumentActionsMenu from './DocumentActionsMenu'

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
  /** Opens a card action (share/new version/delete); omit to hide the actions menu. */
  onAction?: (doc: LibraryDocument, kind: DocumentActionKind) => void
}

// Right padding that keeps the title clear of the corner cluster, widening with
// each extra item beyond the always-present version badge.
const TITLE_PADDING_BY_EXTRAS = ['pr-10', 'pr-20', 'pr-28', 'pr-36'] as const

export default function DocumentCard({
  doc,
  isFavorited = false,
  onToggleFavorite,
  onAction,
}: DocumentCardProps) {
  const fileType = FILE_TYPE_STYLES[doc.fileType]
  const isFailed = doc.status === 'scan_failed'
  const hasAiIndicator =
    doc.aiStatus === JobStatus.COMPLETED ||
    doc.aiStatus === JobStatus.PENDING ||
    doc.aiStatus === JobStatus.PROCESSING
  const hasFavoriteButton = Boolean(onToggleFavorite)
  const hasMenu = Boolean(onAction && doc.isWritable)
  // Keep the title clear of the corner cluster (star + AI indicator + menu + version badge).
  const cornerExtras = Number(hasAiIndicator) + Number(hasFavoriteButton) + Number(hasMenu)
  const titleRightPadding = TITLE_PADDING_BY_EXTRAS[cornerExtras]

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
        {hasMenu && onAction ? (
          <DocumentActionsMenu
            onShare={() => onAction(doc, 'share')}
            onNewVersion={() => onAction(doc, 'newVersion')}
            onDelete={() => onAction(doc, 'delete')}
          />
        ) : null}
      </div>

      <div className={`flex items-start gap-3 ${titleRightPadding}`}>
        <div className={`shrink-0 rounded-ds-lg p-2 ${fileType.chipClassName}`}>
          <Icon name={fileType.icon} />
        </div>
        <div className="flex min-w-0 flex-col gap-1.5">
          <h3 className="line-clamp-2 break-words font-ds-heading text-ds-headline-sm text-ds-on-surface transition-colors group-hover:text-ds-primary">
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
