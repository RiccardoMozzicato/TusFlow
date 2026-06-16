import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import AiMetadataPanel from './AiMetadataPanel'

function renderPanel(overrides: Partial<Parameters<typeof AiMetadataPanel>[0]> = {}) {
  const onSave = vi.fn().mockResolvedValue(undefined)
  const utils = render(
    <AiMetadataPanel
      analysisStatus="completed"
      summary="A concise summary."
      sensitivity="—"
      tags={['Q3', '2023']}
      canEdit
      isSaving={false}
      saveError={null}
      onSave={onSave}
      {...overrides}
    />,
  )
  return { onSave, ...utils }
}

describe('AiMetadataPanel', () => {
  it('shows the AI summary and humanized sensitivity in read-only view', () => {
    renderPanel({ summary: 'Quarterly results.', sensitivity: 'HIGHLY_CONFIDENTIAL' })
    expect(screen.getByText(/Quarterly results\./)).toBeInTheDocument()
    expect(screen.getByText('Highly Confidential')).toBeInTheDocument()
  })

  it('shows the analysis indicator for the current status', () => {
    const { rerender } = render(
      <AiMetadataPanel
        analysisStatus="completed"
        summary="x"
        sensitivity="—"
        tags={[]}
        canEdit={false}
        isSaving={false}
        saveError={null}
        onSave={vi.fn()}
      />,
    )
    expect(screen.getByText('Analyzed')).toBeInTheDocument()

    rerender(
      <AiMetadataPanel
        analysisStatus="failed"
        summary="x"
        sensitivity="—"
        tags={[]}
        canEdit={false}
        isSaving={false}
        saveError={null}
        onSave={vi.fn()}
      />,
    )
    expect(screen.getByText('Analysis failed')).toBeInTheDocument()
  })

  it('hides the Edit affordance when the user cannot write', () => {
    renderPanel({ canEdit: false })
    expect(screen.queryByRole('button', { name: /edit/i })).toBeNull()
  })

  it('requires a sensitivity level before the edit can be saved', () => {
    renderPanel({ sensitivity: '—' })
    fireEvent.click(screen.getByRole('button', { name: /edit/i }))
    expect(screen.getByRole('button', { name: /save/i })).toBeDisabled()

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'CONFIDENTIAL' } })
    expect(screen.getByRole('button', { name: /save/i })).toBeEnabled()
  })

  it('submits the edited summary, sensitivity and parsed tags, then exits edit mode', async () => {
    const { onSave } = renderPanel({ summary: 'Old.', sensitivity: '—', tags: [] })
    fireEvent.click(screen.getByRole('button', { name: /edit/i }))

    fireEvent.change(screen.getByRole('textbox', { name: /summary/i }), {
      target: { value: '  New summary.  ' },
    })
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'CONFIDENTIAL' } })
    fireEvent.change(screen.getByRole('textbox', { name: /tags/i }), {
      target: { value: 'A, B ,C' },
    })
    fireEvent.click(screen.getByRole('button', { name: /save/i }))

    expect(onSave).toHaveBeenCalledWith({
      summary: 'New summary.',
      sensitivity: 'CONFIDENTIAL',
      tags: ['A', 'B', 'C'],
    })
    expect(await screen.findByRole('button', { name: /edit/i })).toBeInTheDocument()
  })

  it('surfaces a save error and keeps the form open', () => {
    renderPanel({ saveError: 'Access denied' })
    fireEvent.click(screen.getByRole('button', { name: /edit/i }))
    expect(screen.getByText('Access denied')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /save/i })).toBeInTheDocument()
  })
})
