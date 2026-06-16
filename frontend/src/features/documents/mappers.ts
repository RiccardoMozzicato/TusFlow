import { formatBytes } from '@/features/upload/format'
import { SharingScope, type FileDTO } from './documentsApi'
import type { JobStatus } from './jobsApi'
import type { DocumentScope, DocumentStatus, FileType, LibraryDocument } from './types'

// Maps a raw MIME content type onto the library's coarse FileType buckets.
// Anything we don't recognize falls back to the generic document icon.
const PDF_CONTENT_TYPE = 'application/pdf'
const TXT_CONTENT_TYPE = 'text/plain'
const WORD_CONTENT_TYPES = [
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
]

function contentTypeToFileType(contentType?: string): FileType | null {
  if (!contentType) return null
  const type = contentType.toLowerCase()

  if (type === PDF_CONTENT_TYPE) return 'pdf'
  if (WORD_CONTENT_TYPES.includes(type)) return 'docx'
  if (type === TXT_CONTENT_TYPE) return 'txt'
  if (type.startsWith('image/')) return 'image'
  if (type.startsWith('video/')) return 'video'
  return null
}

export function mapContentTypeToFileType(contentType?: string): FileType {
  return contentTypeToFileType(contentType) ?? 'file'
}

// Maps a file extension onto a FileType bucket. Used as a fallback because the
// tus uploader stores the protocol's `application/offset+octet-stream` as the
// version contentType, which says nothing about the actual file.
const EXTENSION_TO_FILE_TYPE: Record<string, FileType> = {
  pdf: 'pdf',
  doc: 'docx',
  docx: 'docx',
  txt: 'txt',
  png: 'image',
  jpg: 'image',
  jpeg: 'image',
  gif: 'image',
  webp: 'image',
  svg: 'image',
  mp4: 'video',
  mov: 'video',
  avi: 'video',
  webm: 'video',
  mkv: 'video',
}

function fileNameToFileType(fileName?: string): FileType | null {
  const extension = fileName?.split('.').pop()?.toLowerCase()
  if (!extension || extension === fileName?.toLowerCase()) return null
  return EXTENSION_TO_FILE_TYPE[extension] ?? null
}

/**
 * Resolves the FileType bucket for a backend file: a recognized MIME content
 * type wins, then the file-name extension, then the generic 'file' bucket.
 */
export function resolveFileType(contentType?: string, fileName?: string): FileType {
  return contentTypeToFileType(contentType) ?? fileNameToFileType(fileName) ?? 'file'
}

// Maps a coarse FileType onto the `contentType` filter token sent to getFiles.
// The backend does a case-insensitive *contains* match, so we send the lightest
// substring that uniquely identifies the bucket:
//  - `word` matches both application/msword and the OpenXML .docx MIME
//  - `text/plain` also matches `text/plain; charset=utf-8`
// MIME families (image/*, video/*) still aren't offered as single-token filters.
export const FILE_TYPE_CONTENT_TYPE: Partial<Record<FileType, string>> = {
  pdf: PDF_CONTENT_TYPE,
  docx: 'word',
  txt: TXT_CONTENT_TYPE,
}

const MISSING_VALUE = '—'

const DATE_FORMATTER = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
})

const DATE_TIME_FORMATTER = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

// Formats an ISO timestamp into a short human-readable date. Returns a dash for
// missing or unparseable input so the UI never shows "Invalid Date".
export function formatDate(raw?: string): string {
  return formatWith(DATE_FORMATTER, raw)
}

// Like formatDate, but also includes the time of day (local timezone).
export function formatDateTime(raw?: string): string {
  return formatWith(DATE_TIME_FORMATTER, raw)
}

function formatWith(formatter: Intl.DateTimeFormat, raw?: string): string {
  if (!raw) return MISSING_VALUE

  const parsed = new Date(raw)
  if (Number.isNaN(parsed.getTime())) return MISSING_VALUE

  return formatter.format(parsed)
}

// Maps the backend SharingScope onto the library's presentation scope. Anything
// unrecognized (or absent) falls back to 'personal', the most restrictive scope.
const SCOPE_BY_SHARING_SCOPE: Record<SharingScope, DocumentScope> = {
  [SharingScope.PERSONAL]: 'personal',
  [SharingScope.DEPARTMENT]: 'department',
  [SharingScope.ORGANIZATION]: 'organization',
}

export function mapSharingScopeToScope(scope?: string): DocumentScope {
  const key = scope?.toUpperCase() as SharingScope | undefined
  return (key && SCOPE_BY_SHARING_SCOPE[key]) ?? 'personal'
}

// Maps the backend version status onto a library badge. A MERGE_PENDING current
// version means the upload is still being assembled, so it reads as 'processing';
// anything else (AVAILABLE, or an absent status) reads as 'ready'.
export function mapStatusToDocumentStatus(status?: string): DocumentStatus {
  return status?.toUpperCase() === 'MERGE_PENDING' ? 'processing' : 'ready'
}

// Projects a backend FileDTO onto the presentation-facing LibraryDocument. The
// AI-job status is sourced separately (jobs service) and passed in.
export function mapFileToDocument(dto: FileDTO, aiStatus?: JobStatus): LibraryDocument {
  return {
    id: dto.id,
    title: dto.fileName ?? 'Untitled',
    fileType: resolveFileType(dto.contentType, dto.fileName),
    scope: mapSharingScopeToScope(dto.sharingScope),
    status: mapStatusToDocumentStatus(dto.status),
    owner: dto.ownerName ?? dto.owner ?? 'Unknown',
    created: formatDateTime(dto.createdAt),
    version: `v${dto.version ?? 1}`,
    versionNumber: dto.version ?? 1,
    size: dto.size != null ? formatBytes(dto.size) : MISSING_VALUE,
    aiStatus,
  }
}
