import { useEffect, useId } from 'react'
import Icon from '@/components/Icon'
import type { DocumentStatus, FileType, LibraryDocument } from '../types'
import { AiIndicator, FILE_TYPE_STYLES, ScopeIndicator } from './documentIndicators'

interface FileInfoDialogProps {
  doc: LibraryDocument
  onClose: () => void
}

const MISSING = '—'

// Human-readable label for the coarse file-type bucket.
const FILE_TYPE_LABELS: Record<FileType, string> = {
  pdf: 'PDF document',
  docx: 'Word document',
  txt: 'Text document',
  image: 'Image',
  video: 'Video',
  file: 'File',
}

// Human-readable label for the current-version status.
const STATUS_LABELS: Record<DocumentStatus, string> = {
  processing: 'Processing',
  ready: 'Ready',
  draft: 'Draft',
  scan_failed: 'Scan failed',
}

// One label/value line in the info list.
function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 py-2">
      <dt className="shrink-0 font-ds-body text-ds-body-sm text-ds-secondary">{label}</dt>
      <dd className="min-w-0 break-words text-right font-ds-body text-ds-body-sm text-ds-on-surface">
        {children}
      </dd>
    </div>
  )
}

// File information modal: the details once shown inline in the row's 3-dots menu,
// now with the file's department and organization. Opened from the "Information"
// item in DocumentActionsMenu. Shares the overlay treatment of ConfirmDialog.
export default function FileInfoDialog({ doc, onClose }: FileInfoDialogProps) {
  const titleId = useId()
  const fileType = FILE_TYPE_STYLES[doc.fileType]

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(event) => event.stopPropagation()}
        className="w-full max-w-md rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-6 shadow-xl"
      >
        <div className="flex items-start gap-3">
          <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-ds ${fileType.chipClassName}`}>
            <Icon name={fileType.icon} className="text-[22px]" />
          </div>
          <div className="min-w-0 flex-1">
            <h2
              id={titleId}
              title={doc.title}
              className="truncate font-ds-heading text-ds-headline-sm text-ds-on-surface"
            >
              {doc.title}
            </h2>
            <p className="font-ds-body text-ds-body-sm text-ds-secondary">
              {FILE_TYPE_LABELS[doc.fileType]}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-ds-full p-1.5 text-ds-secondary transition-colors hover:bg-ds-surface-container hover:text-ds-on-surface"
          >
            <Icon name="close" className="text-[20px]" />
          </button>
        </div>

        <dl className="mt-4 divide-y divide-ds-outline-variant/50 border-t border-ds-outline-variant/50">
          <InfoRow label="Sharing">
            <ScopeIndicator scope={doc.scope} />
          </InfoRow>
          <InfoRow label="Owner">{doc.isOwnedByMe ? 'You' : doc.owner}</InfoRow>
          <InfoRow label="Department">{doc.department || MISSING}</InfoRow>
          <InfoRow label="Organization">{doc.organization || MISSING}</InfoRow>
          <InfoRow label="Size">{doc.size}</InfoRow>
          <InfoRow label="Created">{doc.created}</InfoRow>
          <InfoRow label="Version">{doc.version}</InfoRow>
          <InfoRow label="Status">
            <span className="inline-flex items-center justify-end gap-1.5 align-middle">
              <AiIndicator status={doc.aiStatus} />
              {STATUS_LABELS[doc.status]}
            </span>
          </InfoRow>
        </dl>
      </div>
    </div>
  )
}
