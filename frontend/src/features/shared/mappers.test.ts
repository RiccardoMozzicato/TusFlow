import { describe, expect, it } from 'vitest'
import { mapPermission, mapToSharedDocument } from './mappers'
import type { FileDTO } from '@/features/documents/documentsApi'

const baseDto: FileDTO = {
  id: 'file-1',
  fileName: 'Report.pdf',
  contentType: 'application/pdf',
  createdAt: '2023-10-01T09:00:00Z',
  sharedAt: '2023-10-12T09:00:00Z',
  size: 2_516_582,
  ownerName: 'Sarah Jenkins',
  permission: 'READ_WRITE',
}

describe('mapPermission', () => {
  it('maps READ_WRITE to edit (case-insensitive)', () => {
    expect(mapPermission('READ_WRITE')).toBe('edit')
    expect(mapPermission('read_write')).toBe('edit')
  })

  it('treats READ_ONLY and anything else as view', () => {
    expect(mapPermission('READ_ONLY')).toBe('view')
    expect(mapPermission(undefined)).toBe('view')
    expect(mapPermission('weird')).toBe('view')
  })
})

describe('mapToSharedDocument', () => {
  it('projects a shared FileDTO onto the table model', () => {
    const doc = mapToSharedDocument(baseDto)
    expect(doc).toMatchObject({
      id: 'file-1',
      title: 'Report.pdf',
      fileType: 'pdf',
      typeLabel: 'PDF Document',
      owner: 'Sarah Jenkins',
      permission: 'edit',
    })
    expect(doc.size).toBe('2.4 MB')
    expect(doc.sharedDate).toContain('Oct 12, 2023')
  })

  it('applies fallbacks for missing fields', () => {
    const doc = mapToSharedDocument({ id: 'x', createdAt: '2023-10-01T09:00:00Z' })
    expect(doc.title).toBe('Untitled')
    expect(doc.owner).toBe('Unknown')
    expect(doc.size).toBe('—')
    expect(doc.sharedDate).toBe('—')
    expect(doc.permission).toBe('view')
    expect(doc.fileType).toBe('file')
  })

  it('falls back to the owner UUID when no display name is present', () => {
    const doc = mapToSharedDocument({
      id: 'x',
      createdAt: '2023-10-01T09:00:00Z',
      owner: 'uuid-9',
    })
    expect(doc.owner).toBe('uuid-9')
  })
})
