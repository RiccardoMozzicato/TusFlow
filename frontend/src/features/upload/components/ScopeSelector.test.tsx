import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import ScopeSelector from './ScopeSelector'
import { SharingScope } from '@/features/documents/documentsApi'

function renderSelector(overrides = {}) {
  const props = {
    scope: SharingScope.PERSONAL,
    onScopeChange: vi.fn(),
    ...overrides,
  }
  const utils = render(<ScopeSelector {...props} />)
  return { ...utils, props }
}

describe('ScopeSelector', () => {
  it('emits the chosen sharing scope', async () => {
    const { props } = renderSelector()

    await userEvent.click(screen.getByRole('button', { name: 'Department' }))

    expect(props.onScopeChange).toHaveBeenCalledWith(SharingScope.DEPARTMENT)
  })

  it('only renders the scopes it is given', () => {
    renderSelector({
      scopes: [
        { value: SharingScope.PERSONAL, label: 'Personal' },
        { value: SharingScope.DEPARTMENT, label: 'Department' },
      ],
    })

    expect(screen.getByRole('button', { name: 'Personal' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Department' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Organization' })).not.toBeInTheDocument()
  })
})
