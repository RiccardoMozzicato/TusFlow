import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import UserMetadataPanel from './UserMetadataPanel'
import type { MetadataEntry } from '../../types'

function renderPanel(overrides: Partial<Parameters<typeof UserMetadataPanel>[0]> = {}) {
  const onSave = vi.fn().mockResolvedValue(undefined)
  const utils = render(
    <UserMetadataPanel
      entries={[{ key: 'department', value: 'Finance' }]}
      canEdit
      isSaving={false}
      saveError={null}
      onSave={onSave}
      {...overrides}
    />,
  )
  return { onSave, ...utils }
}

describe('UserMetadataPanel', () => {
  it('renders each user metadata key and value in read-only view', () => {
    renderPanel({
      entries: [
        { key: 'department', value: 'Finance' },
        { key: 'project', value: 'Atlas' },
      ],
    })
    expect(screen.getByText('department')).toBeInTheDocument()
    expect(screen.getByText('Finance')).toBeInTheDocument()
    expect(screen.getByText('project')).toBeInTheDocument()
    expect(screen.getByText('Atlas')).toBeInTheDocument()
  })

  it('shows an empty state when there is no user metadata', () => {
    renderPanel({ entries: [] })
    expect(screen.getByText(/no user-added metadata/i)).toBeInTheDocument()
  })

  it('hides the Edit affordance when the user cannot write', () => {
    renderPanel({ canEdit: false })
    expect(screen.queryByRole('button', { name: /edit/i })).toBeNull()
  })

  it('saves an edited value and exits edit mode', async () => {
    const { onSave } = renderPanel({ entries: [{ key: 'department', value: 'Finance' }] })
    fireEvent.click(screen.getByRole('button', { name: /edit/i }))

    fireEvent.change(screen.getByLabelText('Metadata value'), {
      target: { value: 'Legal' },
    })
    fireEvent.click(screen.getByRole('button', { name: /save/i }))

    expect(onSave).toHaveBeenCalledWith({
      metadata: { department: 'Legal' },
      entries: [{ key: 'department', value: 'Legal' }],
    })
    expect(await screen.findByRole('button', { name: /edit/i })).toBeInTheDocument()
  })

  it('blanks a removed key in the patch so the merge-only backend drops it', async () => {
    const entries: MetadataEntry[] = [
      { key: 'a', value: '1' },
      { key: 'b', value: '2' },
    ]
    const { onSave } = renderPanel({ entries })
    fireEvent.click(screen.getByRole('button', { name: /edit/i }))

    // Remove the first field ("a").
    fireEvent.click(screen.getAllByRole('button', { name: /remove field/i })[0])
    fireEvent.click(screen.getByRole('button', { name: /save/i }))

    expect(onSave).toHaveBeenCalledWith({
      metadata: { b: '2', a: '' },
      entries: [{ key: 'b', value: '2' }],
    })
    await screen.findByRole('button', { name: /edit/i })
  })

  it('surfaces a save error and keeps the editor open', () => {
    renderPanel({ saveError: 'Access denied' })
    fireEvent.click(screen.getByRole('button', { name: /edit/i }))
    expect(screen.getByText('Access denied')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /save/i })).toBeInTheDocument()
  })
})
