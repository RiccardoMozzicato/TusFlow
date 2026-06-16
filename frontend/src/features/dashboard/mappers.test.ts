import { describe, expect, it } from 'vitest'
import { buildStatCards, mapFileToDocumentRow, startOfTodayIso } from './mappers'
import type { FileDTO } from '@/features/documents/documentsApi'

describe('buildStatCards', () => {
  const stats = { pending: 34, completedSince: 128, failed: 2 }

  it('builds the four dashboard cards from totals and job stats', () => {
    const cards = buildStatCards(12482, stats)

    expect(cards.map((card) => card.label)).toEqual([
      'Total Documents',
      'Pending Processing',
      'Completed Today',
      'Failed Processing',
    ])
    expect(cards[0].value).toBe('12,482')
    expect(cards[1].value).toBe('34')
    expect(cards[2].value).toBe('128')
    expect(cards[3].value).toBe('2')
  })

  it('flags failed jobs as action required only when present', () => {
    expect(buildStatCards(0, stats)[3].note).toBe('Action required')
    expect(buildStatCards(0, { ...stats, failed: 0 })[3].note).toBeUndefined()
  })
})

describe('mapFileToDocumentRow', () => {
  const base: FileDTO = {
    id: 'abc',
    fileName: 'Report.pdf',
    contentType: 'application/pdf',
    createdAt: '2023-10-20T09:00:00Z',
    status: 'AVAILABLE',
  }

  it('maps an available PDF to a processed row', () => {
    const row = mapFileToDocumentRow(base)
    expect(row.id).toBe('abc')
    expect(row.name).toBe('Report.pdf')
    expect(row.type).toBe('PDF')
    expect(row.status).toBe('processed')
    expect(row.icon).toBe('picture_as_pdf')
    expect(row.modified).toContain('Oct 20, 2023')
  })

  it('maps a MERGE_PENDING file to in_review', () => {
    expect(mapFileToDocumentRow({ ...base, status: 'MERGE_PENDING' }).status).toBe('in_review')
  })

  it('derives the type from the file name when the content type is opaque', () => {
    const row = mapFileToDocumentRow({
      ...base,
      fileName: 'Contract.docx',
      contentType: 'application/offset+octet-stream',
    })
    expect(row.type).toBe('DOCX')
    expect(row.icon).toBe('description')
  })
})

describe('startOfTodayIso', () => {
  it('returns an ISO instant at local midnight', () => {
    const iso = startOfTodayIso()
    expect(new Date(iso).getHours()).toBe(0)
    expect(new Date(iso).getMinutes()).toBe(0)
  })
})
