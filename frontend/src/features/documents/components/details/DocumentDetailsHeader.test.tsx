import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import DocumentDetailsHeader from './DocumentDetailsHeader'
import type { Breadcrumb } from '../../types'

const breadcrumbs: Breadcrumb[] = [
  { label: 'Documents', to: '/documents' },
  { label: 'Report.pdf' },
]

function renderHeader(
  overrides: Partial<Parameters<typeof DocumentDetailsHeader>[0]> = {},
) {
  const props = {
    breadcrumbs,
    onPrint: vi.fn(),
    isPrinting: false,
    onDownload: vi.fn(),
    isDownloading: false,
    canDelete: false,
    onDeleteFile: vi.fn(),
    ...overrides,
  }
  render(
    <MemoryRouter>
      <DocumentDetailsHeader {...props} />
    </MemoryRouter>,
  )
  return props
}

describe('DocumentDetailsHeader', () => {
  it('invokes onPrint when the print button is clicked', () => {
    const { onPrint } = renderHeader()
    fireEvent.click(screen.getByRole('button', { name: /print/i }))
    expect(onPrint).toHaveBeenCalledTimes(1)
  })

  it('invokes onDownload when the download button is clicked', () => {
    const { onDownload } = renderHeader()
    fireEvent.click(screen.getByRole('button', { name: /download/i }))
    expect(onDownload).toHaveBeenCalledTimes(1)
  })

  it('disables the print and download buttons while their actions are busy', () => {
    renderHeader({ isPrinting: true, isDownloading: true })
    expect(screen.getByRole('button', { name: /print/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /download/i })).toBeDisabled()
  })

  it('omits the overflow menu when deletion is not allowed', () => {
    renderHeader({ canDelete: false })
    expect(screen.queryByRole('button', { name: /more actions/i })).toBeNull()
  })
})
