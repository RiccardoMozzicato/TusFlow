import DocumentCard from './DocumentCard'
import type { LibraryDocument, ViewMode } from '../types'

interface DocumentGridProps {
  documents: LibraryDocument[]
  viewMode: ViewMode
  /** Whether a doc's current version is favorited; pairs with onToggleFavorite. */
  isFavorited?: (doc: LibraryDocument) => boolean
  /** Toggles the favorite on a doc's current version; omit to hide the stars. */
  onToggleFavorite?: (doc: LibraryDocument) => void
}

// Grid mode = responsive multi-column card grid; list mode = single column.
const LAYOUT_CLASSES: Record<ViewMode, string> = {
  grid: 'grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3',
  list: 'grid grid-cols-1 gap-4',
}

export default function DocumentGrid({
  documents,
  viewMode,
  isFavorited,
  onToggleFavorite,
}: DocumentGridProps) {
  if (documents.length === 0) {
    return (
      <p className="py-12 text-center font-ds-body text-ds-body-sm text-ds-secondary">
        No documents match the current filters.
      </p>
    )
  }

  return (
    <div className={`auto-rows-max ${LAYOUT_CLASSES[viewMode]}`}>
      {documents.map((doc) => (
        <DocumentCard
          key={doc.id}
          doc={doc}
          isFavorited={isFavorited?.(doc) ?? false}
          onToggleFavorite={onToggleFavorite ? () => onToggleFavorite(doc) : undefined}
        />
      ))}
    </div>
  )
}
