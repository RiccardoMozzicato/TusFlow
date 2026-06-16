import { describe, expect, it } from 'vitest'
import { mapToFavoriteDocument } from './mappers'
import type { FileVersionFavoriteDTO } from './favoritesApi'

const baseDto: FileVersionFavoriteDTO = {
  id: 1,
  fileId: 'file-1',
  fileName: 'Report.pdf',
  version: 3,
  size: 2_516_582,
  contentType: 'application/pdf',
  status: 'AVAILABLE',
  ownerId: 'uuid-9',
  ownerName: 'Sarah Jenkins',
  favoritedAt: '2023-10-12T09:00:00Z',
}

describe('mapToFavoriteDocument', () => {
  it('projects a favorite DTO onto the table model', () => {
    const doc = mapToFavoriteDocument(baseDto)
    expect(doc).toMatchObject({
      id: 'file-1-v3',
      fileId: 'file-1',
      title: 'Report.pdf',
      version: 3,
      versionLabel: 'v3',
      fileType: 'pdf',
      typeLabel: 'PDF Document',
      owner: 'Sarah Jenkins',
    })
    expect(doc.size).toBe('2.4 MB')
    expect(doc.favoritedDate).toContain('Oct 12, 2023')
  })

  it('builds distinct row keys for different versions of the same file', () => {
    const v1 = mapToFavoriteDocument({ ...baseDto, version: 1 })
    const v3 = mapToFavoriteDocument({ ...baseDto, version: 3 })
    expect(v1.id).not.toBe(v3.id)
  })

  it('applies fallbacks for missing fields', () => {
    const doc = mapToFavoriteDocument({
      ...baseDto,
      size: null,
      contentType: null,
      fileName: 'notes',
      ownerName: null,
    })
    expect(doc.size).toBe('—')
    expect(doc.fileType).toBe('file')
    expect(doc.owner).toBe('uuid-9')
  })
})
