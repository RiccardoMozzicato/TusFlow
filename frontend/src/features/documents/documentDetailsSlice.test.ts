import { configureStore } from '@reduxjs/toolkit'
import { afterEach, describe, expect, it, vi } from 'vitest'
import authReducer from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import documentsReducer from '@/features/documents/documentsSlice'
import onboardingReducer from '@/features/onboarding/onboardingSlice'
import uiReducer from '@/features/ui/uiSlice'
import documentDetailsReducer, {
  applyJobUpdate,
  deleteDocument,
  loadDocumentDetail,
  loadShares,
  loadWritable,
  removeShare,
  saveGeneratedMetadata,
  saveUserMetadata,
  selectCanEditMetadata,
  selectSaveMetadataError,
  updateShare,
} from './documentDetailsSlice'
import {
  deleteFile,
  deleteFileShare,
  isFileWritable,
  updateFileShare,
  updateFileVersionMetadata,
  updateGeneratedMetadata,
} from './detailsApi'
import { Permission } from './detailsApi'
import { SharingScope } from './documentsApi'
import type { DocumentDetail, DocumentShare } from './types'

// Stub only the network functions the thunks call; keep the rest real.
vi.mock('./detailsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./detailsApi')>()
  return {
    ...actual,
    updateGeneratedMetadata: vi.fn(),
    updateFileVersionMetadata: vi.fn(),
    isFileWritable: vi.fn(),
    deleteFile: vi.fn(),
    deleteFileShare: vi.fn(),
    updateFileShare: vi.fn(),
  }
})

const mockedUpdate = vi.mocked(updateGeneratedMetadata)
const mockedUpdateVersion = vi.mocked(updateFileVersionMetadata)
const mockedIsWritable = vi.mocked(isFileWritable)
const mockedDeleteFile = vi.mocked(deleteFile)
const mockedDeleteShare = vi.mocked(deleteFileShare)
const mockedUpdateShare = vi.mocked(updateFileShare)

function makeShare(overrides: Partial<DocumentShare> = {}): DocumentShare {
  return {
    id: '1',
    shareId: 1,
    userId: 'user-1',
    userName: 'Alex',
    permission: Permission.READ_ONLY,
    sharedAt: 'Oct 24, 2023',
    ...overrides,
  }
}

function makeDetail(overrides: Partial<DocumentDetail> = {}): DocumentDetail {
  return {
    id: 'file-1',
    title: 'Report.pdf',
    fileType: 'pdf',
    sharingScope: SharingScope.PERSONAL,
    currentVersion: 2,
    breadcrumbs: [],
    aiAnalysisStatus: 'pending',
    aiSummary: null,
    sensitivity: 'INTERNAL',
    tags: ['old'],
    userMetadata: [],
    owner: 'Alex',
    date: 'Oct 24, 2023',
    processing: [],
    versions: [],
    ...overrides,
  }
}

function makeStore() {
  return configureStore({
    reducer: {
      auth: authReducer,
      chat: chatReducer,
      documents: documentsReducer,
      documentDetails: documentDetailsReducer,
      onboarding: onboardingReducer,
      ui: uiReducer,
    },
  })
}

afterEach(() => {
  vi.clearAllMocks()
})

describe('loadWritable', () => {
  it('sets canEditMetadata from the resolved writability', () => {
    const state = documentDetailsReducer(
      undefined,
      loadWritable.fulfilled(true, '', 'file-1'),
    )
    expect(state.canEditMetadata).toBe(true)
  })

  it('leaves editing disabled when the check rejects', () => {
    const state = documentDetailsReducer(
      undefined,
      loadWritable.rejected(new Error('boom'), '', 'file-1'),
    )
    expect(state.canEditMetadata).toBe(false)
  })

  it('reflects the writable result through the store/selector', async () => {
    mockedIsWritable.mockResolvedValue(true)
    const store = makeStore()
    await store.dispatch(loadWritable('file-1'))
    expect(mockedIsWritable).toHaveBeenCalledWith('file-1')
    expect(selectCanEditMetadata(store.getState())).toBe(true)
  })
})

