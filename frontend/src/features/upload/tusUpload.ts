import * as tus from 'tus-js-client'
import { API, XSRF_COOKIE_NAME } from '@/config'
import { getCookie } from '@/lib/cookies'
import { SharingScope } from '@/features/documents/documentsApi'
import { isRetryableUploadStatus, uploadErrorMessage, uploadErrorStatus } from './uploadErrors'

// Resumable upload client for the file service's tus endpoint.
//
// Ported from the previous frontend's working integration, adapted to the new
// cookie-session app: we add `withCredentials` (the gateway session cookie) and
// inject the CSRF header from the XSRF-TOKEN cookie, since tus bypasses the
// shared axios instance.
//
// The backend's create call is NOT vanilla tus: the client generates the file
// UUID and passes it (plus the sharing scope) as query params on the initial
// POST — see TusController.postUploads.

// Chunk size for resumable PATCHes (256 KB), matching the proven prior config.
const CHUNK_SIZE = 256 * 1024
const RETRY_DELAYS = [0, 3000, 5000, 10000, 20000]

/** Imperative handle for an in-flight upload. */
export interface UploadController {
  pause: () => void
  resume: () => void
  abort: () => void
}

export interface CreateUploadOptions {
  file: File
  /** Client-generated file UUID, sent as the `fileId` query param. */
  fileId: string
  scope: SharingScope
  /** User-provided key/value metadata, merged into the tus `Upload-Metadata` header. */
  metadata?: Record<string, string>
  /**
   * Create the upload paused (a restored upload that was paused before reload): the
   * transfer doesn't start until `resume()` is called. Defaults to false.
   */
  startPaused?: boolean
  /** Reports bytes flushed to the socket (optimistic, includes the in-flight chunk). */
  onProgress?: (bytesUploaded: number, bytesTotal: number) => void
  /**
   * Reports bytes the server has *confirmed* (its persisted offset), fired once
   * per accepted chunk PATCH. Monotonic, so it never regresses on a retry.
   */
  onChunkComplete?: (bytesAccepted: number, bytesTotal: number) => void
  /** Fired once all bytes are accepted by the server. */
  onSuccess?: () => void
  /** Fired on a genuine failure (not on an intentional pause/abort). */
  onError?: (error: Error) => void
}

export function createUpload({
  file,
  fileId,
  scope,
  metadata,
  startPaused = false,
  onProgress,
  onChunkComplete,
  onSuccess,
  onError,
}: CreateUploadOptions): UploadController {
  // Distinguish user-initiated pause/abort from real transport errors so we
  // don't surface them as upload failures. A restored-paused upload starts paused.
  let isPausedIntentionally = startPaused
  let isAbortedIntentionally = false

  const params = new URLSearchParams({ fileId, sharingScope: scope })
  const endpoint = `${API.uploads}?${params.toString()}`

  const xsrfToken = getCookie(XSRF_COOKIE_NAME)

  const upload = new tus.Upload(file, {
    endpoint,
    chunkSize: CHUNK_SIZE,
    retryDelays: RETRY_DELAYS,
    // Persist the upload URL in localStorage so an upload can be resumed after a
    // full page reload (e.g. logout → login). The default fingerprint is
    // content-based (name+type+size), which would make re-uploading the same file
    // resume a prior, finished upload's URL — whose server resource is gone — and
    // 404. We avoid that by keying the fingerprint on this upload's fresh fileId,
    // so each upload owns a unique, stable storage entry that we recreate verbatim
    // when restoring from IndexedDB. The entry is auto-removed on success.
    storeFingerprintForResuming: true,
    removeFingerprintOnSuccess: true,
    fingerprint: () => Promise.resolve(`tus-${fileId}`),
    // Retry only transient failures; permanent rejections (e.g. 413 when the
    // file exceeds the user's storage quota) fail immediately.
    onShouldRetry: (error) => {
      if (isPausedIntentionally || isAbortedIntentionally) return false
      return isRetryableUploadStatus(uploadErrorStatus(error))
    },
    // User metadata first so it can never clobber the filename the backend keys on.
    metadata: { ...metadata, filename: file.name },
    headers: xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken } : {},

    onBeforeRequest: (req) => {
      // The XHR must carry the session cookie; tus-js-client exposes the
      // underlying request so we can opt into credentials.
      const xhr = req.getUnderlyingObject() as XMLHttpRequest
      xhr.withCredentials = true
      // Only the creation POST advertises the real media type; chunk PATCHes
      // use the tus octet-stream content type that the client sets itself.
      if (req.getMethod() === 'POST' && file.type) {
        req.setHeader('Content-Type', file.type)
      }
    },

    onProgress: (bytesUploaded, bytesTotal) => {
      onProgress?.(bytesUploaded, bytesTotal)
    },

    // Fires after each PATCH succeeds, carrying the server-confirmed offset.
    onChunkComplete: (_chunkSize, bytesAccepted, bytesTotal) => {
      onChunkComplete?.(bytesAccepted, bytesTotal)
    },

    onSuccess: () => {
      onSuccess?.()
    },

    onError: (error) => {
      if (isPausedIntentionally || isAbortedIntentionally) return
      const raw = error instanceof Error ? error : new Error(String(error))
      // Surface a friendly message for known rejections (413 quota exceeded).
      onError?.(new Error(uploadErrorMessage(uploadErrorStatus(raw), raw.message)))
    },
  })

  // Start (or resume) the transfer. Resume from a stored URL if one exists for this
  // fileId (set after a reload by recreating the upload from its persisted bytes);
  // otherwise findPreviousUploads returns nothing and we start fresh with a POST.
  // Bails if the upload was paused or aborted while the async lookup was in flight.
  const beginTransfer = () => {
    void upload.findPreviousUploads().then((previous) => {
      if (isAbortedIntentionally || isPausedIntentionally) return
      if (previous.length > 0) upload.resumeFromPreviousUpload(previous[0])
      upload.start()
    })
  }

  // A restored-paused upload waits for resume(); everything else starts now.
  if (!startPaused) beginTransfer()

  return {
    pause: () => {
      isPausedIntentionally = true
      void upload.abort()
    },
    resume: () => {
      isPausedIntentionally = false
      // Resume-aware so a restored-paused upload (never started) continues from the
      // server offset instead of POSTing a fresh upload.
      beginTransfer()
    },
    abort: () => {
      isAbortedIntentionally = true
      void upload.abort(true)
    },
  }
}
