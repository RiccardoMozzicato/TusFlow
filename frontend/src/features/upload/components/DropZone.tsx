import Icon from '@/components/Icon'

const SUPPORTED_FORMATS = 'PDF, DOCX, XLSX, MP4, SVG, PNG. Maximum file size 5GB.'

// Presentational drag-and-drop target. File selection / DnD handlers get wired
// when the tus upload client lands.
export default function DropZone() {
  return (
    <button
      type="button"
      className="group mb-8 flex w-full cursor-pointer flex-col items-center justify-center rounded-ds-xl border-2 border-dashed border-ds-outline-variant bg-ds-surface-container-lowest px-6 py-12 transition-colors duration-200 hover:bg-ds-surface-container-low"
    >
      <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-ds-full bg-ds-surface-variant transition-transform group-hover:scale-105">
        <Icon name="cloud_upload" className="text-[32px] text-ds-primary" />
      </div>
      <p className="mb-1 font-ds-heading text-ds-headline-sm text-ds-on-surface">
        Drop files here or browse
      </p>
      <p className="max-w-md text-center text-ds-body-sm text-ds-on-surface-variant">
        Supported formats: {SUPPORTED_FORMATS}
      </p>
    </button>
  )
}
