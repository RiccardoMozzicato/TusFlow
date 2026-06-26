import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { loadDocuments } from '@/features/documents/documentsSlice'
import type { SharingScope } from '@/features/documents/documentsApi'
import { selectCurrentUser } from '@/features/auth/authSlice'
import { createUpload, type UploadController } from './tusUpload'
import { isTerminalJobStatus, onJobUpdate, type TerminalJobStatus } from '@/features/realtime/jobsSocket'
import { fileTypeChip } from './fileType'
import { formatBytes, formatEta, formatSpeed } from './format'
import { toMetadataRecord } from './metadata'
import { deriveCounts, uploadQueueReducer } from './uploadQueueReducer'
import { deleteUpload, getAllUploads, putUpload, setUploadPaused } from './uploadBlobStore'
import { getMyPendingUploads } from './pendingUploadsApi'
import type { MetadataField, StagedFile, UploadItem, UploadSummary } from './types'

// Orchestrates live uploads: drives the tus client and listens on the shared jobs
// socket for the AI outcome, projecting both onto the presentational UploadItem
// queue. Non-serializable handles (controllers, socket unsubscribers, byte/speed
// bookkeeping) live in refs — only the view-model queue goes through the reducer.

// Exponential smoothing factor for the instantaneous-speed estimate.
const SPEED_SMOOTHING = 0.3

// Safety net: if no terminal AI update arrives for an uploaded file within this
// window, treat it as done so the row never sticks on "Generating metadata…".
const PROCESSING_TIMEOUT_MS = 120_000

interface ItemMeta {
  bytesTotal: number
  lastBytes: number
  lastTime: number
  /** Smoothed bytes/sec; 0 once the upload leaves the active phase. */
  speed: number
}

function newId(): string {
  return (
    globalThis.crypto?.randomUUID?.() ??
    `up-${Date.now()}-${Math.random().toString(16).slice(2)}`
  )
}

// Persists an in-flight upload's bytes + descriptor so it survives a full reload
// (logout → login) and can be resumed. Best-effort: a storage-quota failure must
// not stop the upload from running in memory, so we only log it.
function persistUpload(
  file: File,
  fileId: string,
  ownerId: string,
  scope: SharingScope,
  metadata: Record<string, string>,
): void {
  void putUpload({
    fileId,
    ownerId,
    name: file.name,
    size: file.size,
    type: file.type,
    scope,
    metadata,
    paused: false,
    createdAt: Date.now(),
    blob: file,
  }).catch((error) => console.error('Failed to persist upload for resume', error))
}

// Best-effort removal of a persisted upload once it is terminal (done/cancelled/failed).
function forgetUpload(fileId: string): void {
  void deleteUpload(fileId).catch((error) =>
    console.error('Failed to remove persisted upload', error),
  )
}

// Detail lines for an actively uploading row.
function activeDetail(meta: ItemMeta, bytesUploaded: number) {
  const remaining = meta.bytesTotal - bytesUploaded
  const etaSeconds = meta.speed > 0 ? remaining / meta.speed : Infinity
  const detailPrimary = [formatSpeed(meta.speed), formatEta(etaSeconds)]
    .filter(Boolean)
    .join(' • ')
  const detailSecondary = `${formatBytes(bytesUploaded)} / ${formatBytes(meta.bytesTotal)}`
  return { detailPrimary, detailSecondary }
}

export interface UseUploadQueue {
  items: UploadItem[]
  /** Files selected but not yet uploaded, awaiting metadata + confirmation. */
  staged: StagedFile[]
  summary: UploadSummary
  addFiles: (files: File[]) => void
  updateStagedMetadata: (id: string, fields: MetadataField[]) => void
  /** Sets a staged file's per-file scope override; undefined clears it (inherit general). */
  setStagedScope: (id: string, scope?: SharingScope) => void
  removeStaged: (id: string) => void
  startStaged: (id: string, scope: SharingScope) => void
  startAllStaged: (scope: SharingScope) => void
  pause: (id: string) => void
  resume: (id: string) => void
  cancel: (id: string) => void
  /** Drops a row from the queue locally, without aborting on the server. */
  dismiss: (id: string) => void
  retry: (id: string) => void
  pauseAll: () => void
  resumeAll: () => void
  clearCompleted: () => void
}

