import Icon from '@/components/Icon'
import { JobStatus } from '../jobsApi'
import type { DocumentScope, FileType, LibraryDocument } from '../types'

// File-type icon chip styling, keyed by file type. Shared by the card and table
// views so both render the same icon + colour for a given type.
export const FILE_TYPE_STYLES: Record<FileType, { icon: string; chipClassName: string }> = {
  pdf: { icon: 'picture_as_pdf', chipClassName: 'bg-ds-error-container text-ds-on-error-container' },
  docx: { icon: 'description', chipClassName: 'bg-ds-primary-fixed text-ds-primary' },
  txt: { icon: 'article', chipClassName: 'bg-ds-surface-container-high text-ds-secondary' },
  image: { icon: 'image', chipClassName: 'bg-ds-secondary-fixed text-ds-secondary' },
  video: { icon: 'movie', chipClassName: 'bg-ds-surface-container-high text-ds-secondary' },
  file: { icon: 'insert_drive_file', chipClassName: 'bg-ds-surface-container-high text-ds-secondary' },
}

// Visibility-scope indicator: one person for personal, a group for department,
// a building for organization.
const SCOPE_STYLES: Record<DocumentScope, { icon: string; label: string }> = {
  personal: { icon: 'person', label: 'Personal' },
  department: { icon: 'groups', label: 'Department' },
  organization: { icon: 'apartment', label: 'Organization' },
}

export function ScopeIndicator({ scope }: { scope: DocumentScope }) {
  const { icon, label } = SCOPE_STYLES[scope]
  return (
    <span
      title={`${label} file`}
      aria-label={`${label} file`}
      className="inline-flex shrink-0 self-start items-center gap-1 rounded-ds-full border border-ds-outline-variant/50 bg-ds-surface-container px-2 py-0.5 font-ds-label text-ds-label-sm text-ds-on-surface-variant"
    >
      <Icon name={icon} className="text-[14px]" />
      {label}
    </span>
  )
}

// AI-job indicator: a sparkle once metadata is ready, a spinner while it's still
// being generated. Other statuses (failed, unsupported, or no job) render nothing.
export function AiIndicator({ status }: { status?: LibraryDocument['aiStatus'] }) {
  if (status === JobStatus.COMPLETED) {
    return (
      <span
        title="AI-generated metadata"
        aria-label="AI-generated metadata"
        className="flex items-center rounded-ds-full bg-ds-tertiary-fixed p-1 text-ds-on-tertiary-fixed-variant"
      >
        <Icon name="auto_awesome" className="text-[14px]" />
      </span>
    )
  }

  if (status === JobStatus.PENDING || status === JobStatus.PROCESSING) {
    return (
      <span
        title="AI processing"
        aria-label="AI processing"
        className="flex items-center rounded-ds-full bg-ds-surface-container-high p-1 text-ds-on-surface-variant"
      >
        <Icon name="progress_activity" className="animate-spin text-[14px]" />
      </span>
    )
  }

  return null
}
