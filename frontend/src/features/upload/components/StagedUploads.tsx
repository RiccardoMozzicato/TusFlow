import Icon from '@/components/Icon'
import { formatBytes } from '../format'
import type { MetadataField, StagedFile } from '../types'
import MetadataEditor from './MetadataEditor'

interface StagedUploadsProps {
  staged: StagedFile[]
  onMetadataChange: (id: string, fields: MetadataField[]) => void
  onRemove: (id: string) => void
  onStart: (id: string) => void
  onStartAll: () => void
}

// Holding area for selected-but-not-yet-uploaded files. Each card lets the user
// attach free-form metadata, then start that file's upload (or start them all).
export default function StagedUploads({
  staged,
  onMetadataChange,
  onRemove,
  onStart,
  onStartAll,
}: StagedUploadsProps) {
  return (
    <section className="mb-8 rounded-ds-xl border border-ds-outline-variant bg-ds-surface p-5 shadow-[0px_4px_6px_-1px_rgba(0,0,0,0.02)]">
      <div className="mb-4 flex items-center justify-between">
        <div>
          <h3 className="font-ds-heading text-ds-headline-sm text-ds-on-surface">
            Ready to upload
          </h3>
          <p className="text-ds-body-sm text-ds-on-surface-variant">
            Add optional metadata, then start each upload.
          </p>
        </div>
        <button
          type="button"
          onClick={onStartAll}
          className="flex items-center gap-2 rounded-ds-lg bg-ds-primary px-4 py-2 font-ds-label text-ds-label-md text-ds-on-primary shadow-sm transition-colors hover:bg-ds-primary-container"
        >
          <Icon name="cloud_upload" filled={false} className="text-[18px]" />
          Upload all ({staged.length})
        </button>
      </div>

      <div className="flex flex-col gap-3">
        {staged.map((file) => (
          <div
            key={file.id}
            className="rounded-ds-lg border border-ds-outline-variant bg-ds-surface-container-lowest p-4"
          >
            <div className="mb-3 flex items-center justify-between gap-4">
              <div className="flex min-w-0 items-center gap-3">
                <div className={`flex w-8 justify-center ${file.iconClassName}`}>
                  <Icon name={file.icon} />
                </div>
                <div className="flex min-w-0 flex-col">
                  <span className="truncate font-medium text-ds-body-sm text-ds-on-surface">
                    {file.name}
                  </span>
                  <span className="font-ds-label text-ds-label-sm text-ds-on-surface-variant">
                    {formatBytes(file.size)}
                  </span>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => onStart(file.id)}
                  className="flex items-center gap-1.5 rounded-ds-lg bg-ds-primary px-3 py-1.5 font-ds-label text-ds-label-sm text-ds-on-primary transition-colors hover:bg-ds-primary-container"
                >
                  <Icon name="upload" filled={false} className="text-[16px]" />
                  Upload
                </button>
                <button
                  type="button"
                  onClick={() => onRemove(file.id)}
                  title="Remove"
                  aria-label="Remove"
                  className="rounded-ds p-1.5 text-ds-secondary transition-colors hover:bg-ds-error-container hover:text-ds-error"
                >
                  <Icon name="close" filled={false} className="text-[18px]" />
                </button>
              </div>
            </div>

            <MetadataEditor
              fields={file.metadata}
              onChange={(fields) => onMetadataChange(file.id, fields)}
            />
          </div>
        ))}
      </div>
    </section>
  )
}
