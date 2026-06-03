import Icon from '@/components/Icon'
import type { FileType } from '../../types'

interface PrimaryInfoCardProps {
  title: string
  fileType: FileType
  aiSummary: string
}

// File-type icon shown beside the title. Mirrors the library card's mapping.
const FILE_TYPE_ICON: Record<FileType, string> = {
  pdf: 'picture_as_pdf',
  docx: 'description',
  image: 'image',
  video: 'movie',
}

// Top card of the metadata panel: document title + file icon, with the
// generated AI summary called out beneath it.
export default function PrimaryInfoCard({ title, fileType, aiSummary }: PrimaryInfoCardProps) {
  return (
    <div className="flex flex-col gap-4 rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-6 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <h2 className="break-words font-ds-heading text-ds-headline-sm leading-tight text-ds-on-surface">
          {title}
        </h2>
        <Icon name={FILE_TYPE_ICON[fileType]} className="shrink-0 text-[28px] text-ds-primary" />
      </div>

      <div className="rounded-ds-lg border border-ds-outline-variant border-l-4 border-l-ds-tertiary-fixed bg-ds-surface-container-low p-4">
        <div className="mb-3 flex items-center gap-2">
          <span className="flex items-center gap-1 rounded bg-ds-tertiary-fixed px-2 py-0.5 font-ds-label text-ds-label-sm uppercase tracking-wider text-ds-on-tertiary-fixed">
            <Icon name="auto_awesome" className="text-[12px]" />
            AI Summary
          </span>
        </div>
        <p className="font-ds-body text-ds-body-sm leading-relaxed text-ds-on-surface-variant">
          &ldquo;{aiSummary}&rdquo;
        </p>
      </div>
    </div>
  )
}