export function useUploadQueue(): UseUploadQueue {
  const reduxDispatch = useAppDispatch()
  // The owner stamped on persisted uploads and used to gate restore. Always set
  // here: the provider lives behind ProtectedRoute, which renders only when authed.
  const currentUserId = useAppSelector((state) => selectCurrentUser(state)?.uuid)
  const [items, dispatch] = useReducer(uploadQueueReducer, [])

  // Files awaiting metadata + confirmation, separate from the active queue so
  // the transfer reducer/statuses stay focused on in-flight uploads.
  const [staged, setStaged] = useState<StagedFile[]>([])

  // Latest queue snapshot for callbacks that fan out over current items
  // (pauseAll/resumeAll) without re-binding on every render.
  const itemsRef = useRef<UploadItem[]>(items)
  itemsRef.current = items

  // Mirror of `staged` for startAll, plus the real File handles (non-serializable,
  // so kept out of state). The scope is resolved at upload time, not here.
  const stagedRef = useRef<StagedFile[]>(staged)
  stagedRef.current = staged
  const stagedSources = useRef(new Map<string, { file: File }>())

  const controllers = useRef(new Map<string, UploadController>())
  // Per-upload teardown for the jobs-socket listener + its safety timeout.
  const jobSubs = useRef(new Map<string, () => void>())
  const meta = useRef(new Map<string, ItemMeta>())

  const patch = useCallback(
    (id: string, changes: Partial<Omit<UploadItem, 'id' | 'fileId'>>) =>
      dispatch({ type: 'patch', id, changes }),
    [],
  )

  const closeJobSub = useCallback((id: string) => {
    jobSubs.current.get(id)?.()
    jobSubs.current.delete(id)
  }, [])

  // --- tus event handlers --------------------------------------------------

  const handleProgress = useCallback(
    (id: string, bytesUploaded: number, bytesTotal: number) => {
      const m = meta.current.get(id)
      if (!m) return

      const now = Date.now()
      const dt = (now - m.lastTime) / 1000
      if (dt > 0) {
        const instant = (bytesUploaded - m.lastBytes) / dt
        m.speed = m.speed ? m.speed * (1 - SPEED_SMOOTHING) + instant * SPEED_SMOOTHING : instant
        m.lastBytes = bytesUploaded
        m.lastTime = now
      }
      m.bytesTotal = bytesTotal

      patch(id, {
        status: 'uploading',
        progress: bytesTotal > 0 ? bytesUploaded / bytesTotal : 0,
        ...activeDetail(m, bytesUploaded),
      })
    },
    [patch],
  )

  // Server-confirmed offset: drives the solid portion of the bar. Monotonic, so
  // a retried chunk never makes the confirmed fill jump backwards.
  const handleChunkComplete = useCallback(
    (id: string, bytesAccepted: number, bytesTotal: number) => {
      patch(id, {
        confirmedProgress: bytesTotal > 0 ? bytesAccepted / bytesTotal : 0,
      })
    },
    [patch],
  )

  // A terminal AI outcome (or the safety timeout) for an uploaded file: the bytes
  // are in, so surface a completed row and refresh the library.
  const handleJobTerminal = useCallback(
    (id: string, status: TerminalJobStatus) => {
      const m = meta.current.get(id)
      const size = m ? formatBytes(m.bytesTotal) : ''

      const primaryByStatus: Record<TerminalJobStatus, string> = {
        completed: 'Done',
        unsupported: 'Uploaded (no AI preview)',
        failed: 'Uploaded (analysis failed)',
        deadlettered: 'Uploaded (analysis failed)',
      }
      patch(id, {
        status: 'completed',
        progress: 1,
        detailPrimary: primaryByStatus[status],
        detailSecondary: size,
      })
      closeJobSub(id)
      void reduxDispatch(loadDocuments({ page: 0 }))
    },
    [patch, closeJobSub, reduxDispatch],
  )

  const handleTusSuccess = useCallback(
    (id: string) => {
      const m = meta.current.get(id)
      if (m) m.speed = 0
      patch(id, {
        status: 'processing',
        progress: 1,
        confirmedProgress: 1,
        detailPrimary: 'Generating metadata…',
        detailSecondary: m ? `100% (${formatBytes(m.bytesTotal)})` : '100%',
      })

      // All bytes are on the server now; the persisted copy is no longer needed
      // to resume, so drop it (and its tus URL entry was auto-removed on success).
      forgetUpload(id)

      // Listen on the shared jobs socket for this file's terminal AI outcome. The
      // file was created with `id` as its fileId, so match on that.
      const unsubscribe = onJobUpdate((update) => {
        if (update.fileId !== id) return
        // The row already shows "Generating metadata…" for the pending/processing
        // pings; only a terminal outcome flips it to a completed row.
        if (!isTerminalJobStatus(update.status)) return
        handleJobTerminal(id, update.status)
      })
      // Safety net: never leave the row stuck if the update is missed (e.g. it
      // landed before this listener registered, or the socket was down).
      const timeout = setTimeout(() => handleJobTerminal(id, 'completed'), PROCESSING_TIMEOUT_MS)
      jobSubs.current.set(id, () => {
        unsubscribe()
        clearTimeout(timeout)
      })
    },
    [patch, handleJobTerminal],
  )

  const handleTusError = useCallback(
    (id: string, error: Error) => {
      const m = meta.current.get(id)
      if (m) m.speed = 0
      patch(id, {
        status: 'failed',
        detailPrimary: 'Interrupted',
        detailSecondary: m ? formatBytes(m.bytesTotal) : '',
        errorMessage: error.message || 'Upload failed',
      })
      // Terminal failure: stop persisting it across reloads (in-session retry
      // still works off the live tus controller).
      forgetUpload(id)
    },
    [patch],
  )

  // --- public API ----------------------------------------------------------

  // Registers a queue row, its byte bookkeeping, and the tus controller for a given
  // file + id. Shared by fresh uploads (launchUpload) and resumed ones (restore on
  // boot), which differ only in the id source and the initial detail line.
  const beginUpload = useCallback(
    (
      file: File,
      id: string,
      scope: SharingScope,
      metadata: Record<string, string>,
      options: {
        detailPrimary?: string
        startPaused?: boolean
        /** Confirmed progress (0–1) to seed the row with, e.g. a restored paused
         *  upload's last offset so it doesn't read "0 B" until it's resumed. */
        initialProgress?: number
      } = {},
    ) => {
      const { detailPrimary = 'Starting…', startPaused = false, initialProgress = 0 } = options
      const uploadedBytes = initialProgress * file.size
      const chip = fileTypeChip(file.type)
      meta.current.set(id, {
        bytesTotal: file.size,
        lastBytes: uploadedBytes,
        lastTime: Date.now(),
        speed: 0,
      })
      const item: UploadItem = {
        id,
        fileId: id,
        name: file.name,
        icon: chip.icon,
        iconClassName: chip.iconClassName,
        status: startPaused ? 'paused' : 'uploading',
        progress: initialProgress,
        confirmedProgress: initialProgress,
        detailPrimary,
        detailSecondary: `${formatBytes(uploadedBytes)} / ${formatBytes(file.size)}`,
      }
      dispatch({ type: 'add', items: [item] })

      const controller = createUpload({
        file,
        fileId: id,
        scope,
        metadata,
        startPaused,
        onProgress: (uploaded, total) => handleProgress(id, uploaded, total),
        onChunkComplete: (accepted, total) => handleChunkComplete(id, accepted, total),
        onSuccess: () => handleTusSuccess(id),
        onError: (error) => handleTusError(id, error),
      })
      controllers.current.set(id, controller)
    },
    [handleProgress, handleChunkComplete, handleTusSuccess, handleTusError],
  )

  // Kicks off a brand-new upload: mints an id, persists it (stamped with the owner
  // so only they can resume it) for resume, then starts.
  const launchUpload = useCallback(
    (file: File, scope: SharingScope, metadata: Record<string, string>) => {
      const id = newId()
      if (currentUserId) persistUpload(file, id, currentUserId, scope, metadata)
      beginUpload(file, id, scope, metadata)
    },
    [beginUpload, currentUserId],
  )

  // Selecting files stages them (no upload yet) so the user can attach metadata
  // and optionally override the scope. The effective scope is resolved at upload.
  const addFiles = useCallback((files: File[]) => {
    if (files.length === 0) return

    const newStaged: StagedFile[] = files.map((file) => {
      const id = newId()
      const chip = fileTypeChip(file.type)
      stagedSources.current.set(id, { file })
      return {
        id,
        name: file.name,
        size: file.size,
        icon: chip.icon,
        iconClassName: chip.iconClassName,
        metadata: [],
      }
    })

    setStaged((prev) => [...prev, ...newStaged])
  }, [])

  const updateStagedMetadata = useCallback((id: string, fields: MetadataField[]) => {
    setStaged((prev) =>
      prev.map((file) => (file.id === id ? { ...file, metadata: fields } : file)),
    )
  }, [])

  // Sets/clears a staged file's per-file scope override; undefined means inherit
  // the general scope chosen at upload time.
  const setStagedScope = useCallback((id: string, scope?: SharingScope) => {
    setStaged((prev) =>
      prev.map((file) => (file.id === id ? { ...file, scopeOverride: scope } : file)),
    )
  }, [])

  const removeStaged = useCallback((id: string) => {
    stagedSources.current.delete(id)
    setStaged((prev) => prev.filter((file) => file.id !== id))
  }, [])

  const startStaged = useCallback(
    (id: string, generalScope: SharingScope) => {
      const source = stagedSources.current.get(id)
      const stagedFile = stagedRef.current.find((file) => file.id === id)
      if (!source || !stagedFile) return

      // A per-file override wins; otherwise the file inherits the general scope.
      const scope = stagedFile.scopeOverride ?? generalScope
      launchUpload(source.file, scope, toMetadataRecord(stagedFile.metadata))
      stagedSources.current.delete(id)
      setStaged((prev) => prev.filter((file) => file.id !== id))
    },
    [launchUpload],
  )

  const startAllStaged = useCallback(
    (generalScope: SharingScope) => {
      stagedRef.current.forEach((file) => startStaged(file.id, generalScope))
    },
    [startStaged],
  )

  const pause = useCallback(
    (id: string) => {
      controllers.current.get(id)?.pause()
      const m = meta.current.get(id)
      if (m) m.speed = 0
      // Persist the paused state AND the confirmed offset so a restore after reload
      // keeps it paused and shows its real size, not "0 B".
      const confirmedProgress = itemsRef.current.find((item) => item.id === id)?.confirmedProgress
      void setUploadPaused(id, true, confirmedProgress).catch(() => {})
      patch(id, { status: 'paused', detailPrimary: 'Paused' })
    },
    [patch],
  )

  const resume = useCallback(
    (id: string) => {
      const m = meta.current.get(id)
      if (m) m.lastTime = Date.now()
      controllers.current.get(id)?.resume()
      void setUploadPaused(id, false).catch(() => {})
      patch(id, { status: 'uploading', detailPrimary: 'Resuming…' })
    },
    [patch],
  )

  const cancel = useCallback(
    (id: string) => {
      controllers.current.get(id)?.abort()
      controllers.current.delete(id)
      closeJobSub(id)
      meta.current.delete(id)
      forgetUpload(id)
      dispatch({ type: 'remove', id })
    },
    [closeJobSub],
  )

  // Removes a finished row from the queue without contacting the server. The
  // upload already completed, so there is nothing to abort — we only drop the
  // local view-model row and release its bookkeeping.
  const dismiss = useCallback(
    (id: string) => {
      controllers.current.delete(id)
      closeJobSub(id)
      meta.current.delete(id)
      dispatch({ type: 'remove', id })
    },
    [closeJobSub],
  )

  const retry = useCallback(
    (id: string) => {
      const m = meta.current.get(id)
      if (m) {
        m.lastTime = Date.now()
        m.speed = 0
      }
      controllers.current.get(id)?.resume()
      patch(id, { status: 'uploading', detailPrimary: 'Retrying…', errorMessage: undefined })
    },
    [patch],
  )

  const pauseAll = useCallback(() => {
    itemsRef.current
      .filter((item) => item.status === 'uploading')
      .forEach((item) => pause(item.id))
  }, [pause])

  const resumeAll = useCallback(() => {
    itemsRef.current
      .filter((item) => item.status === 'paused')
      .forEach((item) => resume(item.id))
  }, [resume])

  const clearCompleted = useCallback(() => {
    itemsRef.current
      .filter((item) => item.status === 'completed')
      .forEach((item) => {
        controllers.current.delete(item.id)
        closeJobSub(item.id)
        meta.current.delete(item.id)
      })
    dispatch({ type: 'clearCompleted' })
  }, [closeJobSub])

  // Resume uploads that were in flight before a full reload (e.g. logout → login),
  // exactly once. The queue lives in an app-level provider that survives navigation,
  // so the only teardown is on a full reload — where the server keeps the partial
  // upload and we restore it here.
  //
  // The BACKEND is the authority on what may resume: GET /my-uploads returns the
  // caller's resumable uploads (scoped to them by JWT), so a persisted blob is
  // resumed only if its fileId is in that set — we never resume on the strength of
  // the locally-stored ownerId alone. Security/privacy invariant: every other blob is
  // PURGED, so IndexedDB ends up holding only the current user's resumable uploads and
  // no other user's bytes linger at rest. If the backend can't be reached we resume
  // nothing (don't trust the frontend) but still purge blobs that aren't the current
  // user's by their stored ownerId — keeping our own so a later reload can resume them.
  //
  // No cancel-on-cleanup flag: StrictMode runs the effect setup→cleanup→setup, and
  // cancelling the first run's async read would drop the restore entirely. The
  // didRestore ref skips the second setup, and the per-id guard below is a belt-and
  // -suspenders against any double start (refs persist across the StrictMode cycle).
  const didRestore = useRef(false)
  useEffect(() => {
    if (!currentUserId) return
    if (didRestore.current) return
    didRestore.current = true

    const resume = (record: Awaited<ReturnType<typeof getAllUploads>>[number]) => {
      if (controllers.current.has(record.fileId)) return
      const file = new File([record.blob], record.name, { type: record.type })
      // Honor the persisted paused state: a paused upload comes back paused (and
      // doesn't auto-start) until the user resumes it, seeded with its last offset
      // so the size reads correctly instead of "0 B" while paused.
      beginUpload(file, record.fileId, record.scope, record.metadata, {
        detailPrimary: record.paused ? 'Paused' : 'Resuming…',
        startPaused: record.paused,
        initialProgress: record.progress ?? 0,
      })
    }

    void Promise.all([getMyPendingUploads(), getAllUploads()])
      .then(([pending, records]) => {
        const resumableIds = new Set(pending.map((upload) => upload.fileId))
        records.forEach((record) => {
          if (resumableIds.has(record.fileId)) {
            // Backend confirms this is the caller's resumable upload.
            resume(record)
          } else {
            // Anything the backend doesn't confirm — another user's blob, or ours but
            // finished/expired/cancelled, or orphaned → purge it (security invariant).
            void deleteUpload(record.fileId).catch(() => {})
          }
        })
      })
      .catch((error) => {
        // Can't confirm with the backend → don't trust the frontend to resume. Still
        // purge anything that isn't the current user's (for privacy); keep our own so a
        // later reload, once the backend is reachable, can resume them.
        console.error('Could not confirm resumable uploads with backend; purging foreign blobs only', error)
        void getAllUploads()
          .then((records) =>
            records
              .filter((record) => record.ownerId !== currentUserId)
              .forEach((record) => void deleteUpload(record.fileId).catch(() => {})),
          )
          .catch((readError) => console.error('Failed to purge foreign uploads', readError))
      })
  }, [beginUpload, currentUserId])

  const summary = useMemo<UploadSummary>(() => {
    const counts = deriveCounts(items)
    const activeSpeed = items
      .filter((item) => item.status === 'uploading')
      .reduce((sum, item) => sum + (meta.current.get(item.id)?.speed ?? 0), 0)
    return {
      completed: counts.completed,
      total: counts.total,
      progress: counts.progress,
      speed: formatSpeed(activeSpeed),
    }
  }, [items])

  return {
    items,
    staged,
    summary,
    addFiles,
    updateStagedMetadata,
    setStagedScope,
    removeStaged,
    startStaged,
    startAllStaged,
    pause,
    resume,
    cancel,
    dismiss,
    retry,
    pauseAll,
    resumeAll,
    clearCompleted,
  }
}
