import { render, screen, fireEvent, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SharedWithMePage from './SharedWithMePage'
import type { SharedDocument } from '@/features/shared/types'

const downloadMock = vi.fn()

let sharedState: {
  documents: SharedDocument[]
  status: 'loading' | 'succeeded' | 'failed'
  error: string | null
  pageInfo: {
    page: number
    totalElements: number
    totalPages: number
    isFirst: boolean
    isLast: boolean
  }
}

vi.mock('@/features/shared/useSharedFiles', () => ({
  useSharedFiles: () => ({ ...sharedState, goToPage: vi.fn(), reload: vi.fn() }),
}))

vi.mock('@/features/shared/useSharedDownload', () => ({
  useSharedDownload: () => ({ downloadingId: null, error: null, download: downloadMock }),
}))

function doc(id: string, title: string, permission: 'edit' | 'view'): SharedDocument {
  return {
    id,
    title,
    fileType: 'pdf',
    typeLabel: 'PDF Document',
    size: '2.4 MB',
    owner: 'Sarah Jenkins',
    sharedDate: 'Oct 12, 2023',
    permission,
  }
}

const DOCS = [
  doc('1', 'Q4 Projections', 'edit'),
  doc('2', 'Strategy', 'view'),
  doc('3', 'Roadmap', 'edit'),
]

function renderPage() {
  return render(
    <MemoryRouter>
      <SharedWithMePage />
    </MemoryRouter>,
  )
}

describe('SharedWithMePage', () => {
  beforeEach(() => {
    downloadMock.mockClear()
    sharedState = {
      documents: DOCS,
      status: 'succeeded',
      error: null,
      pageInfo: { page: 0, totalElements: 3, totalPages: 1, isFirst: true, isLast: true },
    }
  })

  it('renders all shared documents by default', () => {
    renderPage()
    expect(screen.getByText('Q4 Projections')).toBeInTheDocument()
    expect(screen.getByText('Strategy')).toBeInTheDocument()
    expect(screen.getByText('Roadmap')).toBeInTheDocument()
    expect(screen.getByText('Showing 3 of 3 shared documents')).toBeInTheDocument()
  })

  it('filters to editable documents via the Can Edit chip', () => {
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'Can Edit' }))
    expect(screen.getByText('Q4 Projections')).toBeInTheDocument()
    expect(screen.getByText('Roadmap')).toBeInTheDocument()
    expect(screen.queryByText('Strategy')).not.toBeInTheDocument()
  })

  it('filters by the search box', () => {
    renderPage()
    fireEvent.change(screen.getByPlaceholderText('Search shared documents…'), {
      target: { value: 'road' },
    })
    expect(screen.getByText('Roadmap')).toBeInTheDocument()
    expect(screen.queryByText('Q4 Projections')).not.toBeInTheDocument()
  })

  it('shows an empty state when nothing is shared', () => {
    sharedState = {
      documents: [],
      status: 'succeeded',
      error: null,
      pageInfo: { page: 0, totalElements: 0, totalPages: 0, isFirst: true, isLast: true },
    }
    renderPage()
    expect(
      screen.getByText('No documents have been shared with you yet.'),
    ).toBeInTheDocument()
  })

  it('triggers a download from the row action', () => {
    renderPage()
    const row = screen.getByText('Q4 Projections').closest('tr') as HTMLTableRowElement
    fireEvent.click(within(row).getByLabelText('Download'))
    expect(downloadMock).toHaveBeenCalledWith('1', 'Q4 Projections')
  })
})
