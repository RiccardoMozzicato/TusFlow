import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import DocumentTable from './DocumentTable'
import { SORT_NEWEST, SORT_OLDEST, SORT_SIZE_ASC, SORT_SIZE_DESC } from '../filterOptions'
import type { LibraryDocument } from '../types'

const baseDoc: LibraryDocument = {
  id: 'd1',
  title: 'Quarterly Report',
  fileType: 'pdf',
  scope: 'personal',
  status: 'ready',
  owner: 'Alex',
  isOwnedByMe: false,
  isWritable: false,
  sharingScope: 'PERSONAL',
  created: 'Oct 20, 2023, 09:00 AM',
  createdRelative: '3 days ago',
  version: 'v1',
  versionNumber: 1,
  size: '2.3 MB',
}

function renderTable(props: Partial<React.ComponentProps<typeof DocumentTable>> = {}) {
  render(
    <MemoryRouter>
      <DocumentTable documents={[baseDoc]} {...props} />
    </MemoryRouter>,
  )
}

describe('DocumentTable size column sorting', () => {
  it('starts ascending when not currently sorted by size', async () => {
    const onSortChange = vi.fn()
    renderTable({ sort: SORT_NEWEST, onSortChange })

    await userEvent.click(screen.getByRole('button', { name: 'Sort by size, ascending' }))

    expect(onSortChange).toHaveBeenCalledWith(SORT_SIZE_ASC)
  })

  it('toggles ascending to descending', async () => {
    const onSortChange = vi.fn()
    renderTable({ sort: SORT_SIZE_ASC, onSortChange })

    await userEvent.click(screen.getByRole('button', { name: 'Sort by size, descending' }))

    expect(onSortChange).toHaveBeenCalledWith(SORT_SIZE_DESC)
  })

  it('toggles descending back to ascending', async () => {
    const onSortChange = vi.fn()
    renderTable({ sort: SORT_SIZE_DESC, onSortChange })

    await userEvent.click(screen.getByRole('button', { name: 'Sort by size, ascending' }))

    expect(onSortChange).toHaveBeenCalledWith(SORT_SIZE_ASC)
  })

  it('marks the size header active while sorting by size', () => {
    renderTable({ sort: SORT_SIZE_DESC, onSortChange: vi.fn() })

    const button = screen.getByRole('button', { name: 'Sort by size, ascending' })
    expect(button).toHaveAttribute('aria-pressed', 'true')
  })

  it('renders a static size header when no sort handler is provided', () => {
    renderTable({ sort: SORT_NEWEST })

    expect(screen.queryByRole('button', { name: /sort by size/i })).not.toBeInTheDocument()
  })
})

describe('DocumentTable date column sorting', () => {
  it('shows the active descending direction by default (newest first)', () => {
    renderTable({ sort: SORT_NEWEST, onSortChange: vi.fn() })

    const button = screen.getByRole('button', { name: 'Sort by date created, ascending' })
    expect(button).toHaveAttribute('aria-pressed', 'true')
  })

  it('toggles date created from descending to ascending', async () => {
    const onSortChange = vi.fn()
    renderTable({ sort: SORT_NEWEST, onSortChange })

    await userEvent.click(screen.getByRole('button', { name: 'Sort by date created, ascending' }))

    expect(onSortChange).toHaveBeenCalledWith(SORT_OLDEST)
  })
})

describe('DocumentTable document name sorting', () => {
  it('starts ascending (A→Z) when not currently sorted by name', async () => {
    const onSortChange = vi.fn()
    renderTable({ sort: SORT_NEWEST, onSortChange })

    await userEvent.click(screen.getByRole('button', { name: 'Sort by document name, ascending' }))

    expect(onSortChange).toHaveBeenCalledWith('lastFilename,asc')
  })

  it('toggles name ascending to descending', async () => {
    const onSortChange = vi.fn()
    renderTable({ sort: 'lastFilename,asc', onSortChange })

    await userEvent.click(screen.getByRole('button', { name: 'Sort by document name, descending' }))

    expect(onSortChange).toHaveBeenCalledWith('lastFilename,desc')
  })
})
