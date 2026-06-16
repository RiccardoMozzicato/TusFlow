import DocumentCard from './DocumentCard'
import type { DocumentActionKind, LibraryDocument } from '../types'

interface DocumentGridProps {
  documents: LibraryDocument[]
  /** Whether a doc's current version is favorited; pairs with onToggleFavorite. */
  isFavorited?: (doc: LibraryDocument) => boolean
  /** Toggles the favorite on a doc's current version; omit to hide the stars. */
  onToggleFavorite?: (doc: LibraryDocument) => void
  /** Opens a card action (share/new version/delete); omit to hide the actions menu. */
  onAction?: (doc: LibraryDocument, kind: DocumentActionKind) => void
}

// The optional card view: a responsive multi-column grid. The default row view is
// rendered by DocumentTable instead.
export default function DocumentGrid({
  documents,
  isFavorited,
  onToggleFavorite,
  onAction,
}: DocumentGridProps) {
  if (documents.length === 0) {
    return (
      <p className="py-12 text-center font-ds-body text-ds-body-sm text-ds-secondary">
        No documents match the current filters.
      </p>
    )
  }

  return (
    <div className="grid min-w-0 auto-rows-max grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
      {documents.map((doc) => (
        <DocumentCard
          key={doc.id}
          doc={doc}
          isFavorited={isFavorited?.(doc) ?? false}
          onToggleFavorite={onToggleFavorite ? () => onToggleFavorite(doc) : undefined}
          onAction={onAction}
        />
      ))}
    </div>
  )
}
