import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { RootState } from '@/app/store'
import type { JobUpdateStatus } from '@/features/realtime/jobsSocket'
import {
  createFileShare,
  deleteFile,
  deleteFileShare,
  getFileMetadata,
  getFileShares,
  getFileVersions,
  getGeneratedMetadata,
  isFileWritable,
  updateFileShare,
  updateFileVersionMetadata,
  updateGeneratedMetadata,
  type GeneratedMetadataUpdate,
  type Permission,
} from './detailsApi'
import { mapShare, mapToDocumentDetail, parseTags } from './detailsMappers'
import type { AiAnalysisStatus, DocumentDetail, DocumentShare, MetadataEntry } from './types'

type LoadStatus = 'idle' | 'loading' | 'succeeded' | 'failed'

interface DocumentDetailsState {
  status: LoadStatus
  detail: DocumentDetail | null
  error: string | null
  shares: DocumentShare[]
  sharesStatus: LoadStatus
  sharesError: string | null
  isCreatingShare: boolean
  createShareError: string | null
  /** Shared busy flag for removing/updating an existing share. */
  isMutatingShare: boolean
  shareMutationError: string | null
  isDeletingFile: boolean
  deleteFileError: string | null
  /** Whether the current user may edit this file's metadata (write access). */
  canEditMetadata: boolean
  isSavingMetadata: boolean
  saveMetadataError: string | null
  isSavingUserMetadata: boolean
  saveUserMetadataError: string | null
}

const initialState: DocumentDetailsState = {
  status: 'idle',
  detail: null,
  error: null,
  shares: [],
  sharesStatus: 'idle',
  sharesError: null,
  isCreatingShare: false,
  createShareError: null,
  isMutatingShare: false,
  shareMutationError: null,
  isDeletingFile: false,
  deleteFileError: null,
  canEditMetadata: false,
  isSavingMetadata: false,
  saveMetadataError: null,
  isSavingUserMetadata: false,
  saveUserMetadataError: null,
}

// Loads the full detail view for a file: core metadata, AI metadata (optional),
// and version history are fetched in parallel and projected onto DocumentDetail.
export const loadDocumentDetail = createAsyncThunk<DocumentDetail, string>(
  'documentDetails/load',
  async (id) => {
    const [file, aiMeta, versions] = await Promise.all([
      getFileMetadata(id),
      getGeneratedMetadata(id),
      getFileVersions(id),
    ])
    return mapToDocumentDetail(file, aiMeta, versions)
  },
)

// Loads the list of users a file is shared with.
export const loadShares = createAsyncThunk<DocumentShare[], string>(
  'documentDetails/loadShares',
  async (id) => {
    const page = await getFileShares(id)
    return page.content.map(mapShare)
  },
)

// Shares a file with a user, then returns the created share for the list.
export const createShare = createAsyncThunk<
  DocumentShare,
  { id: string; userId: string; permission: Permission }
>('documentDetails/createShare', async ({ id, userId, permission }) => {
  const created = await createFileShare(id, userId, permission)
  return mapShare(created)
})

// Updates an existing share's permission, returning the refreshed share so the
// list can replace it in place.
export const updateShare = createAsyncThunk<
  DocumentShare,
  { id: string; shareId: number; permission: Permission }
>('documentDetails/updateShare', async ({ id, shareId, permission }) => {
  const updated = await updateFileShare(id, shareId, permission)
  return mapShare(updated)
})

// Removes an existing share, returning its id so the list can drop it.
export const removeShare = createAsyncThunk<
  number,
  { id: string; shareId: number }
>('documentDetails/removeShare', async ({ id, shareId }) => {
  await deleteFileShare(id, shareId)
  return shareId
})

// Permanently deletes the file (and all versions). The page navigates away on
// success, so no document/share state is mutated here.
export const deleteDocument = createAsyncThunk<void, { id: string }>(
  'documentDetails/deleteDocument',
  async ({ id }) => {
    await deleteFile(id)
  },
)

// Resolves whether the current user may edit this file's metadata. Kept separate
// from the detail load so a failure here only hides the edit affordance rather
// than failing the whole page.
export const loadWritable = createAsyncThunk<boolean, string>(
  'documentDetails/loadWritable',
  (id) => isFileWritable(id),
)

