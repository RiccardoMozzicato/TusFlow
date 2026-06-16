import { configureStore } from '@reduxjs/toolkit'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import authReducer from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import uiReducer from '@/features/ui/uiSlice'
import documentDetailsReducer from './documentDetailsSlice'
import documentsReducer, {
  loadDocuments,
  resetFilters,
  selectDocumentFilters,
  selectDocuments,
  selectDocumentsPageInfo,
  selectDocumentsStatus,
  setFilters,
} from './documentsSlice'
import {
  getFiles,
  SharingScope,
  type FileDTO,
  type PageResponse,
} from './documentsApi'
import { getJobStatuses } from './jobsApi'

const ALL_SCOPES = [
  SharingScope.PERSONAL,
  SharingScope.DEPARTMENT,
  SharingScope.ORGANIZATION,
]

// Default getFiles params for a no-filter page request, used to assert the
// thunk threads the slice's filter state into the call.
const defaultParams = (page: number) => ({
  page,
  size: 12,
  contentType: undefined,
  dateFrom: undefined,
  dateTo: undefined,
  sort: 'createdAt,desc',
  sharingScope: ALL_SCOPES,
})

// The thunk's only side effect is the getFiles network call; mock it (while
// keeping the real SharingScope export) so the slice/mapper integration can be
// exercised deterministically.
vi.mock('./documentsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./documentsApi')>()
  return { ...actual, getFiles: vi.fn() }
})

// The AI-status fetch is a secondary side effect; mock it so the slice tests
// stay focused on the file page. Defaults to "no statuses".
vi.mock('./jobsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./jobsApi')>()
  return { ...actual, getJobStatuses: vi.fn().mockResolvedValue([]) }
})

const mockedGetFiles = vi.mocked(getFiles)
const mockedGetJobStatuses = vi.mocked(getJobStatuses)

// Includes the auth reducer so the store's state matches RootState, which the
// strictly-typed selectors expect.
function makeStore() {
  return configureStore({
    reducer: {
      auth: authReducer,
      chat: chatReducer,
      documents: documentsReducer,
      documentDetails: documentDetailsReducer,
      ui: uiReducer,
    },
  })
}

function makePage(content: FileDTO[], overrides: Partial<PageResponse<FileDTO>> = {}) {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    size: 12,
    number: 0,
    first: true,
    last: true,
    empty: content.length === 0,
    ...overrides,
  } satisfies PageResponse<FileDTO>
}

