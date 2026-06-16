import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import FilterPanel from './FilterPanel'
import { SharingScope } from '../documentsApi'
import type { DocumentFilters } from '../documentsSlice'

const baseFilters: DocumentFilters = {
  sort: 'createdAt,desc',
  sharingScope: [
    SharingScope.PERSONAL,
    SharingScope.DEPARTMENT,
    SharingScope.ORGANIZATION,
  ],
}

function renderPanel(overrides: Partial<DocumentFilters> = {}) {
  const onChange = vi.fn()
  const onClear = vi.fn()
  render(
    <FilterPanel
      filters={{ ...baseFilters, ...overrides }}
      onChange={onChange}
      onClear={onClear}
    />,
  )
  return { onChange, onClear }
}

describe('FilterPanel', () => {
  it('renders the supported filter groups', () => {
    renderPanel()
    expect(screen.getByText('File Type')).toBeInTheDocument()
    expect(screen.getByText('Created Date')).toBeInTheDocument()
    expect(screen.getByText('Sharing Scope')).toBeInTheDocument()
    expect(screen.getByText('Sort')).toBeInTheDocument()
  })

  it('emits the exact contentType when a File Type is selected', async () => {
    const { onChange } = renderPanel()
    await userEvent.click(screen.getByRole('button', { name: /pdf/i }))
    expect(onChange).toHaveBeenCalledWith({ contentType: 'application/pdf' })
  })

  it('clears the contentType when the active File Type is re-clicked', async () => {
    const { onChange } = renderPanel({ contentType: 'application/pdf' })
    await userEvent.click(screen.getByRole('button', { name: /pdf/i }))
    expect(onChange).toHaveBeenCalledWith({ contentType: undefined })
  })

  it('emits an ISO lower bound when a From date is set', async () => {
    const { onChange } = renderPanel()
    const fromInput = screen.getByLabelText('From')
    await userEvent.type(fromInput, '2023-10-20')
    expect(onChange).toHaveBeenLastCalledWith({ dateFrom: '2023-10-20T00:00:00Z' })
  })

  it('toggles a sharing scope off', async () => {
    const { onChange } = renderPanel()
    await userEvent.click(screen.getByLabelText('Personal'))
    expect(onChange).toHaveBeenCalledWith({
      sharingScope: [SharingScope.DEPARTMENT, SharingScope.ORGANIZATION],
    })
  })

  it('calls onClear from the Clear All button', async () => {
    const { onClear } = renderPanel()
    await userEvent.click(screen.getByRole('button', { name: /clear all/i }))
    expect(onClear).toHaveBeenCalledOnce()
  })
})
