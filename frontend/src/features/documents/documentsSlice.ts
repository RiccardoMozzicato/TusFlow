import {
  createAsyncThunk,
  createSelector,
  createSlice,
  type PayloadAction,
} from '@reduxjs/toolkit'
import type { RootState } from '@/app/store'
import { selectCurrentUser } from '@/features/auth/authSlice'
import {
  getFiles,
  SharingScope,
  type Ownership,
  type PageResponse,
  type FileDTO,
} from './documentsApi'
import { getJobStatuses, type JobStatus } from './jobsApi'
import { getWritableFileIds } from './detailsApi'
import { mapFileToDocument } from './mappers'
import type { LibraryDocument } from './types'

// Maps fileId → current AI-job status for a page of files.
type AiStatusByFile = Record<string, JobStatus>

// Best-effort: fetch AI-job statuses for a page's files. The library must still
// render if the AI service is unavailable, so failures resolve to no statuses.
async function fetchAiStatuses(files: FileDTO[]): Promise<AiStatusByFile> {
  try {
    const keys = files.map((file) => ({ fileId: file.id, version: file.version ?? 1 }))
    const statuses = await getJobStatuses(keys)
    return Object.fromEntries(statuses.map((s) => [s.fileId, s.status]))
  } catch {
    return {}
  }
}

// Best-effort: which of a page's files the current user may modify. Drives the
// row/card actions menu (Share/New Version/Delete). The library must still render
// if the check fails, so failures resolve to "none writable".
async function fetchWritableIds(files: FileDTO[]): Promise<string[]> {
  try {
    return await getWritableFileIds(files.map((file) => file.id))
  } catch {
    return []
  }
}

// Page size for the library grid. A multiple of the grid columns (1/2/3) keeps
// the last row tidy at common breakpoints.
export const DEFAULT_PAGE_SIZE = 12

// The library shows everything the user can see: their own files plus those
// shared at department and organization scope.
const ALL_SHARING_SCOPES: SharingScope[] = [
  SharingScope.PERSONAL,
  SharingScope.DEPARTMENT,
  SharingScope.ORGANIZATION,
]

type LoadStatus = 'idle' | 'loading' | 'succeeded' | 'failed'

// Server-side query filters, mapping onto the `getFiles` params the backend
// supports. `undefined` fields are simply omitted from the request.
export interface DocumentFilters {
  /** Exact MIME type; undefined means any type. */
  contentType?: string
  /** ISO-8601 lower bound on createdAt (inclusive). */
  dateFrom?: string
  /** ISO-8601 upper bound on createdAt (inclusive). */
  dateTo?: string
  /** Sort spec, e.g. 'createdAt,desc'. */
  sort: string
  /** Visibility scopes combined with OR; defaults to all three. */
  sharingScope: SharingScope[]
  /** Ownership partition; undefined means no ownership restriction (show all). */
  ownership?: Ownership
}

const DEFAULT_FILTERS: DocumentFilters = {
  sort: 'createdAt,desc',
  sharingScope: ALL_SHARING_SCOPES,
}

interface DocumentsState {
  status: LoadStatus
  documents: LibraryDocument[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  isFirst: boolean
  isLast: boolean
  error: string | null
  filters: DocumentFilters
}

// What loadDocuments resolves with: the file page plus the AI statuses fetched
// for it (kept together so the reducer maps documents with their status in one shot).
interface LoadDocumentsResult {
  page: PageResponse<FileDTO>
  aiStatusByFile: AiStatusByFile
  /** Ids on this page the caller may modify; lets the reducer flag writable files. */
  writableIds: string[]
  /** Current user id at fetch time; lets the reducer flag files the caller owns. */
  currentUserId?: string
}

const initialState: DocumentsState = {
  status: 'idle',
  documents: [],
  page: 0,
  size: DEFAULT_PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
  isFirst: true,
  isLast: true,
  error: null,
  filters: DEFAULT_FILTERS,
}

// An empty result, returned without hitting the backend when no sharing scope
// is selected (the backend requires at least one, and "none selected" should
// read as "show nothing", not "show everything").
const EMPTY_PAGE: PageResponse<FileDTO> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: DEFAULT_PAGE_SIZE,
  number: 0,
  first: true,
  last: true,
  empty: true,
}

