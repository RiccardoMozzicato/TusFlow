import Icon from '@/components/Icon'
import type { FileType } from '../../types'

interface PrimaryInfoCardProps {
  title: string
  fileType: FileType
  owner: string
  /** Pre-formatted creation date. */
  date: string
}

// File-type icon shown beside the title. Mirrors the library card's mapping.
const FILE_TYPE_ICON: Record<FileType, string> = {
  pdf: 'picture_as_pdf',
  docx: 'description',
  txt: 'article',
  image: 'image',
  video: 'movie',
  file: 'insert_drive_file',
}

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

// Top card of the metadata panel: document title + file icon, with the core file
// properties (owner and creation date) called out beneath it.
export default function PrimaryInfoCard({ title, fileType, owner, date }: PrimaryInfoCardProps) {
  return (
    <div className="flex flex-col gap-4 rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-6 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <h2 className="break-words font-ds-heading text-ds-headline-sm leading-tight text-ds-on-surface">
          {title}
        </h2>
        <Icon name={FILE_TYPE_ICON[fileType]} className="shrink-0 text-[28px] text-ds-primary" />
      </div>

      <div className="grid grid-cols-1 gap-y-2">
        <div className="flex items-center justify-between py-1 border-b border-dashed border-ds-surface-container">
          <span className="font-ds-body text-ds-body-sm text-ds-secondary">Owner</span>
          <div className="flex items-center gap-2">
            <div className="flex h-5 w-5 items-center justify-center rounded-ds-full bg-ds-primary-container font-ds-label text-ds-label-sm font-bold text-ds-on-primary-container">
              {initialsOf(owner)}
            </div>
            <span className="font-ds-body text-ds-body-sm font-medium text-ds-on-surface">
              {owner}
            </span>
          </div>
        </div>
        <div className="flex items-center justify-between py-1">
          <span className="font-ds-body text-ds-body-sm text-ds-secondary">Date</span>
          <span className="font-ds-body text-ds-body-sm font-medium text-ds-on-surface">
            {date}
          </span>
        </div>
      </div>
    </div>
  )
}