// Saves edited AI-generated metadata (summary, sensitivity, and/or tags) for the
// current version, then echoes the applied values so the detail updates in place.
export const saveGeneratedMetadata = createAsyncThunk<
  { summary?: string; sensitivity?: string; tags?: string[] },
  { id: string; version: number; summary?: string; sensitivity?: string; tags?: string[] }
>('documentDetails/saveGeneratedMetadata', async ({ id, version, summary, sensitivity, tags }) => {
  const patch: GeneratedMetadataUpdate = {}
  if (summary !== undefined) patch.summary = summary
  if (sensitivity) patch.sensitivity = sensitivity
  // Join into the comma-separated string the backend expects; an empty array
  // clears the tags.
  if (tags) patch.tags = tags.join(',')

  if (Object.keys(patch).length === 0) {
    throw new Error('No metadata changes to save')
  }

  await updateGeneratedMetadata(id, version, patch)
  return { summary, sensitivity, tags }
})

// Saves edited user (non-AI) metadata for the current version. `metadata` is the
// merge patch sent to the backend (blanked keys remove entries); `entries` is the
// resulting visible set echoed back so the detail updates in place.
export const saveUserMetadata = createAsyncThunk<
  MetadataEntry[],
  { id: string; version: number; metadata: Record<string, string>; entries: MetadataEntry[] }
>('documentDetails/saveUserMetadata', async ({ id, version, metadata, entries }) => {
  await updateFileVersionMetadata(id, version, metadata)
  return entries
})

const documentDetailsSlice = createSlice({
  name: 'documentDetails',
  initialState,
  reducers: {
    // Clears state when leaving the page so a stale document never flashes on
    // the next visit before its data loads.
    resetDetail: () => initialState,
    // Live AI-metadata update pushed over the jobs socket. When the open detail is
    // the file that just finished analysis, patch its AI fields and processing
    // timeline in place so the page reflects the result without a reload. No-op for
    // any other file (or when no detail is loaded).
    applyJobUpdate(
      state,
      action: PayloadAction<{
        fileId: string
        status: JobUpdateStatus
        metadata?: Record<string, unknown> | null
      }>,
    ) {
      const { fileId, status, metadata } = action.payload
      const detail = state.detail
      if (!detail || detail.id !== fileId) return

      // Reflect the terminal outcome on the analysis indicator ('deadlettered'
      // reads as a failed analysis to the user).
      const statusByUpdate: Record<JobUpdateStatus, AiAnalysisStatus> = {
        completed: 'completed',
        unsupported: 'unsupported',
        failed: 'failed',
        deadlettered: 'failed',
      }
      detail.aiAnalysisStatus = statusByUpdate[status]

      // completed/unsupported carry the enriched metadata; patch what's present.
      if (metadata) {
        const { summary, sensitivity, tags } = metadata
        if (typeof summary === 'string') detail.aiSummary = summary
        if (typeof sensitivity === 'string') detail.sensitivity = sensitivity
        if (typeof tags === 'string') detail.tags = parseTags(tags)
      }

      // Analysis reached a terminal state, so the AI step is done; the pipeline is
      // "Completed" only on a clean completion.
      detail.processing = detail.processing.map((step) => {
        if (step.label === 'Processing AI Summary') return { ...step, done: true }
        if (step.label === 'Completed') return { ...step, done: status === 'completed' }
        return step
      })
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loadDocumentDetail.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(loadDocumentDetail.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.detail = action.payload
      })
      .addCase(loadDocumentDetail.rejected, (state, action) => {
        state.status = 'failed'
        state.detail = null
        state.error = action.error.message ?? 'Failed to load document'
      })
      .addCase(loadShares.pending, (state) => {
        state.sharesStatus = 'loading'
        state.sharesError = null
      })
      .addCase(loadShares.fulfilled, (state, action) => {
        state.sharesStatus = 'succeeded'
        state.shares = action.payload
      })
      .addCase(loadShares.rejected, (state, action) => {
        state.sharesStatus = 'failed'
        state.sharesError = action.error.message ?? 'Failed to load shares'
      })
      .addCase(createShare.pending, (state) => {
        state.isCreatingShare = true
        state.createShareError = null
      })
      .addCase(createShare.fulfilled, (state, action) => {
        state.isCreatingShare = false
        state.shares.push(action.payload)
      })
      .addCase(createShare.rejected, (state, action) => {
        state.isCreatingShare = false
        state.createShareError = action.error.message ?? 'Failed to share file'
      })
      .addCase(updateShare.pending, (state) => {
        state.isMutatingShare = true
        state.shareMutationError = null
      })
      .addCase(updateShare.fulfilled, (state, action) => {
        state.isMutatingShare = false
        const idx = state.shares.findIndex((s) => s.shareId === action.payload.shareId)
        if (idx !== -1) state.shares[idx] = action.payload
      })
      .addCase(updateShare.rejected, (state, action) => {
        state.isMutatingShare = false
        state.shareMutationError = action.error.message ?? 'Failed to update share'
      })
      .addCase(removeShare.pending, (state) => {
        state.isMutatingShare = true
        state.shareMutationError = null
      })
      .addCase(removeShare.fulfilled, (state, action) => {
        state.isMutatingShare = false
        state.shares = state.shares.filter((s) => s.shareId !== action.payload)
      })
      .addCase(removeShare.rejected, (state, action) => {
        state.isMutatingShare = false
        state.shareMutationError = action.error.message ?? 'Failed to remove share'
      })
      .addCase(deleteDocument.pending, (state) => {
        state.isDeletingFile = true
        state.deleteFileError = null
      })
      .addCase(deleteDocument.fulfilled, (state) => {
        state.isDeletingFile = false
      })
      .addCase(deleteDocument.rejected, (state, action) => {
        state.isDeletingFile = false
        state.deleteFileError = action.error.message ?? 'Failed to delete file'
      })
      .addCase(loadWritable.fulfilled, (state, action) => {
        state.canEditMetadata = action.payload
      })
      .addCase(loadWritable.rejected, (state) => {
        state.canEditMetadata = false
      })
      .addCase(saveGeneratedMetadata.pending, (state) => {
        state.isSavingMetadata = true
        state.saveMetadataError = null
      })
      .addCase(saveGeneratedMetadata.fulfilled, (state, action) => {
        state.isSavingMetadata = false
        if (state.detail) {
          const { summary, sensitivity, tags } = action.payload
          if (summary !== undefined) state.detail.aiSummary = summary
          if (sensitivity !== undefined) state.detail.sensitivity = sensitivity
          if (tags !== undefined) state.detail.tags = tags
        }
      })
      .addCase(saveGeneratedMetadata.rejected, (state, action) => {
        state.isSavingMetadata = false
        state.saveMetadataError = action.error.message ?? 'Failed to update metadata'
      })
      .addCase(saveUserMetadata.pending, (state) => {
        state.isSavingUserMetadata = true
        state.saveUserMetadataError = null
      })
      .addCase(saveUserMetadata.fulfilled, (state, action) => {
        state.isSavingUserMetadata = false
        if (state.detail) state.detail.userMetadata = action.payload
      })
      .addCase(saveUserMetadata.rejected, (state, action) => {
        state.isSavingUserMetadata = false
        state.saveUserMetadataError = action.error.message ?? 'Failed to update metadata'
      })
  },
})

