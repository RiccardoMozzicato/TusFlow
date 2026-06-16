import { SharingScope, type FileDTO } from './documentsApi'
import type { FileShareDTO, FileVersionDTO, MetadataAIDTO } from './detailsApi'
import type {
  AiAnalysisStatus,
  DocumentDetail,
  DocumentShare,
  DocumentVersion,
  MetadataEntry,
  ProcessingStep,
} from './types'
import { formatDate, resolveFileType } from './mappers'

const MISSING = '—'
const UNTITLED = 'Untitled'
const UNKNOWN_OWNER = 'Unknown'

// Keys present in a version's metadata map that are system-managed rather than
// user-entered, and so excluded from the "user metadata" view. AI fields are
// listed defensively even though they live in a separate map server-side.
const RESERVED_METADATA_KEYS = new Set([
  'filename',
  'analysisStatus',
  'summary',
  'tags',
  'sensitivity',
  'status',
  'contentType',
])

// Backend statuses that mean a file's current version is fully available.
const AVAILABLE_STATUS = 'AVAILABLE'

// Formats an ISO timestamp into a short date + time for the timeline / versions.
const DATE_TIME_FORMATTER = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

function formatDateTime(raw?: string): string {
  if (!raw) return MISSING
  const parsed = new Date(raw)
  if (Number.isNaN(parsed.getTime())) return MISSING
  return DATE_TIME_FORMATTER.format(parsed)
}

// Splits the backend's comma-separated tag string into trimmed, non-empty tags.
export function parseTags(raw?: string | null): string[] {
  if (!raw) return []
  return raw
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
}

// Derives the AI analysis outcome from a file's generated metadata. Prefers the
// explicit analysisStatus; falls back to the summary sentinels / presence of
// content for files analyzed before analysisStatus was persisted (mirrors the
// backend's completed/failed/unsupported heuristics).
export function deriveAiAnalysisStatus(aiMeta: MetadataAIDTO | null): AiAnalysisStatus {
  if (!aiMeta) return 'pending'
  switch (aiMeta.analysisStatus?.toUpperCase()) {
    case 'COMPLETED':
      return 'completed'
    case 'FAILED':
      return 'failed'
    case 'UNSUPPORTED':
      return 'unsupported'
  }
  if (aiMeta.summary === 'AI analysis failed') return 'failed'
  if (aiMeta.summary === 'AI analysis not supported') return 'unsupported'
  if (aiMeta.summary || aiMeta.tags) return 'completed'
  return 'pending'
}

// Derives the processing timeline from the file's lifecycle and AI metadata.
// Upload always completed; AI summary done once metadata exists; the file is
// "Completed" once its current version is AVAILABLE.
export function buildProcessingSteps(
  file: FileDTO,
  aiMeta: MetadataAIDTO | null,
): ProcessingStep[] {
  const aiDone = aiMeta != null && Boolean(aiMeta.summary || aiMeta.tags)
  const completed = file.completedAt != null || aiMeta?.status === AVAILABLE_STATUS

  return [
    { label: 'Uploaded', timestamp: formatDateTime(file.createdAt), done: true },
    {
      label: 'Processing AI Summary',
      timestamp: aiDone ? formatDateTime(file.completedAt) : undefined,
      done: aiDone,
    },
    { label: 'Completed', timestamp: undefined, done: completed },
  ]
}

// Maps version DTOs onto the view model, newest-first. Versions carry no author,
// so the file owner is used as the best available attribution.
export function mapVersions(
  versions: FileVersionDTO[],
  currentVersion: number | undefined,
  ownerName: string,
): DocumentVersion[] {
  return [...versions]
    .sort((a, b) => b.version - a.version)
    .map((v) => ({
      id: String(v.id ?? v.version),
      version: v.version,
      label: `v${v.version}`,
      filename: v.filename,
      author: ownerName,
      date: formatDate(v.createdAt),
      isCurrent: currentVersion != null && v.version === currentVersion,
    }))
}

// Extracts the current version's user-entered metadata as sorted key/value
// entries, dropping system-managed keys and blanks. Falls back to the newest
// version's map when the current version can't be matched.
export function extractUserMetadata(
  versions: FileVersionDTO[],
  currentVersion: number | undefined,
): MetadataEntry[] {
  const current =
    versions.find((version) => version.version === currentVersion) ?? versions[0]
  const map = current?.metadata
  if (!map) return []
  return Object.entries(map)
    .filter(([key, value]) => !RESERVED_METADATA_KEYS.has(key) && Boolean(value))
    .map(([key, value]) => ({ key, value }))
    .sort((a, b) => a.key.localeCompare(b.key))
}

// Projects the backend DTOs onto the page's DocumentDetail view model.
export function mapToDocumentDetail(
  file: FileDTO,
  aiMeta: MetadataAIDTO | null,
  versions: FileVersionDTO[],
): DocumentDetail {
  const title = file.fileName ?? UNTITLED
  const owner = file.ownerName ?? file.owner ?? UNKNOWN_OWNER

  return {
    id: file.id,
    title,
    fileType: resolveFileType(file.contentType, file.fileName),
    sharingScope: file.sharingScope ?? SharingScope.PERSONAL,
    currentVersion: file.version ?? 1,
    breadcrumbs: [{ label: 'Documents', to: '/documents' }, { label: title }],
    aiAnalysisStatus: deriveAiAnalysisStatus(aiMeta),
    aiSummary: aiMeta?.summary ?? null,
    sensitivity: aiMeta?.sensitivity ?? MISSING,
    tags: parseTags(aiMeta?.tags),
    userMetadata: extractUserMetadata(versions, file.version),
    owner,
    date: formatDate(file.createdAt),
    processing: buildProcessingSteps(file, aiMeta),
    versions: mapVersions(versions, file.version, owner),
  }
}

// Projects a share DTO onto the sharing panel's view model.
export function mapShare(dto: FileShareDTO): DocumentShare {
  return {
    id: String(dto.id ?? dto.userID),
    shareId: dto.id,
    userId: dto.userID,
    userName: dto.userName ?? null,
    permission: dto.permission,
    sharedAt: formatDate(dto.timestamp),
  }
}
