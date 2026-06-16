import { formatBytes } from '@/features/upload/format'
import { formatDate, resolveFileType } from '@/features/documents/mappers'
import type { FileType } from '@/features/documents/types'
import type { FileVersionFavoriteDTO } from './favoritesApi'
import type { FavoriteDocument } from './types'

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

// Projects a favorited version DTO onto the table's view model.
export function mapToFavoriteDocument(dto: FileVersionFavoriteDTO): FavoriteDocument {
  const fileType = resolveFileType(dto.contentType ?? undefined, dto.fileName)
  return {
    id: `${dto.fileId}-v${dto.version}`,
    fileId: dto.fileId,
    title: dto.fileName,
    version: dto.version,
    versionLabel: `v${dto.version}`,
    fileType,
    typeLabel: TYPE_LABEL[fileType],
    size: dto.size != null ? formatBytes(dto.size) : MISSING,
    owner: dto.ownerName ?? dto.ownerId ?? 'Unknown',
    favoritedDate: formatDate(dto.favoritedAt),
  }
}