describe('saveGeneratedMetadata reducer', () => {
  const seeded = documentDetailsReducer(
    undefined,
    loadDocumentDetail.fulfilled(makeDetail(), '', 'file-1'),
  )

  it('applies the saved sensitivity and tags onto the detail', () => {
    const state = documentDetailsReducer(
      seeded,
      saveGeneratedMetadata.fulfilled(
        { sensitivity: 'CONFIDENTIAL', tags: ['x', 'y'] },
        '',
        { id: 'file-1', version: 2, sensitivity: 'CONFIDENTIAL', tags: ['x', 'y'] },
      ),
    )
    expect(state.detail?.sensitivity).toBe('CONFIDENTIAL')
    expect(state.detail?.tags).toEqual(['x', 'y'])
    expect(state.isSavingMetadata).toBe(false)
  })

  it('records the error message on rejection', () => {
    const state = documentDetailsReducer(
      seeded,
      saveGeneratedMetadata.rejected(new Error('Forbidden'), '', {
        id: 'file-1',
        version: 2,
        sensitivity: 'CONFIDENTIAL',
        tags: [],
      }),
    )
    expect(state.isSavingMetadata).toBe(false)
    expect(selectSaveMetadataError({ documentDetails: state } as never)).toBe('Forbidden')
  })
})

describe('saveGeneratedMetadata thunk', () => {
  it('joins tags into a comma-separated string for the request', async () => {
    mockedUpdate.mockResolvedValue(undefined)
    const store = makeStore()

    await store.dispatch(
      saveGeneratedMetadata({
        id: 'file-1',
        version: 2,
        sensitivity: 'CONFIDENTIAL',
        tags: ['Q3', '2023'],
      }),
    )

    expect(mockedUpdate).toHaveBeenCalledWith('file-1', 2, {
      sensitivity: 'CONFIDENTIAL',
      tags: 'Q3,2023',
    })
  })

  it('rejects without a request when there is nothing to save', async () => {
    const store = makeStore()
    const result = await store.dispatch(
      saveGeneratedMetadata({ id: 'file-1', version: 2 }),
    )
    expect(result.type).toBe('documentDetails/saveGeneratedMetadata/rejected')
    expect(mockedUpdate).not.toHaveBeenCalled()
  })
})

describe('saveUserMetadata', () => {
  it('PATCHes the version metadata and applies the resulting entries to the detail', async () => {
    mockedUpdateVersion.mockResolvedValue(undefined)
    const store = makeStore()
    store.dispatch(loadDocumentDetail.fulfilled(makeDetail(), '', 'file-1'))

    await store.dispatch(
      saveUserMetadata({
        id: 'file-1',
        version: 2,
        metadata: { department: 'Finance', stale: '' },
        entries: [{ key: 'department', value: 'Finance' }],
      }),
    )

    expect(mockedUpdateVersion).toHaveBeenCalledWith('file-1', 2, {
      department: 'Finance',
      stale: '',
    })
    expect(store.getState().documentDetails.detail?.userMetadata).toEqual([
      { key: 'department', value: 'Finance' },
    ])
  })

  it('records the error message on rejection', () => {
    const seeded = documentDetailsReducer(
      undefined,
      loadDocumentDetail.fulfilled(makeDetail(), '', 'file-1'),
    )
    const state = documentDetailsReducer(
      seeded,
      saveUserMetadata.rejected(new Error('Forbidden'), '', {
        id: 'file-1',
        version: 2,
        metadata: { a: 'b' },
        entries: [{ key: 'a', value: 'b' }],
      }),
    )
    expect(state.isSavingUserMetadata).toBe(false)
    expect(state.saveUserMetadataError).toBe('Forbidden')
  })
})

describe('removeShare', () => {
  const seeded = documentDetailsReducer(
    undefined,
    loadShares.fulfilled(
      [makeShare({ id: '1', shareId: 1 }), makeShare({ id: '2', shareId: 2, userId: 'user-2' })],
      '',
      'file-1',
    ),
  )

  it('drops the removed share from the list', () => {
    const state = documentDetailsReducer(seeded, removeShare.fulfilled(1, '', { id: 'file-1', shareId: 1 }))
    expect(state.shares.map((s) => s.shareId)).toEqual([2])
    expect(state.isMutatingShare).toBe(false)
  })

  it('records the error message on rejection', () => {
    const state = documentDetailsReducer(
      seeded,
      removeShare.rejected(new Error('Forbidden'), '', { id: 'file-1', shareId: 1 }),
    )
    expect(state.isMutatingShare).toBe(false)
    expect(state.shareMutationError).toBe('Forbidden')
    expect(state.shares).toHaveLength(2) // unchanged
  })

  it('calls the delete API through the store', async () => {
    mockedDeleteShare.mockResolvedValue(undefined)
    const store = makeStore()
    await store.dispatch(removeShare({ id: 'file-1', shareId: 7 }))
    expect(mockedDeleteShare).toHaveBeenCalledWith('file-1', 7)
  })
})

