import { useEffect, useRef, useState } from 'react'
import { renderAsync } from 'docx-preview'
import Icon from '@/components/Icon'
import type { FileType } from '../../types'
import { usePreviewBlob } from '../../usePreviewBlob'

interface DocumentPreviewProps {
  /** File id whose bytes are fetched for the preview. */
  fileId: string
  /** Triggers a download; used by the fallback when a type can't be rendered. */
  onDownload: () => void
  /** File name, shown in the toolbar. */
  title: string
  /** Coarse type, used to pick a renderer. */
  fileType: FileType
}

// Types rendered directly from the blob's object URL.
const IFRAME_TYPES: ReadonlySet<FileType> = new Set(['pdf', 'txt'])

// Expected MIME per type, used to correct a missing/generic content type from
// the server so blob: URLs render correctly (notably PDFs in an iframe).
const EXPECTED_MIME: Partial<Record<FileType, string>> = {
  pdf: 'application/pdf',
  txt: 'text/plain',
}

// Shown when a file can't be rendered in the browser. Offers an explicit
// download instead of leaving the pane blank.
function PreviewFallback({ onDownload }: { onDownload: () => void }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 p-8 text-center">
      <Icon name="visibility_off" filled={false} className="text-[40px] text-ds-secondary" />
      <p className="font-ds-body text-ds-body-sm text-ds-secondary">
        Preview isn't available for this file type.
      </p>
      <button
        type="button"
        onClick={onDownload}
        className="flex items-center gap-2 rounded-ds-lg bg-ds-primary px-4 py-2 font-ds-label text-ds-label-md text-ds-on-primary transition-colors hover:bg-ds-primary-container"
      >
        <Icon name="download" filled={false} className="text-[18px]" />
        Download to view
      </button>
    </div>
  )
}

// Renders a DOCX in-page from its bytes via docx-preview. Falls back to a
// download prompt if rendering fails (e.g. legacy .doc, which isn't supported).
function DocxPreview({ blob, onDownload }: { blob: Blob; onDownload: () => void }) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    const container = containerRef.current
    if (!container) return

    setFailed(false)
    container.innerHTML = ''
    void blob
      .arrayBuffer()
      .then((buffer) => renderAsync(buffer, container, undefined, {
        inWrapper: true,
        breakPages: true,
      }))
      .catch(() => {
        if (!cancelled) setFailed(true)
      })

    return () => {
      cancelled = true
    }
  }, [blob])

  if (failed) return <PreviewFallback onDownload={onDownload} />
  return <div ref={containerRef} className="h-full overflow-auto bg-white p-4" />
}

// Picks the right renderer for the loaded blob based on the file type.
function PreviewBody({
  fileType,
  url,
  blob,
  title,
  onDownload,
}: {
  fileType: FileType
  url: string
  blob: Blob
  title: string
  onDownload: () => void
}) {
  if (fileType === 'image') {
    return <img src={url} alt={title} className="h-full w-full object-contain" />
  }
  if (fileType === 'video') {
    return <video src={url} controls className="h-full w-full bg-black" />
  }
  if (IFRAME_TYPES.has(fileType)) {
    return <iframe src={url} title={title} className="h-full w-full border-0" />
  }
  if (fileType === 'docx') {
    return <DocxPreview blob={blob} onDownload={onDownload} />
  }
  // Generic 'file' bucket (unknown type): no inline renderer, offer download.
  return <PreviewFallback onDownload={onDownload} />
}

// Document preview pane: fetches the file's bytes once and renders them inline —
// DOCX via docx-preview, and PDF/text/image/video from a same-origin object URL
// (which, unlike the raw preview URL, isn't blocked by X-Frame-Options).
export default function DocumentPreview({
  fileId,
  onDownload,
  title,
  fileType,
}: DocumentPreviewProps) {
  const { status, url, blob } = usePreviewBlob(fileId, EXPECTED_MIME[fileType])

  return (
    <div className="flex h-[600px] flex-col overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-sm lg:h-full lg:min-h-0">
      <div className="flex items-center justify-between border-b border-ds-outline-variant bg-ds-surface-container-low px-4 py-3">
        <span className="truncate font-ds-body text-ds-body-sm font-medium text-ds-on-surface">
          {title}
        </span>
        {url ? (
          <a
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            aria-label="Open preview in new tab"
            title="Open in new tab"
            className="rounded-ds p-1 text-ds-secondary transition-colors hover:bg-ds-surface-container hover:text-ds-primary"
          >
            <Icon name="open_in_new" filled={false} className="text-[20px]" />
          </a>
        ) : null}
      </div>

      <div className="min-h-0 flex-1 overflow-hidden bg-ds-surface-dim">
        {status === 'loading' ? (
          <div className="flex h-full flex-col items-center justify-center gap-3">
            <Icon
              name="hourglass_empty"
              filled={false}
              className="text-[36px] text-ds-secondary"
            />
            <p className="font-ds-body text-ds-body-sm text-ds-secondary">Loading preview…</p>
          </div>
        ) : status === 'error' || !url || !blob ? (
          <PreviewFallback onDownload={onDownload} />
        ) : (
          <PreviewBody
            fileType={fileType}
            url={url}
            blob={blob}
            title={title}
            onDownload={onDownload}
          />
        )}
      </div>
    </div>
  )
}
