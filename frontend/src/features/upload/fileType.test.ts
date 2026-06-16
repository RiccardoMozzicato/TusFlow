import { describe, expect, it } from 'vitest'
import { fileTypeChip } from './fileType'

describe('fileTypeChip', () => {
  it('maps PDF to the error-tinted pdf icon', () => {
    expect(fileTypeChip('application/pdf')).toEqual({
      icon: 'picture_as_pdf',
      iconClassName: 'text-ds-error',
    })
  })

  it('maps images and videos to their chips', () => {
    expect(fileTypeChip('image/png').icon).toBe('image')
    expect(fileTypeChip('video/mp4').icon).toBe('movie')
  })

  it('falls back to the generic file chip for unknown or missing types', () => {
    expect(fileTypeChip('application/zip').icon).toBe('insert_drive_file')
    expect(fileTypeChip(undefined).icon).toBe('insert_drive_file')
  })
})
