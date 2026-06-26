import { useCallback, useEffect, useRef, useState } from 'react'
import { createUpload, type UploadController } from '@/features/upload/tusUpload'
import type { SharingScope } from './documentsApi'

// Drives a single new-version upload from the Document Details page. A new
// version reuses the file's *existing* UUID and is created through the canonical
// `POST /api/v1/files/{fileId}/versions` endpoint (see `asNewVersion` in
// createUpload): the backend validates the file + write access, then the tus
// upload appends a new FileVersion instead of creating a new file.

export type VersionUploadStatus = 'idle' | 'uploading' | 'success' | 'error'

export interface VersionUploadState {
  status: VersionUploadStatus
  /** Upload progress in the 0..1 range; meaningful only while 'uploading'. */
  progress: number
  /** Name of the file being uploaded; null when idle. */
  fileName: string | null
  /** User-facing failure reason; null unless status is 'error'. */
  error: string | null
}

export interface UseVersionUpload extends VersionUploadState {
  /** Starts uploading the given file as a new version. No-op while one is in flight. */
  upload: (file: File) => void
  /** Clears a finished/failed result back to idle. */
  reset: () => void
}

const IDLE: VersionUploadState = {
  status: 'idle',
  progress: 0,
  fileName: null,
  error: null,
}

/**
 * @param fileId   Existing document UUID — the new version is attached to it.
 * @param scope    The file's current sharing scope, reused for authorization.
 * @param onComplete Called once the new version is fully uploaded, so the caller
 *                   can refresh the version list and metadata.
 */
export function useVersionUpload(
  fileId: string,
  scope: SharingScope,
  onComplete: () => void,
): UseVersionUpload {
  const [state, setState] = useState<VersionUploadState>(IDLE)
  const controllerRef = useRef<UploadController | null>(null)

  const upload = useCallback(
    (file: File) => {
      // Ignore a second pick while an upload is already running.
      if (controllerRef.current) return

      setState({ status: 'uploading', progress: 0, fileName: file.name, error: null })

      controllerRef.current = createUpload({
        file,
        fileId,
        scope,
        asNewVersion: true,
        onProgress: (uploaded, total) => {
          setState((prev) => ({
            ...prev,
            progress: total > 0 ? uploaded / total : 0,
          }))
        },
        onSuccess: () => {
          controllerRef.current = null
          setState((prev) => ({ ...prev, status: 'success', progress: 1 }))
          onComplete()
        },
        onError: (error) => {
          controllerRef.current = null
          setState((prev) => ({
            ...prev,
            status: 'error',
            error: error.message || 'Upload failed',
          }))
        },
      })
    },
    [fileId, scope, onComplete],
  )

  const reset = useCallback(() => {
    setState(IDLE)
  }, [])

  // Abort an in-flight upload if the page unmounts mid-transfer.
  useEffect(() => {
    return () => {
      controllerRef.current?.abort()
      controllerRef.current = null
    }
  }, [])

  return { ...state, upload, reset }
}
