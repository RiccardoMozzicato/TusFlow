import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { printBlob } from './print'

// jsdom doesn't implement the object-URL APIs, so provide mocks to assert against.
const createObjectURL = vi.fn(() => 'blob:mock-url')
const revokeObjectURL = vi.fn()

describe('printBlob', () => {
  beforeEach(() => {
    URL.createObjectURL = createObjectURL as typeof URL.createObjectURL
    URL.revokeObjectURL = revokeObjectURL as typeof URL.revokeObjectURL
  })

  afterEach(() => {
    vi.restoreAllMocks()
    createObjectURL.mockClear()
    revokeObjectURL.mockClear()
  })

  it('mounts a hidden iframe pointing at the blob object URL', () => {
    const blob = new Blob(['%PDF-1.4'], { type: 'application/pdf' })
    // Intercept the append so jsdom never creates a (blob-origin) browsing
    // context for the frame — we only assert on how it was set up.
    const appendSpy = vi
      .spyOn(document.body, 'appendChild')
      .mockImplementation((node) => node)

    printBlob(blob)

    expect(createObjectURL).toHaveBeenCalledWith(blob)
    expect(appendSpy).toHaveBeenCalledTimes(1)
    const iframe = appendSpy.mock.calls[0][0] as HTMLIFrameElement
    expect(iframe.tagName).toBe('IFRAME')
    // getAttribute returns the literal src (no base-URL resolution).
    expect(iframe.getAttribute('src')).toBe('blob:mock-url')
    expect(iframe.style.visibility).toBe('hidden')
  })
})
