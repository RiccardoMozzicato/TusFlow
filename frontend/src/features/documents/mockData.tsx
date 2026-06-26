import type { FileTypeOption } from './types'

// File-type options for the library filter rail.
export const FILE_TYPE_OPTIONS: FileTypeOption[] = [
  { type: 'pdf', label: 'PDF', icon: 'picture_as_pdf', iconClassName: 'text-ds-error' },
  { type: 'docx', label: 'DOCX', icon: 'description', iconClassName: 'text-ds-primary' },
  { type: 'txt', label: 'TXT', icon: 'article', iconClassName: 'text-ds-secondary' },
  { type: 'image', label: 'Image', icon: 'image', iconClassName: 'text-ds-tertiary-container' },
  { type: 'video', label: 'Video', icon: 'movie', iconClassName: 'text-ds-secondary' },
]