beforeEach(() => {
  mockedGetJobStatuses.mockResolvedValue([])
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('documentsSlice reducer', () => {
  it('starts idle and empty', () => {
    const state = makeStore().getState()
    expect(selectDocumentsStatus(state)).toBe('idle')
    expect(selectDocuments(state)).toEqual([])
  })

  it('marks loading and clears any prior error on pending', () => {
    const state = documentsReducer(undefined, loadDocuments.pending('', undefined))
    expect(state.status).toBe('loading')
    expect(state.error).toBeNull()
  })

  it('records the error message on rejection', () => {
    const action = loadDocuments.rejected(new Error('boom'), '', undefined)
    const state = documentsReducer(undefined, action)
    expect(state.status).toBe('failed')
    expect(state.error).toBe('boom')
  })
})

describe('loadDocuments thunk', () => {
  it('maps the returned page content into documents and stores page meta', async () => {
    const dto: FileDTO = {
      id: 'f1',
      fileName: 'Q3.pdf',
      contentType: 'application/pdf',
      version: 2,
      createdAt: '2023-10-20T09:00:00Z',
      ownerName: 'Alex',
    }
    mockedGetFiles.mockResolvedValue(
      makePage([dto], { totalElements: 25, totalPages: 3, number: 1, first: false, last: false }),
    )

    const store = makeStore()
    await store.dispatch(loadDocuments({ page: 1 }))

    const state = store.getState()
    expect(mockedGetFiles).toHaveBeenCalledWith(defaultParams(1))
    expect(selectDocumentsStatus(state)).toBe('succeeded')
    expect(selectDocuments(state)).toHaveLength(1)
    expect(selectDocuments(state)[0]).toMatchObject({
      id: 'f1',
      title: 'Q3.pdf',
      fileType: 'pdf',
      status: 'ready',
      owner: 'Alex',
      version: 'v2',
    })
    // created carries date + time; assert the date part (time is TZ-dependent).
    expect(selectDocuments(state)[0].created).toContain('Oct 20, 2023')
    expect(selectDocumentsPageInfo(state)).toEqual({
      page: 1,
      totalElements: 25,
      totalPages: 3,
      isFirst: false,
      isLast: false,
    })
  })

  it('defaults to page 0 when no argument is given', async () => {
    mockedGetFiles.mockResolvedValue(makePage([]))
    await makeStore().dispatch(loadDocuments())
    expect(mockedGetFiles).toHaveBeenCalledWith(defaultParams(0))
  })

  it('transitions to failed when getFiles rejects', async () => {
    mockedGetFiles.mockRejectedValue(new Error('network down'))
    const store = makeStore()
    await store.dispatch(loadDocuments({ page: 0 }))
    expect(selectDocumentsStatus(store.getState())).toBe('failed')
  })

  it('merges AI-job statuses onto the matching documents', async () => {
    const a: FileDTO = { id: 'fa', fileName: 'a.pdf', version: 1, createdAt: '2023-10-20T09:00:00Z' }
    const b: FileDTO = { id: 'fb', fileName: 'b.pdf', version: 1, createdAt: '2023-10-20T09:00:00Z' }
    mockedGetFiles.mockResolvedValue(makePage([a, b], { totalElements: 2 }))
    mockedGetJobStatuses.mockResolvedValue([
      { jobId: 'j1', fileId: 'fa', version: 1, status: 'COMPLETED' },
    ])

    const store = makeStore()
    await store.dispatch(loadDocuments({ page: 0 }))

    expect(mockedGetJobStatuses).toHaveBeenCalledWith([
      { fileId: 'fa', version: 1 },
      { fileId: 'fb', version: 1 },
    ])
    const docs = selectDocuments(store.getState())
    expect(docs.find((d) => d.id === 'fa')?.aiStatus).toBe('COMPLETED')
    expect(docs.find((d) => d.id === 'fb')?.aiStatus).toBeUndefined()
  })

  it('still succeeds when the AI-status fetch fails', async () => {
    mockedGetFiles.mockResolvedValue(
      makePage([{ id: 'fa', version: 1, createdAt: '2023-10-20T09:00:00Z' }], { totalElements: 1 }),
    )
    mockedGetJobStatuses.mockRejectedValue(new Error('ai service down'))

    const store = makeStore()
    await store.dispatch(loadDocuments({ page: 0 }))

    expect(selectDocumentsStatus(store.getState())).toBe('succeeded')
    expect(selectDocuments(store.getState())[0].aiStatus).toBeUndefined()
  })
})

describe('filters', () => {
  it('setFilters merges into the current filter state', () => {
    const store = makeStore()
    store.dispatch(setFilters({ contentType: 'application/pdf' }))
    store.dispatch(setFilters({ sort: 'createdAt,asc' }))

    expect(selectDocumentFilters(store.getState())).toEqual({
      contentType: 'application/pdf',
      sort: 'createdAt,asc',
      sharingScope: ALL_SCOPES,
    })
  })

  it('threads the active filters into the getFiles call', async () => {
    mockedGetFiles.mockResolvedValue(makePage([]))
    const store = makeStore()
    store.dispatch(setFilters({ contentType: 'application/pdf' }))
    await store.dispatch(loadDocuments({ page: 0 }))

    expect(mockedGetFiles).toHaveBeenCalledWith({
      ...defaultParams(0),
      contentType: 'application/pdf',
    })
  })

  it('resetFilters restores the defaults', () => {
    const store = makeStore()
    store.dispatch(setFilters({ contentType: 'application/pdf', sort: 'createdAt,asc' }))
    store.dispatch(resetFilters())

    expect(selectDocumentFilters(store.getState())).toEqual({
      sort: 'createdAt,desc',
      sharingScope: ALL_SCOPES,
    })
  })

  it('returns an empty page without calling getFiles when no scope is selected', async () => {
    const store = makeStore()
    store.dispatch(setFilters({ sharingScope: [] }))
    await store.dispatch(loadDocuments({ page: 0 }))

    expect(mockedGetFiles).not.toHaveBeenCalled()
    const state = store.getState()
    expect(selectDocumentsStatus(state)).toBe('succeeded')
    expect(selectDocuments(state)).toEqual([])
  })
})
