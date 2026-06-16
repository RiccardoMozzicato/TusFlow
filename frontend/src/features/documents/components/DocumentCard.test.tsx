import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import DocumentCard from './DocumentCard'
import type { LibraryDocument } from '../types'

const baseDoc: LibraryDocument = {
  id: 'd1',
  title: 'Quarterly Report',
  fileType: 'pdf',
  scope: 'personal',
  status: 'ready',
  owner: 'Alex',
  created: 'Oct 20, 2023, 09:00 AM',
  version: 'v1',
  versionNumber: 1,
  size: '2.3 MB',
}

function renderCard(
  doc: LibraryDocument,
  favoriteProps?: { isFavorited: boolean; onToggleFavorite: () => void },
) {
  render(
    <MemoryRouter>
      <DocumentCard doc={doc} {...favoriteProps} />
    </MemoryRouter>,
  )
}

describe('DocumentCard AI indicator', () => {
  it('shows the sparkle when AI processing completed', () => {
    renderCard({ ...baseDoc, aiStatus: 'COMPLETED' })
    expect(screen.getByLabelText('AI-generated metadata')).toBeInTheDocument()
    expect(screen.queryByLabelText('AI processing')).not.toBeInTheDocument()
  })

  it('shows the in-progress indicator while AI is running', () => {
    renderCard({ ...baseDoc, aiStatus: 'PROCESSING' })
    expect(screen.getByLabelText('AI processing')).toBeInTheDocument()
    expect(screen.queryByLabelText('AI-generated metadata')).not.toBeInTheDocument()
  })

  it('omits any AI indicator when there is no job or it failed', () => {
    renderCard(baseDoc)
    expect(screen.queryByLabelText('AI-generated metadata')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('AI processing')).not.toBeInTheDocument()

    renderCard({ ...baseDoc, aiStatus: 'FAILED' })
    expect(screen.queryByLabelText('AI-generated metadata')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('AI processing')).not.toBeInTheDocument()
  })

  it('renders the file size', () => {
    renderCard(baseDoc)
    expect(screen.getByText('2.3 MB')).toBeInTheDocument()
  })
})

describe('DocumentCard favorite button', () => {
  it('omits the star when no favorite handler is provided', () => {
    renderCard(baseDoc)
    expect(screen.queryByRole('button', { name: /favorite/i })).not.toBeInTheDocument()
  })

  it('toggles the favorite without navigating', async () => {
    const onToggleFavorite = vi.fn()
    renderCard(baseDoc, { isFavorited: false, onToggleFavorite })

    await userEvent.click(screen.getByRole('button', { name: 'Favorite Quarterly Report' }))

    expect(onToggleFavorite).toHaveBeenCalledTimes(1)
  })

  it('reads as unfavorite when the current version is favorited', () => {
    renderCard(baseDoc, { isFavorited: true, onToggleFavorite: vi.fn() })
    const button = screen.getByRole('button', { name: 'Unfavorite Quarterly Report' })
    expect(button).toHaveAttribute('aria-pressed', 'true')
  })
})

describe('DocumentCard scope indicator', () => {
  it('labels a personal file', () => {
    renderCard(baseDoc)
    expect(screen.getByLabelText('Personal file')).toBeInTheDocument()
  })

  it('labels a department file', () => {
    renderCard({ ...baseDoc, scope: 'department' })
    expect(screen.getByLabelText('Department file')).toBeInTheDocument()
  })

  it('labels an organization file', () => {
    renderCard({ ...baseDoc, scope: 'organization' })
    expect(screen.getByLabelText('Organization file')).toBeInTheDocument()
  })
})
