import { useEffect, useState } from 'react'
import { getPreviewBlob } from './detailsApi'

interface PreviewBlobState {
  status: 'loading' | 'ready' | 'error'
  /** The raw bytes — needed by renderers that work on the buffer (e.g. DOCX). */
  blob: Blob | null
  /** Same-origin object URL for the blob — safe to embed (no X-Frame-Options). */
  url: string | null
  error: string | null
}

const INITIAL: PreviewBlobState = { status: 'loading', blob: null, url: null, error: null }

// The preview endpoint may serve bytes with a missing/generic content type (the
// stored MIME can be absent). A blob: URL takes its content type from the
// Blob's `type`, and an <iframe> only renders a PDF when that type is correct —
// so re-tag the blob with the expected MIME when we have one and the response
// didn't provide a usable one.
function retag(blob: Blob, expectedMime?: string): Blob {
  if (!expectedMime) return blob
  const current = blob.type.split(';')[0].trim().toLowerCase()
  if (current === expectedMime) return blob
  return new Blob([blob], { type: expectedMime })
}

// Fetches a file's preview bytes via the authed client and exposes them as both
// a Blob and an object URL, revoking the URL on unmount / id change so we don't
// leak them. `expectedMime` lets the caller correct a missing/wrong server MIME.
export function usePreviewBlob(fileId: string, expectedMime?: string): PreviewBlobState {
  const [state, setState] = useState<PreviewBlobState>(INITIAL)

  useEffect(() => {
    let cancelled = false
    let objectUrl: string | null = null
    setState(INITIAL)

    getPreviewBlob(fileId)
      .then((raw) => {
        if (cancelled) return
        const blob = retag(raw, expectedMime)
        objectUrl = URL.createObjectURL(blob)
        setState({ status: 'ready', blob, url: objectUrl, error: null })
      })
      .catch((err: unknown) => {
        if (cancelled) return
        const error = err instanceof Error ? err.message : 'Failed to load preview'
        setState({ status: 'error', blob: null, url: null, error })
      })

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [fileId, expectedMime])

  return state
}
