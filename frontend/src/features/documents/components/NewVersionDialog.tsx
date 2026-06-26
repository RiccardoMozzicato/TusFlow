import { useEffect, useRef, useState } from 'react'
import Icon from '@/components/Icon'
import OverviewModal from '@/features/profile/components/OverviewModal'
import { useVersionUpload } from '../useVersionUpload'
import type { LibraryDocument } from '../types'

interface NewVersionDialogProps {
  doc: LibraryDocument
  onClose: () => void
  /** Called once a new version finishes uploading, so the library can refresh. */
  onUploaded: () => void
}

// Upload-a-new-version modal opened from the library. A new version is a tus
// upload that reuses the file's UUID and scope (see useVersionUpload).
export default function NewVersionDialog({ doc, onClose, onUploaded }: NewVersionDialogProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [isDragging, setIsDragging] = useState(false)
  // Refresh the library exactly once, when the upload completes.
  const notifiedRef = useRef(false)
  const { status, progress, fileName, error, upload } = useVersionUpload(
    doc.id,
    doc.sharingScope,
    () => {
      if (notifiedRef.current) return
      notifiedRef.current = true
      onUploaded()
    },
  )
  const isUploading = status === 'uploading'

  // Guard the post-success refresh against StrictMode double-invocation.
  useEffect(() => {
    notifiedRef.current = false
  }, [])

  function handlePick(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = '' // Allow re-picking the same file.
    if (file) upload(file)
  }

  function handleDrop(event: React.DragEvent) {
    event.preventDefault()
    setIsDragging(false)
    const file = event.dataTransfer.files?.[0]
    if (file) upload(file)
  }

  return (
    <OverviewModal title={`Upload new version of "${doc.title}"`} onClose={onClose}>
      <div className="flex flex-col gap-4">
        {status === 'success' ? (
          <div className="flex flex-col items-center gap-2 py-6 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-ds-full bg-ds-tertiary-fixed text-ds-on-tertiary-fixed-variant">
              <Icon name="check" filled={false} className="text-[24px]" />
            </span>
            <p className="font-ds-body text-ds-body-md text-ds-on-surface">New version uploaded</p>
            <button
              type="button"
              onClick={onClose}
              className="mt-2 rounded-ds-full bg-ds-primary px-4 py-2 font-ds-label text-ds-label-md text-ds-on-primary transition-colors hover:bg-ds-primary-container"
            >
              Done
            </button>
          </div>
        ) : (
          <>
            <input ref={inputRef} type="file" className="hidden" onChange={handlePick} disabled={isUploading} />
            <button
              type="button"
              onClick={() => inputRef.current?.click()}
              onDragOver={(event) => {
                event.preventDefault()
                setIsDragging(true)
              }}
              onDragLeave={() => setIsDragging(false)}
              onDrop={handleDrop}
              disabled={isUploading}
              className={`flex w-full cursor-pointer flex-col items-center justify-center gap-2 rounded-ds-xl border-2 border-dashed px-6 py-10 transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${
                isDragging
                  ? 'border-ds-primary bg-ds-primary-fixed/20'
                  : 'border-ds-outline-variant bg-ds-surface-container-lowest hover:border-ds-primary hover:bg-ds-surface-container-low'
              }`}
            >
              <Icon
                name={isUploading ? 'progress_activity' : 'cloud_upload'}
                filled={false}
                className={`text-[32px] text-ds-primary ${isUploading ? 'animate-spin' : ''}`}
              />
              <p className="font-ds-body text-ds-body-md font-medium text-ds-on-surface">
                {isUploading ? `Uploading ${fileName}…` : 'Click to upload or drag and drop'}
              </p>
            </button>

            {isUploading ? (
              <div className="h-2 w-full overflow-hidden rounded-ds-full bg-ds-surface-variant">
                <div
                  className="h-full rounded-ds-full bg-ds-primary transition-[width] duration-200"
                  style={{ width: `${Math.round(progress * 100)}%` }}
                />
              </div>
            ) : null}

            {error ? (
              <p className="flex items-center gap-1 font-ds-body text-ds-body-sm text-ds-error">
                <Icon name="error" filled={false} className="text-[16px]" />
                {error}
              </p>
            ) : null}
          </>
        )}
      </div>
    </OverviewModal>
  )
}
