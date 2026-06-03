import { useState } from 'react'
import Icon from '@/components/Icon'

interface DocumentPreviewProps {
  /** Total page count shown in the toolbar. */
  pageCount: number
}

const ZOOM_MIN = 50
const ZOOM_MAX = 200
const ZOOM_STEP = 10

// Document preview pane: a toolbar (page count, zoom, fullscreen) over a
// simulated PDF page. Zoom is the live control; the page body is placeholder
// content until a real renderer is wired.
export default function DocumentPreview({ pageCount }: DocumentPreviewProps) {
  const [zoom, setZoom] = useState(100)

  const zoomOut = () => setZoom((z) => Math.max(ZOOM_MIN, z - ZOOM_STEP))
  const zoomIn = () => setZoom((z) => Math.min(ZOOM_MAX, z + ZOOM_STEP))

  return (
    <div className="flex h-[600px] flex-col overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-sm lg:h-auto">
      <div className="flex items-center justify-between border-b border-ds-outline-variant bg-ds-surface-container-low px-4 py-3">
        <div className="flex items-center gap-4">
          <span className="font-ds-body text-ds-body-sm font-medium text-ds-on-surface">
            Page 1 of {pageCount}
          </span>
          <div className="h-4 w-px bg-ds-outline-variant" />
          <button
            type="button"
            onClick={zoomOut}
            disabled={zoom <= ZOOM_MIN}
            aria-label="Zoom out"
            className="text-ds-secondary transition-colors hover:text-ds-primary disabled:cursor-not-allowed disabled:opacity-40"
          >
            <Icon name="zoom_out" filled={false} className="text-[20px]" />
          </button>
          <span className="w-12 text-center font-ds-body text-ds-body-sm text-ds-secondary">
            {zoom}%
          </span>
          <button
            type="button"
            onClick={zoomIn}
            disabled={zoom >= ZOOM_MAX}
            aria-label="Zoom in"
            className="text-ds-secondary transition-colors hover:text-ds-primary disabled:cursor-not-allowed disabled:opacity-40"
          >
            <Icon name="zoom_in" filled={false} className="text-[20px]" />
          </button>
        </div>
        <button
          type="button"
          aria-label="Fullscreen"
          className="rounded-ds p-1 text-ds-secondary transition-colors hover:bg-ds-surface-container hover:text-ds-primary"
        >
          <Icon name="fullscreen" filled={false} className="text-[20px]" />
        </button>
      </div>

      <div className="relative flex flex-1 justify-center overflow-auto bg-ds-surface-dim p-6">
        <div
          className="relative min-h-[1000px] w-full max-w-[800px] origin-top rounded-ds-sm border border-ds-outline-variant bg-white p-8 shadow-md transition-transform"
          style={{ transform: `scale(${zoom / 100})` }}
        >
          <div className="mb-8 h-48 rounded bg-ds-surface-container-low" />
          <h2 className="mb-4 font-ds-heading text-ds-headline-md text-ds-on-surface">
            Q3 Fiscal Performance Overview
          </h2>
          <p className="mb-4 font-ds-body text-ds-body-md leading-relaxed text-ds-on-surface-variant">
            This document serves as the finalized financial report for the third quarter of 2023.
            It encompasses detailed breakdowns of revenue streams, operational expenditures, and
            strategic growth metrics observed across all global departments.
          </p>
          <div className="mb-8 grid grid-cols-2 gap-4">
            <div className="rounded-ds-lg border border-ds-outline-variant bg-ds-surface-container p-4">
              <span className="mb-1 block font-ds-label text-ds-label-sm uppercase tracking-wide text-ds-secondary">
                SaaS Revenue
              </span>
              <span className="font-ds-heading text-ds-headline-sm text-ds-primary">+15.2%</span>
            </div>
            <div className="rounded-ds-lg border border-ds-outline-variant bg-ds-surface-container p-4">
              <span className="mb-1 block font-ds-label text-ds-label-sm uppercase tracking-wide text-ds-secondary">
                OpEx Reduction
              </span>
              <span className="font-ds-heading text-ds-headline-sm text-ds-tertiary">-4.8%</span>
            </div>
          </div>
          <p className="font-ds-body text-ds-body-md leading-relaxed text-ds-on-surface-variant">
            Key drivers for this quarter's performance include the successful rollout of the new
            enterprise tier, resulting in higher average contract values, alongside continued
            efficiency optimizations in server infrastructure costs.
          </p>
          <div className="absolute bottom-8 left-8 right-8 flex justify-between border-t border-ds-outline-variant pt-3 font-ds-label text-ds-label-sm text-ds-secondary">
            <span>DocPortal Secure View</span>
            <span>CONFIDENTIAL</span>
          </div>
        </div>
      </div>
    </div>
  )
}
