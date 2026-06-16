import { describe, expect, it } from 'vitest'
import {
  buildProcessingSteps,
  deriveAiAnalysisStatus,
  extractUserMetadata,
  mapShare,
  mapToDocumentDetail,
  mapVersions,
  parseTags,
} from './detailsMappers'
import type { FileDTO } from './documentsApi'
import type { FileShareDTO, FileVersionDTO, MetadataAIDTO } from './detailsApi'

const baseFile: FileDTO = {
  id: 'file-1',
  fileName: 'Report.pdf',
  contentType: 'application/pdf',
  version: 2,
  createdAt: '2023-10-24T09:12:00Z',
  completedAt: '2023-10-24T09:20:00Z',
  ownerName: 'Alex Mercer',
  owner: 'uuid-owner',
}

const aiMeta: MetadataAIDTO = {
  tags: 'Q3, 2023 ,Final',
  summary: 'A concise summary.',
  sensitivity: 'Confidential',
  status: 'AVAILABLE',
}

describe('parseTags', () => {
  it('splits, trims, and drops empty tags', () => {
    expect(parseTags('Q3, 2023 ,Final, ')).toEqual(['Q3', '2023', 'Final'])
  })

  it('returns an empty array for null or empty input', () => {
    expect(parseTags(null)).toEqual([])
    expect(parseTags(undefined)).toEqual([])
    expect(parseTags('')).toEqual([])
  })
})

describe('buildProcessingSteps', () => {
  it('marks all steps done when AI metadata exists and the file is complete', () => {
    const steps = buildProcessingSteps(baseFile, aiMeta)
    expect(steps.map((s) => s.done)).toEqual([true, true, true])
    expect(steps[0].label).toBe('Uploaded')
  })

  it('leaves AI and completed steps pending when metadata is absent', () => {
    const steps = buildProcessingSteps({ ...baseFile, completedAt: undefined }, null)
    expect(steps[0].done).toBe(true)
    expect(steps[1].done).toBe(false)
    expect(steps[2].done).toBe(false)
  })

  it('treats AVAILABLE AI status as completed even without completedAt', () => {
    const steps = buildProcessingSteps({ ...baseFile, completedAt: undefined }, aiMeta)
    expect(steps[2].done).toBe(true)
  })
})

describe('mapVersions', () => {
  const versions: FileVersionDTO[] = [
    {
      id: 1,
      version: 1,
      filename: 'Report.pdf',
      size: 10,
      contentType: 'application/pdf',
      status: 'AVAILABLE',
      createdAt: '2023-09-01T00:00:00Z',
      lastUpdateAt: '2023-09-01T00:00:00Z',
    },
    {
      id: 2,
      version: 2,
      filename: 'Report.pdf',
      size: 12,
      contentType: 'application/pdf',
      status: 'AVAILABLE',
      createdAt: '2023-10-24T09:12:00Z',
      lastUpdateAt: '2023-10-24T09:12:00Z',
    },
  ]

  it('sorts newest-first and flags the current version', () => {
    const mapped = mapVersions(versions, 2, 'Alex Mercer')
    expect(mapped.map((v) => v.label)).toEqual(['v2', 'v1'])
    expect(mapped[0].isCurrent).toBe(true)
    expect(mapped[1].isCurrent).toBe(false)
    expect(mapped[0].author).toBe('Alex Mercer')
  })
})

describe('extractUserMetadata', () => {
  const versionWith = (metadata: Record<string, string> | null): FileVersionDTO => ({
    id: 2,
    version: 2,
    filename: 'Report.pdf',
    size: 12,
    contentType: 'application/pdf',
    status: 'AVAILABLE',
    metadata,
    createdAt: '2023-10-24T09:12:00Z',
    lastUpdateAt: '2023-10-24T09:12:00Z',
  })

  it('returns the current version user keys, dropping system keys and blanks', () => {
    const versions = [
      versionWith({
        department: 'Finance',
        project: 'Atlas',
        filename: 'Report.pdf',
        analysisStatus: 'COMPLETED',
        summary: 'x',
        empty: '',
      }),
    ]
    expect(extractUserMetadata(versions, 2)).toEqual([
      { key: 'department', value: 'Finance' },
      { key: 'project', value: 'Atlas' },
    ])
  })

  it('returns an empty array when the current version has no metadata', () => {
    expect(extractUserMetadata([versionWith(null)], 2)).toEqual([])
    expect(extractUserMetadata([], 2)).toEqual([])
  })
})

