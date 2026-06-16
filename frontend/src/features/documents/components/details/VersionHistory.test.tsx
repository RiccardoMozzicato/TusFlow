import { render, screen, fireEvent } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import VersionHistory from './VersionHistory'
import type { DocumentVersion } from '../../types'

// Stub the upload hook so the component test stays free of the tus client.
const uploadMock = vi.fn()
const resetMock = vi.fn()
let hookState = {
  status: 'idle' as 'idle' | 'uploading' | 'success' | 'error',
  progress: 0,
  fileName: null as string | null,
  error: null as string | null,
}

vi.mock('../../useVersionUpload', () => ({
  useVersionUpload: () => ({ ...hookState, upload: uploadMock, reset: resetMock }),
}))

const downloadMock = vi.fn()
let downloadState = { downloadingVersion: null as number | null, error: null as string | null }

vi.mock('../../useVersionDownload', () => ({
  useVersionDownload: () => ({ ...downloadState, download: downloadMock }),
}))

function makeVersions(count: number): DocumentVersion[] {
  return Array.from({ length: count }, (_, index) => {
    const version = count - index // newest first
    return {
      id: String(version),
      version,
      label: `v${version}`,
      filename: `report-v${version}.pdf`,
      author: 'Alex',
      date: `Oct ${version}, 2023`,
      isCurrent: index === 0,
    }
  })
}

function renderHistory(versions: DocumentVersion[]) {
  return render(
    <VersionHistory
      versions={versions}
      fileId="file-1"
      scope="PERSONAL"
      onUploaded={() => {}}
    />,
  )
}

describe('VersionHistory', () => {
  beforeEach(() => {
    uploadMock.mockClear()
    resetMock.mockClear()
    downloadMock.mockClear()
    hookState = { status: 'idle', progress: 0, fileName: null, error: null }
    downloadState = { downloadingVersion: null, error: null }
  })

  it('hides "View All" when there are few versions', () => {
    renderHistory(makeVersions(2))
    expect(screen.getByText('v2')).toBeInTheDocument()
    expect(screen.getByText('v1')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /View All/i })).not.toBeInTheDocument()
  })

  it('collapses to the latest three and expands on "View All"', () => {
    renderHistory(makeVersions(5))

    // Collapsed: newest 3 shown, oldest 2 hidden.
    expect(screen.getByText('v5')).toBeInTheDocument()
    expect(screen.getByText('v3')).toBeInTheDocument()
    expect(screen.queryByText('v2')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'View All (5)' }))
    expect(screen.getByText('v1')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Show less' }))
    expect(screen.queryByText('v1')).not.toBeInTheDocument()
  })

  it('downloads a specific version by its number and filename', () => {
    renderHistory(makeVersions(2))
    fireEvent.click(screen.getByRole('button', { name: 'Download v1' }))
    expect(downloadMock).toHaveBeenCalledWith(1, 'report-v1.pdf')
  })

  it('surfaces a download error', () => {
    downloadState = { downloadingVersion: null, error: 'Version not available' }
    renderHistory(makeVersions(1))
    expect(screen.getByText('Version not available')).toBeInTheDocument()
  })

  it('starts an upload when a file is picked', () => {
    const { container } = renderHistory(makeVersions(1))
    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    const file = new File(['data'], 'report-v2.pdf', { type: 'application/pdf' })

    fireEvent.change(input, { target: { files: [file] } })
    expect(uploadMock).toHaveBeenCalledWith(file)
  })

  it('shows progress while uploading', () => {
    hookState = { status: 'uploading', progress: 0.42, fileName: 'report-v2.pdf', error: null }
    renderHistory(makeVersions(1))
    expect(screen.getByText('Uploading…')).toBeInTheDocument()
    expect(screen.getByText(/report-v2\.pdf • 42%/)).toBeInTheDocument()
  })

  it('surfaces an upload error with a dismiss action', () => {
    hookState = { status: 'error', progress: 0, fileName: 'big.pdf', error: 'Not enough storage space' }
    renderHistory(makeVersions(1))
    expect(screen.getByText('Not enough storage space')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(resetMock).toHaveBeenCalled()
  })
})