// Loads a single page of files from the file service, honoring the current
// filters held in state, then enriches it with each file's AI-job status.
// Defaults to page 0.
export const loadDocuments = createAsyncThunk<
  LoadDocumentsResult,
  { page?: number } | undefined,
  { state: RootState }
>('documents/loadDocuments', async (args, { getState }) => {
  const state = getState()
  const { filters } = state.documents
  const currentUserId = selectCurrentUser(state)?.uuid
  if (filters.sharingScope.length === 0) {
    return { page: EMPTY_PAGE, aiStatusByFile: {}, writableIds: [], currentUserId }
  }

  const page = await getFiles({
    page: args?.page ?? 0,
    size: DEFAULT_PAGE_SIZE,
    contentType: filters.contentType,
    dateFrom: filters.dateFrom,
    dateTo: filters.dateTo,
    sort: filters.sort,
    sharingScope: filters.sharingScope,
    ownership: filters.ownership,
  })

  const [aiStatusByFile, writableIds] = await Promise.all([
    fetchAiStatuses(page.content),
    fetchWritableIds(page.content),
  ])
  return { page, aiStatusByFile, writableIds, currentUserId }
})

const documentsSlice = createSlice({
  name: 'documents',
  initialState,
  reducers: {
    // Merges a partial filter change; callers re-fetch page 0 afterwards.
    setFilters(state, action: PayloadAction<Partial<DocumentFilters>>) {
      state.filters = { ...state.filters, ...action.payload }
    },
    resetFilters(state) {
      state.filters = DEFAULT_FILTERS
    },
    // Live AI-job update pushed over the jobs socket: patch the matching document's
    // AI status in place so the Library indicator updates without a refetch. A no-op
    // when the file is not on the current page.
    applyJobUpdate(state, action: PayloadAction<{ fileId: string; aiStatus: JobStatus }>) {
      const doc = state.documents.find((document) => document.id === action.payload.fileId)
      if (doc) doc.aiStatus = action.payload.aiStatus
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loadDocuments.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(loadDocuments.fulfilled, (state, action) => {
        const { page, aiStatusByFile, writableIds, currentUserId } = action.payload
        const writable = new Set(writableIds)
        state.status = 'succeeded'
        state.documents = page.content.map((file) =>
          mapFileToDocument(file, aiStatusByFile[file.id], currentUserId, writable.has(file.id)),
        )
        state.page = page.number
        state.size = page.size
        state.totalElements = page.totalElements
        state.totalPages = page.totalPages
        state.isFirst = page.first
        state.isLast = page.last
      })
      .addCase(loadDocuments.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.error.message ?? 'Failed to load documents'
      })
  },
})

export default documentsSlice.reducer
export const { setFilters, resetFilters, applyJobUpdate } = documentsSlice.actions

// Selectors
export const selectDocuments = (state: RootState) => state.documents.documents
export const selectDocumentsStatus = (state: RootState) => state.documents.status
export const selectDocumentsError = (state: RootState) => state.documents.error
export const selectDocumentFilters = (state: RootState) => state.documents.filters
// Memoized so the assembled object keeps a stable reference across renders
// (an inline object selector would trip react-redux's identity warning).
export const selectDocumentsPageInfo = createSelector(
  (state: RootState) => state.documents.page,
  (state: RootState) => state.documents.totalElements,
  (state: RootState) => state.documents.totalPages,
  (state: RootState) => state.documents.isFirst,
  (state: RootState) => state.documents.isLast,
  (page, totalElements, totalPages, isFirst, isLast) => ({
    page,
    totalElements,
    totalPages,
    isFirst,
    isLast,
  }),
)
