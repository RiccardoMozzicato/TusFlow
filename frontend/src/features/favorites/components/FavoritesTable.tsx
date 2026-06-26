import { Link } from 'react-router-dom'
import Icon from '@/components/Icon'
import { initialsOf } from '@/lib/initials'
import type { FileType } from '@/features/documents/types'
import type { FavoriteDocument } from '../types'

// File-type icon + chip styling, aligned with the Document Library cards.
const FILE_TYPE_STYLES: Record<FileType, { icon: string; className: string }> = {
  pdf: { icon: 'picture_as_pdf', className: 'bg-ds-error-container/50 text-ds-on-error-container' },
  docx: { icon: 'description', className: 'bg-ds-primary-fixed text-ds-primary' },
  txt: { icon: 'article', className: 'bg-ds-surface-container-high text-ds-secondary' },
  image: { icon: 'image', className: 'bg-ds-secondary-fixed text-ds-secondary' },
  video: { icon: 'movie', className: 'bg-ds-surface-container-high text-ds-secondary' },
  file: { icon: 'insert_drive_file', className: 'bg-ds-surface-container-high text-ds-secondary' },
}

const HEADERS = ['Document Name', 'Version', 'Owner', 'Favorited']

function ActionButton({
  icon,
  title,
  to,
  onClick,
  spinning,
  tone = 'text-ds-secondary',
}: {
  icon: string
  title: string
  to?: string
  onClick?: () => void
  spinning?: boolean
  tone?: string
}) {
  const className = `flex h-9 w-9 items-center justify-center rounded-ds ${tone} transition-all duration-200 hover:bg-ds-surface-container-high hover:text-ds-primary disabled:opacity-100`
  const content = (
    <Icon
      name={spinning ? 'progress_activity' : icon}
      filled={false}
      className={`text-[18px] ${spinning ? 'animate-spin' : ''}`}
    />
  )
  if (to) {
    return (
      <Link to={to} title={title} aria-label={title} className={className}>
        {content}
      </Link>
    )
  }
  return (
    <button type="button" onClick={onClick} disabled={spinning} title={title} aria-label={title} className={className}>
      {content}
    </button>
  )
}

function FavoriteRow({
  doc,
  isDownloading,
  isRemoving,
  onDownload,
  onRemove,
  last,
}: {
  doc: FavoriteDocument
  isDownloading: boolean
  isRemoving: boolean
  onDownload: () => void
  onRemove: () => void
  last: boolean
}) {
  const fileStyle = FILE_TYPE_STYLES[doc.fileType]
  return (
    <tr
      className={`group transition-colors hover:bg-ds-surface/60 ${
        last ? '' : 'border-b border-ds-outline-variant/30'
      }`}
    >
      <td className="px-6 py-4">
        <div className="flex items-center gap-3">
          <div
            className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-ds-lg transition-transform duration-200 group-hover:scale-105 ${fileStyle.className}`}
          >
            <Icon name={fileStyle.icon} className="text-[20px]" />
          </div>
          <div className="min-w-0">
            <Link
              to={`/documents/${doc.fileId}`}
              className="block truncate font-semibold text-ds-on-surface transition-colors hover:text-ds-primary"
            >
              {doc.title}
            </Link>
            <p className="font-ds-label text-ds-label-sm text-ds-secondary">
              {doc.typeLabel} • {doc.size}
            </p>
          </div>
        </div>
      </td>
      <td className="px-6 py-4">
        <span className="rounded-ds bg-ds-primary-fixed px-2 py-0.5 font-ds-label text-ds-label-sm font-bold text-ds-primary">
          {doc.versionLabel}
        </span>
      </td>
      <td className="px-6 py-4">
        <div className="flex items-center gap-2">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-ds-full bg-ds-secondary-fixed font-ds-label text-[10px] font-bold text-ds-on-secondary-fixed">
            {initialsOf(doc.owner)}
          </span>
          <span className="truncate text-ds-on-surface">{doc.owner}</span>
        </div>
      </td>
      <td className="px-6 py-4 text-ds-secondary">{doc.favoritedDate}</td>
      <td className="px-6 py-4">
        <div className="flex items-center justify-end gap-1">
          <ActionButton
            icon="open_in_new"
            title="Open"
            to={`/documents/${doc.fileId}`}
            tone="text-ds-primary"
          />
          <ActionButton
            icon="download"
            title={`Download ${doc.versionLabel}`}
            onClick={onDownload}
            spinning={isDownloading}
          />
          <ActionButton
            icon="star"
            title={`Unfavorite ${doc.versionLabel}`}
            onClick={onRemove}
            spinning={isRemoving}
            tone="text-ds-primary"
          />
        </div>
      </td>
    </tr>
  )
}

export default function FavoritesTable({
  documents,
  downloadingId,
  removingId,
  onDownload,
  onRemove,
}: {
  documents: FavoriteDocument[]
  downloadingId: string | null
  removingId: string | null
  onDownload: (doc: FavoriteDocument) => void
  onRemove: (doc: FavoriteDocument) => void
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
          {documents.map((doc, index) => (
            <FavoriteRow
              key={doc.id}
              doc={doc}
              isDownloading={downloadingId === doc.id}
              isRemoving={removingId === doc.id}
              onDownload={() => onDownload(doc)}
              onRemove={() => onRemove(doc)}
              last={index === documents.length - 1}
            />
          ))}
        </tbody>
      </table>
    </div>
  )
}