export const { resetDetail, applyJobUpdate } = documentDetailsSlice.actions
export default documentDetailsSlice.reducer

// Selectors
export const selectDetail = (state: RootState) => state.documentDetails.detail
export const selectDetailStatus = (state: RootState) => state.documentDetails.status
export const selectDetailError = (state: RootState) => state.documentDetails.error
export const selectShares = (state: RootState) => state.documentDetails.shares
export const selectSharesStatus = (state: RootState) => state.documentDetails.sharesStatus
export const selectIsCreatingShare = (state: RootState) =>
  state.documentDetails.isCreatingShare
export const selectCreateShareError = (state: RootState) =>
  state.documentDetails.createShareError
export const selectIsMutatingShare = (state: RootState) =>
  state.documentDetails.isMutatingShare
export const selectShareMutationError = (state: RootState) =>
  state.documentDetails.shareMutationError
export const selectIsDeletingFile = (state: RootState) =>
  state.documentDetails.isDeletingFile
export const selectDeleteFileError = (state: RootState) =>
  state.documentDetails.deleteFileError
export const selectCanEditMetadata = (state: RootState) =>
  state.documentDetails.canEditMetadata
export const selectIsSavingMetadata = (state: RootState) =>
  state.documentDetails.isSavingMetadata
export const selectSaveMetadataError = (state: RootState) =>
  state.documentDetails.saveMetadataError
export const selectIsSavingUserMetadata = (state: RootState) =>
  state.documentDetails.isSavingUserMetadata
export const selectSaveUserMetadataError = (state: RootState) =>
  state.documentDetails.saveUserMetadataError
