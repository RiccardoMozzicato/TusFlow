import { describe, expect, it } from 'vitest'
import {
  formatDate,
  formatDateTime,
  mapContentTypeToFileType,
  mapFileToDocument,
  mapSharingScopeToScope,
  mapStatusToDocumentStatus,
  resolveFileType,
} from './mappers'
import type { FileDTO } from './documentsApi'

describe('mapContentTypeToFileType', () => {
  it('maps application/pdf to pdf', () => {
    expect(mapContentTypeToFileType('application/pdf')).toBe('pdf')
  })

  it('maps Word MIME types to docx', () => {
    expect(mapContentTypeToFileType('application/msword')).toBe('docx')
    expect(
      mapContentTypeToFileType(
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      ),
    ).toBe('docx')
  })

  it('maps text/plain to txt', () => {
    expect(mapContentTypeToFileType('text/plain')).toBe('txt')
  })

  it('maps image/* to image and video/* to video', () => {
    expect(mapContentTypeToFileType('image/png')).toBe('image')
    expect(mapContentTypeToFileType('video/mp4')).toBe('video')
  })

  it('is case-insensitive', () => {
    expect(mapContentTypeToFileType('IMAGE/JPEG')).toBe('image')
  })

  it('falls back to the generic file bucket for unknown or missing content types', () => {
    expect(mapContentTypeToFileType('application/zip')).toBe('file')
    expect(mapContentTypeToFileType(undefined)).toBe('file')
  })
})

describe('resolveFileType', () => {
  it('prefers a recognized content type', () => {
    expect(resolveFileType('application/pdf', 'notes.txt')).toBe('pdf')
  })

  it('falls back to the file extension when the content type is unrecognized', () => {
    expect(resolveFileType('application/offset+octet-stream', 'Report.pdf')).toBe('pdf')
    expect(resolveFileType(undefined, 'Contract.docx')).toBe('docx')
    expect(resolveFileType(undefined, 'legacy.doc')).toBe('docx')
    expect(resolveFileType(undefined, 'readme.TXT')).toBe('txt')
    expect(resolveFileType(undefined, 'photo.png')).toBe('image')
    expect(resolveFileType(undefined, 'clip.mp4')).toBe('video')
  })

  it('falls back to the generic bucket when neither helps', () => {
    expect(resolveFileType(undefined, 'archive.zip')).toBe('file')
    expect(resolveFileType(undefined, 'no-extension')).toBe('file')
    expect(resolveFileType(undefined, undefined)).toBe('file')
  })
})

describe('formatDate', () => {
  it('formats an ISO timestamp as a short date', () => {
    expect(formatDate('2023-10-20T09:00:00Z')).toBe('Oct 20, 2023')
  })

  it('returns a dash for unparseable input', () => {
    expect(formatDate('not-a-date')).toBe('—')
  })

  it('returns a dash for empty or missing input', () => {
    expect(formatDate('')).toBe('—')
    expect(formatDate(undefined)).toBe('—')
  })
})

describe('formatDateTime', () => {
  it('includes the date and a time component', () => {
    // Exact time is timezone-dependent; assert the date plus an HH:MM AM/PM part.
    const formatted = formatDateTime('2023-10-20T09:00:00Z')
    expect(formatted).toContain('Oct 20, 2023')
    expect(formatted).toMatch(/\d{1,2}:\d{2}\s?(AM|PM)/i)
  })

  it('returns a dash for missing input', () => {
    expect(formatDateTime(undefined)).toBe('—')
  })
})

describe('mapFileToDocument', () => {
  it('maps a fully populated DTO', () => {
    const dto: FileDTO = {
      id: 'abc',
      fileName: 'Report.pdf',
      contentType: 'application/pdf',
      version: 3,
      createdAt: '2023-10-20T09:00:00Z',
      completedAt: '2023-10-24T09:12:00Z',
      ownerName: 'Alex Mercer',
      owner: 'uuid-1',
      sharingScope: 'DEPARTMENT',
    }

    const doc = mapFileToDocument(dto)
    expect(doc).toMatchObject({
      id: 'abc',
      title: 'Report.pdf',
      fileType: 'pdf',
      scope: 'department',
      status: 'ready',
      owner: 'Alex Mercer',
      version: 'v3',
    })
    // created carries date + time; assert the date part (time is TZ-dependent).
    expect(doc.created).toContain('Oct 20, 2023')
  })

  it('applies fallbacks for missing fields', () => {
    const doc = mapFileToDocument({ id: 'x', createdAt: '2023-10-20T09:00:00Z' })
    expect(doc.title).toBe('Untitled')
    expect(doc.owner).toBe('Unknown')
    expect(doc.version).toBe('v1')
    expect(doc.fileType).toBe('file')
    expect(doc.scope).toBe('personal')
    expect(doc.status).toBe('ready')
  })

  it('derives the file type from the file name when the content type is opaque', () => {
    const doc = mapFileToDocument({
      id: 'x',
      createdAt: '2023-10-20T09:00:00Z',
      fileName: 'Report.pdf',
      contentType: 'application/offset+octet-stream',
    })
    expect(doc.fileType).toBe('pdf')
  })

  it('uses the owner UUID when no display name is present', () => {
    const doc = mapFileToDocument({
      id: 'x',
      createdAt: '2023-10-20T09:00:00Z',
      owner: 'uuid-42',
    })
    expect(doc.owner).toBe('uuid-42')
  })

  it('marks a MERGE_PENDING file as processing', () => {
    const doc = mapFileToDocument({
      id: 'x',
      createdAt: '2023-10-20T09:00:00Z',
      status: 'MERGE_PENDING',
    })
    expect(doc.status).toBe('processing')
  })

  it('formats the size, falling back to a dash when absent', () => {
    expect(
      mapFileToDocument({ id: 'x', createdAt: '2023-10-20T09:00:00Z', size: 2411724 }).size,
    ).toBe('2.3 MB')
    expect(
      mapFileToDocument({ id: 'x', createdAt: '2023-10-20T09:00:00Z' }).size,
    ).toBe('—')
  })

  it('carries the AI status passed in, leaving it undefined when omitted', () => {
    expect(
      mapFileToDocument({ id: 'x', createdAt: '2023-10-20T09:00:00Z' }, 'COMPLETED').aiStatus,
    ).toBe('COMPLETED')
    expect(
      mapFileToDocument({ id: 'x', createdAt: '2023-10-20T09:00:00Z' }).aiStatus,
    ).toBeUndefined()
  })
})

describe('mapSharingScopeToScope', () => {
  it('maps each known scope (case-insensitive)', () => {
    expect(mapSharingScopeToScope('PERSONAL')).toBe('personal')
    expect(mapSharingScopeToScope('department')).toBe('department')
    expect(mapSharingScopeToScope('ORGANIZATION')).toBe('organization')
  })

  it('falls back to personal for missing or unknown scopes', () => {
    expect(mapSharingScopeToScope(undefined)).toBe('personal')
    expect(mapSharingScopeToScope('NONSENSE')).toBe('personal')
  })
})

describe('mapStatusToDocumentStatus', () => {
  it('maps MERGE_PENDING to processing (case-insensitive)', () => {
    expect(mapStatusToDocumentStatus('MERGE_PENDING')).toBe('processing')
    expect(mapStatusToDocumentStatus('merge_pending')).toBe('processing')
  })

  it('maps AVAILABLE and missing status to ready', () => {
    expect(mapStatusToDocumentStatus('AVAILABLE')).toBe('ready')
    expect(mapStatusToDocumentStatus(undefined)).toBe('ready')
  })
})
