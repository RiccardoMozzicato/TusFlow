import { mapContentTypeToFileType } from '@/features/documents/mappers'
import type { FileType } from '@/features/documents/types'

// Visual treatment for a transfer-queue row's file-type chip. The library's
// DocumentCard tints a filled chip; the upload row tints just the icon, so we
// keep a small text-color map here while reusing the library's content-type
// bucketing (mapContentTypeToFileType) to stay DRY.

export interface FileTypeChip {
  icon: string
  /** Tailwind text-color class applied to the icon. */
  iconClassName: string
}

const CHIP_BY_TYPE: Record<FileType, FileTypeChip> = {
  pdf: { icon: 'picture_as_pdf', iconClassName: 'text-ds-error' },
  docx: { icon: 'description', iconClassName: 'text-ds-on-primary-fixed-variant' },
  txt: { icon: 'text_snippet', iconClassName: 'text-ds-outline' },
  image: { icon: 'image', iconClassName: 'text-ds-outline' },
  video: { icon: 'movie', iconClassName: 'text-ds-tertiary' },
  file: { icon: 'insert_drive_file', iconClassName: 'text-ds-outline' },
}

/** Maps a File's MIME type onto the row's icon + tint. */
export function fileTypeChip(contentType?: string): FileTypeChip {
  return CHIP_BY_TYPE[mapContentTypeToFileType(contentType)]
}
