import { useState } from 'react'
import FilterPanel from '@/features/documents/components/FilterPanel'
import LibraryToolbar from '@/features/documents/components/LibraryToolbar'
import DocumentGrid from '@/features/documents/components/DocumentGrid'
import { DOCUMENTS } from '@/features/documents/mockData'
import type { ViewMode } from '@/features/documents/types'

// Document Library: filter rail + a canvas with a toolbar and a card grid.
// Data is mock today; the view-mode toggle is the only live interaction.
export default function DocumentLibraryPage() {
  const [viewMode, setViewMode] = useState<ViewMode>('grid')

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 className="font-ds-heading text-ds-headline-md text-ds-on-surface">
          Document Library
        </h2>
        <p className="mt-1 text-ds-body-sm text-ds-secondary">
          Browse, filter, and manage your organization's documents.
        </p>
      </div>

      <div className="flex gap-6">
        <FilterPanel />

        <section className="flex min-w-0 flex-1 flex-col overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest">
          <LibraryToolbar
            count={DOCUMENTS.length}
            viewMode={viewMode}
            onViewModeChange={setViewMode}
          />
          <div className="p-6">
            <DocumentGrid documents={DOCUMENTS} viewMode={viewMode} />
          </div>
        </section>
      </div>
    </div>
  )
}
