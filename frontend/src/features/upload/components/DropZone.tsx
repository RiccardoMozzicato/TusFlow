import { useRef, useState } from 'react'
import Icon from '@/components/Icon'

const SUPPORTED_FORMATS = 'PDF, DOCX, XLSX, MP4, SVG, PNG. Maximum file size 5GB.'

interface DropZoneProps {
  /** Receives the files chosen via browse or drop. */
  onFiles: (files: File[]) => void
}

// Drag-and-drop target. Selected files are handed to the caller, which stages
// them for metadata entry before the upload starts.
export default function DropZone({ onFiles }: DropZoneProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [isDragging, setIsDragging] = useState(false)

  function emitFiles(list: FileList | null) {
    if (!list || list.length === 0) return
    onFiles(Array.from(list))
  }

  function handleDrop(event: React.DragEvent) {
    event.preventDefault()
    setIsDragging(false)
    emitFiles(event.dataTransfer.files)
  }

  return (
    <div className="mb-8">
      <div
        data-tour="upload-dropzone"
        role="button"
        tabIndex={0}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') inputRef.current?.click()
        }}
        onDragOver={(event) => {
          event.preventDefault()
          setIsDragging(true)
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
        className={`group flex w-full cursor-pointer flex-col items-center justify-center rounded-ds-xl border-2 border-dashed px-6 py-12 transition-colors duration-200 ${
          isDragging
            ? 'border-ds-primary bg-ds-primary-container/30'
            : 'border-ds-outline-variant bg-ds-surface-container-lowest hover:bg-ds-surface-container-low'
        }`}
      >
        <input
          ref={inputRef}
          type="file"
          multiple
          className="hidden"
          onChange={(event) => {
            emitFiles(event.target.files)
            // Reset so selecting the same file again re-triggers onChange.
            event.target.value = ''
          }}
        />
        <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-ds-full bg-ds-surface-variant transition-transform group-hover:scale-105">
          <Icon name="cloud_upload" className="text-[32px] text-ds-primary" />
        </div>
        <p className="mb-1 font-ds-heading text-ds-headline-sm text-ds-on-surface">
          Drop files here or browse
        </p>
        <p className="max-w-md text-center text-ds-body-sm text-ds-on-surface-variant">
          Supported formats: {SUPPORTED_FORMATS}
        </p>
      </div>
    </div>
  )
}
