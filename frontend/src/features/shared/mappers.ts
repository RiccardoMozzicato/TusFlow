import { formatBytes } from '@/features/upload/format'
import { formatDate, resolveFileType } from '@/features/documents/mappers'
import type { FileDTO } from '@/features/documents/documentsApi'
import type { FileType } from '@/features/documents/types'
import type { SharedDocument, SharePermission } from './types'

const MISSING = '—'

// Human-readable label per coarse file type, shown under the file name.
const TYPE_LABEL: Record<FileType, string> = {
  pdf: 'PDF Document',
  docx: 'Word Document',
  txt: 'Text File',
  image: 'Image',
  video: 'Video',
  file: 'Document',
}

// Maps the backend PERMISSION_LEVEL onto the view's coarse bucket. Anything
// other than READ_WRITE reads as view-only (the least-privileged default).
export function mapPermission(permission?: string): SharePermission {
  return permission?.toUpperCase() === 'READ_WRITE' ? 'edit' : 'view'
}

// Projects a shared FileDTO onto the table's view model.
export function mapToSharedDocument(dto: FileDTO): SharedDocument {
  const fileType = resolveFileType(dto.contentType, dto.fileName)
  return {
    id: dto.id,
    title: dto.fileName ?? 'Untitled',
    fileType,
    typeLabel: TYPE_LABEL[fileType],
    size: dto.size != null ? formatBytes(dto.size) : MISSING,
    owner: dto.ownerName ?? dto.owner ?? 'Unknown',
    sharedDate: formatDate(dto.sharedAt),
    permission: mapPermission(dto.permission),
  }
}
