// Fallback cleanup delay for browsers that don't fire `onafterprint` on the
// hidden print frame (e.g. some PDF viewers). Generous so it never races the
// print dialog.
const PRINT_CLEANUP_DELAY_MS = 60_000

// Prints an in-memory blob by loading it into a hidden iframe and invoking the
// browser's print dialog on its content, then revoking the object URL and
// removing the iframe once printing finishes. Suitable for natively-embeddable
// types (PDF, text, images); callers should gate out types the browser can't
// render inline (e.g. DOCX), which would otherwise be downloaded by the frame.
export function printBlob(blob: Blob): void {
  const url = URL.createObjectURL(blob)
  const iframe = document.createElement('iframe')
  iframe.style.position = 'fixed'
  iframe.style.right = '0'
  iframe.style.bottom = '0'
  iframe.style.width = '0'
  iframe.style.height = '0'
  iframe.style.border = '0'
  iframe.style.visibility = 'hidden'

  iframe.onload = () => {
    const frameWindow = iframe.contentWindow

    // Revoking the URL or removing the iframe before the print dialog has read
    // the document cancels printing, so defer cleanup until after-print (with a
    // timeout fallback). Guarded so it runs at most once.
    let cleaned = false
    const cleanup = () => {
      if (cleaned) return
      cleaned = true
      URL.revokeObjectURL(url)
      iframe.remove()
    }

    if (!frameWindow) {
      cleanup()
      return
    }

    frameWindow.onafterprint = cleanup
    window.setTimeout(cleanup, PRINT_CLEANUP_DELAY_MS)

    frameWindow.focus()
    frameWindow.print()
  }

  iframe.src = url
  document.body.appendChild(iframe)
}
