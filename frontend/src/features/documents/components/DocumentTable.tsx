import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Icon from '@/components/Icon'
import { initialsOf } from '@/lib/initials'
import { saveBlob } from '@/lib/download'
import { getDownloadBlob } from '../detailsApi'
import type { DocumentActionKind, LibraryDocument } from '../types'
import { AiIndicator, FILE_TYPE_STYLES, ScopeIndicator } from './documentIndicators'
import DocumentActionsMenu from './DocumentActionsMenu'

interface DocumentTableProps {
  documents: LibraryDocument[]
  /** Whether a doc's current version is favorited; pairs with onToggleFavorite. */
  isFavorited?: (doc: LibraryDocument) => boolean
  /** Toggles the favorite on a doc's current version; omit to hide the stars. */
  onToggleFavorite?: (doc: LibraryDocument) => void
  /** Opens a row action (share/new version/delete); omit to hide the actions menu. */
  onAction?: (doc: LibraryDocument, kind: DocumentActionKind) => void
}

// Secondary columns are dropped as the viewport narrows so the table always
// fits without horizontal scrolling — Document Name + Actions are always shown.
const SIZE_COL = 'hidden sm:table-cell'
const SHARING_COL = 'hidden md:table-cell'
const DATE_COL = 'hidden md:table-cell'
const OWNER_COL = 'hidden lg:table-cell'
const STATUS_COL = 'hidden xl:table-cell'

const COLUMNS: { label: string; className?: string }[] = [
  { label: 'Document Name' },
  { label: 'Sharing', className: SHARING_COL },
  { label: 'Owner', className: OWNER_COL },
  { label: 'Size', className: SIZE_COL },
  { label: 'Status', className: STATUS_COL },
  { label: 'Date Created', className: DATE_COL },
]

// Stops a row action (button/link) from also triggering the row's navigation.
function stop(event: React.MouseEvent): void {
  event.stopPropagation()
}

function OwnerCell({ doc }: { doc: LibraryDocument }) {
  const label = doc.isOwnedByMe ? 'You' : doc.owner
  return (
    <div className="flex items-center gap-2">
      <div
        className={`flex h-6 w-6 items-center justify-center rounded-ds-full text-[10px] font-bold ${
          doc.isOwnedByMe
            ? 'bg-ds-primary-fixed text-ds-on-primary-fixed'
            : 'bg-ds-secondary-fixed text-ds-on-secondary-fixed-variant'
        }`}
      >
        {initialsOf(label)}
      </div>
      <span className="font-ds-body text-ds-body-sm text-ds-on-surface-variant">{label}</span>
    </div>
  )
}

