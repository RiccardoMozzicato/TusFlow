import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import StagedScopeSelector from './StagedScopeSelector'
import { ALL_SCOPE_OPTIONS } from '../uploadScopes'
import { SharingScope } from '@/features/documents/documentsApi'

function renderSelector(overrides = {}) {
  const props = {
    scopes: ALL_SCOPE_OPTIONS,
    generalScope: SharingScope.PERSONAL,
    override: undefined as SharingScope | undefined,
    onChange: vi.fn(),
    ...overrides,
  }
  const utils = render(<StagedScopeSelector {...props} />)
  return { ...utils, props }
}

describe('StagedScopeSelector', () => {
  it('shows the inherited general scope on the Default pill and marks it active', () => {
    renderSelector({ generalScope: SharingScope.DEPARTMENT })

    const defaultPill = screen.getByRole('button', { name: /Default · Department/ })
    expect(defaultPill).toHaveAttribute('aria-pressed', 'true')
  })

  it('emits a scope when a specific pill is chosen', async () => {
    const { props } = renderSelector()

    await userEvent.click(screen.getByRole('button', { name: 'Organization' }))

    expect(props.onChange).toHaveBeenCalledWith(SharingScope.ORGANIZATION)
  })

  it('clears the override when Default is chosen', async () => {
    const { props } = renderSelector({ override: SharingScope.ORGANIZATION })

    await userEvent.click(screen.getByRole('button', { name: /Default/ }))

    expect(props.onChange).toHaveBeenCalledWith(undefined)
  })

  it('marks the active override pill as pressed', () => {
    renderSelector({ override: SharingScope.ORGANIZATION })

    expect(screen.getByRole('button', { name: 'Organization' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(screen.getByRole('button', { name: /Default/ })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
  })
})