describe('mapToDocumentDetail', () => {
  it('projects file + AI metadata + versions onto the view model', () => {
    const detail = mapToDocumentDetail(
      { ...baseFile, sharingScope: 'DEPARTMENT' },
      aiMeta,
      [],
    )
    expect(detail).toMatchObject({
      id: 'file-1',
      title: 'Report.pdf',
      fileType: 'pdf',
      sharingScope: 'DEPARTMENT',
      currentVersion: 2,
      aiSummary: 'A concise summary.',
      sensitivity: 'Confidential',
      tags: ['Q3', '2023', 'Final'],
      userMetadata: [],
      owner: 'Alex Mercer',
      date: 'Oct 24, 2023',
      aiAnalysisStatus: 'completed',
    })
    expect(detail.breadcrumbs).toEqual([
      { label: 'Documents', to: '/documents' },
      { label: 'Report.pdf' },
    ])
  })

  it('applies fallbacks when AI metadata and names are missing', () => {
    const detail = mapToDocumentDetail(
      { id: 'x', createdAt: '2023-10-24T09:12:00Z' },
      null,
      [],
    )
    expect(detail.title).toBe('Untitled')
    expect(detail.owner).toBe('Unknown')
    expect(detail.aiSummary).toBeNull()
    expect(detail.sensitivity).toBe('—')
    expect(detail.tags).toEqual([])
    // No backend scope → falls back to the most restrictive scope.
    expect(detail.sharingScope).toBe('PERSONAL')
    // No backend version → defaults to the first version.
    expect(detail.currentVersion).toBe(1)
    // No AI metadata at all → analysis still pending.
    expect(detail.aiAnalysisStatus).toBe('pending')
  })
})

describe('deriveAiAnalysisStatus', () => {
  it('maps the explicit analysisStatus', () => {
    expect(deriveAiAnalysisStatus({ analysisStatus: 'COMPLETED', status: 'AVAILABLE' })).toBe('completed')
    expect(deriveAiAnalysisStatus({ analysisStatus: 'FAILED', status: 'AVAILABLE' })).toBe('failed')
    expect(deriveAiAnalysisStatus({ analysisStatus: 'UNSUPPORTED', status: 'AVAILABLE' })).toBe('unsupported')
  })

  it('falls back to summary sentinels and content presence', () => {
    expect(deriveAiAnalysisStatus({ summary: 'AI analysis failed', status: 'AVAILABLE' })).toBe('failed')
    expect(deriveAiAnalysisStatus({ summary: 'AI analysis not supported', status: 'AVAILABLE' })).toBe('unsupported')
    expect(deriveAiAnalysisStatus({ summary: 'Real summary', status: 'AVAILABLE' })).toBe('completed')
  })

  it('is pending when there is no metadata or content', () => {
    expect(deriveAiAnalysisStatus(null)).toBe('pending')
    expect(deriveAiAnalysisStatus({ status: 'AVAILABLE' })).toBe('pending')
  })
})

describe('mapShare', () => {
  it('maps a share DTO onto the panel view model, carrying the resolved name', () => {
    const dto: FileShareDTO = {
      id: 7,
      fileID: 'file-1',
      userID: 'abcd1234-5678-90ef-ghij-klmnopqrstuv',
      permission: 'READ_ONLY',
      timestamp: '2023-10-24T09:12:00Z',
      userName: 'Sarah Jenkins',
    }
    expect(mapShare(dto)).toEqual({
      id: '7',
      shareId: 7,
      userId: 'abcd1234-5678-90ef-ghij-klmnopqrstuv',
      userName: 'Sarah Jenkins',
      permission: 'READ_ONLY',
      sharedAt: 'Oct 24, 2023',
    })
  })

  it('defaults userName to null when the backend did not resolve it', () => {
    const dto: FileShareDTO = {
      id: 8,
      fileID: 'file-1',
      userID: 'abcd1234-5678-90ef-ghij-klmnopqrstuv',
      permission: 'READ_WRITE',
      timestamp: '2023-10-24T09:12:00Z',
    }
    expect(mapShare(dto).userName).toBeNull()
  })
})
