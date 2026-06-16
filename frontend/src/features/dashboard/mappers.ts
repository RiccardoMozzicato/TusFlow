import type { FileDTO } from '@/features/documents/documentsApi'
import type { FileType } from '@/features/documents/types'
import {
  formatDateTime,
  mapStatusToDocumentStatus,
  resolveFileType,
} from '@/features/documents/mappers'
import type { JobStatsDTO } from './dashboardApi'
import type { DocStatus, DocumentRow, StatCard } from './types'

// Projects backend data (files page + job stats) onto the dashboard view models.

// Table chip + label per file-type bucket, mirroring the design mock's styles.
const ROW_TYPE_STYLES: Record<FileType, { label: string; icon: string; iconClassName: string }> = {
  pdf: { label: 'PDF', icon: 'picture_as_pdf', iconClassName: 'bg-ds-error-container/20 text-ds-error' },
  docx: { label: 'DOCX', icon: 'description', iconClassName: 'bg-ds-primary-fixed/50 text-ds-primary' },
  txt: { label: 'TXT', icon: 'article', iconClassName: 'bg-ds-surface-variant text-ds-secondary' },
  image: { label: 'Image', icon: 'image', iconClassName: 'bg-ds-secondary-fixed text-ds-secondary' },
  video: { label: 'Video', icon: 'movie', iconClassName: 'bg-ds-surface-variant text-ds-secondary' },
  file: { label: 'File', icon: 'insert_drive_file', iconClassName: 'bg-ds-surface-variant text-ds-secondary' },
}

/** Maps a backend file onto a Recent Documents table row. */
export function mapFileToDocumentRow(dto: FileDTO): DocumentRow {
  const style = ROW_TYPE_STYLES[resolveFileType(dto.contentType, dto.fileName)]
  const status: DocStatus =
    mapStatusToDocumentStatus(dto.status) === 'processing' ? 'in_review' : 'processed'

  return {
    id: dto.id,
    name: dto.fileName ?? 'Untitled',
    type: style.label,
    status,
    modified: formatDateTime(dto.completedAt ?? dto.createdAt),
    icon: style.icon,
    iconClassName: style.iconClassName,
  }
}

/** Builds the four stat cards from the files total and the job counters. */
export function buildStatCards(totalDocuments: number, jobs: JobStatsDTO): StatCard[] {
  return [
    {
      label: 'Total Documents',
      value: totalDocuments.toLocaleString('en-US'),
      icon: 'description',
      iconClassName: 'bg-ds-surface-variant text-ds-on-surface',
    },
    {
      label: 'Pending Processing',
      value: jobs.pending.toLocaleString('en-US'),
      note: 'files',
      icon: 'hourglass_empty',
      iconClassName: 'bg-ds-primary-fixed text-ds-primary',
    },
    {
      label: 'Completed Today',
      value: jobs.completedSince.toLocaleString('en-US'),
      icon: 'check_circle',
      iconClassName: 'bg-ds-tertiary-fixed text-ds-tertiary',
    },
    {
      label: 'Failed Processing',
      value: jobs.failed.toLocaleString('en-US'),
      note: jobs.failed > 0 ? 'Action required' : undefined,
      noteTone: jobs.failed > 0 ? 'error' : undefined,
      icon: 'error',
      iconClassName: 'bg-ds-error-container text-ds-error',
      to: '/failed-jobs',
    },
  ]
}

/** ISO instant for the start of the local day — the "today" boundary for stats. */
export function startOfTodayIso(): string {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  return start.toISOString()
}
