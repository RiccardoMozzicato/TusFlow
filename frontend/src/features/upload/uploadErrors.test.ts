import { describe, expect, it } from 'vitest'
import { isRetryableUploadStatus, uploadErrorMessage } from './uploadErrors'

describe('isRetryableUploadStatus', () => {
  it('does not retry client errors like 413 quota rejections', () => {
    expect(isRetryableUploadStatus(413)).toBe(false)
    expect(isRetryableUploadStatus(400)).toBe(false)
    expect(isRetryableUploadStatus(404)).toBe(false)
  })

  it('retries lock/conflict statuses that tus treats as transient', () => {
    expect(isRetryableUploadStatus(409)).toBe(true)
    expect(isRetryableUploadStatus(423)).toBe(true)
  })

  it('retries server errors and unknown (network) failures', () => {
    expect(isRetryableUploadStatus(500)).toBe(true)
    expect(isRetryableUploadStatus(undefined)).toBe(true)
  })
})

describe('uploadErrorMessage', () => {
  it('maps 413 to a storage-quota message', () => {
    expect(uploadErrorMessage(413, 'tus: unexpected response')).toBe(
      'Not enough storage space — the file exceeds your remaining quota',
    )
  })

  it('falls back to the raw message for other statuses', () => {
    expect(uploadErrorMessage(500, 'tus: unexpected response')).toBe('tus: unexpected response')
    expect(uploadErrorMessage(undefined, '')).toBe('Upload failed')
  })
})