// Row layout for the library: a dense table mirroring the design mock. The whole
// row navigates to the document; the action buttons stop propagation so they act
// without also opening the row.
export default function DocumentTable({
  documents,
  isFavorited,
  onToggleFavorite,
  onAction,
}: DocumentTableProps) {
  const navigate = useNavigate()
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleDownload(doc: LibraryDocument) {
    setError(null)
    setDownloadingId(doc.id)
    try {
      const blob = await getDownloadBlob(doc.id)
      saveBlob(blob, doc.title)
    } catch {
      setError(`Could not download "${doc.title}". Please try again.`)
    } finally {
      setDownloadingId(null)
    }
  }

  if (documents.length === 0) {
    return (
      <p className="py-12 text-center font-ds-body text-ds-body-sm text-ds-secondary">
        No documents match the current filters.
      </p>
    )
  }

  return (
    <div className="flex min-w-0 flex-col gap-3">
      {error ? (
        <p className="flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-error">
          <Icon name="error" className="text-[14px]" />
          {error}
        </p>
      ) : null}
      <div className="overflow-x-auto rounded-ds-xl border border-ds-outline-variant">
        <table className="w-full border-collapse text-left">
          <thead>
            <tr className="border-b border-ds-outline-variant bg-ds-surface font-ds-label text-ds-label-md text-ds-on-surface-variant">
              {COLUMNS.map((column) => (
                <th key={column.label} className={`px-3 py-3 font-semibold ${column.className ?? ''}`}>
                  {column.label}
                </th>
              ))}
              <th className="px-3 py-3 text-right font-semibold">Actions</th>
            </tr>
          </thead>
          <tbody>
            {documents.map((doc) => {
              const fileType = FILE_TYPE_STYLES[doc.fileType]
              const favorited = isFavorited?.(doc) ?? false
              return (
                <tr
                  key={doc.id}
                  onClick={() => navigate(`/documents/${doc.id}`)}
                  className="group cursor-pointer border-b border-ds-outline-variant/50 transition-colors last:border-0 hover:bg-ds-surface-container-low"
                >
                  <td className="px-3 py-3">
                    <div className="flex min-w-0 items-center gap-3">
                      <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-ds ${fileType.chipClassName}`}>
                        <Icon name={fileType.icon} className="text-[18px]" />
                      </div>
                      <span
                        title={doc.title}
                        className="block max-w-[150px] truncate font-ds-body text-ds-body-sm font-medium text-ds-on-surface transition-colors group-hover:text-ds-primary sm:max-w-[200px] lg:max-w-[320px]"
                      >
                        {doc.title}
                      </span>
                    </div>
                  </td>
                  <td className={`px-3 py-3 ${SHARING_COL}`}>
                    <ScopeIndicator scope={doc.scope} />
                  </td>
                  <td className={`px-3 py-3 ${OWNER_COL}`}>
                    <OwnerCell doc={doc} />
                  </td>
                  <td className={`px-3 py-3 font-ds-body text-ds-body-sm text-ds-on-surface-variant ${SIZE_COL}`}>
                    {doc.size}
                  </td>
                  <td className={`px-3 py-3 ${STATUS_COL}`}>
                    <div className="flex items-center gap-2">
                      <AiIndicator status={doc.aiStatus} />
                      <span className="rounded-ds border border-ds-outline-variant/50 bg-ds-surface-container px-1.5 font-ds-label text-ds-label-sm text-ds-on-surface-variant">
                        {doc.version}
                      </span>
                    </div>
                  </td>
                  <td
                    title={doc.created}
                    className={`whitespace-nowrap px-3 py-3 font-ds-body text-ds-body-sm text-ds-on-surface-variant ${DATE_COL}`}
                  >
                    {doc.createdRelative}
                  </td>
                  <td className="px-3 py-3">
                    <div className="flex items-center justify-end gap-1">
                      {onToggleFavorite ? (
                        <button
                          type="button"
                          onClick={(event) => {
                            stop(event)
                            onToggleFavorite(doc)
                          }}
                          aria-pressed={favorited}
                          aria-label={favorited ? `Unfavorite ${doc.title}` : `Favorite ${doc.title}`}
                          title={favorited ? 'Unfavorite' : 'Favorite'}
                          className={`rounded-ds-full p-1.5 transition-colors hover:bg-ds-surface-container ${
                            favorited ? 'text-ds-primary' : 'text-ds-secondary hover:text-ds-primary'
                          }`}
                        >
                          <Icon name="star" filled={favorited} className="text-[18px]" />
                        </button>
                      ) : null}
                      <button
                        type="button"
                        onClick={(event) => {
                          stop(event)
                          void handleDownload(doc)
                        }}
                        disabled={downloadingId === doc.id}
                        aria-label={`Download ${doc.title}`}
                        title="Download"
                        className="rounded-ds-full p-1.5 text-ds-secondary transition-colors hover:bg-ds-surface-container hover:text-ds-primary disabled:opacity-50"
                      >
                        <Icon
                          name={downloadingId === doc.id ? 'progress_activity' : 'download'}
                          className={`text-[18px] ${downloadingId === doc.id ? 'animate-spin' : ''}`}
                        />
                      </button>
                      {onAction && doc.isWritable ? (
                        <DocumentActionsMenu
                          onShare={() => onAction(doc, 'share')}
                          onNewVersion={() => onAction(doc, 'newVersion')}
                          onDelete={() => onAction(doc, 'delete')}
                        />
                      ) : null}
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
