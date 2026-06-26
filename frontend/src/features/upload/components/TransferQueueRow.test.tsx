import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import TransferQueueRow from './TransferQueueRow'
import type { UploadItem } from '../types'

function makeItem(overrides: Partial<UploadItem> = {}): UploadItem {
  return {
    id: 'a',
    fileId: 'a',
    name: 'file.pdf',
    icon: 'picture_as_pdf',
    iconClassName: 'text-ds-error',
    status: 'uploading',
    progress: 0,
    confirmedProgress: 0,
    detailPrimary: '',
    detailSecondary: '',
    ...overrides,
  }
}

function renderRow(item: UploadItem) {
  const { container } = render(<TransferQueueRow item={item} last onAction={() => {}} />)
  // The two bar fills are the only width-styled elements.
  return Array.from(
    container.querySelectorAll<HTMLElement>('div[style*="width"]'),
  ).map((el) => el.style.width)
}

describe('TransferQueueRow progress bar', () => {
  it('renders the buffered (sent) and confirmed (server) fills separately', () => {
    const widths = renderRow(makeItem({ progress: 0.6, confirmedProgress: 0.4 }))
    // Buffered fill leads, confirmed fill trails behind it.
    expect(widths).toEqual(['60%', '40%'])
  })

  it('confirmed fill can lag at 0 while bytes are still in flight', () => {
    const widths = renderRow(makeItem({ progress: 0.25, confirmedProgress: 0 }))
    expect(widths).toEqual(['25%', '0%'])
  })

  it('shows an indeterminate bar (no width fills) while processing', () => {
    const widths = renderRow(makeItem({ status: 'processing', progress: 1, confirmedProgress: 1 }))
    expect(widths).toEqual([])
  })
})