describe('updateShare reducer', () => {
  const seeded = documentDetailsReducer(
    undefined,
    loadShares.fulfilled([makeShare({ id: '1', shareId: 1 })], '', 'file-1'),
  )

  it('replaces the updated share in place', () => {
    const updated = makeShare({ id: '1', shareId: 1, permission: Permission.READ_WRITE })
    const state = documentDetailsReducer(
      seeded,
      updateShare.fulfilled(updated, '', { id: 'file-1', shareId: 1, permission: Permission.READ_WRITE }),
    )
    expect(state.shares[0].permission).toBe(Permission.READ_WRITE)
    expect(state.isMutatingShare).toBe(false)
  })

  it('records the error message on rejection', () => {
    const state = documentDetailsReducer(
      seeded,
      updateShare.rejected(new Error('Forbidden'), '', {
        id: 'file-1',
        shareId: 1,
        permission: Permission.READ_WRITE,
      }),
    )
    expect(state.isMutatingShare).toBe(false)
    expect(state.shareMutationError).toBe('Forbidden')
    expect(state.shares[0].permission).toBe(Permission.READ_ONLY) // unchanged
  })

  it('calls the update API through the store', async () => {
    mockedUpdateShare.mockResolvedValue({
      id: 7,
      fileID: 'file-1',
      userID: 'user-1',
      permission: Permission.READ_WRITE,
      timestamp: '2023-10-24T00:00:00Z',
    })
    const store = makeStore()
    await store.dispatch(updateShare({ id: 'file-1', shareId: 7, permission: Permission.READ_WRITE }))
    expect(mockedUpdateShare).toHaveBeenCalledWith('file-1', 7, Permission.READ_WRITE)
  })
})

describe('deleteDocument', () => {
  it('calls the delete API and clears the busy flag on success', async () => {
    mockedDeleteFile.mockResolvedValue(undefined)
    const store = makeStore()
    await store.dispatch(deleteDocument({ id: 'file-1' }))
    expect(mockedDeleteFile).toHaveBeenCalledWith('file-1')
    expect(store.getState().documentDetails.isDeletingFile).toBe(false)
  })

  it('records the error message on rejection', () => {
    const state = documentDetailsReducer(
      undefined,
      deleteDocument.rejected(new Error('Forbidden'), '', { id: 'file-1' }),
    )
    expect(state.isDeletingFile).toBe(false)
    expect(state.deleteFileError).toBe('Forbidden')
  })
})

describe('applyJobUpdate (live AI metadata)', () => {
  const seeded = documentDetailsReducer(
    undefined,
    loadDocumentDetail.fulfilled(
      makeDetail({
        aiSummary: null,
        sensitivity: 'INTERNAL',
        tags: ['old'],
        processing: [
          { label: 'Uploaded', done: true },
          { label: 'Processing AI Summary', done: false },
          { label: 'Completed', done: false },
        ],
      }),
      '',
      'file-1',
    ),
  )

  it('patches AI fields and completes the timeline on a completed update for the open file', () => {
    const state = documentDetailsReducer(
      seeded,
      applyJobUpdate({
        fileId: 'file-1',
        status: 'completed',
        metadata: { summary: 'A concise summary', sensitivity: 'CONFIDENTIAL', tags: 'q3, finance' },
      }),
    )
    expect(state.detail?.aiAnalysisStatus).toBe('completed')
    expect(state.detail?.aiSummary).toBe('A concise summary')
    expect(state.detail?.sensitivity).toBe('CONFIDENTIAL')
    expect(state.detail?.tags).toEqual(['q3', 'finance'])
    expect(state.detail?.processing.find((s) => s.label === 'Processing AI Summary')?.done).toBe(true)
    expect(state.detail?.processing.find((s) => s.label === 'Completed')?.done).toBe(true)
  })

  it('ignores an update for a different file', () => {
    const state = documentDetailsReducer(
      seeded,
      applyJobUpdate({
        fileId: 'other-file',
        status: 'completed',
        metadata: { summary: 'nope', sensitivity: 'PUBLIC', tags: 'x' },
      }),
    )
    expect(state.detail?.aiSummary).toBeNull()
    expect(state.detail?.sensitivity).toBe('INTERNAL')
    expect(state.detail?.tags).toEqual(['old'])
  })

  it('marks the AI step done but not Completed on a failed update', () => {
    const state = documentDetailsReducer(
      seeded,
      applyJobUpdate({ fileId: 'file-1', status: 'failed' }),
    )
    expect(state.detail?.aiAnalysisStatus).toBe('failed')
    expect(state.detail?.processing.find((s) => s.label === 'Processing AI Summary')?.done).toBe(true)
    expect(state.detail?.processing.find((s) => s.label === 'Completed')?.done).toBe(false)
    // No metadata on a failure → AI fields untouched.
    expect(state.detail?.aiSummary).toBeNull()
  })
})
