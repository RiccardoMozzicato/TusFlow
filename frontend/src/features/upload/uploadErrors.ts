import * as tus from 'tus-js-client'

// Error classification for tus uploads. The backend rejects uploads that
// exceed the user's storage quota with 413 (see TusUploaderService's
// UploadTooLargeException handler), which must neither be retried nor shown
// as a raw transport error.

const PAYLOAD_TOO_LARGE = 413
const QUOTA_EXCEEDED_MESSAGE =
  'Not enough storage space — the file exceeds your remaining quota'
const FALLBACK_MESSAGE = 'Upload failed'

// 4xx statuses tus considers transient (resource lock / offset conflict).
const RETRYABLE_CLIENT_STATUSES = [409, 423]

/** Extracts the HTTP status from a tus error, if the response made it back. */
export function uploadErrorStatus(error: Error): number | undefined {
  if (!(error instanceof tus.DetailedError)) return undefined
  return error.originalResponse?.getStatus()
}

/**
 * Whether a failed request is worth retrying: network failures and server
 * errors are; client errors (e.g. 413 quota exceeded) are permanent, except
 * the lock/conflict statuses the tus protocol treats as transient.
 */
export function isRetryableUploadStatus(status?: number): boolean {
  if (status === undefined) return true
  if (RETRYABLE_CLIENT_STATUSES.includes(status)) return true
  return status < 400 || status >= 500
}

/** User-facing message for a failed upload, keyed off the HTTP status. */
export function uploadErrorMessage(status: number | undefined, rawMessage: string): string {
  if (status === PAYLOAD_TOO_LARGE) return QUOTA_EXCEEDED_MESSAGE
  return rawMessage || FALLBACK_MESSAGE
}