import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import { useAppDispatch } from '@/app/hooks'
import { loadDocuments } from '@/features/documents/documentsSlice'
import type { SharingScope } from '@/features/documents/documentsApi'
import { createUpload, type UploadController } from './tusUpload'
import { onJobUpdate, type JobUpdateStatus } from '@/features/realtime/jobsSocket'
import { fileTypeChip } from './fileType'
import { formatBytes, formatEta, formatSpeed } from './format'
import { toMetadataRecord } from './metadata'
import { deriveCounts, uploadQueueReducer } from './uploadQueueReducer'
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
  addFiles: (files: File[], scope: SharingScope) => void
  updateStagedMetadata: (id: string, fields: MetadataField[]) => void
  removeStaged: (id: string) => void
  startStaged: (id: string) => void
  startAllStaged: () => void
  pause: (id: string) => void
  resume: (id: string) => void
  cancel: (id: string) => void
  retry: (id: string) => void
  pauseAll: () => void
  resumeAll: () => void
  clearCompleted: () => void
}

export function useUploadQueue(): UseUploadQueue {
  const reduxDispatch = useAppDispatch()
  const [items, dispatch] = useReducer(uploadQueueReducer, [])

  // Files awaiting metadata + confirmation, separate from the active queue so
  // the transfer reducer/statuses stay focused on in-flight uploads.
  const [staged, setStaged] = useState<StagedFile[]>([])

  // Latest queue snapshot for callbacks that fan out over current items
  // (pauseAll/resumeAll) without re-binding on every render.
  const itemsRef = useRef<UploadItem[]>(items)
  itemsRef.current = items

  // Mirror of `staged` for startAll, plus the real File handles (non-serializable,
  // so kept out of state) and the scope captured when each file was selected.
  const stagedRef = useRef<StagedFile[]>(staged)
  stagedRef.current = staged
  const stagedSources = useRef(new Map<string, { file: File; scope: SharingScope }>())

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
    (id: string, status: JobUpdateStatus) => {
      const m = meta.current.get(id)
      const size = m ? formatBytes(m.bytesTotal) : ''

      const primaryByStatus: Record<JobUpdateStatus, string> = {
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

      // Listen on the shared jobs socket for this file's terminal AI outcome. The
      // file was created with `id` as its fileId, so match on that.
      const unsubscribe = onJobUpdate((update) => {
        if (update.fileId !== id) return
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
    },
    [patch],
  )

  // --- public API ----------------------------------------------------------

  // Kicks off a single tus upload: registers the queue row and wires its events.
  const launchUpload = useCallback(
    (file: File, scope: SharingScope, metadata: Record<string, string>) => {
      const id = newId()
      const chip = fileTypeChip(file.type)
      meta.current.set(id, {
        bytesTotal: file.size,
        lastBytes: 0,
        lastTime: Date.now(),
        speed: 0,
      })
      const item: UploadItem = {
        id,
        fileId: id,
        name: file.name,
        icon: chip.icon,
        iconClassName: chip.iconClassName,
        status: 'uploading',
        progress: 0,
        confirmedProgress: 0,
        detailPrimary: 'Starting…',
        detailSecondary: `0 B / ${formatBytes(file.size)}`,
      }
      dispatch({ type: 'add', items: [item] })

      const controller = createUpload({
        file,
        fileId: id,
        scope,
        metadata,
        onProgress: (uploaded, total) => handleProgress(id, uploaded, total),
        onChunkComplete: (accepted, total) => handleChunkComplete(id, accepted, total),
        onSuccess: () => handleTusSuccess(id),
        onError: (error) => handleTusError(id, error),
      })
      controllers.current.set(id, controller)
    },
    [handleProgress, handleChunkComplete, handleTusSuccess, handleTusError],
  )

  // Selecting files stages them (no upload yet) so the user can attach metadata.
  const addFiles = useCallback((files: File[], scope: SharingScope) => {
    if (files.length === 0) return

    const newStaged: StagedFile[] = files.map((file) => {
      const id = newId()
      const chip = fileTypeChip(file.type)
      stagedSources.current.set(id, { file, scope })
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

  const removeStaged = useCallback((id: string) => {
    stagedSources.current.delete(id)
    setStaged((prev) => prev.filter((file) => file.id !== id))
  }, [])

  const startStaged = useCallback(
    (id: string) => {
      const source = stagedSources.current.get(id)
      const stagedFile = stagedRef.current.find((file) => file.id === id)
      if (!source || !stagedFile) return

      launchUpload(source.file, source.scope, toMetadataRecord(stagedFile.metadata))
      stagedSources.current.delete(id)
      setStaged((prev) => prev.filter((file) => file.id !== id))
    },
    [launchUpload],
  )

  const startAllStaged = useCallback(() => {
    stagedRef.current.forEach((file) => startStaged(file.id))
  }, [startStaged])

  const pause = useCallback(
    (id: string) => {
      controllers.current.get(id)?.pause()
      const m = meta.current.get(id)
      if (m) m.speed = 0
      patch(id, { status: 'paused', detailPrimary: 'Paused' })
    },
    [patch],
  )

  const resume = useCallback(
    (id: string) => {
      const m = meta.current.get(id)
      if (m) m.lastTime = Date.now()
      controllers.current.get(id)?.resume()
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

  // Abort everything still in flight when the page unmounts.
  useEffect(() => {
    const activeControllers = controllers.current
    const activeJobSubs = jobSubs.current
    return () => {
      activeControllers.forEach((controller) => controller.abort())
      activeJobSubs.forEach((close) => close())
      activeControllers.clear()
      activeJobSubs.clear()
    }
  }, [])

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
    removeStaged,
    startStaged,
    startAllStaged,
    pause,
    resume,
    cancel,
    retry,
    pauseAll,
    resumeAll,
    clearCompleted,
  }
}
